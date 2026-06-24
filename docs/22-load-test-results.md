# Load Test Results

## Test Environment

- Local machine: TBD
- Docker Compose: TBD
- API: TBD
- Consumer: TBD
- Kafka: TBD
- PostgreSQL: TBD
- Redis: TBD

## Scenario Summary

| Scenario | Date | Command | Result | Notes |
| --- | --- | --- | --- | --- |
| Smoke | 2026-06-24 | `make k6-smoke` | PASS | 1 VU, 10s, API/Consumer health UP after run |
| Normal Load | TBD | `make k6-normal` | TBD | |
| Peak Load | TBD | `make k6-peak` | TBD | |
| Duplicate Replay | TBD | `make k6-duplicate` | TBD | |
| Redis Down Load | TBD | `make k6-redis-down` | TBD | |

## Smoke Result

- Command: `make k6-smoke`
- Date: 2026-06-24
- Total requests: 1825
- RPS: 182.443698/s
- http_req_failed: 0.00%
- p50: 4.65ms
- p95: 8.22ms
- p99: 12.46ms
- Max: 606.29ms
- API health after run: UP
- Consumer health after run: UP
- Consumer fraud metric sample: `fraud_redis_window_record_latency_seconds_count 1104`
- Notes: Smoke now uses dedicated `load-test/k6/scenarios/smoke.js` so normal-load arrival-rate options remain separate.

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

- Duplicate eventId: TBD
- Expected: fraud result count = 1
- Actual: TBD
- API response policy: TBD
- Manual consistency command: `scripts/load_tests/check_duplicate_result_count.sh phase12-duplicate-fixed-event-id`
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

## Bottleneck Analysis

- API bottleneck: TBD
- Kafka bottleneck: TBD
- Consumer bottleneck: TBD
- Redis bottleneck: TBD
- PostgreSQL bottleneck: TBD

## Follow-up

- Consumer lag metric
- Grafana dashboard
- CI scheduled load test
- DB index/connection pool tuning

## Recording Rules

- Do not write unmeasured numbers.
- Keep raw k6 JSON/CSV files out of git.
- Record failed results with the observed symptom and likely bottleneck.
- Treat local Docker Compose results as local evidence, not absolute production capacity.
