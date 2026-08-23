# Hot P2 Partition Distribution

Run: `phase3-hot-p2-c6-20260823-001`

Workload: `v3-phase3-partition-skew-hot-p2`

Consumer concurrency: `6`

Driver result:

| Metric | Value |
| --- | ---: |
| Configured events | 36,000 |
| Emitted events | 36,000 |
| Target EPS | 300 |
| Achieved EPS | 299.94 |
| Dropped iterations | 0 |
| HTTP request failure rate | 0 |
| HTTP p95 | 191.30 ms |

Expected vs achieved partition distribution:

| Partition | Expected events | Expected share | Achieved processed events | Achieved share |
| --- | ---: | ---: | ---: | ---: |
| P0 | 2,880 | 8.00% | 2,880 | 8.00% |
| P1 | 2,880 | 8.00% | 2,880 | 8.00% |
| P2 | 21,600 | 60.00% | 21,600 | 60.00% |
| P3 | 2,880 | 8.00% | 2,880 | 8.00% |
| P4 | 2,880 | 8.00% | 2,880 | 8.00% |
| P5 | 2,880 | 8.00% | 2,880 | 8.00% |

User distribution check:

| Metric | Value |
| --- | ---: |
| Generated unique users | 600 |
| Events per user p50/p95/p99/max | 60 / 60 / 60 / 60 |
| Top user share | 0.17% |
| P2 unique users | 360 |
| Non-P2 unique users | 48 per partition |

This confirms the hot P2 workload increased partition-level traffic share without introducing a hot-user workload.
