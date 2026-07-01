# Observability

## 1. 관측 목표

API가 빠르게 응답하더라도 Consumer Lag이 계속 증가하면 위험 거래 탐지가 늦어집니다. 따라서 API latency뿐 아니라 Consumer Lag, end-to-end detection latency, DLQ backlog/status count를 함께 보는 것이 운영 목표입니다.

Phase 17 local dashboard는 이 목표 지표 전체를 완성한 것이 아니라, 현재 애플리케이션이 실제로 노출하는 Prometheus metric만 연결합니다. Kafka Consumer Lag, end-to-end detection latency, DLT backlog/status gauge는 future work로 분리합니다.

## 2. 핵심 지표

접수 안정성:

- API error rate
- Kafka publish success rate
- API p50/p95/p99 latency

탐지 실시간성:

- Kafka Consumer Lag
- Consumer processing latency for new results
- end-to-end detection latency

처리 신뢰성:

- DLT backlog/status count
- DLT operation count
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

Phase 17 이전에는 Prometheus scrape foundation과 Grafana container mount는 있었지만, Grafana datasource provisioning, dashboard provider, dashboard JSON이 repository에 없었습니다. 따라서 로컬 Grafana 화면이 비어 있는 것은 정상 상태였습니다. Phase 17에서는 local Docker Compose 환경에서 재현 가능한 evidence dashboard를 위해 Prometheus datasource, dashboard provider, dashboard JSON, Prometheus alert rule 후보를 추가했습니다.

## 4. 현재 구현된 Metrics

Phase 7부터 app-consumer는 Redis Sliding Window와 degraded mode를 관측하기 위한 metric foundation을 제공합니다.

| Metric | Type | Purpose |
|---|---|---|
| `fraud.redis.window.record.latency` | Timer | Redis window record/get 처리 시간 |
| `fraud.redis.window.degraded.total` | Counter | Redis unavailable로 degraded window result를 반환한 횟수 |
| `fraud.rule.skipped.total` | Counter | degraded mode로 skipped 처리된 rule 수 |
| `fraud.detection.degraded.total` | Counter | `degraded=true` fraud result 저장 횟수 |
| `fraud.detection.processing.latency` | Timer | Kafka listener 처리 시작부터 신규 fraud result 저장 완료까지의 처리 시간 |
| `fraud.dlt.published.total` | Counter | Consumer가 DLT envelope publish에 성공한 횟수 |
| `fraud.dlt.reprocess.requested.total` | Counter | Admin API DLT reprocess 요청 결과 |
| `fraud.dlt.discarded.total` | Counter | Admin API DLT discard 요청 결과 |

Prometheus scrape에서는 dot format metric 이름이 snake_case 형태로 노출될 수 있습니다.
Timer인 `fraud.redis.window.record.latency`는 Prometheus에서 `fraud_redis_window_record_latency_seconds_count`, `fraud_redis_window_record_latency_seconds_sum`, `fraud_redis_window_record_latency_seconds_max`처럼 `_seconds_*` suffix가 붙은 시계열로 노출될 수 있습니다.

Metric tag에는 `eventId`, `traceId`, `userId`, `accountId`를 넣지 않습니다. 이 값들은 cardinality가 높아 Prometheus 저장 비용과 query 성능에 악영향을 줄 수 있고, 운영 환경에서는 식별자 노출 위험도 있습니다. 개별 이벤트 추적은 structured log의 `traceId`/`eventId`로 수행하고, metric은 `rule`, `mode`, `result` 수준의 낮은 cardinality tag만 사용합니다.

`fraud.redis.window.degraded.total`은 Redis window store가 degraded result를 반환한 횟수이고, `fraud.detection.degraded.total`은 `degraded=true` fraud result가 신규 저장된 횟수입니다. Detection degraded/skipped rule metric은 fraud result가 신규 저장된 경우에만 증가시키고, duplicate result path에서는 증가시키지 않습니다.

`fraud.detection.processing.latency`는 event 발생 시각 기준의 end-to-end latency가 아니라 Consumer 내부 처리 latency입니다. 기준은 Kafka listener가 message 처리를 시작한 시각부터 신규 fraud result 저장 직후까지입니다. `eventTime`, `receivedAt`, `detectedAt` 기준 end-to-end latency는 별도 지표로 확장할 수 있으나, Phase 17에서는 명명 과장을 피하기 위해 processing latency로 기록합니다. 음수 duration은 기록하지 않고, duplicate/idempotent path에서는 중복 기록하지 않습니다.

DLT metric은 status별 gauge가 아니라 operation counter로 구현했습니다. `fraud.dlt.reprocess.requested.total`과 `fraud.dlt.discarded.total`은 `result=success|failed` tag만 사용합니다. `operatorId`, `eventId`, `traceId`, `reason`, raw payload는 metric tag 또는 value로 노출하지 않습니다.

`fraud.redis.window.degraded.total`, `fraud.rule.skipped.total`, `fraud.detection.degraded.total`은 Redis 장애나 timeout이 탐지 민감도 저하로 이어지는지 판단하는 alert 후보입니다. 실제 alert threshold는 Redis down failure scenario와 부하 테스트 결과를 보고 정합니다.

