# BEFORE_REDIS_UPDATE Redelivery Evidence

Run ID: `phase4-before-redis-threshold-20260824-002`

Failure point: `BEFORE_REDIS_UPDATE`

Target event: `v3-phase4-phase4-before-redis-threshold-20260824-002-3`

Next event: `v3-phase4-phase4-before-redis-threshold-20260824-002-4`

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

## Failure-Time Redis Snapshot

The drill used `pause-before-throw=20s` to inspect Redis after the failure injection log and before the exception reached Spring Kafka's error handler.

```text
ZCARD fraud:tx:user:v3-phase4-stateful-redelivery-user-phase4-before-redis-threshold-20260824-002:events
3

EXISTS fraud:tx:event:v3-phase4-phase4-before-redis-threshold-20260824-002-3
0

ZRANGE fraud:tx:user:v3-phase4-stateful-redelivery-user-phase4-before-redis-threshold-20260824-002:events 0 -1
v3-phase4-phase4-before-redis-threshold-20260824-002-0
v3-phase4-phase4-before-redis-threshold-20260824-002-1
v3-phase4-phase4-before-redis-threshold-20260824-002-2
```

## Redelivery Log Evidence

```text
stateful redelivery drill failure injected
eventId=v3-phase4-phase4-before-redis-threshold-20260824-002-3
failurePoint=BEFORE_REDIS_UPDATE

KafkaException: Seek to current after exception
StatefulRedeliveryDrillException: Injected stateful redelivery failure at BEFORE_REDIS_UPDATE

transaction event consumed
eventId=v3-phase4-phase4-before-redis-threshold-20260824-002-3
partition=4
offset=3
processingDuplicateSkipped=true
fraudDuplicateSkipped=false
transactionCount=4
amountSum=400000
riskScore=0
riskLevel=LOW
decision=APPROVE
```

## Threshold-Adjacent Next Event

```text
eventId=v3-phase4-phase4-before-redis-threshold-20260824-002-4
partition=4
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
event_id                                                | risk_score | risk_level | decision | matched_rules
--------------------------------------------------------+------------+------------+----------+-------------------------
v3-phase4-phase4-before-redis-threshold-20260824-002-3  | 0          | LOW        | APPROVE  |
v3-phase4-phase4-before-redis-threshold-20260824-002-4  | 30         | MEDIUM     | REVIEW   | RAPID_TRANSACTION_COUNT
```

Target result row count:

```text
v3-phase4-phase4-before-redis-threshold-20260824-002-3 => 1
```

Final run counts:

```text
receipts        = 20
fraud_results   = 20
processing_logs = 20
```

## Redis Evidence

```text
Final ZCARD = 20
Final ZCOUNT -inf +inf = 20
```

## Final Lag

```text
partition 4 current-offset=20 log-end-offset=20 lag=0
```

Conclusion: failure before Redis did not mutate target event state on the first delivery. Redelivery processed the same Kafka record, the target result row remained unique, the threshold-adjacent next event produced the expected rule match and risk score, and final lag drained to zero.
