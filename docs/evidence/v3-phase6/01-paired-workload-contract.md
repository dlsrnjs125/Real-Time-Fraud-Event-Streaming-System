# Paired Workload Contract

Run date: 2026-08-24

| Item | Organic Burst | Catch-up Burst |
|---|---:|---:|
| Run ID | `phase6-organic-20260824-003` | `phase6-catchup-20260824-001` |
| Commit SHA | `c7e348a` | `c7e348a` |
| Manifest | `organic-burst-v1.json` | `catch-up-burst-v1.json` |
| Target EPS | 300 | 300 |
| Duration | 30s | 30s |
| Event limit | 9000 | 9000 |
| Emitted events | 9000 | 9000 |
| Users | 1000 | 1000 |
| Amount | 100000 KRW | 100000 KRW |
| Event-time mode | `REBASE_TO_ARRIVAL` | `CONTROLLED_LATENESS` |
| Source profile | `NORMAL` | `BATCH_CATCHUP` |
| Configured source delay | 0s | 270s |
| Expected too-late events | 0 | 0 |
| HTTP failure rate | 0 | 0 |
| Dropped iterations | 0 | 0 |

The two workloads keep the same runtime shape and change only event-time/source-delay semantics.

`randomSeed` is retained as common V3 manifest metadata, but the Phase 6 runner uses deterministic modulo user assignment rather than seeded random selection.
