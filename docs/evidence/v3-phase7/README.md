# V3 Phase 7 Runtime Evidence

Date: 2026-08-31

## Goal

V3 Phase 7 verifies that historical replay traffic can run beside live traffic without contaminating live Kafka routing or live Redis detection state.

The phase validates logical isolation only. PostgreSQL and Redis runtime remain physically shared in the local Docker environment, so Live + Replay evidence must be interpreted as isolation plus concurrent-run latency observation, not full physical tenancy isolation.

## Accepted Runs

| Scope | Run ID | Workload | Target EPS | Duration | Emitted | HTTP failure | Dropped | Final Lag |
|---|---|---|---:|---:|---:|---:|---:|---:|
| Live Only | `phase7-live-only-20260831-002` | `v3-phase2-state-size-baseline` | 100 | 120s | 12,000 | 0 | absent | 0 |
| Replay Only | `phase7-replay-only-20260831-002` | `v3-phase7-historical-replay` | 150 | 30s | 4,500 | 0 | 0 | 0 |
| Live + Replay | `phase7-live-plus-20260831-001` | `v3-phase2-state-size-baseline` | 100 | 120s | 12,000 | 0 | absent | 0 |
| Live + Replay | `phase7-replay-plus-20260831-001` | `v3-phase7-historical-replay` | 150 | 30s | 4,500 | 0 | 0 | 0 |

For the live workload, `dropped_iterations` was absent from the k6 summary when the run emitted the full configured event count. The discarded live run produced a concrete `droppedIterations=7`, so accepted live runs are interpreted as no dropped-iteration series observed rather than a serialized numeric zero.

## Discarded Runs

| Run ID | Reason |
|---|---|
| `phase7-live-only-20260831-001` | Emitted 11,993 of 12,000 events with `droppedIterations=7`; not used as accepted evidence. |
| `phase7-replay-only-20260831-001` | Emitted 4,479 of 4,500 events with `droppedIterations=22`; strict k6 gate failed. A follow-up preflight observed replay group Lag 751 and stopped before rerun. |

## Key Result

The 24-hour historical replay workload processed 4,500 accepted replay events through a separate Kafka topic, separate Consumer group, and separate Redis namespace. During Live + Replay, both live and replay Consumer Lag drained to 0. Supplemental Redis evidence directly verified that replay users created user sliding-window ZSET state only under the replay namespace and live users created user sliding-window ZSET state only under the live namespace.

## Routing Isolation

| Signal | Live | Replay |
|---|---|---|
| API port | `8080` | `8082` |
| Consumer port | `8081` | `8083` |
| Producer topic | `transaction-events` | `transaction-events-replay` |
| Consumer group | `fraud-event-consumer` | `fraud-event-replay-consumer` |
| Redis namespace | `fraud:tx:live:*` | `fraud:tx:replay:*` |
| Final Kafka Lag after concurrent run | 0 | 0 |

`/actuator/info` verified the live and replay mode/topic/group/namespace routing before runtime evidence was accepted.

## State Isolation

Primary Redis state under test:

```text
fraud:tx:{namespace}:user:{userId}:events
```

Event hash keys under `fraud:tx:{namespace}:event:{eventId}` are retained as secondary evidence, but the user sliding-window ZSET is the core state for Phase 7.

| Check | Result |
|---|---:|
| Replay users in live namespace | 0 |
| Replay users in replay namespace | 500 |
| Live users in replay namespace | 0 |
| Live users in live namespace | 1,000 |

Sample key checks confirmed `TYPE=zset` for both live and replay user-window keys. Event hash key checks also stayed isolated:

| Event-key check | Result |
|---|---:|
| Replay-only event keys in live namespace | 0 |
| Replay-only event keys in replay namespace | 4,500 |
| Replay-plus event keys in live namespace | 0 |
| Live-plus event keys in replay namespace | 0 |
| Replay-plus event keys in replay namespace | 4,500 |
| Live-plus event keys in live namespace | 12,000 |

Redis-dependent skipped rules were 0 for the accepted replay-only run, confirming that replay mode bypassed the live freshness skip and created replay sliding-window state.

## Concurrent-Run Latency Observation

| Metric | Live Only | Live + Replay |
|---|---:|---:|
| Live HTTP p95 | 11.024 ms | 12.801 ms |
| Live persisted ingress age p99 | 0.012039s | 0.092850s |

Prometheus 10-minute split after the concurrent run:

| Metric | Live | Replay |
|---|---:|---:|
| Consumer service p99 | 0.039101s | 0.037297s |
| Redis state p99 | 0.026369s | 0.022121s |
| Kafka final lag | 0 | 0 |

The concurrent run showed higher live latency than the Live Only run. Because PostgreSQL and Redis are physically shared, shared-resource contention is a possible contributor, but this experiment does not establish causality. The latency observation is not evidence of live/replay state contamination.

## Final Consistency

| Run | Receipts | Fraud results | Processing logs |
|---|---:|---:|---:|
| `phase7-live-only-20260831-002` | 12,000 | 12,000 | 12,000 |
| `phase7-replay-only-20260831-002` | 4,500 | 4,500 | 4,500 |
| `phase7-live-plus-20260831-001` | 12,000 | 12,000 | 12,000 |
| `phase7-replay-plus-20260831-001` | 4,500 | 4,500 | 4,500 |

The discarded runs are retained in raw consistency output for traceability but are not used as accepted Phase 7 evidence.

## Evidence Files

| File | Purpose |
|---|---|
| `01-isolation-workload-contract.md` | Phase 7 workload and isolation contract. |
| `02-routing-clean-state-preflight.txt` | Initial live/replay routing and clean-state preflight. |
| `02b-replay-only-preflight.txt` | Replay-only preflight before the discarded replay run. |
| `02c-replay-only-rerun-preflight.txt` | Rerun preflight after replay Lag drained back to 0. |
| `03-live-only-k6-summary.json` | Accepted Live Only k6 summary. |
| `03-live-only-baseline.md` | Live Only interpretation and DB consistency. |
| `04-replay-only-k6-summary.json` | Accepted Replay Only k6 summary. |
| `04-replay-only-isolation.md` | Replay Only routing/state evidence. |
| `05-event-ingress-age-comparison.txt` | Persisted live vs replay ingress-age quantiles. |
| `05a-live-plus-k6-summary.json` | Accepted concurrent live k6 summary. |
| `05b-replay-plus-k6-summary.json` | Accepted concurrent replay k6 summary. |
| `05-live-plus-replay-comparison.md` | Live + Replay comparison and interpretation. |
| `06-redis-namespace-isolation.txt` | Redis user sliding-window ZSET and event-key namespace collision checks. |
| `07-kafka-group-isolation.txt` | Kafka live/replay group and topic Lag evidence. |
| `08-final-consistency.txt` | Final PostgreSQL receipt/result/log counts. |
| `09-grafana-live-replay-isolation.png` | Chrome-captured Grafana screenshot with Phase 7 live/replay panels. |
| `10-dlt-replay-routing.txt` | Synthetic replay DLT row reprocess routing back to replay topic. |
| `11-prometheus-live-replay-metrics.txt` | Prometheus live/replay split metrics and healthy scrape targets. |

## Completion Decision

Accepted. V3 Phase 7 has runtime evidence for Live Only, Replay Only, and Live + Replay. The evidence supports logical historical replay isolation across Kafka topic, Consumer group, and Redis namespace, with explicit shared-resource interpretation boundaries.
