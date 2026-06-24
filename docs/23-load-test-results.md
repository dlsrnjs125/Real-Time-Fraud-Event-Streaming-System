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
| Smoke | 2026-06-24 | `make k6-smoke` | PASS | Dedicated 3-request `smoke.js`, API/Consumer health UP after run |
| Normal Load | TBD | `make k6-normal` | TBD | |
| Peak Load | TBD | `make k6-peak` | TBD | |
| Duplicate Replay | TBD | `make k6-duplicate-check` | TBD | k6 replay plus DB count check |
| Redis Down Load | TBD | `make k6-redis-down` | TBD | |

## Smoke Result

- VUs: 1
- Iterations: 3
- Max duration: 30s
- Total requests: 3
- RPS: 54.501853/s
- http_req_failed: 0.00%
- p50: 10.6ms
- p95: 24ms
- p99: 25.19ms
- Max: 25.49ms
- Status code distribution: not captured in default smoke output
- API health after run: UP
- Consumer health after run: UP
- Notes: Smoke is not a capacity test. It only confirms k6 execution and the API intake path; interpret RPS and latency in Normal/Peak results. The first immediate run after app startup failed the p95 threshold by cold-start noise (`p95=501.33ms`); the warm rerun passed.

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
- Consistency command: `make k6-duplicate-check`
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
- Script evidence: `make k6-redis-down` validates Redis/detection degraded metric increases and waits for Redis readiness during cleanup.
- Notes:

## Prometheus / Actuator Metrics

- `fraud_redis_window_record_latency_seconds_count`: 6
- `fraud_redis_window_record_latency_seconds_sum`: 0.062478834
- `fraud_redis_window_record_latency_seconds_max`: 0.038931292
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

k6 `http_req_duration` measures the synchronous path where app-api accepts the event and publishes to Kafka. FraudResult persistence is asynchronous Consumer work, so final detection completion must be checked with fraud result lookup, processing log evidence, and Redis degraded metrics.
