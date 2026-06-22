# Failure Scenarios

## 1. Consumer 장애

상황:

- `app-consumer` 프로세스 중단
- `transaction-events`에 이벤트 계속 유입

기대 결과:

- Kafka topic에 이벤트 보존
- Consumer Lag 증가
- Consumer 재시작 후 마지막 commit 이후 메시지 재소비
- 중복 FraudResult 미생성

## 2. PostgreSQL 일시 장애

상황:

- 탐지 결과 저장 시 DB connection 오류

기대 결과:

- retry topic으로 이동
- backoff 이후 재처리
- 재시도 초과 시 DLT 이동

## 3. Redis 장애

상황:

- Redis timeout 또는 connection 실패

기대 결과:

- DEGRADED mode 전환
- 단건 기반 rule만 수행
- Redis 기반 rule은 skipped 기록
- `fraud_result.degraded=true` 저장

## 4. 잘못된 payload

상황:

- 필수 필드 누락 또는 역직렬화 불가능한 이벤트

기대 결과:

- 자동 재시도보다 DLT 보관 우선
- 운영자 API에서 실패 원인 조회 가능
- 재처리 불가능하면 `FAILED_PERMANENT` 또는 `DISCARDED`

## 5. Hot Partition

상황:

- 특정 `userId`에 이벤트 집중

기대 결과:

- 해당 partition lag 증가 관측
- 사용자별 순서 보장은 유지
- partition별 lag 편차를 측정해 key 전략의 trade-off 기록

## 6. Kafka Publish Timeout

상황:

- `app-api`가 Kafka publish 중 timeout 발생

기대 결과:

- Kafka publish 성공 전에는 `ACCEPTED`를 반환하지 않음
- 실패 응답과 traceId 기록
- publish failure metric 증가

## 7. Consumer Rebalance 중 중복 처리

상황:

- Consumer scale out 또는 재시작 중 partition rebalance 발생

기대 결과:

- 같은 Kafka message가 다시 소비될 수 있음을 허용
- `eventId` unique constraint로 중복 FraudResult 방어
- duplicate 처리 로그 기록

## 8. DLT 재처리 중 중복 FraudResult 생성 시도

상황:

- 이미 처리된 `eventId`를 가진 DLT 이벤트 재처리

기대 결과:

- 중복 FraudResult 생성 실패 또는 skip
- reprocessing_history에 duplicate result 기록
- 운영자가 결과를 조회 가능

## 9. Redis 복구 직후 Stale Window 데이터

상황:

- Redis 장애 이후 복구되었지만 오래된 window 데이터가 남아 있음

기대 결과:

- eventTime 기준으로 window 밖 데이터를 제거
- stale 데이터가 velocity rule을 오탐하지 않도록 방어
- Redis command latency와 stale cleanup count 관측

## 10. PostgreSQL Unique Constraint Conflict

상황:

- 같은 `eventId` 또는 같은 `topic, partition, offset` 처리 로그 저장 시도

기대 결과:

- 중복 결과를 생성하지 않음
- duplicate 처리로 분류
- DLQ 이동 여부는 실패 원인에 따라 결정

## 11. Observability Stack 장애

상황:

- Prometheus 또는 Grafana 장애로 metric 확인 불가

기대 결과:

- 애플리케이션 처리 자체는 계속 수행
- 구조화 로그로 최소한의 추적 가능
- metric backend 장애를 비즈니스 처리 실패로 취급하지 않음

## 12. Invalid schemaVersion 이벤트

상황:

- Consumer가 지원하지 않는 `schemaVersion` 이벤트 유입

기대 결과:

- 임의 변환 없이 DLT로 이동
- failure_reason에 unsupported schema version 기록
- schema 호환성 변경은 문서와 테스트를 함께 수정

## 13. Future eventTime 이벤트

상황:

- `eventTime`이 `receivedAt`보다 미래인 이벤트 유입

기대 결과:

- 허용 가능한 clock skew 범위를 초과하면 validation 실패 또는 DLT 이동
- ingest_delay 계산에서 음수 지연이 조용히 기록되지 않음
- 원인 분석을 위해 traceId와 eventId 기록

## 14. Phase 8 Drill Scenarios

### Redis Down During Detection

