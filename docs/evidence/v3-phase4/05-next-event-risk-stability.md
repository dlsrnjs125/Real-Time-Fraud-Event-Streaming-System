# Next Event Risk Stability

Each accepted Phase 4 drill uses E3 as the injected failure target and E4 as the next event. E4 verifies whether the user's stateful Redis window and fraud decision remain stable at a threshold-adjacent boundary after redelivery.

| Failure point | Run ID | E4 transaction count | E4 amount sum | E4 matched rules | E4 risk score | E4 risk level | E4 decision |
| --- | --- | ---: | ---: | --- | ---: | --- | --- |
| `BEFORE_REDIS_UPDATE` | `phase4-before-redis-threshold-20260824-002` | 5 | 500000 | `RAPID_TRANSACTION_COUNT` | 30 | MEDIUM | REVIEW |
| `AFTER_REDIS_UPDATE_BEFORE_RESULT` | `phase4-after-redis-threshold-20260824-001` | 5 | 500000 | `RAPID_TRANSACTION_COUNT` | 30 | MEDIUM | REVIEW |
| `AFTER_RESULT_SAVE_BEFORE_ACK` | `phase4-after-result-threshold-20260824-001` | 5 | 500000 | `RAPID_TRANSACTION_COUNT` | 30 | MEDIUM | REVIEW |

Baseline expectation:

```text
E0-E4 amount each = 100000
window count at E4 = 5
window amount sum at E4 = 500000
matched rule at E4 = RAPID_TRANSACTION_COUNT
risk score at E4 = 30
risk level at E4 = MEDIUM
decision at E4 = REVIEW
```

Why this is stronger than the initial E0/E1 evidence:

```text
E1 count = 2 and amount = 200000 were far from the rule thresholds.
E4 count = 5 is exactly where RAPID_TRANSACTION_COUNT fires.
If redelivery contaminated Redis state, E4's transaction count or rule outcome would be the first place to check.
```

Conclusion: all three failure-point drills preserved the next-event state and decision at the rapid-transaction threshold boundary.
