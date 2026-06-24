# SLO and Operational Readiness

## 1. 목표

이 문서는 초기 구현이 달성해야 할 수치를 확정하기보다, 무엇을 어떤 기준으로 측정할지 먼저 정의합니다.

API가 빠르게 응답하더라도 Consumer Lag과 detection latency가 커지면 이상거래 탐지는 실시간성을 잃습니다. 따라서 API 지표와 Consumer 지표를 함께 봅니다.

## 2. 초기 SLO 후보

| 항목 | 초기 목표 | 의미 |
|---|---:|---|
| API p95 latency | <= 300ms | 거래 이벤트 접수 응답성 |
| API p99 latency | <= 800ms | tail latency 확인 |
| Kafka publish success rate | >= 99.5% | 이벤트 발행 안정성 |
| Consumer processing latency p95 | <= 1s | Consumer 단건 처리 시간 |
| Fraud detection latency p95 | <= 2s | 접수부터 탐지 완료까지 지연 |
| Consumer Lag recovery time | <= 3min | Consumer 재시작 후 Lag 회복 시간 |
| DLQ event loss | 0 | 실패 이벤트 추적 가능성 |
| Duplicate FraudResult count | 0 | 재처리 idempotency |
| Redis degraded result count | observable | Redis 장애 영향 관측 가능성 |

## 3. 측정 기준

- API latency: `app-api` HTTP server metric
- Kafka publish success rate: producer success/failure count
- Consumer processing latency: Kafka message 처리 시작부터 완료까지
- Fraud detection latency: `detectedAt - receivedAt`
- End-to-end latency: `detectedAt - eventTime`
- Consumer Lag recovery time: Consumer 재시작 시점부터 Lag 정상화까지
- DLQ event loss: 실패 이벤트와 DLQ 저장 건수 비교
- Duplicate FraudResult count: unique constraint conflict와 duplicate skip count
- Redis degraded result count: `degraded=true` FraudResult count

API별 확인 기준:

| API | 확인 항목 |
|---|---|
| `POST /api/v1/transactions/events` | API latency, validation error rate, Kafka publish success/failure |
| `GET /api/v1/transactions/events/{eventId}` | receipt 저장 여부, `receivedAt`, trace 연결 |
| `GET /api/v1/admin/fraud-results` | riskLevel/degraded/ruleCode 조회 가능 여부 |
| `GET /api/v1/admin/fraud-results/{eventId}` | ruleResults, skippedRuleCodes, detection latency |
| `GET /api/v1/admin/dlq-events` | DLQ count, raw payload 미노출 |
| `POST /api/v1/admin/dlq-events/{dlqId}/reprocess` | 원본 eventId 보존, 중복 FraudResult 미생성 |
| `GET /api/v1/admin/events/{eventId}/processing-log` | topic/partition/offset/status 추적 |

## 4. Operational Readiness Checklist

Phase별 기능 구현 후 아래 항목을 확인합니다.

- `docker compose -f infra/docker-compose.yml config` 통과
- `./gradlew test` 통과
- `bash -n scripts/*.sh` 통과
- Kafka topic 생성 스크립트 재실행 가능
- `/actuator/health` 확인 가능
- `/actuator/prometheus` 확인 가능
- DLQ 이벤트 조회 가능
- DLQ 재처리 이력 조회 가능
- Consumer 재시작 후 미처리 이벤트 재소비 확인
- Redis 장애 시 degraded mode 확인
- 중복 `eventId` 재처리 시 FraudResult 중복 미생성

## 5. Exit Criteria

각 Phase는 코드 작성 여부가 아니라, 검증 명령과 증빙이 남았는지를 기준으로 완료 처리합니다.

완료 처리 기준:

- 실행한 검증 명령이 문서에 남아 있음
- 실패한 검증이 있다면 원인과 후속 작업이 남아 있음
- 관련 README/docs가 구현 상태와 일치함
- 주요 metric 또는 로그로 동작을 확인할 수 있음
- 남은 한계가 명시되어 있음

## 6. Alert 후보

초기에는 알림 시스템을 구현하지 않지만, 관측 기준은 다음과 같이 둡니다.

- API error rate 급증
- Kafka publish failure 발생
- Consumer Lag 지속 증가
- DLQ count 증가
- Redis degraded count 증가
- Fraud detection latency p95 목표 초과
- Duplicate FraudResult conflict 발생

## 7. Dashboard 구성

Prometheus/Grafana dashboard는 최소 4개 관점으로 나눕니다.

### API Dashboard

- API request count
- API p50/p95/p99 latency
- API error rate
- Kafka publish success/failure

### Consumer Dashboard

- Consumed event count
- Consumer processing latency
- Fraud detection latency
- Consumer Lag
- Retry count
- DLT count
- Duplicate skip count

### Redis/PostgreSQL Dashboard

- Redis command latency
- Redis error count
- Redis degraded count
- PostgreSQL connection pool usage
- DB insert latency
- DB constraint violation count

### Fraud Detection Dashboard

- RiskLevel count
- Rule matched count
- Rule skipped count
- High risk event count
- Degraded fraud result count
- False-positive review count, if implemented

## 8. Known Limits

초기 SLO는 로컬 개발과 부하 재현 기준입니다. 운영 환경의 실제 SLO는 트래픽 규모, partition 수, Consumer 수, DB/Redis 리소스, 네트워크 조건에 따라 다시 산정합니다.

## Phase 12 Performance Target

Phase 12는 목표와 실제 결과를 분리해서 기록합니다. 아래 Actual 값은 `docs/22-load-test-results.md`에 측정 evidence가 남기 전까지 `TBD`로 둡니다.

| Metric | Target | Actual | Notes |
| --- | ---: | ---: | --- |
| Normal load p95 | < 500ms | TBD | Local Docker Compose |
| Normal load error rate | < 1% | TBD | Local Docker Compose |
| Peak load p95 | < 1000ms | TBD | Local Docker Compose |
| Peak load error rate | < 5% | TBD | Local Docker Compose |
| Duplicate replay consistency | fraud result count = 1 | TBD | PostgreSQL unique constraint 기준 |
| Redis down processing | degraded result stored | TBD | Redis-dependent rules skipped |

부하 테스트 결과는 로컬 장비, Docker resource limit, JVM warmup, Kafka partition 상태, Consumer 실행 여부에 영향을 받습니다. 따라서 결과는 병목 후보를 설명하기 위한 evidence로 사용하고 절대 성능 수치로 일반화하지 않습니다.

## Phase 13 Performance Target

Phase 13은 목표와 실제 결과를 분리해서 기록합니다. 아래 Actual 값은 `docs/23-load-test-results.md`에 측정 evidence가 남기 전까지 `TBD`로 둡니다.

| Metric | Target | Actual | Notes |
| --- | ---: | ---: | --- |
| Normal load p95 | < 500ms | TBD | Local Docker Compose |
| Normal load error rate | < 1% | TBD | Local Docker Compose |
| Peak load p95 | < 1000ms | TBD | Local Docker Compose |
| Peak load error rate | < 5% | TBD | Local Docker Compose |
| Duplicate replay consistency | fraud result count = 1 | TBD | PostgreSQL unique constraint 기준 |
| Redis down processing | degraded result stored | TBD | Redis-dependent rules skipped |

Phase 13 결과는 운영 SLO 확정값이 아닙니다. 로컬 장비, Docker resource limit, JVM warmup, Kafka partition 상태, Consumer 실행 여부에 따라 달라지므로 병목 후보와 후속 개선 방향을 설명하는 evidence로만 사용합니다.
