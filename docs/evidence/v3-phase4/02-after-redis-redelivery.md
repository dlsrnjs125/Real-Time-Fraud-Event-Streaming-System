# AFTER_REDIS_UPDATE_BEFORE_RESULT Redelivery Evidence

Run ID: `phase4-after-redis-20260824-001`

Failure point: `AFTER_REDIS_UPDATE_BEFORE_RESULT`

Target event: `v3-phase4-phase4-after-redis-20260824-001-0`

Next event: `v3-phase4-phase4-after-redis-20260824-001-1`

## Workload

```text
targetEps: 1
duration: 20s
eventLimit: 20
emittedEventCount: 20
httpRequestFailedRate: 0
checkSuccessRate: 1
```

## Redelivery Log Evidence

First delivery injected a local drill failure after Redis update and before result save:

```text
stateful redelivery drill failure injected
eventId=v3-phase4-phase4-after-redis-20260824-001-0
failurePoint=AFTER_REDIS_UPDATE_BEFORE_RESULT
```

Spring Kafka then sought the same offset after the listener exception:

```text
KafkaException: Seek to current after exception
StatefulRedeliveryDrillException: Injected stateful redelivery failure at AFTER_REDIS_UPDATE_BEFORE_RESULT
```

Redelivery processed the same Kafka offset successfully:

```text
transaction event consumed
eventId=v3-phase4-phase4-after-redis-20260824-001-0
partition=2
offset=0
processingDuplicateSkipped=true
fraudDuplicateSkipped=false
transactionCount=1
amountSum=100000
riskScore=0
riskLevel=LOW
decision=APPROVE
```

## Redis Duplicate Count Evidence

The first delivery already added E0 to Redis before the injected failure. Redelivery performs the same ZSET member write with the same `eventId`, so final user window cardinality remains equal to the number of distinct workload events.

```text
ZCARD fraud:tx:user:v3-phase4-stateful-redelivery-user-phase4-after-redis-20260824-001:events
20
```

```text
ZCOUNT fraud:tx:user:v3-phase4-stateful-redelivery-user-phase4-after-redis-20260824-001:events -inf +inf
20
```

First members:

```text
v3-phase4-phase4-after-redis-20260824-001-0
v3-phase4-phase4-after-redis-20260824-001-1
v3-phase4-phase4-after-redis-20260824-001-2
```

## Next Event Stability

The next event saw the expected two-event state after E0 redelivery:

```text
eventId=v3-phase4-phase4-after-redis-20260824-001-1
partition=2
offset=1
transactionCount=2
amountSum=200000
riskScore=0
riskLevel=LOW
decision=APPROVE
```

## PostgreSQL Evidence

```text
event_id                                     | risk_score | risk_level | decision | matched_rules
---------------------------------------------+------------+------------+----------+--------------
v3-phase4-phase4-after-redis-20260824-001-0  | 0          | LOW        | APPROVE  |
v3-phase4-phase4-after-redis-20260824-001-1  | 0          | LOW        | APPROVE  |
```

Target result row count:

```text
v3-phase4-phase4-after-redis-20260824-001-0 => 1
```

Final run counts:

```text
receipts        = 20
fraud_results   = 20
processing_logs = 20
```

## Final Lag

```text
partition 2 current-offset=20 log-end-offset=20 lag=0
```

Conclusion: failure after Redis update caused Kafka redelivery without result persistence on the first delivery. Because Redis ZSET member identity is `eventId`, redelivery did not duplicate the target event in the user window. The target result row remained unique, the next event observed stable state, and final lag drained to zero.
