# V3 High-Throughput Fraud Stream Processing Direction

## 1. Document Status

- Status: V3 direction baseline
- Scope: documentation setup before V3 Phase 0 implementation
- Runtime implementation: not started
- Source reviewed: 2026-08-19

This document supersedes the previous V3 Production Hardening plan. The previous plan treated PostgreSQL bottleneck tuning, Retry/DLT recovery, real-time result delivery, and downstream backpressure as the primary sequence. The new V3 direction centers on stream throughput, user-state processing, and event freshness.

## 2. Project Definition

The project is a stateful stream-processing system that reproduces high-volume, burst, hot-key, late-event, and historical-replay conditions over financial transaction events.

The central question is:

> How much financial event traffic can the system process while preserving per-user recent state, and can it explain whether delay originated at the source, Kafka backlog, state processing, rule execution, or the result sink?

Using Kafka and Redis is not itself the result. V3 must provide measured evidence for:

- sustainable event throughput
- backlog growth and drain behavior
- partition-level imbalance
- user-state processing cost
- event-time and arrival-time differences
- stateful redelivery semantics
- live and historical replay isolation

## 3. Project Boundary

This repository is not a financial ledger or settlement system. It consumes transaction events after a financial action has occurred and produces fraud-detection results.

### Primary V3 responsibility

```text
Financial Event Stream
  -> Kafka Partition
  -> Consumer Parallelism
  -> Redis User State
  -> Fraud Rule
  -> Result Sink
```

V3 focuses on:

- Kafka partitions and Consumer Lag
- sustainable throughput and backlog recovery
- Redis sliding-window state
- hot users and partition skew
- event time, late events, and out-of-order arrival
- source delay and catch-up traffic
- replay isolation

### Safety mechanisms retained but not promoted to primary V3 phases

- PostgreSQL unique constraints and result idempotency
- manual acknowledgement after required persistence
- bounded retry and DLT
- controlled DLT reprocessing
- Redis degraded mode
- audit records

These mechanisms remain mandatory correctness and operational safeguards. V3 does not remove them, but it does not organize the main phase sequence around them.

### Explicitly outside the V3 core theme

- ledger consistency and reconciliation
- write suspension and fail-closed settlement behavior
- PostgreSQL disaster recovery
- generic incident-analysis platforms
- generic API latency attribution
- Redis cache-versus-lock comparisons
- Kubernetes or a broad microservice split
- production fraud-model quality claims

## 4. Three V3 Axes

Every V3 phase must improve or clarify at least one of these axes.

### Axis 1: Throughput

Question:

> How fast can events enter and leave the stream pipeline, and how quickly can backlog recover?

Core evidence:

- ingress EPS
- Consumer EPS
- total Consumer Lag
- partition Lag
- Lag growth rate
- Lag drain rate
- recovery time
- Consumer scaling efficiency

### Axis 2: Stateful Processing

Question:

> How quickly and correctly can the system maintain recent per-user transaction state?

Core evidence:

- events per user
- sliding-window size
- Redis operations per event
- Redis p95/p99
- hot-user ratio
- Redis CPU and memory
- stateful redelivery outcome
- risk-score and decision stability

### Axis 3: Event Freshness

Question:

> When did the event occur, when was it dispatched and received, and at which boundary did it become late?

Core evidence:

- event time
- source dispatch time
- ingress time
- Kafka queue delay
- Consumer service time
- late and out-of-order event count
- historical replay isolation

## 5. Technology Responsibilities

### Kafka

Kafka is responsible for:

1. separating API intake from fraud processing
2. buffering bursts when input EPS temporarily exceeds Consumer EPS
3. preserving order within a `userId` partition
4. exposing partition-based parallelism and its scaling limit

Kafka Lag is not automatically an error or a broker bottleneck. It is backlog evidence that must be interpreted with ingress rate, Consumer rate, partition distribution, Redis latency, rule latency, and sink latency.

### Redis

Redis is an online state store for recent per-user transaction state, not a generic DB cache and not durable truth.

V3 treats the following as first-class experiment variables:

- events per user
- window duration
- hot-user concentration
- Redis operations per event
- state representation and cleanup cost

### PostgreSQL

PostgreSQL is the durable sink for detection results, processing logs, DLT metadata, and audit records.

PostgreSQL can constrain stream throughput, but V3 must not assume it is the bottleneck before measurement. It is investigated when result-sink latency, HikariCP, and PostgreSQL evidence identify it as the limiting stage.

