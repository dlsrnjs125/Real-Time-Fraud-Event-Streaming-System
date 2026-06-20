# Redis Sliding Window

## 1. Redis 역할

Redis는 사용자별 최근 거래 패턴을 빠르게 계산하기 위한 단기 상태 저장소입니다. 최종 탐지 결과나 감사 기준 데이터는 PostgreSQL에 저장합니다.

## 2. Sliding Window 방식

사용자별 ZSET과 이벤트별 Hash를 함께 사용합니다.

- User window key: `fraud:tx:user:{userId}:events`
- User window type: Sorted Set
- Score: `eventTime` epoch milliseconds
- Member: `eventId`
- Event metadata key: `fraud:tx:event:{eventId}`
- Event metadata type: Hash
- Hash fields: `amount`, `currency`, `eventTime`, `userId`

처리 흐름:

1. 현재 거래 이벤트를 ZSET에 추가합니다.
2. window 범위 밖의 오래된 이벤트를 제거합니다.
3. 이벤트별 Hash에 amount/userId/eventTime을 저장합니다.
4. window 범위 밖의 오래된 이벤트를 제거합니다.
5. window 내부 이벤트 수와 누적 금액을 계산합니다.
6. 기준 횟수 또는 누적 금액을 초과하면 stateful rule을 매칭합니다.

Phase 6 기본 기준:

- window: 5 minutes
- TTL: 10 minutes
- `RAPID_TRANSACTION_COUNT`: 최근 5분 내 5건 이상, +30
- `WINDOW_AMOUNT_SUM`: 최근 5분 누적 3,000,000 KRW 이상, +40

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

## 5. 측정 항목

- Redis command latency
- Redis 장애 중 처리된 이벤트 수
- `degraded=true` 탐지 결과 수
- Redis 기반 rule skipped count

## 6. TTL and Cleanup

- 각 이벤트 처리 시 `ZREMRANGEBYSCORE`로 window 범위 밖 이벤트를 제거합니다.
- ZSET key에는 window보다 긴 TTL을 둡니다.
- 예: window = 5분, key TTL = 10분
- TTL은 사용자가 더 이상 거래하지 않을 때 Redis key가 무기한 남지 않도록 하기 위한 메모리 보호 장치입니다.

같은 `eventId`가 재처리될 때 ZSET member도 같은 `eventId`이므로 Redis count가 중복 증가하지 않습니다. Hash metadata도 같은 key에 덮어쓰기 때문에 Redis 내부 중복은 완화됩니다. 단, Redis 상태는 보조 상태일 뿐이므로 최종 중복 방어 기준으로 사용하지 않습니다.

## 7. Clock Skew 기준

`eventTime`이 `receivedAt`보다 과도하게 미래인 경우 Redis window 계산이 왜곡될 수 있습니다.

허용 가능한 clock skew를 초과하면 validation failure 또는 DLT 대상으로 분류합니다. 이 기준은 `docs/10-failure-scenarios.md`의 Future eventTime 시나리오와 함께 검증합니다.
