# User Out-of-Order Ordering

Representative user: `v3-phase5-late-user-0`

## Arrival Order

The following records were emitted for the same user in increasing event-id order. Their event times intentionally do not follow arrival order.

| Arrival | Event ID | Bucket | Event Time UTC | Received At UTC |
| ---: | --- | --- | --- | --- |
| 1 | `v3-phase5-phase5-late-out-of-order-20260824-001-0` | `ON_TIME` | `2026-08-24 02:36:27.010` | `2026-08-24 02:36:27.166177` |
| 2 | `v3-phase5-phase5-late-out-of-order-20260824-001-30` | `LATE_2M` | `2026-08-24 02:34:30.010` | `2026-08-24 02:36:30.020402` |
| 3 | `v3-phase5-phase5-late-out-of-order-20260824-001-60` | `LATE_30S` | `2026-08-24 02:36:03.010` | `2026-08-24 02:36:33.019010` |

## Redis ZSET Ordering

Command:

```bash
docker exec fraud-redis redis-cli ZRANGE fraud:tx:user:v3-phase5-late-user-0:events 0 -1 WITHSCORES
```

Result excerpt:

```text
v3-phase5-phase5-late-out-of-order-20260824-001-270
1787538715011
v3-phase5-phase5-late-out-of-order-20260824-001-30
1787538870010
v3-phase5-phase5-late-out-of-order-20260824-001-210
1787538888011
v3-phase5-phase5-late-out-of-order-20260824-001-150
1787538942011
v3-phase5-phase5-late-out-of-order-20260824-001-60
1787538963010
v3-phase5-phase5-late-out-of-order-20260824-001-240
1787538981011
v3-phase5-phase5-late-out-of-order-20260824-001-0
1787538987010
v3-phase5-phase5-late-out-of-order-20260824-001-180
1787539005010
```

The Redis ZSET is ordered by event-time score, not by HTTP arrival order or Kafka consumption order.
