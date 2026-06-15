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
