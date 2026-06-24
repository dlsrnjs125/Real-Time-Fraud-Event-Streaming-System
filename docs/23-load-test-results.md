# Phase 13 Load Test Results

## Test Environment

- Local machine: TBD
- Docker Compose: local Docker Compose
- API: app-api on `http://localhost:8080`
- Consumer: app-consumer on `http://localhost:8081`
- Kafka: local `fraud-kafka`
- PostgreSQL: local `fraud-postgres`
- Redis: local `fraud-redis`

## Scenario Summary

| Scenario | Date | Command | Result | Notes |
| --- | --- | --- | --- | --- |
| Smoke | 2026-06-24 | `make k6-smoke` | PASS | Dedicated `smoke.js`, API/Consumer health UP after run |
| Normal Load | TBD | `make k6-normal` | TBD | |
| Peak Load | TBD | `make k6-peak` | TBD | |
| Duplicate Replay | TBD | `make k6-duplicate` | TBD | |
| Redis Down Load | TBD | `make k6-redis-down` | TBD | |

## Smoke Result

- VUs: 1
- Duration: 10s
- Total requests: 1459
- RPS: 145.851329/s
- http_req_failed: 0.00%
- p50: 5.73ms
- p95: 9.41ms
- p99: 19.55ms
- Max: 535.4ms
- Status code distribution: not captured in default smoke output
- API health after run: UP
- Consumer health after run: UP
- Notes: Dedicated `load-test/k6/scenarios/smoke.js` executed against local Docker Compose infra.

## Normal Load Result

- RPS: TBD
- http_req_failed: TBD
- p50: TBD
- p95: TBD
- p99: TBD
- Status code distribution: TBD
- Notes:

## Peak Load Result

- RPS: TBD
- http_req_failed: TBD
- p50: TBD
- p95: TBD
- p99: TBD
- Status code distribution: TBD
- Notes:

## Duplicate Replay Result

- Duplicate eventId: `phase13-duplicate-fixed-event-id`
- Expected: fraud result count = 1
- Actual: TBD
- API response policy: 2xx accepted or 409 duplicate response are interpreted with the consistency check
- Manual consistency command: `scripts/load_tests/check_duplicate_result_count.sh phase13-duplicate-fixed-event-id`
- Notes:

## Redis Down Load Result

- Redis degraded metric before: TBD
- Redis degraded metric after: TBD
- detection degraded metric before: TBD
- detection degraded metric after: TBD
- skipped rule metric before: TBD
- skipped rule metric after: TBD
- DLT count before: TBD
- DLT count after: TBD
- Redis container recovery: TBD
- Script evidence: `make k6-redis-down` prints degraded metric before/after values and waits for Redis readiness during cleanup.
- Notes:

## Prometheus / Actuator Metrics

- `fraud_redis_window_record_latency_seconds_count`: 306
- `fraud_redis_window_record_latency_seconds_sum`: 20.056054248
- `fraud_redis_window_record_latency_seconds_max`: 0.152756209
- `fraud_redis_window_degraded_total`: TBD
- `fraud_rule_skipped_total`: TBD
- `fraud_detection_degraded_total`: TBD
- DLT metric count: TBD

## Bottleneck Analysis

- API bottleneck: TBD
- Kafka bottleneck: TBD
- Consumer bottleneck: TBD
- Redis bottleneck: TBD
- PostgreSQL bottleneck: TBD

## Follow-up

- Consumer lag metric
- Grafana dashboard screenshot evidence
- CI scheduled load test
- DB index/connection pool tuning

## Notes

Do not generalize these local Docker Compose results as production capacity. Values depend on local hardware, Docker resource limits, JVM warmup, Kafka partition state, and whether app-api/app-consumer are already warmed up.
