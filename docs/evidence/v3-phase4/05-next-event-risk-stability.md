# Next Event Risk Stability

Each accepted Phase 4 drill uses E0 as the injected failure target and E1 as the next event. E1 verifies whether the user's stateful Redis window and fraud decision remain explainable after redelivery.

| Failure point | Run ID | E1 transaction count | E1 amount sum | E1 risk score | E1 risk level | E1 decision |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `BEFORE_REDIS_UPDATE` | `phase4-before-redis-20260824-001` | 2 | 200000 | 0 | LOW | APPROVE |
| `AFTER_REDIS_UPDATE_BEFORE_RESULT` | `phase4-after-redis-20260824-001` | 2 | 200000 | 0 | LOW | APPROVE |
| `AFTER_RESULT_SAVE_BEFORE_ACK` | `phase4-after-result-20260824-001` | 2 | 200000 | 0 | LOW | APPROVE |

Baseline expectation:

```text
E0 amount = 100000
E1 amount = 100000
window count at E1 = 2
window amount sum at E1 = 200000
risk score at E1 = 0
risk level at E1 = LOW
decision at E1 = APPROVE
```

Conclusion: all three failure-point drills preserved the next-event state and decision for the first post-redelivery event.
