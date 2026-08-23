# Hot P2 Partition Lag

Run: `phase3-hot-p2-c6-20260823-001`

Workload: `v3-phase3-partition-skew-hot-p2`

Consumer concurrency: `6`

Driver result:

| Metric | Value |
| --- | ---: |
| Configured events | 36,000 |
| Emitted events | 36,000 |
| Target EPS | 300 |
| Achieved EPS | 299.94 |
| Dropped iterations | 0 |
| HTTP request failure rate | 0 |

Immediate Kafka consumer group state after workload completion:

```text
GROUP                TOPIC              PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
fraud-event-consumer transaction-events 0          32771           32771           0
fraud-event-consumer transaction-events 1          32773           32773           0
fraud-event-consumer transaction-events 2          38949           51503           12554
fraud-event-consumer transaction-events 3          32772           32772           0
fraud-event-consumer transaction-events 4          32776           32776           0
fraud-event-consumer transaction-events 5          32770           32770           0
```

Partial DB distribution at the same point:

```text
 partition_no | processed
--------------+-----------
            0 |      2880
            1 |      2880
            2 |      9101
            3 |      2880
            4 |      2880
            5 |      2880
```

Drain observation:

```text
lag_sum=11226
lag_sum=10776
lag_sum=10337
lag_sum=9933
lag_sum=9500
lag_sum=9078
lag_sum=8653
...
lag_sum=401
lag_sum=191
lag_sum=0
```

Interpretation:

- P0/P1/P3/P4/P5 completed and stayed at lag 0.
- P2 alone retained backlog because it received 60% of total traffic while one consumer thread owned that partition.
- This separates partition-local backlog from a whole-system API/Kafka/DB outage. The accepted k6 run had no HTTP failures and no dropped iterations.
