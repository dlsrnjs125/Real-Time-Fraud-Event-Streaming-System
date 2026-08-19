# V3 Production Hardening Direction

This document defines the preparation scope before starting V3 Phase 0.

V3 does not start by adding more features. It starts by changing how every hardening phase is designed, measured, troubleshot, improved, and closed with evidence.

Source note: this direction is based on the Notion page `Real-Time Fraud Event Streaming System(고도화)` reviewed on 2026-08-19.

## Current Baseline

The current RT-FESS implementation already has the following foundations:

- Spring Boot API and Consumer execution separation
- Kafka-based asynchronous transaction event processing
- `userId` Kafka partition key for user-level ordering
- manual Kafka acknowledgement
- PostgreSQL unique constraint based idempotency for fraud results
- Redis sliding window and degraded mode
- DLT storage, admin query, discard, and reprocess flow
- Consumer processing latency, Redis degraded, skipped rule, DLT operation, and Kafka Consumer Lag dashboard foundation
- k6 normal, peak, duplicate replay, Redis-down scenario foundations
- PaySim replay/evaluation toolchain
- `ruleVersion` traceability across runtime result and evaluator contract

V3 treats those as starting assets, not as final proof of production readiness.

## Hardening Goal

V3 should make the system answer these questions with reproducible evidence:

1. When transaction traffic bursts, how fast can the system process events and recover accumulated lag?
2. When Consumer failure, rebalance, or duplicate consumption happens, can event ordering assumptions and business results still be trusted?
3. Can Kafka-processed fraud results be delivered to user-facing or operator-facing runtime surfaces without coupling slow downstream clients to the fraud detection core path?

The focus is performance engineering, bottleneck isolation, failure recovery, and operational evidence.

## Common Phase Rule

Every V3 phase must follow the same troubleshooting and evidence loop:

1. Baseline: measure throughput and latency in the normal environment.
2. Realistic Bottleneck Injection: create a realistic bottleneck or failure condition.
3. Observation: inspect Grafana, Prometheus, Actuator, application logs, and database/Kafka evidence.
4. Hypothesis: state which layer is suspected and why.
5. Isolation: separate Kafka, Consumer, Redis, PostgreSQL, and downstream delivery effects.
6. Improvement: change code, configuration, architecture, or runbook behavior.
7. Same-Load Re-test: rerun the same workload, not a similar workload.
8. Trade-off: record the cost, limit, or correctness impact of the improvement.
9. Evidence: capture Before/After metrics and screenshots or command output.

Do not close a V3 phase only because a feature was added. A phase closes only when the failure mode or bottleneck was reproduced, explained, improved or intentionally accepted, and recorded.

## Evidence Standards

Each phase should record at least:

- experiment fingerprint:
  - commit SHA
  - workload scenario name and version
  - host hardware notes and container CPU/memory limits
  - Kafka partition count
  - Consumer instance count and Consumer concurrency
  - HikariCP pool configuration
  - PostgreSQL, Redis, Kafka, and Docker image versions
  - dataset name, sample policy, and deterministic seed when applicable
- workload shape, duration, event count, and environment notes
- input TPS and Consumer TPS
- Kafka Consumer Lag, preferably by partition when relevant
- p50/p95/p99 for the phase's critical latency signals
- Redis latency/degraded signals when Redis is in path
- DB persistence latency and HikariCP pool signals when PostgreSQL is in path
- duplicate consumption and duplicate persisted result counts when redelivery is tested
- DLT count, retry count, replay count, and recovery result when recovery is tested
- screenshots or saved outputs from Grafana/Prometheus/k6/DB queries
- known limitations and follow-up candidates

Do not write estimated numbers as measured results. Use `TBD` until evidence exists.

## Phase Overview

| V3 Phase | Theme | Primary Question | Status Before Start |
|---:|---|---|---|
| Phase 0 | Performance Observability Foundation | Can we identify where Consumer processing time is spent? | In progress |
| Phase 1 | Market Open Burst / PostgreSQL Bottleneck | Can the system handle burst traffic and recover lag after DB saturation? | Not started |
| Phase 2 | Kafka Hot Partition / Data Skew | Can we detect and explain partition-level traffic skew? | Not started |
| Phase 3 | Consumer Rebalance / Redelivery / Idempotency | Can redelivery happen without duplicate business results? | Not started |
| Phase 4 | Retry / DLT Recovery Architecture | Are transient and permanent failures classified and isolated correctly? | Not started |
| Phase 5 | Recovery / Replay Safety | Can DLT replay recover safely without creating a new incident? | Not started |
| Phase 6 | Real-Time Fraud Result Delivery | Can fraud results be delivered downstream without coupling delivery to detection? | Optional / not started |
| Phase 7 | Slow Consumer / Backpressure | Can slow clients be isolated with bounded buffering and catch-up? | Optional / not started |

