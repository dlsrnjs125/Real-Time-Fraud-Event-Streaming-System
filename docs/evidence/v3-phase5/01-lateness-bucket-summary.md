# Lateness Bucket Summary

## Workload Contract

| Field | Value |
| --- | --- |
| Run ID | `phase5-late-out-of-order-20260824-001` |
| Commit SHA | `d540bc7` |
| Target EPS | `10` |
| Duration | `30s` |
| Configured events | `300` |
| Emitted events | `300` |
| Dropped iterations | `0` |
| HTTP failures | `0` |
| Check success rate | `1.0` |
| Achieved EPS | `9.999096748260406` |

## Bucket Counts

| Bucket | Expected | Actual |
| --- | ---: | ---: |
| `ON_TIME` | 50 | 50 |
| `LATE_30S` | 50 | 50 |
| `LATE_2M` | 50 | 50 |
| `LATE_NEAR_BOUNDARY_4M59S` | 50 | 50 |
| `TOO_LATE_10M` | 50 | 50 |
| `LATE_1M_OUT_OF_ORDER_RETURN` | 50 | 50 |

## Expected Freshness Split

| Classification | Count |
| --- | ---: |
| Accepted stateful events | 250 |
| Too-late events | 50 |

The runtime workload uses `4m59s` for near-boundary accepted events. Exact `5m` equality is covered by deterministic tests because HTTP transport and API receipt timing make runtime equality unstable.
