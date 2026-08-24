# V3 Phase 5 Runtime Evidence

Phase 5 verifies event-time lateness handling for the Redis sliding-window path.
The accepted runtime scope is intentionally small: reproduce late and out-of-order events, prove the freshness policy boundary, and confirm that too-late events are excluded from live Redis state while still producing durable fraud results.

## Run

| Field | Value |
| --- | --- |
| Run ID | `phase5-late-out-of-order-20260824-001` |
| Commit SHA | `d540bc7` |
| Workload | `v3-phase5-late-out-of-order` |
| Target EPS | `10` |
| Duration | `30s` |
| Configured events | `300` |
| Emitted events | `300` |
| Dropped iterations | `0` |
| HTTP failures | `0` |
| Expected accepted stateful events | `250` |
| Expected too-late events | `50` |

## Evidence Files

| File | Purpose |
| --- | --- |
| `01-lateness-bucket-summary.md` | Confirms workload bucket contract and expected freshness split. |
| `02-user-out-of-order-ordering.md` | Shows arrival order and Redis event-time ordering for one user. |
| `03-near-boundary-accepted.md` | Shows `4m59s` late event accepted and recorded in Redis. |
| `04-too-late-redis-exclusion.txt` | Shows `10m` late event excluded from Redis but persisted in PostgreSQL. |
| `05-freshness-policy-result.md` | Separates freshness-policy degradation from Redis infrastructure failure. |
| `06-final-consistency.txt` | Confirms final DB counts and Consumer Lag. |
| `07-grafana-event-freshness.png` | Grafana freshness counters and processing context. |
| `08-grafana-processing-health.png` | Grafana processing health and final lag context. |

Grafana screenshots are supporting runtime evidence. Phase 5 is not a throughput benchmark; latency panels are used only to confirm that the pipeline continued processing and drained after the late/out-of-order workload.
