# Organic Ingress Age

Run ID: `phase6-organic-20260824-003`

Persisted source of truth:

```text
transaction_event_receipts.received_at - transaction_event_receipts.event_time
```

| Metric | Value |
|---|---:|
| p50 | 0.002309s |
| p95 | 0.239387s |
| p99 | 0.645115s |
| min | 0.000948s |
| max | 1.182201s |

Sample rows:

| Event ID | User | Event Time UTC | Received At UTC | Event-to-Ingress Age |
|---|---|---|---|---:|
| `v3-phase6-phase6-organic-20260824-003-0` | `v3-phase6-user-0` | 2026-08-24 10:31:29.961 | 2026-08-24 10:31:30.774103 | 0.813103s |
| `v3-phase6-phase6-organic-20260824-003-1` | `v3-phase6-user-1` | 2026-08-24 10:31:29.961 | 2026-08-24 10:31:30.758615 | 0.797615s |
| `v3-phase6-phase6-organic-20260824-003-10` | `v3-phase6-user-10` | 2026-08-24 10:31:29.990 | 2026-08-24 10:31:30.783236 | 0.793236s |
| `v3-phase6-phase6-organic-20260824-003-100` | `v3-phase6-user-100` | 2026-08-24 10:31:30.290 | 2026-08-24 10:31:30.912567 | 0.622567s |
| `v3-phase6-phase6-organic-20260824-003-1000` | `v3-phase6-user-0` | 2026-08-24 10:31:33.290 | 2026-08-24 10:31:33.295271 | 0.005271s |

Freshness policy skip results: `0`

Matched rule distribution:

| Matched Rules | Count |
|---|---:|
| `RAPID_TRANSACTION_COUNT` | 5000 |
| `(none)` | 4000 |
