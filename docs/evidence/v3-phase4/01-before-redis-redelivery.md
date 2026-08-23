# BEFORE_REDIS_UPDATE Redelivery Evidence

Run ID: `phase4-before-redis-20260824-001`

Failure point: `BEFORE_REDIS_UPDATE`

Target event: `v3-phase4-phase4-before-redis-20260824-001-0`

Next event: `v3-phase4-phase4-before-redis-20260824-001-1`

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

First delivery injected a local drill failure before Redis update:

```text
stateful redelivery drill failure injected
eventId=v3-phase4-phase4-before-redis-20260824-001-0
failurePoint=BEFORE_REDIS_UPDATE
```

Spring Kafka then sought the same offset after the listener exception:

```text
KafkaException: Seek to current after exception
StatefulRedeliveryDrillException: Injected stateful redelivery failure at BEFORE_REDIS_UPDATE
```

Second delivery processed the same offset successfully:

```text
transaction event consumed
eventId=v3-phase4-phase4-before-redis-20260824-001-0
partition=0
offset=0
processingDuplicateSkipped=true
fraudDuplicateSkipped=false
transactionCount=1
amountSum=100000
riskScore=0
riskLevel=LOW
decision=APPROVE
```

## Next Event Stability

The next event saw the expected two-event state after E0 redelivery:

```text
eventId=v3-phase4-phase4-before-redis-20260824-001-1
partition=0
offset=1
transactionCount=2
amountSum=200000
riskScore=0
riskLevel=LOW
decision=APPROVE
```

## PostgreSQL Evidence

```text
event_id                                      | risk_score | risk_level | decision | matched_rules
----------------------------------------------+------------+------------+----------+--------------
v3-phase4-phase4-before-redis-20260824-001-0  | 0          | LOW        | APPROVE  |
v3-phase4-phase4-before-redis-20260824-001-1  | 0          | LOW        | APPROVE  |
```

Target result row count:

```text
v3-phase4-phase4-before-redis-20260824-001-0 => 1
```

Final run counts:

```text
receipts        = 20
fraud_results   = 20
processing_logs = 20
```

## Redis Evidence

```text
ZCARD fraud:tx:user:v3-phase4-stateful-redelivery-user-phase4-before-redis-20260824-001:events
20
```

First members:

```text
v3-phase4-phase4-before-redis-20260824-001-0
v3-phase4-phase4-before-redis-20260824-001-1
v3-phase4-phase4-before-redis-20260824-001-2
```

## Final Lag

```text
partition 0 current-offset=20 log-end-offset=20 lag=0
```

Conclusion: failure before Redis did not commit the offset on first delivery. Redelivery processed the same Kafka record, Redis state reached the expected single-user 20-event window, the target result row remained unique, the next event observed stable state, and final lag drained to zero.
