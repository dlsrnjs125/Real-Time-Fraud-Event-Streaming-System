# Observability

## 1. 관측 목표

API가 빠르게 응답하더라도 Consumer Lag이 계속 증가하면 위험 거래 탐지가 늦어집니다. 따라서 API latency뿐 아니라 Consumer Lag, detection latency, DLQ count를 함께 봅니다.

## 2. 핵심 지표

접수 안정성:

- API error rate
- Kafka publish success rate
- API p50/p95/p99 latency

탐지 실시간성:

- Consumer Lag
- `consumer.processing.duration`
- `fraud.detection.duration`

처리 신뢰성:

- DLQ count
- duplicate result count
- missing event count
- Redis degraded count

지연 시간 계산:

- `ingest_delay = receivedAt - eventTime`
- `detection_latency = detectedAt - receivedAt`
- `end_to_end_latency = detectedAt - eventTime`

## 3. Actuator와 Prometheus

`app-api`와 `app-consumer`는 Spring Boot Actuator와 Micrometer Prometheus registry를 통해 `/actuator/prometheus` endpoint를 노출합니다.

Prometheus는 두 애플리케이션과 Kafka 관련 metric을 scrape하고, Grafana dashboard에서 API/Consumer/Kafka/Redis 상태를 확인합니다.

## 4. 현재 구현된 Metrics

Phase 7부터 app-consumer는 Redis Sliding Window와 degraded mode를 관측하기 위한 metric foundation을 제공합니다.

| Metric | Type | Purpose |
|---|---|---|
| `fraud.redis.window.record.latency` | Timer | Redis window record/get 처리 시간 |
| `fraud.redis.window.degraded.total` | Counter | Redis unavailable로 degraded window result를 반환한 횟수 |
| `fraud.rule.skipped.total` | Counter | degraded mode로 skipped 처리된 rule 수 |
| `fraud.detection.degraded.total` | Counter | `degraded=true` fraud result 저장 횟수 |

Prometheus scrape에서는 dot format metric 이름이 snake_case 형태로 노출될 수 있습니다.
Timer인 `fraud.redis.window.record.latency`는 Prometheus에서 `fraud_redis_window_record_latency_seconds_count`, `fraud_redis_window_record_latency_seconds_sum`, `fraud_redis_window_record_latency_seconds_max`처럼 `_seconds_*` suffix가 붙은 시계열로 노출될 수 있습니다.

Metric tag에는 `eventId`, `traceId`, `userId`, `accountId`를 넣지 않습니다. 이 값들은 cardinality가 높아 Prometheus 저장 비용과 query 성능에 악영향을 줄 수 있고, 운영 환경에서는 식별자 노출 위험도 있습니다. 개별 이벤트 추적은 structured log의 `traceId`/`eventId`로 수행하고, metric은 `rule`, `mode`, `result` 수준의 낮은 cardinality tag만 사용합니다.

`fraud.redis.window.degraded.total`은 Redis window store가 degraded result를 반환한 횟수이고, `fraud.detection.degraded.total`은 `degraded=true` fraud result가 신규 저장된 횟수입니다. Detection degraded/skipped rule metric은 fraud result가 신규 저장된 경우에만 증가시키고, duplicate result path에서는 증가시키지 않습니다.

`fraud.redis.window.degraded.total`, `fraud.rule.skipped.total`, `fraud.detection.degraded.total`은 Redis 장애나 timeout이 탐지 민감도 저하로 이어지는지 판단하는 alert 후보입니다. 실제 alert threshold는 Redis down failure scenario와 부하 테스트 결과를 보고 정합니다.

## 4-1. 후속 Observability 후보

아래 지표는 운영 준비도 판단에 필요하지만, 현재 구현 metric과 분리해 후속 관측성 고도화 범위로 둡니다.

| Candidate | Purpose |
|---|---|
| DLT pending count | 재처리 대기 이벤트 증가 감지 |
| DLT reprocess failed count | 재처리 실패 반복 감지 |
| DLT discarded count | 폐기 이벤트 추세 확인 |
| Kafka consumer lag | 비동기 탐지 지연과 backlog 확인 |
| DB insert latency | fraud result, processing log, DLT 저장 지연 확인 |
| API latency p95/p99 | 접수 API tail latency 확인 |

## 5. 로그 기준

Structured logging 필드:

- `traceId`
- `eventId`
- `userId`
- `topic`
- `partition`
- `offset`
- `riskLevel`
- `degraded`
- `degradedReason`
- `transactionCount`
- `amountSum`
- `matchedRules`
- `skippedRules`
- `riskScore`

Phase 4 Consumer log 최소 필드:

- `traceId`
- `eventId`
- `userId`
- `topic`
- `partition`
- `offset`
- `duplicateSkipped`

Phase 7에서는 Redis degraded와 skipped rule을 먼저 metric foundation으로 추가했습니다. Consumer Lag, detection latency, DLQ count dashboard는 후속 Observability Phase에서 연결합니다.

## 6. Failure Drill에서 확인할 신호

Phase 8 failure drill은 metric 하나만으로 PASS/FAIL을 판단하지 않고 metric, structured log, admin API 조회를 함께 봅니다.

Redis down drill에서 증가를 확인할 metric:

- `fraud_redis_window_degraded_total`
- `fraud_detection_degraded_total`
- `fraud_rule_skipped_total`
- `fraud_redis_window_record_latency_seconds_count`

Redis down drill에서 확인할 API evidence:

- fraud result `degraded=true`
- `skippedRules`에 Redis 의존 rule 포함
- Redis 복구 후 신규 이벤트 `degraded=false`

Consumer restart drill에서 확인할 evidence:

- Consumer 중지 중 API publish 성공
- Consumer 재시작 후 fraud result 조회 가능
- processing log에 `PROCESSED` 기록 존재
- `fraud_detection_results` row count 1건

Kafka unavailable drill의 현재 한계:

- API publish failure와 Consumer reconnect log는 수동 runbook으로 확인합니다.
- Retry/DLT metric, DLQ count, Consumer Lag dashboard는 후속 Phase에서 연결합니다.

## 7. 개인정보와 로그 마스킹

구조화 로그에는 `eventId`와 `traceId`를 남기되, `accountId`와 `deviceId`는 원문 전체를 기록하지 않습니다.

`userId`는 테스트 데이터 기준으로만 기록하고, 실제 운영 가정에서는 hash 또는 masking 값을 사용합니다. location, ipAddress, channel 같은 필드는 장애 분석에 필요한 최소 범위로만 남깁니다.

`amountSum`은 sliding window 동작 확인을 위해 Consumer log에 남기지만 금융 도메인에서 민감할 수 있습니다. 운영 환경에서는 masking, sampling, 또는 debug-level 제한을 검토합니다.
