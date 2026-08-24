# V3 Dataset, Workload, and Time Contract

## 1. Purpose

This document separates the data corpus, runtime arrival pattern, and timestamp semantics used by V3. It is a design contract for Phase 0, not an implementation or measured result.

## 2. Dataset Is Not a Workload

```text
Dataset volume != runtime event velocity
```

The same PaySim dataset can support:

- slow correctness replay
- sustained-capacity tests
- short organic bursts
- delayed catch-up bursts
- hot-key traffic
- late and out-of-order arrival
- historical replay

Dataset and workload therefore have separate versions and fingerprints.

## 3. Existing V2 Data Assets

V2 already provides:

- raw/full-data commit protection
- deterministic normalization
- HMAC-based identifier hashing
- accepted/rejected row handling
- native and normalized transaction-type distributions
- safe sample generation
- HTTP replay and replay reports
- rule/evaluation regression contracts

The current preprocessing report includes row counts, fraud counts, and type distributions. It does not yet provide the complete V3 profile for user concentration, amount quantiles, time-step concentration, and same-user repetition.

Local untracked PaySim reports are not repository evidence and must not be quoted as a committed baseline.

## 4. Phase 0 PaySim Profile Contract

The profiler must produce a versioned summary containing:

| Field | Definition |
|---|---|
| total events | accepted transaction rows in profile scope |
| unique users | distinct normalized origin-user identifiers |
| events/user p50/p95/p99 | quantiles of per-user event counts |
| maximum events/user | largest per-user event count |
| top 1% traffic share | events from the highest-volume 1% of users divided by total events |
| events/user/source-step p50/p95/p99/max | quantiles and maximum of per-user event counts inside one PaySim source step |
| maximum events/source-step/user | largest same-user event count observed in one PaySim source step |
| users with 2+ events/source-step ratio | users reaching at least two events in one source step divided by profiled users |
| users with 5+ events/source-step ratio | users reaching at least five events in one source step divided by profiled users |
| transaction type distribution | count and ratio by PaySim native and normalized type |
| fraud ratio | labelled fraud rows divided by labelled rows |
| amount p50/p95/p99 | quantiles over valid transaction amounts |
| time-step distribution | event count by PaySim step or normalized source-time bucket |
| peak time-step count | maximum events in one source-time bucket |
| same-user repeat rate | events belonging to users with at least two events divided by total events |

Required metadata:

- profile schema version
- profiler script version
- dataset slug and raw filename
- input SHA-256
- accepted/rejected row count
- identifier policy version
- generated timestamp
- quantile method
- top-percent rounding rule
- `sourceTimeResolution`, currently one hour per PaySim step
- source-step boundary and quantile rules

### Profile interpretation

The profile answers:

1. Does the corpus contain repeated same-user transactions inside one source step?
2. Does it contain organic heavy users?
3. Must V3 synthesize a stronger hot-key distribution?
4. Can original time steps seed a normal arrival shape?

It does not answer how many events per second the runtime can process.

### Source-time resolution guardrail

PaySim preprocessing currently maps `eventTime` as `baseTime + step hours`. Every record in the same source step therefore has the same timestamp, while the original order within that hour is unknown.

PaySim source-step density must not be interpreted as observed density inside the five-minute runtime sliding window. Runtime-window statistics finer than the one-hour source resolution require an explicitly synthetic timestamp policy and must be labelled as synthetic workload evidence.

### Synthetic stateful workload profile

Phase 2 workload generation, rather than PaySim corpus profiling, owns runtime-window density controls and reports:

- configured `runtimeWindow`, initially aligned with `SlidingWindowProperties.window`
- events/user/runtime-window p50/p95/p99/max
- maximum events/runtime-window/user
- users with 2+, 5+, 20+, and 100+ events/runtime-window ratios as configured
- runtime-window amount-sum p50/p95/p99/max

This profile answers how Redis behaves when controlled same-user traffic is concentrated inside the runtime window. It is not an observed PaySim corpus property.

## 5. Workload Catalog

Every run uses a workload manifest with a stable `workloadId` and `workloadVersion`.

### Workload A: Volume and Correctness

Input: full or bounded PaySim replay.

Purpose:

- long-running processing
- missing-result checks
- Redis state lifecycle
- memory-growth observation
- rule regression

This workload is not automatically a throughput benchmark.

### Workload B: Normal and Capacity

Purpose: find the highest range where Lag does not continuously grow.

Use a capacity curve such as:

```text
100 EPS -> 250 -> 500 -> 750 -> 1,000 -> ...
```

Do not predeclare a desired capacity result. Determine the knee point from evidence.

### Workload C: Organic Burst

Meaning: genuinely new events arrive faster for a bounded interval.

Expected time relationship:

```text
eventTime ~= sourceSentAt ~= receivedAt
```

