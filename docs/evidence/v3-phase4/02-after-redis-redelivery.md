# AFTER_REDIS_UPDATE_BEFORE_RESULT Redelivery Evidence

Run ID: `phase4-after-redis-threshold-20260824-001`

Failure point: `AFTER_REDIS_UPDATE_BEFORE_RESULT`

Target event: `v3-phase4-phase4-after-redis-threshold-20260824-001-3`

Next event: `v3-phase4-phase4-after-redis-threshold-20260824-001-4`

## Workload

```text
targetEps: 1
duration: 20s
eventLimit: 20
emittedEventCount: 20
httpRequestFailedRate: 0
checkSuccessRate: 1
drillTargetEventIndex: 3
drillNextEventIndex: 4
```

## Redelivery Log Evidence

```text
stateful redelivery drill failure injected
eventId=v3-phase4-phase4-after-redis-threshold-20260824-001-3
failurePoint=AFTER_REDIS_UPDATE_BEFORE_RESULT

KafkaException: Seek to current after exception
StatefulRedeliveryDrillException: Injected stateful redelivery failure at AFTER_REDIS_UPDATE_BEFORE_RESULT

transaction event consumed
eventId=v3-phase4-phase4-after-redis-threshold-20260824-001-3
partition=3
offset=3
processingDuplicateSkipped=true
fraudDuplicateSkipped=false
transactionCount=4
amountSum=400000
riskScore=0
riskLevel=LOW
decision=APPROVE
```

## Redis Duplicate Count Evidence

The first delivery already added E3 to Redis before the injected failure. Redelivery performed the same ZSET member write with the same `eventId`, so final user window cardinality remained equal to the number of distinct workload events.

```text
Final ZCARD = 20
Final ZCOUNT -inf +inf = 20
```

## Threshold-Adjacent Next Event

```text
eventId=v3-phase4-phase4-after-redis-threshold-20260824-001-4
partition=3
offset=4
transactionCount=5
amountSum=500000
matchedRules=[RAPID_TRANSACTION_COUNT]
riskScore=30
riskLevel=MEDIUM
decision=REVIEW
```

## PostgreSQL Evidence

```text
event_id                                               | risk_score | risk_level | decision | matched_rules
-------------------------------------------------------+------------+------------+----------+-------------------------
v3-phase4-phase4-after-redis-threshold-20260824-001-3  | 0          | LOW        | APPROVE  |
v3-phase4-phase4-after-redis-threshold-20260824-001-4  | 30         | MEDIUM     | REVIEW   | RAPID_TRANSACTION_COUNT
```

Target result row count:

```text
v3-phase4-phase4-after-redis-threshold-20260824-001-3 => 1
```

Final run counts:

```text
receipts        = 20
fraud_results   = 20
processing_logs = 20
```

## Final Lag

```text
partition 3 current-offset=20 log-end-offset=20 lag=0
```

Conclusion: failure after Redis update caused Kafka redelivery without result persistence on the first delivery. Because Redis ZSET member identity is `eventId`, redelivery did not duplicate the target event in the user window. The threshold-adjacent next event produced the expected rule match and risk score, and final lag drained to zero.
