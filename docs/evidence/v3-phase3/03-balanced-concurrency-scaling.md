# Balanced Concurrency Scaling

Runtime rows with `Accepted = yes` completed 36,000 emitted events with no dropped iterations.

| Consumer concurrency | Run | Accepted | Achieved EPS | HTTP p95 | Immediate peak observed lag | Final lag |
| ---: | --- | --- | ---: | ---: | ---: | ---: |
| 1 | `phase3-balanced-c1-20260823-002` | yes | 299.93 | 40.62 ms | 21,093 total | 0 |
| 2 | `phase3-balanced-c2-20260823-003` | no | 298.93 | 278.14 ms | not used | 0 after drain |
| 3 | not rerun in this evidence pass | no | - | - | - | - |
| 6 | `phase3-balanced-c6-20260823-001` | yes | 300.13 | 92.32 ms | 2,195 total tail lag | 0 |
| 8 | assignment-only evidence | n/a | - | - | 0 at idle-assignment check | 0 |

Observed c1 lag immediately after the accepted balanced run:

```text
P0 lag 4244
P1 lag 3185
P2 lag 3931
P3 lag 3711
P4 lag 3344
P5 lag 4169
Total lag 21093
```

Observed c6 lag immediately after the accepted balanced run:

```text
P0 lag 369
P1 lag 367
P2 lag 360
P3 lag 364
P4 lag 362
P5 lag 373
Total lag 2195
```

Interpretation:

- Balanced traffic was generated and processed evenly across all six partitions.
- `concurrency=1` accumulated lag across all partitions because one consumer owned all six partitions.
- `concurrency=6` reduced the post-run tail lag substantially and drained to zero quickly.
- The c2 attempts were excluded from accepted scaling evidence because the HTTP driver dropped iterations during local runtime pressure.