### PaySim

PaySim supplies a large synthetic transaction corpus for:

- dataset profiling
- full-volume processing
- rule regression
- historical replay
- user and transaction distribution analysis

Dataset size does not prove runtime throughput. PaySim is data input; a workload contract determines event velocity and arrival shape.

### Load generators

k6 exercises the HTTP intake path. If k6 and the API cannot generate the EPS needed to isolate Kafka/Consumer capacity, a separate Kafka performance producer may be introduced later.

Results from these drivers must stay separate:

```text
HTTP API intake experiment != direct Kafka producer experiment
```

### Source emulator

A Source/PG emulator is a planned V3 boundary for controlling source-side delay and catch-up delivery. Its contract may add:

- `sourceSystem`
- `sourceSentAt`
- `deliveryProfile`

Adding fields to the runtime event schema is not authorized by this documentation setup. Phase 0 must evaluate compatibility and observability value before changing the contract.

### Prometheus and Grafana

V3 dashboards prioritize stream behavior over generic HTTP monitoring:

- ingress and Consumer rates
- total and partition Lag
- Lag growth and drain
- Kafka producer-to-Consumer or queue delay, according to the verified Kafka timestamp policy
- Redis state latency
- rule latency
- result-sink latency
- event freshness

## 6. Data and Workload Principle

```text
Dataset volume != runtime event velocity
```

A dataset with millions of rows can be replayed at 10 EPS or 10,000 EPS. V3 therefore versions the dataset and workload independently. The detailed contract is in [V3 Dataset, Workload, and Time Contract](42-v3-dataset-workload-time-contract.md).

## 7. Common Experiment Rule

Every V3 experiment follows this sequence:

```text
Baseline
  -> Controlled Workload or Injection
  -> Observation
  -> Hypothesis
  -> Isolation
  -> Improvement
  -> Same-load Re-test
  -> Trade-off
  -> Evidence
```

Rules:

- Establish a normal baseline before injecting a problem.
- Change one primary variable at a time when isolating a cause.
- Compare before and after only when workload and environment fingerprints match.
- Observe Kafka, Consumer, Redis, Rule, sink, and freshness on the same time range.
- Record failed hypotheses and inconclusive results.
- Do not promote fixture or smoke-test results to capacity claims.

## 8. V3 Phase Map

| Phase | Theme | Primary question | Status |
|---:|---|---|---|
| 0 | Dataset, Workload, and Stream Observability Foundation | Are data, time, workload, and measurement contracts sufficient for later experiments? | Done |
| 1 | Sustainable Throughput and Backlog Recovery | What EPS is sustainable and how quickly does backlog drain after a burst? | Done |
| 2 | Stateful Sliding-Window Scaling | How does user-state size affect Redis cost and Consumer throughput? | Done |
| 3 | Kafka Partition Skew and Consumer Parallelism | How far does Consumer scale-out help under uniform and skewed keys? | Done |
| 4 | Redelivery and Stateful Processing Semantics | Does redelivery preserve Redis state and subsequent fraud decisions? | Done |
| 5 | Event-Time, Late, and Out-of-Order Processing | What state-update policy preserves window meaning for late arrivals? | Done |
| 6 | External Delay and Catch-up Burst | Can organic bursts be distinguished from delayed upstream catch-up? | Implementation ready; evidence pending |
| 7 | Historical Replay Isolation | Can replay run without contaminating live state and live latency? | Not started |
| Optional 8 | Downstream Streaming and Backpressure | Can slow clients be isolated after the core stream is proven? | Deferred |

### Phase 0: Dataset, Workload, and Stream Observability Foundation

Establish:

- PaySim profile
- workload A-G contracts
- PaySim source-step profile and separate synthetic runtime-window profile
- separate user-skew and partition-skew workloads
- Kafka `CreateTime`/`LogAppendTime` timestamp semantics
- Source emulator contract decision
- stream-stage metrics
- total and partition Lag visibility
- event-freshness visibility
- one baseline workload
- stream-oriented Grafana dashboard

Detailed preparation and completion criteria are in [V3 Phase 0 Foundation Plan](43-v3-phase0-foundation-plan.md).

### Phase 1: Sustainable Throughput and Backlog Recovery

Build a capacity curve rather than choosing a desired TPS in advance.

Measure:

- sustainable EPS
- knee point where Lag starts to grow
- peak Lag
- Lag growth rate
- Lag drain rate
- recovery time after an organic burst

### Phase 2: Stateful Sliding-Window Scaling