This relationship requires `eventTimeMode=REBASE_TO_ARRIVAL`. PaySim contributes transaction attributes, while the workload driver assigns runtime timestamps near actual emission.

### Workload D: Catch-up Burst

Meaning: an upstream source accumulates old events and sends them after recovery.

Expected time relationship:

```text
eventTime < sourceSentAt <= receivedAt
```

Use `eventTimeMode=CONTROLLED_LATENESS` so source age is intentional and reproducible.

Organic and catch-up bursts may have identical input EPS but different freshness and operational meaning.

### Workload E1: User Skew

Compare:

- uniform users
- measured PaySim distribution
- synthetic user skew, such as the top 1% of users generating 50% of traffic

This workload measures same-user concentration and state pressure. It does not prove Kafka partition skew. Record both target and achieved user concentration.

### Workload E2: Partition Skew

Pre-generate user IDs whose Kafka key hashes map to selected target partitions, then drive a configured share such as 50-70% of events to one target partition.

This workload measures partition imbalance and Consumer parallelism limits. Record the actual per-partition event distribution; configured user concentration alone is not valid partition-skew evidence.

### Workload F: Late and Out of Order

Generate controlled lateness buckets and arrival permutations. Examples include on-time, 30 seconds late, 2 minutes late, 5 minutes late, 10 minutes late, and explicit A/C/B arrival.

Phase 5 uses `load-test/workloads/v3/late-out-of-order-v1.json` for this workload. The initial policy sets `allowedLateness=5m`; events at or below that age update Redis live state by `eventTime`, while events beyond that age skip Redis live-state mutation and mark Redis-dependent rules as skipped.

The Phase 5 k6 runner must apply the manifest's `outOfOrderPattern` per user. Workload-wide bucket variety is not enough; at least one user-level sequence must contain accepted events whose event-time order differs from arrival order.

HTTP runtime workloads should avoid exact equality buckets at the allowed-lateness threshold. The client creates `eventTime`, while app-api creates `receivedAt` after network and service delay. Use near-boundary accepted buckets for runtime and keep exact equality checks in controlled tests.

### Workload G: Historical Replay

Replay old events at a controlled rate with `eventTimeMode=PRESERVE_SOURCE_TIME`. Verify that live state, live Lag, and live latency evidence are not contaminated.

## 6. Workload Manifest

Each run records at least:

```text
workloadId
workloadVersion
datasetVersion
driverType
targetEps
duration
eventLimit
userCardinality
userDistribution
heavyUserRatio
targetUserConcentration
achievedUserConcentration
targetPartitionDistribution
achievedPartitionDistribution
partitionAffinityStrategy
sourceProfile
eventTimeMode
sourceTimeResolution
timeScaleFactor
latenessProfile
replayRate
randomSeed
```

`driverType` must distinguish:

- `HTTP_K6`
- `HTTP_SOURCE_EMULATOR`
- `KAFKA_DIRECT_PRODUCER`

Results from different driver types are separate experiments unless the driver overhead is explicitly controlled.

`eventTimeMode` must be one of:

| Mode | Intended workload | Policy |
|---|---|---|
| `PRESERVE_SOURCE_TIME` | historical replay | retain the PaySim-derived timestamp |
| `REBASE_TO_ARRIVAL` | normal, capacity, organic burst | assign `eventTime` near actual emission while retaining PaySim transaction attributes |
| `CONTROLLED_LATENESS` | catch-up, late, out-of-order | derive `eventTime` from the configured lateness and ordering profile |

`sourceTimeResolution` records the source corpus resolution, currently `1h` for PaySim. `timeScaleFactor` is optional and must describe any explicit source-timeline scaling. A simple rebase of the entire hourly PaySim timeline is not `REBASE_TO_ARRIVAL`: compressed replay can otherwise generate future `eventTime` values and violate the API's future-time validation.

Every workload report must repeat the effective `eventTimeMode`, `sourceTimeResolution`, and `timeScaleFactor` so freshness evidence can be reproduced without inferring timestamp behavior from the workload name.

## 7. Canonical Time Model

| Field | Meaning | Owner |
|---|---|---|
| `eventTime` | time the financial transaction occurred | source dataset/system |
| `sourceSentAt` | time the upstream source dispatched the event | source emulator or external source |
| `receivedAt` | time app-api accepted the event | app-api |
| `kafkaTimestamp` | Kafka record timestamp | Kafka producer/broker policy |
| `consumerStartedAt` | time listener processing started | app-consumer |
| `detectedAt` | time fraud processing and required result persistence finished | app-consumer |

Existing runtime events carry `eventTime` and `receivedAt`. Phase 0 keeps source metadata in the workload manifest and driver report only. It does not add `sourceSentAt` to the event schema or Kafka headers because the normal baseline does not require that contract cost and no source emulator yet owns a trustworthy dispatch timestamp. Source-processing and source-transport delay therefore remain unavailable until a later phase introduces and validates that owner.

