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

## 4. Runtime Evidence

| Metric | Baseline | High Density |
|---|---:|---:|
| Run ID | `phase2-state-baseline-clean-20260823-003` | `phase2-state-pressure-clean-20260823-001` |
| Users | 1,000 | 100 |
| Emitted events | 12,000 | 12,000 |
| HTTP failure rate | 0 | 0 |
| Dropped iterations | null | null |
| k6 HTTP p95 | 8.45 ms | 5.50 ms |
| Redis user ZSET count / max ZCARD | 1,000 / 12 | 100 / 120 |
| Redis event metadata Hash keys | 12,000 | 12,000 |
| Redis valid window event count recorded sum/count | 78,000 / 12,000 | 726,000 / 12,000 |
| Redis valid window amount sum max | 3,000,000 KRW | 30,000,000 KRW |
| Redis valid window amount recorded sum/count | 19,500,000,000 / 12,000 | 181,500,000,000 / 12,000 |
| Redis state p95/p99 | 6.20 ms / 12.88 ms | 32.84 ms / 41.75 ms |
| Consumer service p95/p99 | 13.18 ms / 25.69 ms | 38.47 ms / 48.12 ms |
| Redis memory start/final/peak | 1,044,432 / 7,587,736 / 7,590,528 bytes | 1,044,464 / 6,780,384 / 6,783,168 bytes |
| Redis memory final delta | 6,543,304 bytes | 5,735,920 bytes |
| Redis `HGET` calls / per event | 78,000 / 6.5 | 726,000 / 60.5 |
| Redis selected command calls / per event | 150,000 / 12.5 | 798,000 / 66.5 |
| Peak Consumer Lag | 0 | 1 |
| Final Consumer Lag | 0 | 0 |
| Final receipt/result/log count | 12,000 / 12,000 / 12,000 | 12,000 / 12,000 / 12,000 |

The pressure run raised the final per-user Redis window max from 12 to 120 valid events while keeping EPS, duration, event amount, total event count, random seed, and Consumer concurrency fixed. Redis state p95 increased from 6.20 ms to 32.84 ms, and Consumer service p95 increased from 13.18 ms to 38.47 ms. Final Consumer Lag still returned to 0 and durable row counts remained aligned.

Clean Redis memory did not increase with the high-density workload. Both workloads retained 12,000 event metadata Hash keys, while the baseline retained 1,000 user ZSET keys and the pressure run retained 100 user ZSET keys. Therefore the memory result is not evidence that per-user density alone increased Redis memory. The latency result aligns more directly with command count growth: `HGET` calls rose from 6.5 to 60.5 per event, and selected Redis command calls rose from 12.5 to 66.5 per event.

Prometheus histogram quantiles for `fraud.redis.window.event.count` and `fraud.redis.window.amount.sum` are useful directional distribution signals, but the Redis ZSET scan result is the authoritative configured state-size confirmation for this run.

## 5. Partition Distribution

| Partition | Baseline processed | Baseline % | High Density processed | High Density % | Peak Lag Baseline | Peak Lag High Density |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 1,980 | 16.50 | 2,160 | 18.00 | 0 | 1 |
| 1 | 1,788 | 14.90 | 1,680 | 14.00 | 0 | 0 |
| 2 | 2,268 | 18.90 | 3,000 | 25.00 | 0 | 1 |
| 3 | 1,884 | 15.70 | 1,680 | 14.00 | 0 | 0 |
| 4 | 1,932 | 16.10 | 1,560 | 13.00 | 0 | 0 |
| 5 | 2,148 | 17.90 | 1,920 | 16.00 | 0 | 1 |

The high-density workload created a moderate partition distribution shift because Kafka uses `userId` as the partition key and the workload reduced user cardinality from 1,000 to 100. The shift did not create sustained backlog in this run, but it means Consumer latency movement is not a perfectly isolated Redis state-size signal. V3 Phase 3 should use an explicit partition-balanced user set before testing hot-partition behavior.

