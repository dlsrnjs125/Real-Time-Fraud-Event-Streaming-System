# Catch-up Ingress Age

Run ID: `phase6-catchup-20260824-001`

Persisted source of truth:

```text
transaction_event_receipts.received_at - transaction_event_receipts.event_time
```

| Metric | Value |
|---|---:|
| p50 | 270.002635s |
| p95 | 270.127421s |
| p99 | 270.471195s |
| min | 270.000902s |
| max | 271.095674s |

Sample rows:

| Event ID | User | Event Time UTC | Received At UTC | Event-to-Ingress Age |
|---|---|---|---|---:|
| `v3-phase6-phase6-catchup-20260824-001-0` | `v3-phase6-user-0` | 2026-08-24 10:30:12.220 | 2026-08-24 10:34:42.862187 | 270.642187s |
| `v3-phase6-phase6-catchup-20260824-001-1` | `v3-phase6-user-1` | 2026-08-24 10:30:12.220 | 2026-08-24 10:34:42.427491 | 270.207491s |
| `v3-phase6-phase6-catchup-20260824-001-10` | `v3-phase6-user-10` | 2026-08-24 10:30:12.249 | 2026-08-24 10:34:42.883711 | 270.634711s |
| `v3-phase6-phase6-catchup-20260824-001-100` | `v3-phase6-user-100` | 2026-08-24 10:30:12.549 | 2026-08-24 10:34:43.004437 | 270.455437s |
| `v3-phase6-phase6-catchup-20260824-001-1000` | `v3-phase6-user-0` | 2026-08-24 10:30:15.550 | 2026-08-24 10:34:45.559453 | 270.009453s |

Freshness policy skip results: `0`

Matched rule distribution:

| Matched Rules | Count |
|---|---:|
| `RAPID_TRANSACTION_COUNT` | 5000 |
| `(none)` | 4000 |

The matched-rule distribution matched the Organic accepted run, so the 270-second event-time shift did not introduce a time-rule distribution difference in this run window.
