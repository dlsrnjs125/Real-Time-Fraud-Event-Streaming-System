# Balanced vs Hot P2 Summary

Both accepted comparison runs used `consumer concurrency=6`, `targetEps=300`, and `eventLimit=36,000`.

| Metric | Balanced c6 | Hot P2 c6 |
| --- | ---: | ---: |
| Run | `phase3-balanced-c6-20260823-001` | `phase3-hot-p2-c6-20260823-001` |
| Emitted events | 36,000 | 36,000 |
| Achieved EPS | 300.13 | 299.94 |
| Dropped iterations | 0 | 0 |
| HTTP failure rate | 0 | 0 |
| HTTP p95 | 92.32 ms | 191.30 ms |
| P0 traffic share | 16.67% | 8.00% |
| P1 traffic share | 16.67% | 8.00% |
| P2 traffic share | 16.67% | 60.00% |
| P3 traffic share | 16.67% | 8.00% |
| P4 traffic share | 16.67% | 8.00% |
| P5 traffic share | 16.67% | 8.00% |
| Immediate P2 lag after workload | 360 | 12,554 |
| Immediate non-P2 lag after workload | 360-373 per partition | 0 per partition |
| Final lag after drain | 0 | 0 |
| Final receipts/results/logs | 36,000 / 36,000 / 36,000 | 36,000 / 36,000 / 36,000 |

Interpretation:

- Balanced c6 kept all partitions close together and only had a small tail lag after the workload finished.
- Hot P2 c6 produced a partition-local backlog: P2 lag remained high while the other partitions had already drained.
- The hot workload used 600 unique users and 60 events per user, so the observed effect is partition pressure rather than a single hot user.