Phase 6 and Phase 7 are optional until the project explicitly adds a real-time delivery surface. If added, SSE should be considered before WebSocket when the flow is server-to-client only.

### Phase Mapping Note

The repository V3 plan intentionally expands the Notion draft by separating Recovery / Replay Safety into its own Phase 5.

Therefore:

- Notion Real-Time Delivery Phase 5 maps to repository V3 Phase 6.
- Notion Slow Consumer Phase 6 maps to repository V3 Phase 7.

Once V3 implementation starts, this repository roadmap and direction document are the implementation source of truth. The Notion page remains planning input, not the phase-number authority.

## Phase 0. Performance Observability Foundation

### Problem

Current metrics include Redis window latency and overall Consumer processing latency, but the Consumer path is not fully decomposed.

The runtime path is:

```text
Kafka consume
Redis window
Rule engine
PostgreSQL persistence
Ack
```

If only total processing latency is visible, the system cannot reliably tell whether a 300ms event spent most of its time in Redis, rule execution, DB persistence, or waiting for DB connection.

### Implementation Contract

- define metric names and exact measurement boundaries
- decide which timers are application metrics and which are infrastructure/exporter metrics
- document cardinality rules for labels
- document how HikariCP metrics will be exposed and interpreted
- define a Grafana panel list for V3 experiments

The implementation and verification contract is maintained in [V3 Phase 0 Performance Observability Foundation](42-v3-phase0-performance-observability.md). Phase 0 selected Kafka record timestamp as the queue-latency source and did not add producer observability fields to the event contract.

### Candidate Metrics

```text
fraud.kafka.queue.latency
fraud.consumer.processing.latency
fraud.redis.window.latency
fraud.rule.processing.latency
fraud.db.persistence.latency
fraud.event.age
fraud.ingestion.to.detection.latency
fraud.event.e2e.latency
```

### Latency Model

V3 must not use a single overloaded "E2E latency" metric for every workload. Live traffic and replay traffic have different time semantics.

Canonical time fields:

- `eventTime`: when the financial/business event occurred
- `receivedAt`: when `app-api` accepted and published the event
- `producerPublishedAt`: producer-side publish timestamp if explicitly defined after Phase 0 review
- `consumerStart`: when `app-consumer` starts handling the Kafka record
- `processingEnd` / `detectedAt`: when fraud processing and persistence complete

V3 latency metrics:

| Metric | Boundary | Primary Use |
|---|---|---|
| `fraud.event.age` | `consumerStart - eventTime` | how old the business event is when consumed |
| `fraud.kafka.queue.latency` | `consumerStart - Kafka record timestamp or explicitly defined producer timestamp` | Kafka queueing and Consumer backlog |
| `fraud.consumer.processing.latency` | `processingEnd - consumerStart` | Consumer work after record delivery |
| `fraud.ingestion.to.detection.latency` | `detectedAt - receivedAt` | online ingestion-to-detection delay |
| `fraud.event.e2e.latency` | `detectedAt - eventTime` | live-traffic business E2E only |

`fraud.event.e2e.latency` must not be interpreted as an operational SLA for replay workloads. PaySim or historical replay can carry old `eventTime` values, so `detectedAt - eventTime` may describe event age rather than current system latency. For replay evidence, use Consumer processing and queue latency and clearly exclude historical business E2E from live latency claims.

`fraud.kafka.queue.latency` implementation must decide whether its source timestamp is the Kafka record timestamp, such as `ConsumerRecord.timestamp()`, or an explicitly propagated producer timestamp. Do not introduce `producerPublishedAt` into the event contract only for observability without evaluating the Kafka contract cost. Phase 0 must document the chosen timestamp source before adding the metric.

HikariCP signals:

```text
hikaricp_connections_active
hikaricp_connections_idle
hikaricp_connections_pending
hikaricp_connections_timeout_total
```

Kafka signals:

```text
partition lag
consumer group lag
consumed records/sec
partition assignment
rebalance count
```

## Phase 1. Market Open Burst Traffic / PostgreSQL Bottleneck

### Problem

A stable average load does not prove the system is safe under market-open style bursts. V3 should reproduce a workload shape like:

```text
Warmup
Normal
Burst
Recovery
```

The local TPS values can be scaled down to fit local hardware, but the shape must create:

```text
Input TPS > Consumer sustainable TPS
```

### Required Preparation

- add a burst k6 plan, separate from generic ramp-up peak load
- define a DB bottleneck profile with intentionally constrained HikariCP settings
- expose Consumer concurrency as an experiment variable
- define an experiment matrix such as Consumer concurrency 4/8/16 and DB pool 4/8/16
- define DB persistence p95 and Hikari pending evidence capture