- 장애 상황: Redis container 중지 중 transaction event 처리
- 예상 원인: Redis 장애, 네트워크 단절, Redis timeout
- 사용자 영향: Redis 기반 velocity/window amount rule이 skipped 되고 탐지 민감도가 낮아질 수 있음
- 탐지 방법: fraud result `degraded=true`, `skippedRules`, `fraud_redis_window_degraded_total` 증가
- 대응 방법: Redis 복구, degraded metric 추세 확인, 복구 후 신규 이벤트 `degraded=false` 확인
- 재발 방지: Redis health/latency alert, 장애 drill 정기 실행
- README에 기록할 문장: Redis 장애는 전체 Consumer 실패가 아니라 degraded mode로 처리하고 evidence를 남긴다.

### Consumer Stopped While Events Are Produced

- 장애 상황: app-consumer 중지 중 app-api가 transaction event를 Kafka에 publish
- 예상 원인: Consumer 배포, 프로세스 장애, 로컬 재시작
- 사용자 영향: API 접수는 가능하지만 fraud result 생성이 Consumer 재시작까지 지연됨
- 탐지 방법: Consumer health down, fraud result 미생성, Kafka Lag 증가
- 대응 방법: Consumer 재시작 후 fraud result와 processing log 확인
- 재발 방지: Consumer restart drill, lag alert, graceful shutdown 점검
- README에 기록할 문장: Consumer 재시작 후 Kafka에 남은 메시지를 재처리하고 DB unique constraint로 중복 결과를 방어한다.

### Kafka Broker Unavailable During API Publish

- 장애 상황: Kafka broker 중지 중 app-api가 이벤트 publish 시도
- 예상 원인: Kafka container 장애, broker 재시작, 네트워크 단절
- 사용자 영향: API는 publish 성공으로 응답하지 않아야 하며 접수 실패가 명확히 드러나야 함
- 탐지 방법: API non-2xx 응답, 로컬 기준 `503`, publish failure log, Kafka health failure
- 대응 방법: Kafka 복구, topic 상태 확인, 복구 후 신규 이벤트 publish 검증
- 재발 방지: producer timeout/backoff 정책, Retry/DLT Phase에서 보완
- README에 기록할 문장: Kafka publish 성공 전에는 거래 이벤트 접수를 성공으로 간주하지 않는다.

### Fraud Result Duplicate Replay

- 장애 상황: 같은 Kafka message 또는 같은 `eventId`가 재처리됨
- 예상 원인: ack 직전 Consumer 장애, rebalance, manual replay
- 사용자 영향: 중복 fraud result가 생성되면 운영 조회와 지표가 왜곡됨
- 탐지 방법: `fraud_detection_results.event_id` unique constraint, duplicate log, eventId 조회
- 대응 방법: duplicate result는 idempotent 성공으로 처리하고 ack 가능하게 유지
- 재발 방지: unique constraint 유지, duplicate path test, reprocessing drill
- README에 기록할 문장: 최종 중복 방어는 PostgreSQL `fraud_detection_results.event_id` unique constraint가 담당한다.

### Prometheus Metric Endpoint Unavailable

- 장애 상황: `/actuator/prometheus` 또는 Prometheus scrape 실패
- 예상 원인: app-consumer down, management endpoint 장애, Prometheus 장애
- 사용자 영향: 비즈니스 처리는 계속될 수 있지만 degraded trend와 alert 확인이 어려움
- 탐지 방법: actuator endpoint failure, Prometheus target down
- 대응 방법: structured log와 admin API로 단건 evidence 확인, metric backend 복구
- 재발 방지: Prometheus target alert, actuator endpoint 제한 점검
- README에 기록할 문장: metric 장애는 처리 실패와 분리하고, API/log evidence를 함께 확인한다.

### PostgreSQL Unavailable During Result Save

- 장애 상황: Consumer가 fraud result 저장 중 DB connection failure 발생
- 예상 원인: PostgreSQL 중지, connection pool 고갈, network issue
- 사용자 영향: fraud result 저장 실패 시 ack가 호출되지 않아 Kafka 재소비 대상이 됨
- 탐지 방법: Consumer error log, DB health failure, fraud result 미생성
- 대응 방법: PostgreSQL 복구 후 Consumer 재처리 확인
- 재발 방지: DB health/connection pool metric, Retry/DLT Phase에서 transient failure 정책 보강
- README에 기록할 문장: DB 저장 성공 전 offset commit을 하지 않아 silent event loss를 피한다.