## 4-1. Grafana Dashboard

Phase 17 dashboard는 `infra/grafana/dashboards/fraud-observability.json`에 저장되어 있고, Grafana provisioning은 다음 파일로 구성합니다.

- `infra/grafana/provisioning/datasources/prometheus.yml`
- `infra/grafana/provisioning/dashboards/dashboard-provider.yml`

Dashboard title은 `Fraud Event Streaming Observability`입니다. 목적은 production monitoring completeness가 아니라 local evidence capture입니다.

Phase 17 local dashboard에서 실제 연결한 panels:

- Target Health: `up{job=~"app-api|app-consumer"}`
- API Requests by Status: `sum by (status) (increase(http_server_requests_seconds_count{job="app-api", uri!~"/actuator.*"}[$__range]))`
- HTTP Request Rate by Job: `sum by (job) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))`
- Redis Window Degraded Total: `sum(increase(fraud_redis_window_degraded_total[$__range]))`
- Detection Degraded Total: `sum(increase(fraud_detection_degraded_total[$__range]))`
- Rule Skipped Total: `sum by (rule) (increase(fraud_rule_skipped_total[$__range]))`
- Redis Window Record Latency: `max(fraud_redis_window_record_latency_seconds_max)`
- Consumer Processing Latency for New Results: `max(fraud_detection_processing_latency_seconds_max)`
- DLT Operation Counters: `fraud_dlt_published_total`, `fraud_dlt_reprocess_requested_total`, `fraud_dlt_discarded_total`
- API p95 Latency: `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="app-api", uri!~"/actuator.*"}[1m])))`

`http.server.requests` histogram bucket은 `app-api`와 `app-consumer` application config에서 명시적으로 켭니다. p95 panel은 제거하지 않고 `http_server_requests_seconds_bucket` 기반으로 유지합니다. API status/request-rate/p95 query는 `/actuator.*` URI를 제외해 Prometheus scrape와 health check traffic이 business request evidence에 섞이는 것을 줄입니다. Actuator traffic을 제외하면 `app-consumer`의 HTTP request rate는 거의 없을 수 있습니다. 이는 Consumer가 Kafka listener이고 HTTP business API가 아니기 때문에 정상입니다.

API Requests by Status panel은 dashboard time range 내 status별 `increase`를 Bar gauge로 보여줍니다. 목적은 duplicate replay 후 `202` accepted와 `409` conflict bucket을 읽기 쉽게 확인하는 것입니다.

DLT Operation Counters는 DLT publish/reprocess/discard 이벤트가 발생한 뒤에 증가합니다. 정상 duplicate replay나 Redis down drill만 실행한 dashboard에서는 No data가 정상일 수 있습니다. `fraud.dlt.published.total`은 unique DLT backlog count가 아니라 DLT envelope publish success count입니다. DLT backlog/status count는 future work이고, DLT evidence는 별도 DLT admin audit response screenshot과 함께 해석합니다.

Local Admin operation evidence는 `make failure-drill-dlt`로 만들 수 있습니다. 이 target은 synthetic `PENDING` DLT row를 만들고 실제 Admin discard API를 호출한 뒤 `admin_audit_logs`와 `fraud_dlt_discarded_total{result="success"}` 증가를 확인합니다. 이는 Consumer failure path로 DLT publish를 재현하는 drill이 아니라, 이미 DLT에 격리된 이벤트를 운영자가 discard했을 때 audit과 operation counter가 남는지 검증하는 drill입니다. 기본적으로 DB evidence를 남기며, 반복 실행 시 DB row 정리가 필요하면 `KEEP_DLT_DRILL_EVIDENCE=false make failure-drill-dlt`로 실행합니다.

DLT metric endpoint도 소유 앱을 구분합니다. `fraud.dlt.published.total`은 app-consumer Prometheus endpoint에서 확인하고, `fraud.dlt.reprocess.requested.total`과 `fraud.dlt.discarded.total`은 app-api Prometheus endpoint에서 확인합니다.

Kafka Consumer Lag은 운영적으로 중요한 지표지만, 이번 local dashboard에서는 실제 노출되는 Kafka client lag metric이 확인되는 경우에만 panel로 연결합니다. 현재 code/config 검색 기준으로 `kafka_consumer_records_lag_max` 같은 lag metric이 확인되지 않았으므로 fake panel을 만들지 않았습니다. Kafka client metric 설정 또는 Kafka exporter 연동은 future work입니다. Kafka UI Consumer Group Lag 화면은 Phase 18 image capture 후보가 될 수 있지만, Grafana dashboard 구현으로 주장하지 않습니다.

Prometheus alert rule 후보는 `infra/prometheus/rules/fraud-alerts.yml`에 둡니다. Alertmanager, Slack, PagerDuty, automatic incident response는 이번 범위가 아닙니다.

## 4-2. 후속 Observability 후보