Before using `kafkaTimestamp` for a latency metric, Phase 0 must record:

- whether the record timestamp type is `CreateTime` or `LogAppendTime`
- whether the timestamp is owned by the producer or broker
- the effective broker `log.message.timestamp.type` policy
- the exact delay interval represented by the metric

## 8. Delay Attribution

### Source processing delay

```text
sourceSentAt - eventTime
```

Large values indicate an upstream/source-side delay candidate.

### Source transport delay

```text
receivedAt - sourceSentAt
```

This is observable only when `sourceSentAt` is trustworthy and clock assumptions are documented.

### Ingress age

```text
receivedAt - eventTime
```

Ingress age combines source processing and transport. It is available without Kafka or Consumer timestamps.

### Kafka delivery or queue delay

```text
consumerStartedAt - kafkaTimestamp
```

The metric name depends on the timestamp policy:

- With `CreateTime`, use `fraud.kafka.producer.to.consumer.delay`. This interval includes producer-to-broker transport, broker handling, and backlog before Consumer processing.
- With `LogAppendTime`, `fraud.kafka.queue.latency` may be used because the interval starts at broker append time. This requires an explicit broker policy and verified runtime configuration.

Do not expose `fraud.kafka.queue.latency` while the timestamp type is unresolved. A large value under either policy can be caused by insufficient Consumer capacity, a hot partition, Consumer downtime, or burst backlog; it does not prove a Kafka broker defect.

### Consumer service time

```text
detectedAt - consumerStartedAt
```

Consumer service time must be decomposed into Redis state, rule processing, and result-sink stages.

## 9. Lateness and Ordering Contract

Phase 0 must not create two identical metrics for ingress age and lateness.

- `ingress age` is immediately definable as `receivedAt - eventTime`.
- `lateness` is defined in Phase 5 as `receivedAt - eventTime` for Redis live-state eligibility.
- `out-of-order` requires a per-user ordering reference, not only a positive duration.

Phase 5 defines:

- allowed lateness: `fraud.sliding-window.allowed-lateness`, default `5m`
- live-state update policy: accepted late and out-of-order events update Redis by `eventTime` score
- too-late policy: events beyond allowed lateness skip Redis mutation and skip Redis-dependent rules
- out-of-order reference: accepted events are ordered by event-time score inside the user ZSET
- historical handling: still separate from live late/out-of-order handling and remains Phase 7 scope

Detailed runtime policy is in [V3 Phase 5 Event-Time Lateness Semantics Plan](51-v3-phase5-event-time-lateness-plan.md). Runtime evidence remains a separate step.

## 10. Source Emulator Contract

Planned source profiles:

| Profile | Behavior |
|---|---|
| `NORMAL` | short source processing delay with steady dispatch |
| `SLOW_SOURCE` | controlled source-side delay before dispatch |
| `BATCH_CATCHUP` | hold events, then release accumulated events in a burst |

The emulator must apply the manifest's `eventTimeMode`, create `sourceSentAt`, and produce a report containing configured versus achieved delay and EPS. It preserves original `eventTime` only for `PRESERVE_SOURCE_TIME`; other modes must record the generated timestamp policy.

Phase 0 decisions:

- Source profile metadata remains test-only in the versioned manifest and driver report; it is not propagated in the event contract or Kafka headers.
- `REBASE_TO_ARRIVAL` uses the k6 host's UTC wall clock for `eventTime`; app-api and app-consumer use their JVM clocks, and Kafka `CreateTime` uses the producer clock.
- No cross-host clock correction is applied. Negative ingress or producer-to-Consumer durations are rejected from Timer populations instead of being clamped to zero.
- Events without `sourceSentAt` remain backward compatible. Source-processing and transport-delay meters are not registered in Phase 0, so their dashboard absence is expected.
- k6 is sufficient for the normal HTTP baseline. Controlled source delay and catch-up ownership require a dedicated source emulator decision in Phase 6.

## 11. Live and Replay Isolation Contract

Candidate isolation boundaries:

- separate topics
- separate Consumer groups
- separate Redis namespaces
- bounded replay rate
- separate dashboards and evidence labels

Phase 0 documents these candidates; Phase 7 selects and implements them based on live/replay experiments.

Historical business-event age must not be compared with live freshness SLA. Replay reports must identify workload type and exclude replay E2E values from live claims.

## 12. Validation Rules

- No raw/full PaySim data is committed.
- Profile reports must not contain raw user/account identifiers.
- Quantile and concentration definitions are deterministic.
- Workload manifests are immutable for a completed experiment.
- Target EPS and achieved EPS are both recorded.
- Source and replay profiles cannot silently default to organic live traffic.
- Missing source timestamps are reported, not replaced with invented values.

## 13. Source

- [High-Throughput Fraud Stream Processing System (Notion)](https://app.notion.com/p/3b59924eda6e800b9d1dd2f0bba987ea)
