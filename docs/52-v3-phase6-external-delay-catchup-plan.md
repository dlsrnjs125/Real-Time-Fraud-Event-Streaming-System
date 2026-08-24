# V3 Phase 6 External Delay and Catch-up Burst Plan

Status: Done.

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

The two workloads intentionally share EPS, event count, user cardinality, and amount. They differ only in event-time/source-delay semantics.

`randomSeed` remains in both manifests as common V3 metadata, but the Phase 6 k6 runner does not use seeded randomness. User assignment is deterministic through modulo assignment:

```text
globalIteration % userCardinality
```

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

`prepare_v3_phase6_run.sh` is a local-experiment-only guardrail. It runs Redis `FLUSHDB` against the configured local Redis DB and must not be used against shared or production Redis.

## 6. Completion Criteria

Implementation completion:

- organic and catch-up manifests validate through the V3 workload validator
- both workloads use the same EPS, duration, event count, user cardinality, and amount
- source-delay profile rejects drift in expected accepted/too-late counts
- organic/catch-up paired-manifest equality is verified for EPS, duration, event count, user cardinality, and event amount
- k6 source-emulator driver records source profile, event-time mode, source delay, achieved EPS, dropped iterations, and HTTP latency summary
- Makefile exposes separate organic and catch-up burst run targets with clean Redis state and pre-run Consumer Lag guardrails

Accepted runtime evidence:

- organic and catch-up runs executed with the same configured rate and event count
- organic run emitted 9000 events with HTTP failure 0 and dropped iterations 0
- catch-up run emitted 9000 events with HTTP failure 0 and dropped iterations 0
- persisted event-to-ingress age showed organic p95 `0.239387s` and catch-up p95 `270.127421s`
- both runs produced too-late result count `0`, keeping Phase 6 separate from Phase 5 freshness rejection
- API/Kafka/Redis/Consumer/Sink metrics were captured separately from persisted pre-ingress age
- matched-rule distribution was identical for both accepted runs: `RAPID_TRANSACTION_COUNT=5000`, `(none)=4000`
- final DB counts matched emitted events for both runs: receipts/results/logs = `9000/9000/9000`
- final Consumer Lag returned to `0` for both runs

Evidence:

```text
docs/evidence/v3-phase6/
```

## 7. Known Limitations

- Phase 6 does not add `sourceSentAt` to the Kafka event schema or PostgreSQL receipt schema.
- Source transport latency is not measured because persisted `receivedAt` is owned by app-api and `sourceSentAt` is retained only in the workload summary/header boundary.
- Catch-up delay is intentionally below the too-late threshold. Too-late rejection remains Phase 5 behavior.
- Phase 6 shifts catch-up `eventTime` by 270 seconds. If a run crosses a time-based fraud-rule boundary, matched rule distribution may differ between organic and catch-up runs. Evidence must either run away from that boundary or record matched-rule distribution for both runs.
- 300 EPS is a high-rate burst condition. Absolute Lag can include downstream capacity pressure; Phase 6 attribution depends on comparing organic and catch-up runs under the same clean environment.
