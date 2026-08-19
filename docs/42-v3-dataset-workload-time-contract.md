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

### Profile interpretation

The profile answers:

1. Does the corpus contain enough repeated users for sliding-window experiments?
2. Does it contain organic heavy users?
3. Must V3 synthesize a stronger hot-key distribution?
4. Can original time steps seed a normal arrival shape?

It does not answer how many events per second the runtime can process.

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

### Workload D: Catch-up Burst

Meaning: an upstream source accumulates old events and sends them after recovery.

Expected time relationship:

```text
eventTime < sourceSentAt <= receivedAt
```

Organic and catch-up bursts may have identical input EPS but different freshness and operational meaning.

### Workload E: Skew and Hot Key

Compare:

- uniform users
- measured PaySim distribution
- synthetic skew, such as top 1% users generating 50% of traffic

Record both configured and achieved user concentration.

### Workload F: Late and Out of Order

Generate controlled lateness buckets and arrival permutations. Examples include on-time, 30 seconds late, 2 minutes late, 5 minutes late, 10 minutes late, and explicit A/C/B arrival.

### Workload G: Historical Replay

Replay old events at a controlled rate. Verify that live state, live Lag, and live latency evidence are not contaminated.

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
userDistribution
heavyUserRatio
sourceProfile
latenessProfile
replayRate
randomSeed
```

`driverType` must distinguish:

- `HTTP_K6`
- `HTTP_SOURCE_EMULATOR`
- `KAFKA_DIRECT_PRODUCER`

Results from different driver types are separate experiments unless the driver overhead is explicitly controlled.

## 7. Canonical Time Model

| Field | Meaning | Owner |
|---|---|---|
| `eventTime` | time the financial transaction occurred | source dataset/system |
| `sourceSentAt` | time the upstream source dispatched the event | source emulator or external source |
| `receivedAt` | time app-api accepted the event | app-api |
| `kafkaTimestamp` | Kafka record timestamp | Kafka producer/broker policy |
| `consumerStartedAt` | time listener processing started | app-consumer |
| `detectedAt` | time fraud processing and required result persistence finished | app-consumer |

Existing runtime events already carry `eventTime` and `receivedAt`. `sourceSentAt` and source metadata are planned contract candidates, not implemented fields.

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

### Kafka queue delay

```text
consumerStartedAt - kafkaTimestamp
```

Large values can be caused by insufficient Consumer capacity, a hot partition, Consumer downtime, or burst backlog. They do not prove a Kafka broker defect.

### Consumer service time

```text
detectedAt - consumerStartedAt
```

Consumer service time must be decomposed into Redis state, rule processing, and result-sink stages.

## 9. Lateness and Ordering Contract

Phase 0 must not create two identical metrics for ingress age and lateness.

- `ingress age` is immediately definable as `receivedAt - eventTime`.
- `lateness` is reserved for Phase 5 until the project defines its comparison reference, allowed-lateness threshold, and state-update policy.
- `out-of-order` requires a per-user ordering reference, not only a positive duration.

Phase 5 must decide:

- allowed lateness
- live-state update policy
- too-late policy
- out-of-order detection reference
- historical handling

## 10. Source Emulator Contract

Planned source profiles:

| Profile | Behavior |
|---|---|
| `NORMAL` | short source processing delay with steady dispatch |
| `SLOW_SOURCE` | controlled source-side delay before dispatch |
| `BATCH_CATCHUP` | hold events, then release accumulated events in a burst |

The emulator must preserve the original `eventTime`, create `sourceSentAt`, and produce a report containing configured versus achieved delay and EPS.

Open decisions before implementation:

- whether source metadata belongs in the Kafka event contract or test-only headers
- clock source and skew assumptions
- compatibility behavior for events without `sourceSentAt`
- whether k6 can represent the profile accurately or a dedicated emulator is required

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
