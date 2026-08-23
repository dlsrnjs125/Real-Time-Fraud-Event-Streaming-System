# Balanced Partition Distribution

Run: `phase3-balanced-c6-20260823-001`

Workload: `v3-phase3-partition-balanced`

Consumer concurrency: `6`

Driver result:

| Metric | Value |
| --- | ---: |
| Configured events | 36,000 |
| Emitted events | 36,000 |
| Target EPS | 300 |
| Achieved EPS | 300.13 |
| Dropped iterations | 0 |
| HTTP request failure rate | 0 |
| HTTP p95 | 92.32 ms |

Expected vs achieved partition distribution:

| Partition | Expected events | Expected share | Achieved processed events | Achieved share |
| --- | ---: | ---: | ---: | ---: |
| P0 | 6,000 | 16.67% | 6,000 | 16.67% |
| P1 | 6,000 | 16.67% | 6,000 | 16.67% |
| P2 | 6,000 | 16.67% | 6,000 | 16.67% |
| P3 | 6,000 | 16.67% | 6,000 | 16.67% |
| P4 | 6,000 | 16.67% | 6,000 | 16.67% |
| P5 | 6,000 | 16.67% | 6,000 | 16.67% |

Verification query:

```sql
select partition_no, count(*) as processed
from event_processing_logs
where event_id like 'v3-phase3-phase3-balanced-c6-20260823-001-%'
group by partition_no
order by partition_no;
```

Result:

```text
 partition_no | processed
--------------+-----------
            0 |      6000
            1 |      6000
            2 |      6000
            3 |      6000
            4 |      6000
            5 |      6000
```
