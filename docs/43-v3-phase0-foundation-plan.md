# V3 Phase 0 Dataset, Workload, and Stream Observability Foundation

## 1. Status

- Documentation setup: complete
- Phase 0 implementation: complete; see [Phase 0 Foundation Evidence](44-v3-phase0-foundation-evidence.md)
- Measured V3 baseline: local low-rate baseline recorded in the evidence document

This document remains the Phase 0 plan and contract. Actual implementation, local measurements, limitations, and verification commands are recorded separately in the evidence document. A Source emulator remains outside Phase 0.

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
- events/user/source-step p50/p95/p99/max
- maximum events/source-step/user
- users with 2+ and 5+ events/source-step ratios
- `sourceTimeResolution=1h`
- transaction-type counts and ratios
- fraud ratio
- amount p50/p95/p99
- time-step distribution and peak
- same-user repeat rate
- report metadata and deterministic definitions

The PaySim profiler must not report five-minute density from hourly source steps. Synthetic runtime-window density belongs to the workload manifest/report and is Phase 2 input evidence.

### Phase 0-B: Workload Contract

Define versioned manifests for:

- Volume/Correctness
- Normal/Capacity
- Organic Burst
- Catch-up Burst
- User Skew and Partition Skew
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
| PaySim | normalization, validation, sample, replay, evaluation | user concentration, source-step density, amount quantiles, time profile, repeat-rate profiler |
| Workload | k6 smoke/normal/peak/duplicate/Redis-down | versioned A-G manifests, event-time modes, synthetic runtime-window density, and organic/catch-up/skew/late semantics |
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
| `fraud.stream.intake.receipt.persisted.total` | Counter | initial transaction receipt persisted before Kafka publish |
| `fraud.stream.kafka.publish.success.total` | Counter | transaction event publish completed successfully |
| `fraud.stream.kafka.publish.failure.total` | Counter | transaction event publish failed after receipt persistence |
| `fraud.stream.consumer.delivery.total` | Counter | Kafka delivery attempt observed by the fraud Consumer, including redelivery |
| `fraud.kafka.producer.to.consumer.delay` | Timer, conditional | producer `CreateTime` to Consumer processing start |
| `fraud.kafka.queue.latency` | Timer, conditional | broker `LogAppendTime` to Consumer processing start |
| `fraud.redis.state.latency` | Timer | complete Redis state update/read operation |
| `fraud.rule.processing.latency` | Timer | Rule Engine evaluation |
| `fraud.result.sink.latency` | Timer | required detection-result sink operation |
| `fraud.consumer.service.latency` | Timer | Consumer start to detection/result completion |
| `fraud.event.source.processing.delay` | Deferred | requires a trustworthy `sourceSentAt` owner; not registered in Phase 0 |
| `fraud.event.transport.delay` | Deferred | requires a trustworthy `sourceSentAt` owner; not registered in Phase 0 |
| `fraud.event.ingress.age` | Timer | `receivedAt - eventTime` |

Derived queries:

```promql
rate(fraud_stream_intake_receipt_persisted_total[1m])
rate(fraud_stream_kafka_publish_success_total[1m])
rate(fraud_stream_kafka_publish_failure_total[1m])
rate(fraud_stream_consumer_delivery_total[1m])
```

Counter populations are fixed as follows:

- Receipt persisted increments once for a new durable receipt; validation failures and duplicate requests are excluded.
- Publish success increments once when that receipt's Kafka publish completes successfully.
- Publish failure increments once when that receipt's Kafka publish path fails and the failed status remains durable.
- Consumer delivery increments for every listener delivery attempt, including redelivery.

Consumer delivery can therefore exceed publish success. This difference is expected evidence for Phase 4, not necessarily missing or duplicated business results. Phase 0 must choose instrumentation points that preserve these logical populations across transaction commit and rollback behavior.

Only one Kafka delay Timer is selected after verifying `CreateTime` versus `LogAppendTime`, timestamp ownership, and the effective broker `log.message.timestamp.type`. Do not label producer `CreateTime` delay as queue latency.

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

1. receipt persisted, Kafka publish success/failure, and Consumer delivery EPS
2. total Lag
3. partition Lag
4. Lag growth/drain rate
5. Kafka producer-to-Consumer or queue delay p95/p99, according to the verified timestamp policy
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
- preserve target versus achieved user and partition distributions
- define the partition-affinity strategy for partition-skew workloads
- define `eventTimeMode`, `sourceTimeResolution`, and optional `timeScaleFactor`
- distinguish HTTP and direct-Kafka drivers

### Step 3: Time and Source decision

- decide event field versus Kafka header for source metadata
- decide Kafka `CreateTime` versus `LogAppendTime` and verify broker `log.message.timestamp.type`
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

- exact populations for receipt persistence, Kafka publish success/failure, and Consumer delivery counters
- Kafka record timestamp type, owner, broker policy, and corresponding delay metric name
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
| Event-time mode/source resolution/time scale | TBD |
| Host/Docker resources | TBD |
| Kafka/Redis/PostgreSQL versions | TBD |
| Partition count | TBD |
| Consumer count/concurrency | TBD |
| Redis window config | TBD |

### Throughput and backlog evidence

| Metric | Baseline/average | Peak | Final |
|---|---:|---:|---:|
| Receipt persisted EPS | TBD | TBD | TBD |
| Kafka publish success EPS | TBD | TBD | TBD |
| Kafka publish failure EPS | TBD | TBD | TBD |
| Consumer delivery EPS | TBD | TBD | TBD |
| Total Lag | TBD | TBD | TBD |
| Max partition Lag | TBD | TBD | TBD |

### Latency evidence

| Metric | p50 | p95 | p99 |
|---|---:|---:|---:|
| Producer-to-Consumer or Kafka queue delay | TBD | TBD | TBD |
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

## 13. Reviewed Implementation Checklist

- [x] Review the three V3 direction documents together.
- [x] Confirm Phase 0 metric names and populations.
- [x] Confirm profiler report schema and nearest-rank quantile definitions.
- [x] Store manifest schema and baseline under `load-test/workloads/v3`.
- [x] Keep PaySim source-step and synthetic runtime-window profiles separate.
- [x] Use `REBASE_TO_ARRIVAL` for the normal baseline to remain compatible with API future-time validation.
- [x] Keep source metadata in test-only manifest/report artifacts in Phase 0.
- [x] Verify broker `CreateTime` and expose producer-to-Consumer delay only.
- [x] Separate experiments by `driverType`.
- [x] Record the local baseline environment fingerprint.
- [x] Keep V3 Phase 1+ optimization and semantic changes out of Phase 0.

## 14. Source

- [High-Throughput Fraud Stream Processing System (Notion)](https://app.notion.com/p/3b59924eda6e800b9d1dd2f0bba987ea)
