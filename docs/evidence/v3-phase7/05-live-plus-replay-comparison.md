# Live Plus Replay Comparison

Accepted concurrent runs:

| Workload | Run ID | Target EPS | Duration | Emitted | HTTP Failure | Dropped | Checks |
|---|---|---:|---:|---:|---:|---:|---:|
| Live control | `phase7-live-plus-20260831-001` | 100 | 120s | 12,000 | 0 | absent | 1 |
| Historical replay | `phase7-replay-plus-20260831-001` | 150 | 30s | 4,500 | 0 | 0 | 1 |

For the live control summary, `dropped_iterations` was absent while the emitted count matched the configured 12,000 events. This is recorded as no dropped-iteration series observed.

## Routing Result

| Signal | Live | Replay |
|---|---:|---:|
| Topic | `transaction-events` | `transaction-events-replay` |
| Consumer group | `fraud-event-consumer` | `fraud-event-replay-consumer` |
| Final Consumer Lag | 0 | 0 |
| DB receipts | 12,000 | 4,500 |
| DB fraud results | 12,000 | 4,500 |
| DB processing logs | 12,000 | 4,500 |

## State Isolation

Primary Redis state is the per-user sliding-window ZSET:

```text
fraud:tx:{namespace}:user:{userId}:events
```

| Check | Result |
|---|---:|
| Replay users in live namespace | 0 |
| Replay users in replay namespace | 500 |
| Live users in replay namespace | 0 |
| Live users in live namespace | 1,000 |

Supplemental key type checks confirmed `zset` for both live and replay user-window keys. Event-key checks also stayed isolated:

| Event-key check | Result |
|---|---:|
| Replay-plus event keys in live namespace | 0 |
| Live-plus event keys in replay namespace | 0 |
| Replay-plus event keys in replay namespace | 4,500 |
| Live-plus event keys in live namespace | 12,000 |

## Latency Observation

| Metric | Live Only | Live + Replay |
|---|---:|---:|
| Live HTTP p95 | 11.024 ms | 12.801 ms |
| Live persisted ingress age p99 | 0.012039s | 0.092850s |

Prometheus 10-minute live/replay split after the concurrent run:

| Metric | Live | Replay |
|---|---:|---:|
| Consumer service p99 | 0.039101s | 0.037297s |
| Redis state p99 | 0.026369s | 0.022121s |
| Kafka final lag | 0 | 0 |

## Interpretation

Replay traffic did not enter the live Kafka topic/group or live Redis user-window namespace. Live and replay final lag both drained to 0, and prefix-scoped DB counts aligned with emitted events.

The concurrent run showed higher live latency than the Live Only run. PostgreSQL and Redis runtime are still physically shared, so shared-resource contention is a possible contributor, but this experiment does not establish causality. The latency observation is not evidence of state contamination.
