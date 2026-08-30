# Replay Only Isolation

Run ID: `phase7-replay-only-20260831-002`

The replay-only run sent 24-hour historical events through the replay API on port `8082`. The replay API published to `transaction-events-replay`, and the replay Consumer group `fraud-event-replay-consumer` consumed that topic using Redis namespace `fraud:tx:replay:*`.

| Metric | Value |
|---|---:|
| Target replay EPS | 150 |
| Duration | 30s |
| Configured event limit | 4,500 |
| Emitted event count | 4,500 |
| Achieved EPS | 149.982 |
| HTTP failure rate | 0 |
| Dropped iterations | 0 |
| Checks rate | 1 |
| HTTP p95 | 9.539 ms |
| Final replay Consumer Lag | 0 |
| Replay run keys in replay namespace | 4,500 |
| Replay run keys in live namespace | 0 |
| Redis-dependent skipped rules | 0 |

Final DB consistency for the accepted run:

```text
receipts        = 4500
fraud_results   = 4500
processing_logs = 4500
```

Persisted event ingress age confirms historical replay semantics:

```text
p50 = 86400.0020085s
p95 = 86400.00376515s
p99 = 86400.00692167999s
```

Discarded run:

`phase7-replay-only-20260831-001` emitted 4,479 of 4,500 events with `droppedIterations=22`. The strict Phase 7 k6 gate rejected it, and it is not used as accepted evidence.