### Closure Evidence

The phase should distinguish Kafka Lag as a symptom from PostgreSQL saturation as a possible root cause.

Lag evidence must separate:

- `peak_total_group_lag`: highest total lag across the Consumer group during the experiment
- `peak_partition_lag`: highest single-partition lag during the experiment
- `lag_recovery_time`: time from burst phase end until Consumer group lag falls below the baseline threshold and stays there for 30 consecutive seconds

The baseline threshold must be recorded with the experiment. For example, a threshold can be `lag <= baseline_p95_lag` or a fixed local threshold such as `lag < 100`, but it must be chosen before comparing Before/After results.

Example evidence shape:

| Metric | Before | After |
|---|---:|---:|
| Input TPS | TBD | TBD |
| Consumer TPS | TBD | TBD |
| Peak Total Group Lag | TBD | TBD |
| Peak Partition Lag | TBD | TBD |
| Lag Recovery Time | TBD | TBD |
| DB p95 | TBD | TBD |
| E2E p99 | TBD | TBD |

## Phase 2. Kafka Hot Partition / Data Skew

### Problem

`userId` as Kafka key preserves user-level ordering, but it does not guarantee balanced partition traffic. Heavy users or batch-like accounts can create partition skew.

### Required Preparation

- define uniform and skewed load generators
- add partition-level lag and throughput evidence requirements
- record key cardinality and heavy-hitter assumptions
- document why `userId` must not be changed casually
- compare partition count and Consumer concurrency combinations

### Closure Evidence

At minimum, record:

- partition-level incoming traffic distribution
- partition-level Consumer Lag
- active vs idle Consumer behavior
- effect of Consumer scale-out when Consumer count exceeds partition count
- ordering trade-off if any key strategy change is considered

## Phase 3. Consumer Rebalance / Redelivery / Idempotency

### Problem

Kafka is at-least-once. Redelivery must be treated as a normal operational scenario, not as an exceptional system failure.

The target failure drill is:

```text
Consumer processes record
PostgreSQL insert succeeds
Consumer is killed before ack
Same record is redelivered
Business result remains unique
```

### Required Preparation

- define controlled Consumer kill/restart or scale-out drill
- define redelivery and duplicate consumption metrics
- define DB count verification commands
- document how unique constraint violations are interpreted as idempotent duplicate handling, not fatal processing failure
- define how `topic`, `partition`, `offset`, and `eventId` are captured together in evidence

### Runtime Metrics

```text
fraud.kafka.records.consumed
fraud.duplicate.consumption.total
fraud.duplicate.persistence.prevented.total
```

Runtime metrics should avoid state-heavy redelivery detection unless the operational value justifies the complexity. Tracking whether a `topic` + `partition` + `offset` has already been consumed may require additional state and can be unnecessary as an always-on production metric.

Candidate runtime signals can also include Kafka exporter or client metrics such as rebalance count when available.

### Redelivery Definitions

Kafka `ConsumerRecord` does not expose a simple `redelivered=true` flag. V3 therefore separates duplicate and redelivery evidence:

- `duplicate_consumption`: the same `eventId` is observed by the Consumer more than once.
- `redelivery_candidate`: the same `topic` + `partition` + `offset` is consumed again after a crash, restart, or rebalance drill.
- `duplicate_persistence_prevented`: a duplicate `eventId` did not create another `fraud_detection_results` row because the application path or PostgreSQL unique constraint prevented it.

DB unique conflicts alone are not enough to prove Kafka redelivery, because the API could publish two different records with the same `eventId`. Phase 3 drill evidence must retain `topic`, `partition`, `offset`, and `eventId` together.

### Failure Drill Evidence

`redelivery_candidate` can remain controlled drill evidence rather than a first-class runtime counter. The drill should compare consumed records, unique event IDs, reconsumed offsets, stored fraud results, and duplicate result count using logs, DB queries, or drill output.

### Closure Evidence

| Metric | Result |
|---|---:|
| Produced Events | TBD |
| Consumed Records | TBD |
| Unique Event IDs | TBD |
| Reconsumed Offsets | TBD |
| Stored Fraud Results | TBD |
| Duplicate Fraud Results | 0 required |

## Phase 4. Retry / DLT Recovery Architecture

### Problem

Having a retry topic is not the same as having a retry architecture. V3 should classify failure types and keep poison messages from blocking partition progress.

### Required Preparation

- document retryable, degraded/continue, non-retryable, and idempotent duplicate classifications
- choose Spring Kafka retry mechanism intentionally, such as `DefaultErrorHandler` or `@RetryableTopic`
- define retry topic naming and backoff policy
- define retry budget including max attempts, max elapsed time, backoff, and jitter
- define permanent failure isolation into DLT
- document how invalid payloads avoid repeated pointless retries
- document how retry changes same-`userId` ordering semantics

