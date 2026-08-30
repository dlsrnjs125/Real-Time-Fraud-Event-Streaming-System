# Redis Sliding Window

## 1. Redis 역할

Redis는 사용자별 최근 거래 패턴을 빠르게 계산하기 위한 단기 상태 저장소입니다. 최종 탐지 결과나 감사 기준 데이터는 PostgreSQL에 저장합니다.

## 2. Sliding Window 방식

사용자별 ZSET과 이벤트별 Hash를 함께 사용합니다.

- User window key: `fraud:tx:{namespace}:user:{userId}:events`
- User window type: Sorted Set
- Score: `eventTime` epoch milliseconds
- Member: `eventId`
- Event metadata key: `fraud:tx:{namespace}:event:{eventId}`
- Event metadata type: Hash
- Hash fields: `amount`, `currency`, `eventTime`, `userId`

처리 흐름:

1. PostgreSQL fraud result가 이미 존재하는 `eventId`이면 Redis window를 갱신하지 않고 duplicate 처리합니다.
2. `receivedAt - eventTime`이 `allowed-lateness`를 초과하면 Redis window를 갱신하지 않고 Redis 기반 rule을 skipped 처리합니다.
3. 이벤트별 Hash에 amount/userId/eventTime을 저장합니다.
4. 현재 거래 이벤트를 ZSET에 추가합니다.
5. window 범위 밖의 오래된 이벤트를 제거합니다.
6. window 내부 이벤트 수와 누적 금액을 계산합니다.
7. 기준 횟수 또는 누적 금액을 초과하면 stateful rule을 매칭합니다.

Hash metadata가 없는 ZSET member는 부분 실패로 남은 불완전 데이터로 보고 count와 amount sum에서 제외합니다. Phase 6에서는 Redis Lua나 transaction을 사용하지 않으므로, 부분 실패는 degraded 또는 유효 metadata 기준 계산으로 완화합니다.

Phase 6 기본 기준:

- window: 5 minutes
- TTL: 10 minutes
- allowed lateness: 5 minutes
- namespace: `live`
- `RAPID_TRANSACTION_COUNT`: 최근 5분 내 5건 이상, +30
- `WINDOW_AMOUNT_SUM`: 최근 5분 누적 3,000,000 KRW 이상, +40

`namespace`는 Redis DB index가 아니라 key prefix입니다. V3 Phase 7 Historical Replay는 replay Consumer를 `FRAUD_SLIDING_WINDOW_NAMESPACE=replay`로 실행해 historical event가 live 사용자 window에 섞이지 않도록 합니다.

## 3. INCR + TTL을 기본으로 쓰지 않는 이유

INCR + TTL 방식은 구현이 단순하지만 고정 윈도우 경계에서 탐지 정확도가 흔들릴 수 있습니다. ZSET 기반 sliding window는 최근 N초 기준을 더 정확하게 계산할 수 있습니다.

## 4. Redis 장애 모드

### NORMAL

- Redis 사용 가능
- 모든 rule 수행

### DEGRADED

- Redis 사용 불가
- amount rule 등 단건 기반 rule만 수행
- Redis 기반 rule은 `skipped_rules`에 기록
- `fraud_detection_results.degraded=true` 저장
- reason에 Redis degraded mode를 남김

Redis 장애만으로 Consumer ack를 막지 않습니다. Redis는 최종 정합성 저장소가 아니라 최근 거래 패턴 계산을 위한 보조 상태 저장소이기 때문입니다. 최종 결과 저장과 중복 방어는 PostgreSQL `fraud_detection_results.event_id` unique constraint가 담당합니다.

Redis timeout은 Consumer thread가 Redis 장애에 오래 묶이지 않도록 `500ms`로 짧게 설정했습니다. timeout이 발생하면 Redis 기반 rule은 skipped 처리하고, PostgreSQL fraud result 저장과 Kafka ack는 계속 진행합니다.

## 5. 측정 항목

- `fraud.redis.window.record.latency`: Redis window record/get 처리 시간
- `fraud.redis.window.degraded.total`: Redis 장애 중 degraded window result 횟수
- `fraud.redis.window.too_late.total`: allowed-lateness 초과로 Redis live-state 갱신을 건너뛴 이벤트 수
- `fraud.redis.window.event.count`: Redis window 안에 남은 유효 이벤트 수 분포
- `fraud.redis.window.amount.sum`: Redis window 안에 남은 유효 거래 금액 합계 분포
- `fraud.detection.degraded.total`: `degraded=true` fraud result 저장 횟수
- `fraud.rule.skipped.total{rule=...}`: Redis 기반 rule skipped count