Vary:

- events per user
- synthetic events per user inside the configured runtime window
- window size
- hot-user ratio
- Redis operations per event

Any optimization such as pipelining, multi-get, Lua, aggregate state, or data-structure changes must be justified by measured Redis and Consumer evidence.

PaySim's hourly source steps cannot establish observed five-minute density. Phase 2 must control runtime-window timestamps synthetically and label those results separately from corpus statistics.

### Phase 3: Kafka Partition Skew and Consumer Parallelism

Compare uniform partition traffic with a controlled partition-affinity workload across Consumer counts. Record achieved per-partition shares, confirm idle Consumers when concurrency exceeds usable partitions, and quantify the limit imposed by a hot partition. User concentration is separate state-pressure evidence and is not a substitute for partition distribution.

### Phase 4: Redelivery and Stateful Processing Semantics

Inject failures:

- before Redis update
- after Redis update but before result persistence
- after result persistence but before acknowledgement

Verify Redis count/amount, stored result, risk score, decision, and the next event's result. The goal is stateful-processing idempotency, not only duplicate-row prevention.

### Phase 5: Event-Time, Late, and Out-of-Order Processing

Test on-time, late, too-late, and out-of-order events. Define allowed lateness, live-state update policy, too-late handling, and evidence before implementation.

### Phase 6: External Delay and Catch-up Burst

Use controlled source profiles such as normal, slow source, and batch catch-up. Distinguish new market activity from accumulated upstream events arriving together.

### Phase 7: Historical Replay Isolation

Compare live-only, replay-only, and concurrent live/replay operation. Evaluate topic, Consumer group, Redis namespace, and replay-rate isolation based on evidence.

### Optional Phase 8: Downstream Streaming and Backpressure

SSE/WebSocket and slow-client handling remain deferred until the core stream-processing phases are supported by evidence.

## 9. Retry and DLT Position

Retry and DLT are supporting mechanisms, not independent headline phases in V3.

The operational question is:

> Can one poison event avoid blocking partition progress for an unbounded time?

Any later retry change must define a bounded retry budget and preserve the existing correctness rules. That work belongs to the phase whose workload or failure semantics require it.

## 10. Evidence Standard

Every experiment records:

### Experiment fingerprint

- commit SHA
- dataset ID, source hash, and dataset version
- workload ID and version
- host CPU and memory
- Docker CPU and memory limits
- Kafka, Redis, PostgreSQL versions
- partition count
- Consumer count and concurrency
- Redis window configuration
- result-sink configuration when relevant

### Workload description

- target and achieved EPS
- duration and event count
- user distribution
- heavy-user ratio
- event-time mode, source resolution, and time scale
- source delivery profile
- lateness profile
- replay rate
- load-driver type

### Stream evidence

- ingress and Consumer rates
- total and partition Lag
- Lag growth and drain rates
- Kafka producer-to-Consumer or queue-delay p95/p99, according to the verified timestamp policy
- Redis state p95/p99
- rule p95/p99
- result-sink p95/p99
- source delay and event-age signals
- missing, duplicate, degraded, and DLT counts when relevant

## 11. Overclaim Guardrails

Do not write:

> The system handles high-volume traffic.

Write measured scope:

> In the recorded local Docker environment, workload X sustained Y EPS for Z minutes without continuously growing Consumer Lag; p99 and resource evidence are linked.

Do not state that an external PG caused delay without source-side observability. State that the observed source-processing boundary indicates an upstream/source-side delay candidate.

Never claim a root cause beyond the boundaries the system can observe.

## 12. Current Baseline and Gaps

Available foundations:

- API and Kafka Consumer separation
- `userId` partition key
- Redis sliding window and degraded mode
- PostgreSQL result sink and idempotency constraints
- manual acknowledgement
- DLT and controlled reprocessing
- PaySim preprocessing, replay, and rule-evaluation toolchain
- k6 smoke/normal/peak/duplicate/Redis-down scenarios
- Prometheus/Grafana and Kafka exporter foundation

Gaps before V3 implementation:

- PaySim user, amount, and time-distribution profile
- versioned V3 workload manifests
- organic versus catch-up burst distinction
- source emulator decision
- complete timestamp and freshness contract
- stream-stage metrics and rate queries
- partition-oriented dashboard and baseline evidence

## 13. Source

- [High-Throughput Fraud Stream Processing System (Notion)](https://app.notion.com/p/3b59924eda6e800b9d1dd2f0bba987ea)