아래 지표는 운영 준비도 판단에 필요하지만, 현재 구현 metric과 분리해 후속 관측성 고도화 범위로 둡니다.

| Candidate | Purpose |
|---|---|
| Kafka consumer lag | 비동기 탐지 지연과 backlog 확인 |
| End-to-end detection latency | `receivedAt` 또는 `eventTime`부터 `detectedAt`까지의 실제 탐지 지연 확인 |
| DB insert latency | fraud result, processing log, DLT 저장 지연 확인 |
| DLT status gauge | PENDING, REPROCESS_FAILED 등 상태별 backlog 확인 |
| Consumer DLT publish drill | magic trigger 없이 안전한 poison event policy가 정의될 때 Consumer failure path 검증 |
| Alertmanager routing | local alert rule을 실제 notification route와 연결 |

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

Phase 7에서는 Redis degraded와 skipped rule을 먼저 metric foundation으로 추가했습니다. Phase 17에서는 local dashboard에 실제 노출 metric을 연결했습니다. Consumer Lag, end-to-end detection latency, DLT backlog/status dashboard는 후속 Observability Phase에서 연결합니다.

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

## 6-1. Phase 12 Load Test 관측 기준

k6 결과는 API 요청 관점의 지표이고, Prometheus/Actuator metric은 Consumer와 Redis degraded 영향을 설명하는 지표입니다. Phase 12 load test 결과를 해석할 때는 두 축을 함께 봅니다.

k6에서 확인할 항목:

- total requests
- request rate
- `http_req_failed`
- `http_req_duration` p50/p95/p99
- status code distribution

Redis down load에서 확인할 Prometheus metric:

- `fraud_redis_window_record_latency_seconds_count`
- `fraud_redis_window_record_latency_seconds_sum`
- `fraud_redis_window_record_latency_seconds_max`
- `fraud_redis_window_degraded_total`
- `fraud_rule_skipped_total`
- `fraud_detection_degraded_total`

Duplicate Replay는 API 409 응답이 의도된 결과일 수 있으므로 `http_req_failed`만으로 실패를 판단하지 않습니다. 최종 기준은 `fraud_detection_results.event_id` unique constraint와 fraud result count 1건 유지 여부입니다.

현재 Consumer Lag metric은 후속 Observability hardening 후보입니다. Phase 12에서는 Kafka UI, 처리 결과 조회, processing log, degraded metric을 함께 사용해 비동기 처리 영향을 보조적으로 판단합니다.

Metric tag에는 계속 `eventId`, `traceId`, `userId`, `accountId`를 넣지 않습니다. 부하 테스트는 많은 synthetic event를 만들기 때문에 high-cardinality tag가 붙으면 Prometheus 저장 비용과 query 성능 문제가 빠르게 커질 수 있습니다.

## 6-2. Phase 13 Load and Failure Evidence 관측 기준

Phase 13에서는 k6 결과와 Prometheus/Actuator metric을 같은 시간대의 evidence로 묶어서 해석합니다. API 요청이 2xx로 끝났더라도 Consumer가 backlog를 쌓거나 Redis degraded metric이 급증하면 정상 처리로만 해석하지 않습니다.

기록할 k6 지표:

- total requests
- request rate
- `http_req_failed`
- `http_req_duration` p50/p95/p99
- status code distribution

Redis down load에서 확인할 metric:

- `fraud_redis_window_record_latency_seconds_count`
- `fraud_redis_window_record_latency_seconds_sum`
- `fraud_redis_window_record_latency_seconds_max`
- `fraud_redis_window_degraded_total`
- `fraud_rule_skipped_total`
- `fraud_detection_degraded_total`

Duplicate Replay는 consistency evidence와 함께 해석합니다. API가 `409 CONFLICT`를 반환해도 같은 `eventId`에 대한 `fraud_detection_results` count가 1건이면 의도한 중복 방어로 볼 수 있습니다.

Consumer Lag metric은 아직 후속 Observability hardening 후보입니다. Phase 13에서는 Kafka UI, processing log, fraud result 조회, Redis degraded metric을 함께 사용해 비동기 처리 영향을 보조적으로 판단합니다.

Metric tag에는 계속 `eventId`, `traceId`, `userId`, `accountId`, `deviceId`를 넣지 않습니다. 부하 테스트는 synthetic event를 대량 생성하므로 high-cardinality tag 정책을 어기면 Prometheus 저장 비용과 query 성능 문제가 빠르게 커집니다.

## 7. 개인정보와 로그 마스킹

구조화 로그에는 `eventId`와 `traceId`를 남기되, `accountId`와 `deviceId`는 원문 전체를 기록하지 않습니다.

`userId`는 테스트 데이터 기준으로만 기록하고, 실제 운영 가정에서는 hash 또는 masking 값을 사용합니다. location, ipAddress, channel 같은 필드는 장애 분석에 필요한 최소 범위로만 남깁니다.

`amountSum`은 sliding window 동작 확인을 위해 Consumer log에 남기지만 금융 도메인에서 민감할 수 있습니다. 운영 환경에서는 masking, sampling, 또는 debug-level 제한을 검토합니다.