Redis command latency는 `RecentTransactionWindowStore.recordAndGetWindow` 호출 전체를 기준으로 측정합니다. 이 위치는 Hash 저장, ZSET 갱신, cleanup, TTL, window 조회를 모두 포함하므로 Consumer가 Redis 때문에 얼마나 묶이는지 확인하기에 적합합니다.

Window count/amount metrics는 state-size experiment용 분포 신호입니다. `userId`, `eventId`, `traceId`, Kafka offset 같은 high-cardinality tag는 붙이지 않습니다.

## 6. TTL and Cleanup

- 각 이벤트 처리 시 `ZREMRANGEBYSCORE`로 window 범위 밖 이벤트를 제거합니다.
- ZSET key에는 window보다 긴 TTL을 둡니다.
- 예: window = 5분, key TTL = 10분
- TTL은 사용자가 더 이상 거래하지 않을 때 Redis key가 무기한 남지 않도록 하기 위한 메모리 보호 장치입니다.

같은 `eventId`가 재처리될 때 fraud result가 이미 있으면 Redis window를 갱신하지 않는 fast path로 ack합니다. 아직 fraud result가 없는 재소비에서는 ZSET member도 같은 `eventId`이므로 Redis count 중복 증가를 완화합니다. 단, Redis 상태는 보조 상태일 뿐이므로 최종 중복 방어 기준으로 사용하지 않습니다.

V3 Phase 4 redelivery drill은 이 동작을 명시적으로 검증합니다. Redis update 이후 fraud result 저장 전에 실패하면 같은 Kafka record가 다시 전달될 수 있습니다. 이때 ZSET member는 `eventId`라서 동일 이벤트가 같은 사용자 window에 두 번 count되지 않아야 합니다. Fraud result 저장 이후 ack 전에 실패하면 다음 전달은 result duplicate precheck에서 Redis를 다시 갱신하지 않아야 합니다.

## 7. Clock Skew 기준

`eventTime`이 `receivedAt`보다 과도하게 미래인 경우 Redis window 계산이 왜곡될 수 있습니다.

허용 가능한 clock skew를 초과하면 validation failure 또는 DLT 대상으로 분류합니다. 이 기준은 `docs/10-failure-scenarios.md`의 Future eventTime 시나리오와 함께 검증합니다.

## 8. Late and Out-of-Order 기준

V3 Phase 5부터 `fraud.sliding-window.allowed-lateness`를 기준으로 live Redis state 갱신 여부를 결정합니다.

- `receivedAt - eventTime <= allowed-lateness`: Hash와 ZSET을 갱신하고 Redis 기반 rule을 정상 평가합니다.
- `receivedAt - eventTime == allowed-lateness`: boundary late로 보고 정상 평가합니다.
- `receivedAt - eventTime > allowed-lateness`: Hash/ZSET mutation을 수행하지 않고 Redis 기반 rule을 skipped 처리합니다.

허용 지연 안의 out-of-order 이벤트는 도착 순서가 아니라 `eventTime` epoch milliseconds를 ZSET score로 저장합니다. 해당 이벤트의 반환 window는 `[eventTime - window, eventTime]` 범위로 계산합니다.

too-late 이벤트는 Redis 인프라 장애가 아니므로 `fraud.redis.window.degraded.total` 대신 `fraud.redis.window.too_late.total`로 원인을 분리합니다. Rule evaluation에서는 Redis 기반 rule을 실행하지 않아야 하므로 `RecentTransactionWindowStatus.TOO_LATE` 상태를 사용해 skipped rule을 남기고, fraud result reason에는 `Freshness policy skip`을 기록합니다.

## 9. Phase 7 Integration Test 검증 항목

Phase 7에서는 Testcontainers 대신 Docker Compose Redis를 사용하는 별도 integration test target으로 실제 Redis 자료구조 기준 검증을 분리했습니다. Redis integration test는 테스트 전용 Redis database index `15`를 사용하며, 테스트 시작 전 해당 DB만 초기화합니다.

- ZSET `fraud:tx:{namespace}:user:{userId}:events`에 eventId member 저장
- Hash `fraud:tx:{namespace}:event:{eventId}`에 amount/currency/eventTime/userId 저장
- 같은 eventId 재기록 시 ZSET count 중복 증가 없음
- eventTime 기준 window 밖 이벤트 cleanup
- user window와 event metadata key TTL 설정
- Hash metadata 없는 ZSET member를 count/sum에서 제외
- eventTime 기준 window 조회

Testcontainers 기반 검증도 시도했지만 로컬 Docker Desktop provider API 호환 문제로 실패했습니다. Phase 7에서는 기본 CI와 분리된 `make redis-integration-test`가 Docker Compose Redis를 띄우고 readiness 확인 후 integration test를 실행하는 방식으로 검증했습니다.
