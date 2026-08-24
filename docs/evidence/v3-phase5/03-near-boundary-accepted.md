# Near-Boundary Accepted Event

Representative event: `v3-phase5-phase5-late-out-of-order-20260824-001-270`

| Field | Value |
| --- | --- |
| Bucket | `LATE_NEAR_BOUNDARY_4M59S` |
| Allowed lateness | `5m` |
| Event time UTC | `2026-08-24 02:31:55.011` |
| Received at UTC | `2026-08-24 02:36:54.021173` |
| Calculated lateness | `00:04:59.010173` |
| RecentTransactionWindowStatus | `NORMAL` |

## Redis Evidence

```text
EXISTS fraud:tx:event:v3-phase5-phase5-late-out-of-order-20260824-001-270
1

ZSCORE fraud:tx:user:v3-phase5-late-user-0:events v3-phase5-phase5-late-out-of-order-20260824-001-270
1787538715011
```

## Fraud Result Evidence

```text
event_id: v3-phase5-phase5-late-out-of-order-20260824-001-270
risk_score: 20
risk_level: LOW
decision: APPROVE
degraded: false
matched_rules: NIGHT_TIME_TRANSACTION
skipped_rules:
reason: eventTime hour between 0 and 5
```

This event is inside the allowed lateness window, so it remains part of live Redis state and receives normal stateful evaluation.
