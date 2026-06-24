# Load Test Plan

## 1. 목표

정상 부하, 피크 부하, Consumer 장애, Redis 장애를 재현하고 API 응답성과 비동기 탐지 지연을 분리해서 측정합니다.

## 2. 시나리오

## Implemented in Phase 12

Phase 12에서 실제 k6 script로 정리한 시나리오는 아래 4개입니다.

- `normal-load`
- `peak-load`
- `duplicate-replay`
- `redis-down-load`

## Follow-up Scenarios

아래 항목은 부하/장애 검증 후보로 남아 있지만, Phase 12 script 산출물에는 포함하지 않습니다.

- `consumer-lag-test`
- `hot-partition-test`
- `invalid-schema-test`

### normal-load

일반적인 거래 이벤트 유입을 재현합니다.

Target API:

- `POST /api/v1/transactions/events`

측정:

- API p50/p95/p99
- Kafka publish success rate
- Consumer processing duration
- fraud detection latency
- duplicate result count

### peak-load

짧은 시간 동안 대량 이벤트가 몰리는 상황을 재현합니다.

Target API:

- `POST /api/v1/transactions/events`

측정:

- Consumer Lag 최대값
- Lag 회복 시간
- DLQ count
- API error rate
- Kafka publish failure count

### consumer-lag-test

Consumer 중단 후 재시작 상황을 재현합니다.

측정:

- Consumer 중단 시간
- 재시작 후 미처리 이벤트 재소비 여부
- 중복 FraudResult 생성 건수
- `GET /api/v1/admin/events/{eventId}/processing-log` 조회 가능 여부
- `GET /api/v1/admin/fraud-results/{eventId}` 조회 가능 여부

### redis-down-test

Redis 장애 상태에서 degraded mode가 동작하는지 확인합니다.

측정:

- Redis 장애 중 처리된 이벤트 수
- `degraded=true` 탐지 결과 수
- Redis 기반 rule skipped count
- `GET /api/v1/admin/fraud-results?degraded=true` 조회 결과

### hot-partition-test

특정 `userId`에 이벤트를 집중시켜 hot partition과 Consumer Lag 증가를 재현합니다.

측정:

- userId key 사용 여부
- partition별 lag 차이
- Lag 회복 시간
- API latency와 detection latency 분리

### invalid-schema-test

지원하지 않는 `schemaVersion` 또는 invalid payload가 DLT로 이동하는지 확인합니다.

측정:

- DLT count
- `GET /api/v1/admin/dlq-events` 조회 결과
- 재처리 또는 폐기 이력
- raw payload가 admin API에 노출되지 않는지 여부

## 3. 산출물

테스트 결과는 이 문서에 표로 추가합니다. 초기 설계값과 실제 측정값이 다르면 변경 이유를 `docs/11-troubleshooting-log.md`에 기록합니다.

결과 기록 기준:

- scenario name
- VU count
- duration
- event count
- local hardware/environment notes
- API p50/p95/p99
- Consumer Lag max
- Lag recovery time
- detection latency p95/p99
- error rate
- DLQ count
- Redis degraded count
- duplicate result count

## Phase 12 k6 Scenarios

Phase 12는 k6 script와 결과 기록 체계를 추가하고, 실제 측정값은 `docs/22-load-test-results.md`에 분리해 남깁니다. 원시 JSON/CSV 결과 파일은 `load-test/k6/results/`에 임시 저장하되 git에는 커밋하지 않습니다.

| Scenario | Command | Purpose | Expected |
| --- | --- | --- | --- |
| Normal Load | `make k6-normal` | 일반 이벤트 유입 검증 | p95 목표 이내, error rate 낮음 |
| Peak Load | `make k6-peak` | 순간 유입 증가 검증 | latency 상승 관찰, 5xx 원인 기록 |
| Duplicate Replay | `make k6-duplicate` | 중복 eventId 방어 검증 | fraud result count = 1 |
| Redis Down Load | `make k6-redis-down` | degraded mode 검증 | degraded metric 증가, Consumer 유지 |

해석 기준:

- Normal/Peak는 `http_req_failed`, p50/p95/p99, status code 분포를 함께 봅니다.
- Duplicate Replay는 409 또는 duplicate response가 의도된 결과일 수 있으므로 k6 failure rate만으로 성공/실패를 판단하지 않습니다.
- Duplicate Replay 이후 `scripts/load_tests/check_duplicate_result_count.sh <eventId>`로 fraud result count를 확인할 수 있습니다.
- Redis Down Load는 Redis container가 반드시 복구되어야 하며, script가 출력하는 `fraud_redis_window_degraded_total`, `fraud_detection_degraded_total`, `fraud_rule_skipped_total` before/after 값을 함께 확인합니다.
- Consumer Lag과 Grafana dashboard evidence는 후속 Observability hardening 범위로 둡니다.
