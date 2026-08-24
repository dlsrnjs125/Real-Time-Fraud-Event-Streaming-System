# Freshness Policy Result

Representative too-late event: `v3-phase5-phase5-late-out-of-order-20260824-001-120`

## Fraud Result

```text
risk_score: 20
risk_level: LOW
decision: APPROVE
degraded: true
matched_rules: NIGHT_TIME_TRANSACTION
skipped_rules: RAPID_TRANSACTION_COUNT,WINDOW_AMOUNT_SUM
reason: eventTime hour between 0 and 5; Freshness policy skip: Event exceeded allowed lateness; Redis sliding window skipped
```

## Metric Separation

Consumer Prometheus metrics after the run:

```text
fraud_redis_window_too_late_total 50.0
fraud_detection_degraded_total 50.0
fraud_redis_window_degraded_total absent/not incremented
```

Interpretation:

`fraud_detection_degraded_total` means full stateful evaluation was not performed. In this run, the cause was freshness policy rejection, not Redis infrastructure failure.

`fraud_redis_window_too_late_total` captures the freshness-policy path. Redis infrastructure degradation did not occur during this run.
