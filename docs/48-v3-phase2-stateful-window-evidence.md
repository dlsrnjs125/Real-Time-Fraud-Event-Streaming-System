# V3 Phase 2 Stateful Sliding-Window Scaling Evidence

## 1. Status

- Status: Complete for local Docker Phase 2 evidence
- Scope: Redis sliding-window state-size workload and observability foundation
- Completion boundary: this is local HTTP k6 evidence, not direct Kafka producer evidence or Redis data-structure optimization evidence

## 2. Implementation Summary

- Added `STATEFUL_WINDOW_SCALING` workload role.
- Added `statefulWindowProfile` to V3 workload manifests.
- Added low-density and high-density Phase 2 manifests:
  - `state-size-baseline-v1.json`
  - `state-size-high-density-v1.json`
- Added `load-test/k6/scenarios/v3-phase2-stateful-window.js`.
- Added Makefile targets:
  - `make k6-v3-phase2-state-baseline`
  - `make k6-v3-phase2-state-pressure`
- Added Redis state-size metrics:
  - `fraud.redis.window.event.count`
  - `fraud.redis.window.amount.sum`
- Added Grafana panels for Redis window event count and amount sum.

## 3. Workload Contract

Both Phase 2 workloads keep EPS, duration, event amount, event count, driver, and event-time mode fixed.

| Workload | Users | Target EPS | Duration | Events | Expected events/user/window | Expected amount/user/window |
|---|---:|---:|---:|---:|---:|---:|
| `state-size-baseline-v1` | 1,000 | 100 | 120s | 12,000 | 12 | 3,000,000 KRW |
| `state-size-high-density-v1` | 100 | 100 | 120s | 12,000 | 120 | 30,000,000 KRW |

The workload duration is shorter than the configured 5-minute Redis sliding window, so the synthetic runtime-window density is owned by the workload rather than inferred from PaySim source steps.

## 4. Runtime Evidence Template

| Metric | Baseline | High Density |
|---|---:|---:|
| Run ID | `phase2-state-baseline-20260823-003` | `phase2-state-pressure-20260823-001` |
| Users | 1,000 | 100 |
| Emitted events | 12,000 | 12,000 |
| HTTP failure rate | 0 | 0 |
| Dropped iterations | null | null |
| k6 HTTP p95 | 5.80 ms | 5.34 ms |
| Redis window event count max | 12 | 120 |
| Redis window event count recorded sum/count | 78,000 / 12,000 | 726,000 / 12,000 |
| Redis window amount sum max | 3,000,000 KRW | 30,000,000 KRW |
| Redis window amount recorded sum/count | 19,500,000,000 / 12,000 | 181,500,000,000 / 12,000 |
| Redis state p95/p99 | 11.25 ms / 53.37 ms | 55.59 ms / 65.17 ms |
| Consumer service p95/p99 | 22.76 ms / 235.89 ms | 60.68 ms / 204.46 ms |
| Redis memory max | 5,868,600 bytes | 10,138,488 bytes |
| Peak Consumer Lag | 0 | 130 |
| Final Consumer Lag | 0 | 0 |
| Final receipt/result/log count | 12,000 / 12,000 / 12,000 | 12,000 / 12,000 / 12,000 |

The pressure run raised the final per-user Redis window max from 12 to 120 valid events while keeping EPS, duration, event amount, total event count, and Consumer concurrency fixed. Redis state p95 increased from 11.25 ms to 55.59 ms, and Consumer service p95 increased from 22.76 ms to 60.68 ms. Final Consumer Lag still returned to 0 and durable row counts remained aligned.

Prometheus histogram quantiles for `fraud.redis.window.event.count` and `fraud.redis.window.amount.sum` are useful directional distribution signals, but the max gauges are the authoritative configured state-size confirmation for this run because native histogram interpolation can exceed the observed max when buckets are coarse.

## 5. Runtime Commands

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/fraud_phase2_baseline_final \
SPRING_DATA_REDIS_DATABASE=14 \
FRAUD_CONSUMER_CONCURRENCY=6 \
./gradlew :app-consumer:bootRun

V3_RUN_ID=phase2-state-baseline-20260823-003 make k6-v3-phase2-state-baseline

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/fraud_phase2_pressure_final \
SPRING_DATA_REDIS_DATABASE=13 \
FRAUD_CONSUMER_CONCURRENCY=6 \
./gradlew :app-consumer:bootRun

V3_RUN_ID=phase2-state-pressure-20260823-001 make k6-v3-phase2-state-pressure
```

Both runs used app-api with the matching PostgreSQL database and Redis logical database.

## 6. Discarded Run

`phase2-state-baseline-20260823-001` was discarded because the sandboxed k6 process could not connect to `localhost:8080` and all HTTP requests failed with `operation not permitted`. No PostgreSQL rows or Redis keys were written by that run.

## 7. Known Limitations

- This phase uses the HTTP k6 driver and must not be merged with direct Kafka producer results.
- The first Phase 2 workloads change user cardinality, not Kafka partition affinity. Partition skew remains V3 Phase 3.
- The metrics record valid window members after metadata filtering. They intentionally do not tag by `userId` to avoid high-cardinality metrics.
- Redis optimization is intentionally deferred. The measured result establishes a state-size cost baseline before changing Redis access patterns or data structures.
