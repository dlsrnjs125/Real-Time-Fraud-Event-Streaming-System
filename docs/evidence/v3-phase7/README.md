# V3 Phase 7 Historical Replay Isolation Evidence

Status: template only. No accepted runtime evidence has been recorded yet.

## Required Fingerprint

```text
Run ID:
Commit SHA:
Host CPU / Memory:
Docker resource limit:
Kafka topic partitions:
Live consumer group:
Replay consumer group:
Live Redis namespace:
Replay Redis namespace:
Workload manifest:
Replay rate:
Duration:
Event count:
```

## Required Checks

| Check | Expected | Observed |
|---|---|---|
| Topic script created `transaction-events-replay` | PASS | TBD |
| Live group pre-run Lag | 0 | TBD |
| Replay group pre-run Lag | 0 or group absent | TBD |
| Replay Redis namespace cleanup | only `fraud:tx:replay:*` deleted | TBD |
| Replay accepted event count | 4,500 | TBD |
| Replay final Lag | 0 | TBD |
| Live final Lag during replay | no replay-driven increase | TBD |
| Live Redis collision keys | 0 | TBD |
| Replay Redis keys created | `fraud:tx:replay:*` present | TBD |
| PostgreSQL duplicate result count | 0 duplicate result rows | TBD |

## Evidence Files To Add

- `01-preflight.txt`
- `02-replay-k6-summary.json`
- `03-live-vs-replay-lag.md`
- `04-redis-namespace-comparison.txt`
- `05-final-consistency.txt`
- `06-grafana-live-replay-isolation.png`

Do not mark V3 Phase 7 complete until this directory contains accepted runtime output for Live Only, Replay Only, and Live + Replay or explicitly documents a reduced evidence scope.
