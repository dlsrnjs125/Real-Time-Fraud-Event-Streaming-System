# V3 Phase 0 Dataset, Workload, and Stream Observability Foundation

## 1. Status

- Documentation setup: complete in this branch
- Phase 0 implementation: not started
- Measured V3 baseline: not available

This document defines what must be built and verified after the documentation branch is reviewed. It does not claim that the profiler, Source emulator, new metrics, workload manifests, or dashboard panels exist.

## 2. Phase Goal

Phase 0 must establish enough data and observability foundation to answer:

> When fraud detection is late, can the system distinguish source delay, transport/ingress age, Kafka backlog, Consumer service time, Redis state cost, rule cost, and result-sink cost?

## 3. Phase 0 Workstreams

### Phase 0-A: Dataset Profiling

Build a streaming PaySim profiler that produces the contract in [V3 Dataset, Workload, and Time Contract](42-v3-dataset-workload-time-contract.md).

Required output:

- total and unique-user counts
- events/user p50/p95/p99/max
- top 1% traffic share
- transaction-type counts and ratios
- fraud ratio
- amount p50/p95/p99
- time-step distribution and peak
- same-user repeat rate
- report metadata and deterministic definitions

### Phase 0-B: Workload Contract

Define versioned manifests for:

- Volume/Correctness
- Normal/Capacity
- Organic Burst
- Catch-up Burst
- Skew/Hot-Key
- Late/Out-of-Order
- Historical Replay

At least one normal baseline manifest must be committed before Phase 0 closes.

### Phase 0-C: Stream Observability

Expose stage metrics and verify them with real traffic.

### Phase 0-D: Event Freshness

Finalize source timestamp propagation and implement only the freshness metrics whose boundaries are trustworthy.

## 4. Current Repository Gap Analysis

| Area | Existing foundation | Phase 0 gap |
|---|---|---|
| PaySim | normalization, validation, sample, replay, evaluation | user concentration, amount quantiles, time profile, repeat-rate profiler |
| Workload | k6 smoke/normal/peak/duplicate/Redis-down | versioned A-G manifests and organic/catch-up/skew/late semantics |
| Kafka | `userId` key, manual ack, exporter Lag panel | ingress/Consumer rates, partition-first evidence, Lag growth/drain |
| Redis | sliding window, degraded mode, aggregate latency | state-cost contract, operations/event, scaling evidence |
| Rule | deterministic rule engine | dedicated processing Timer |
| Sink | PostgreSQL durable results and logs | result-sink Timer and bottleneck evidence |
| Time | `eventTime`, `receivedAt`, Kafka timestamp available | source dispatch contract, ingress age, trustworthy attribution |
| Replay | PaySim HTTP replay | live/replay state and metric isolation |

## 5. Application Metric Contract

Rates should be derived from monotonic Counters with PromQL, rather than maintained as mutable application gauges.

| Stored meter | Type | Boundary or event |
|---|---|---|
| `fraud.stream.ingress.total` | Counter | event accepted for the selected stream-ingress boundary |
| `fraud.stream.consumed.total` | Counter | Kafka record delivered to the fraud Consumer |
| `fraud.kafka.queue.latency` | Timer | Kafka timestamp to Consumer processing start |
| `fraud.redis.state.latency` | Timer | complete Redis state update/read operation |
| `fraud.rule.processing.latency` | Timer | Rule Engine evaluation |
| `fraud.result.sink.latency` | Timer | required detection-result sink operation |
| `fraud.consumer.service.latency` | Timer | Consumer start to detection/result completion |
| `fraud.event.source.processing.delay` | Timer | `sourceSentAt - eventTime`, when source timestamp exists |
| `fraud.event.transport.delay` | Timer | `receivedAt - sourceSentAt`, when source timestamp exists |
| `fraud.event.ingress.age` | Timer | `receivedAt - eventTime` |

Derived queries:

```promql
rate(fraud_stream_ingress_total[1m])
rate(fraud_stream_consumed_total[1m])
```

Do not add `fraud.event.lateness` in Phase 0 unless the lateness reference and allowed-lateness policy are defined. Do not label metrics with eventId, traceId, userId, accountId, partition offset, or raw source identifiers.

## 6. Kafka and Infrastructure Signals

Required Kafka evidence:

- total Consumer group Lag
- partition Lag
- consumed records/sec
- partition assignment
- rebalance count
- partition incoming rate when available

Derived stream behavior:

```text
Lag growth rate = positive change in Lag per unit time during overload
Lag drain rate  = decrease in Lag per unit time after input returns below capacity
```

The exact PromQL, reset handling, and sampling interval must be fixed before reporting values.

Dependency evidence:

- Redis CPU and memory
- Redis command/state latency
- PostgreSQL/HikariCP only when result-sink evidence points to that boundary
- Consumer process CPU and memory

## 7. Grafana Stream Dashboard Contract

