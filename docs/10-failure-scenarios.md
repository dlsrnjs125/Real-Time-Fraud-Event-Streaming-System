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
