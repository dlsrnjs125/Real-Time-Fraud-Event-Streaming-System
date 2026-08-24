# V3 Phase 6 External Delay and Catch-up Burst Plan

Status: Implementation ready; runtime evidence pending.

## 1. Objective

Phase 6 separates two workloads that can look similar by input EPS:

- organic burst: new activity arrives at high rate
- catch-up burst: an upstream source releases accumulated older events at the same high rate

The goal is delay attribution, not a new fraud rule or retry behavior. Runtime evidence should compare source delay, ingress age, Consumer Lag, API/Kafka/Redis/Consumer latency, and final consistency.

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

The source emulator owns `sourceSentAt` at HTTP dispatch time and records that ownership in the local k6 summary. `sourceSentAt` is sent as an HTTP header for run traceability, but app-api remains the owner of persisted `receivedAt`; no runtime event schema change is introduced in this phase.

## 6. Completion Criteria

Implementation completion:

- organic and catch-up manifests validate through the V3 workload validator
- both workloads use the same EPS, duration, event count, user cardinality, amount, and seed
- source-delay profile rejects drift in expected accepted/too-late counts
- k6 source-emulator driver records source profile, event-time mode, source delay, achieved EPS, dropped iterations, and HTTP latency summary
- Makefile exposes separate organic and catch-up burst run targets

Runtime completion, to be collected next:

- organic and catch-up runs execute with the same configured rate and event count
- catch-up run shows higher ingress age while organic run stays near dispatch time
- API/Kafka/Redis/Consumer metrics are captured to distinguish source delay from downstream bottlenecks
- final DB counts match emitted events for both runs
- final Consumer Lag returns to 0 for both runs

## 7. Known Limitations

- Phase 6 does not add `sourceSentAt` to the Kafka event schema or PostgreSQL receipt schema.
- Source transport delay remains approximate because persisted `receivedAt` is owned by app-api and `sourceSentAt` is retained in the workload summary/header boundary.
- Catch-up delay is intentionally below the too-late threshold. Too-late rejection remains Phase 5 behavior.