Minimum panels:

1. ingress EPS and Consumer EPS
2. total Lag
3. partition Lag
4. Lag growth/drain rate
5. Kafka queue p95/p99
6. Consumer service p95/p99
7. Redis state p95/p99
8. Rule p95/p99
9. Result sink p95/p99
10. ingress age and source-delay signals
11. Consumer assignment and rebalance count
12. Redis/Consumer resource signals

Generic API request panels may remain, but they are not the primary V3 evidence surface.

## 8. Implementation Sequence

### Step 1: Profiler contract and fixture

- add profiler report schema
- add small deterministic fixture tests
- verify privacy and raw-data policy
- do not commit full profile output

### Step 2: Workload manifests

- define workload schema and versioning
- encode one normal baseline
- preserve target versus achieved EPS
- distinguish HTTP and direct-Kafka drivers

### Step 3: Time and Source decision

- decide event field versus Kafka header for source metadata
- document backward compatibility
- document clock-skew assumptions
- define behavior when `sourceSentAt` is absent

### Step 4: Stream-stage metrics

- add Counters and Timers at exact boundaries
- enable histogram buckets needed for p50/p95/p99
- add low-cardinality tests
- confirm duplicate, degraded, and failure-path populations

### Step 5: Partition and resource visibility

- confirm actual exporter/client metric names
- add total and partition Lag queries
- add Redis and Consumer resource panels

### Step 6: Baseline run

- execute the committed normal workload
- capture experiment fingerprint
- verify every expected metric has data
- record gaps as `TBD`, not estimates

## 9. Required Design Decisions Before Code

- precise ingress boundary: API accepted, Kafka publish success, or both as distinct counters
- source metadata transport: event schema versus headers
- clock and skew policy across source, API, broker, and Consumer
- quantile algorithm for the PaySim profiler
- normal baseline workload shape and duration
- workload manifest storage path
- partition incoming-rate source
- result-sink boundary: fraud result only or all mandatory persistence
- duplicate/failure-path inclusion for each Timer

## 10. Evidence Template

### Experiment fingerprint

| Field | Value |
|---|---|
| Commit SHA | TBD |
| Dataset/version/hash | TBD |
| Workload ID/version | TBD |
| Driver type | TBD |
| Host/Docker resources | TBD |
| Kafka/Redis/PostgreSQL versions | TBD |
| Partition count | TBD |
| Consumer count/concurrency | TBD |
| Redis window config | TBD |

### Baseline stream evidence

| Metric | p50/current | p95/peak | p99/final |
|---|---:|---:|---:|
| achieved ingress EPS | TBD | TBD | TBD |
| Consumer EPS | TBD | TBD | TBD |
| total Lag | TBD | TBD | TBD |
| max partition Lag | TBD | TBD | TBD |
| Kafka queue latency | TBD | TBD | TBD |
| Consumer service latency | TBD | TBD | TBD |
| Redis state latency | TBD | TBD | TBD |
| Rule latency | TBD | TBD | TBD |
| Result sink latency | TBD | TBD | TBD |
| Ingress age | TBD | TBD | TBD |

## 11. Phase 0 Completion Criteria

Phase 0 is `Done` only when:

- PaySim profile is generated and validated
- all seven workload roles are documented and versioned
- timestamp ownership and source-emulator decision are recorded
- stream-stage Counters and Timers are exposed
- total and partition Lag are visible
- event freshness is visible at the supported boundaries
- one normal baseline workload is committed and executed
- Grafana stream dashboard renders the expected signals
- evidence includes a reproducible fingerprint
- metric and workload docs match implementation

Completion statement:

> The repository can explain whether delayed fraud processing is associated with source age, Kafka backlog, Consumer/Redis processing, rule execution, or the result sink within the boundaries it actually observes.

## 12. Explicit Non-Goals for Phase 0

- Redis optimization
- PostgreSQL tuning
- Consumer scale-out optimization
- hot-key mitigation
- stateful redelivery redesign
- allowed-lateness implementation
- Source emulator implementation unless required to validate the selected timestamp contract
- replay isolation implementation
- Retry/DLT redesign
- downstream SSE/WebSocket delivery
- measured capacity claims

## 13. Pre-Implementation Checklist

- [ ] Review the three V3 direction documents together.
- [ ] Confirm Phase 0 metric names and populations.
- [ ] Confirm profiler report schema and quantile definitions.
- [ ] Confirm workload manifest path and schema.
- [ ] Decide source metadata propagation.
- [ ] Separate HTTP and direct-Kafka experiments.
- [ ] Define baseline environment fingerprint.
- [ ] Keep V3 Phase 1+ code out of the Phase 0 implementation branch.

## 14. Source

- [High-Throughput Fraud Stream Processing System (Notion)](https://app.notion.com/p/3b59924eda6e800b9d1dd2f0bba987ea)