## 6. Runtime Commands

```bash
env -u DEBUG \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/fraud_phase2_baseline_clean3 \
./gradlew :app-api:bootRun --args='--logging.level.root=INFO --logging.level.org.hibernate.SQL=INFO --logging.level.org.springframework=INFO --debug=false'

env -u DEBUG \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/fraud_phase2_baseline_clean3 \
SPRING_DATA_REDIS_PORT=16382 \
FRAUD_CONSUMER_CONCURRENCY=6 \
./gradlew :app-consumer:bootRun --args='--logging.level.root=INFO --logging.level.org.hibernate.SQL=INFO --logging.level.org.springframework=INFO --debug=false'

env -u DEBUG \
V3_PRE_ALLOCATED_VUS=300 \
V3_MAX_VUS=600 \
V3_RUN_ID=phase2-state-baseline-clean-20260823-003 \
make k6-v3-phase2-state-baseline

env -u DEBUG \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/fraud_phase2_pressure_clean \
./gradlew :app-api:bootRun --args='--logging.level.root=INFO --logging.level.org.hibernate.SQL=INFO --logging.level.org.springframework=INFO --debug=false'

env -u DEBUG \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/fraud_phase2_pressure_clean \
SPRING_DATA_REDIS_PORT=16380 \
FRAUD_CONSUMER_CONCURRENCY=6 \
./gradlew :app-consumer:bootRun --args='--logging.level.root=INFO --logging.level.org.hibernate.SQL=INFO --logging.level.org.springframework=INFO --debug=false'

env -u DEBUG \
V3_PRE_ALLOCATED_VUS=300 \
V3_MAX_VUS=600 \
V3_RUN_ID=phase2-state-pressure-clean-20260823-001 \
make k6-v3-phase2-state-pressure
```

Both runs used app-api with the matching PostgreSQL database and a dedicated Redis container. The dedicated Redis containers avoid logical-database contamination in `used_memory` because Redis memory metrics are instance-level.

## 7. Discarded Runs

`phase2-state-baseline-20260823-001` was discarded because the sandboxed k6 process could not connect to `localhost:8080` and all HTTP requests failed with `operation not permitted`. No PostgreSQL rows or Redis keys were written by that run.

`phase2-state-baseline-clean-20260823-001` and `phase2-state-baseline-clean-20260823-002` were discarded because local k6 dropped iterations before 12,000 emitted events. The accepted clean run used `V3_PRE_ALLOCATED_VUS=300` and `V3_MAX_VUS=600`.

The earlier `phase2-state-baseline-20260823-003` and `phase2-state-pressure-20260823-001` memory comparison used different logical Redis databases on the same Redis instance. Their latency direction remained useful, but their memory values are not authoritative and were replaced by the dedicated-container clean Redis measurements above.

## 8. Known Limitations

- This phase uses the HTTP k6 driver and must not be merged with direct Kafka producer results.
- The first Phase 2 workloads change user cardinality, not Kafka partition affinity. The pressure run produced moderate partition distribution movement, so partition-balanced state-size workloads should be added before claiming perfect isolation. Partition skew remains V3 Phase 3.
- The metrics record valid window members after metadata filtering. They intentionally do not tag by `userId` to avoid high-cardinality metrics.
- `fraud.redis.window.event.count` is the valid rule-calculation window member count after metadata lookup, not a direct Redis key cardinality gauge.
- Redis memory includes the active user ZSETs plus event metadata Hash keys retained until TTL expiry. It must not be interpreted as only the 5-minute calculation window size.
- The current Redis read path performs `ZRANGEBYSCORE` followed by one metadata `HGET` per window member. This is an intentional Phase 2 baseline for the observed O(window size) access pattern; pipelining, batching, Lua, MGET-style metadata reads, or aggregate-state replacement are deferred optimization candidates.
- Redis optimization is intentionally deferred. The measured result establishes a state-size cost baseline before changing Redis access patterns or data structures.
