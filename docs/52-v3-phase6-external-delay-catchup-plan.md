# V3 Phase 6 External Delay and Catch-up Burst Plan

Status: Implementation ready; runtime evidence pending.

## 1. Objective

Phase 6 separates two workloads that can look similar by input EPS:

- organic burst: new activity arrives at high rate
- catch-up burst: an upstream source releases accumulated older events at the same high rate

The goal is upstream event-age attribution, not a new fraud rule or retry behavior. Runtime evidence should compare event-to-ingress age, Consumer Lag, API/Kafka/Redis/Consumer latency, and final consistency.

## 2. Workload Contract

Committed manifests:

```text
load-test/workloads/v3/organic-burst-v1.json
load-test/workloads/v3/catch-up-burst-v1.json
```

Common runtime shape:

| Field | Value |
|---|---:|
| Target EPS | 300 |
| Duration | 30s |
| Event limit | 9000 |
| User cardinality | 1000 |
| Event amount | 100000 |
| Random seed | 660519 |

The two workloads intentionally share EPS, event count, user cardinality, amount, and seed. They differ only in event-time/source-delay semantics.

## 3. Organic Burst

| Field | Value |
|---|---|
| `workloadRole` | `ORGANIC_BURST` |
| `driverType` | `HTTP_SOURCE_EMULATOR` |
| `sourceProfile` | `NORMAL` |
| `eventTimeMode` | `REBASE_TO_ARRIVAL` |
| `sourceDelay` | `0s` |

Expected relationship:

```text
eventTime ~= sourceSentAt <= receivedAt
```

## 4. Catch-up Burst

| Field | Value |
|---|---|
| `workloadRole` | `CATCH_UP_BURST` |
| `driverType` | `HTTP_SOURCE_EMULATOR` |
| `sourceProfile` | `BATCH_CATCHUP` |
| `eventTimeMode` | `CONTROLLED_LATENESS` |
| `sourceDelay` | `270s` |
| `allowedLateness` | `5m` |
| Expected accepted events | 9000 |
| Expected too-late events | 0 |

Expected relationship:

```text
eventTime < sourceSentAt <= receivedAt
```

`270s` keeps catch-up events inside the current 5-minute freshness policy. This avoids mixing Phase 6 delay attribution with Phase 5 too-late rejection.

## 5. Runtime Driver

The Phase 6 driver is:

```text
load-test/k6/scenarios/v3-phase6-source-delay.js
```

Make targets:

```bash
V3_RUN_ID=phase6-organic-001 make k6-v3-phase6-organic-burst
V3_RUN_ID=phase6-catch-up-001 make k6-v3-phase6-catch-up-burst
```

The source emulator owns `sourceSentAt` at HTTP dispatch time and records that ownership in the local k6 summary. `sourceSentAt` is sent as an HTTP header for run traceability, but app-api does not persist it and it is not part of the Kafka payload. Therefore Phase 6 must not claim measured source transport latency. The measured runtime signal is pre-ingress event age:

```text
receivedAt - eventTime
```

For catch-up traffic this includes the configured upstream holding age plus HTTP/API boundary latency. No runtime event schema change is introduced in this phase.

Each accepted Phase 6 run must start from an isolated runtime state:

```text
final Consumer Lag = 0 before run
Redis DB = empty before run
```

The Makefile targets run `scripts/load_tests/prepare_v3_phase6_run.sh` before k6 to enforce this clean-state preflight for Redis-backed sliding-window comparisons.

## 6. Completion Criteria

Implementation completion:

- organic and catch-up manifests validate through the V3 workload validator
- both workloads use the same EPS, duration, event count, user cardinality, amount, and seed
- source-delay profile rejects drift in expected accepted/too-late counts
- organic/catch-up paired-manifest equality is verified for EPS, duration, event count, user cardinality, random seed, and event amount
- k6 source-emulator driver records source profile, event-time mode, source delay, achieved EPS, dropped iterations, and HTTP latency summary
- Makefile exposes separate organic and catch-up burst run targets with clean Redis state and pre-run Consumer Lag guardrails

Runtime completion, to be collected next:

- organic and catch-up runs execute with the same configured rate and event count
- catch-up run shows higher ingress age while organic run stays near dispatch time
- API/Kafka/Redis/Consumer metrics are captured to distinguish pre-ingress event age from downstream bottlenecks
- final DB counts match emitted events for both runs
- final Consumer Lag returns to 0 for both runs

## 7. Known Limitations

- Phase 6 does not add `sourceSentAt` to the Kafka event schema or PostgreSQL receipt schema.
- Source transport latency is not measured because persisted `receivedAt` is owned by app-api and `sourceSentAt` is retained only in the workload summary/header boundary.
- Catch-up delay is intentionally below the too-late threshold. Too-late rejection remains Phase 5 behavior.