### Failure Classification

Retryable:

- PostgreSQL connection acquisition timeout
- PostgreSQL transient connectivity failure
- Kafka or DLT publish transient failure
- other temporary infrastructure failures where processing cannot be completed safely

Degraded / Continue:

- Redis sliding-window timeout or Redis unavailable

Redis sliding-window failure follows the existing degraded-mode policy: continue Consumer processing, skip Redis-dependent rules, persist a degraded fraud result, increment degraded/skipped-rule metrics, and acknowledge after required persistence succeeds. Do not route Redis sliding-window unavailability through Kafka retry unless the degraded-mode policy is explicitly changed in the same phase.

Non-retryable:

- malformed payload
- schema violation
- required field missing
- invalid amount

Idempotent duplicate:

- duplicate `eventId`
- PostgreSQL unique constraint conflict after redelivery

### Retry Mechanism Decision Criteria

`DefaultErrorHandler` and `@RetryableTopic` have different ordering and blocking behavior. The retry mechanism must be selected with `userId` ordering in mind.

`DefaultErrorHandler`:

- keeps retry closer to the original partition processing path
- makes same-partition ordering easier to reason about
- can delay partition progress while a retryable record is retried
- requires an explicit poison-record and retry-exhaustion policy

`@RetryableTopic`:

- can reduce blocking of the main topic partition
- moves failed records to retry topics, which can allow later same-`userId` records on the main topic to be processed first
- can change effective user-level ordering from `event #100 -> event #101` to `event #101 -> retried event #100`
- requires separate validation against fraud rule semantics, especially sliding-window rules

Phase 4 completion must document how retry affects same-`userId` ordering and whether that trade-off is accepted, mitigated, or rejected.

### Closure Evidence

| Metric | Before | After |
|---|---:|---:|
| Permanent Error Retry Count | TBD | TBD |
| Poison Message Blocking | TBD | TBD |
| DLT Isolation | TBD | TBD |
| Recovery Success | TBD | TBD |
| Same-user Ordering Semantics | TBD | TBD |

## Phase 5. Recovery / Replay Safety

### Problem

Replay is an operational recovery path. A large replay can become a new burst and recreate the original incident.

### Required Preparation

- define replay metadata and audit requirements
- define rate-limited replay or batch-size policy
- define replay safety checks before publishing back to `transaction-events`
- define post-replay verification and idempotency checks
- document recovery-induced incident risks

### Candidate Metadata

```text
replayId
originalEventId
retryCount
replayReason
operator
requestedAt
completedAt
result
```

## Phase 6. Real-Time Fraud Result Delivery

### Problem

Kafka fraud detection completion and user/operator-facing delivery should be separated. Slow delivery surfaces must not slow the core detection path.

### Required Preparation

- decide SSE vs WebSocket based on interaction requirements
- define `fraud-results` or equivalent result topic contract
- define streaming gateway ownership boundary
- define catch-up query contract before relying on live stream only
- define delivery latency metrics separately from detection latency

SSE is preferred when delivery is one-way server-to-client. WebSocket needs a clear reason such as bidirectional commands, subscription mutation, client ack, or interactive filtering.

## Phase 7. Slow Consumer / Backpressure

### Problem

Producer speed and client consumption speed are not guaranteed to match. Unlimited buffering for slow clients can turn downstream delivery into memory pressure.

### Required Preparation

- define client-level bounded queue limits
- compare backpressure policies such as disconnect slow client, drop oldest, latest-state-only, or catch-up API
- define cursor or sequence based catch-up API
- define gateway memory, queue depth, delivery p99, and reconnect/catch-up success metrics

Recommended structure:

```text
Live stream
+
Cursor based catch-up
```

## Pre-Phase-0 Setup Checklist

Before starting V3 Phase 0, complete these docs-only tasks:

- keep this direction document linked from README, docs index, and roadmap
- mark V3 phases as planned, not implemented
- define Phase 0 metric boundaries in `docs/08-observability.md` or a dedicated V3 observability spec
- define V3 result templates for Before/After evidence
- decide whether existing Phase 12/13 load result docs will be superseded or preserved as historical evidence
- keep branch and PR scope limited to documentation until Phase 0 implementation starts

## Non-Goals

V3 preparation does not implement:

- new Kafka retry handlers
- new k6 burst or hot-partition scripts
- new metrics
- WebSocket/SSE runtime delivery
- slow-consumer queues
- database schema changes
- production security, RBAC, or deployment automation

Those belong to later V3 implementation phases.
