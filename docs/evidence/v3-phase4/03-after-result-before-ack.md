# AFTER_RESULT_SAVE_BEFORE_ACK Redelivery Evidence

Run ID: `phase4-after-result-threshold-20260824-001`

Failure point: `AFTER_RESULT_SAVE_BEFORE_ACK`

Target event: `v3-phase4-phase4-after-result-threshold-20260824-001-3`

Next event: `v3-phase4-phase4-after-result-threshold-20260824-001-4`

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
eventId=v3-phase4-phase4-after-result-threshold-20260824-001-3
failurePoint=AFTER_RESULT_SAVE_BEFORE_ACK

KafkaException: Seek to current after exception
StatefulRedeliveryDrillException: Injected stateful redelivery failure at AFTER_RESULT_SAVE_BEFORE_ACK

transaction event duplicate fraud result skipped
eventId=v3-phase4-phase4-after-result-threshold-20260824-001-3
partition=5
offset=23
processingDuplicateSkipped=true
```

The duplicate fast path is emitted before Redis/rule/result sink execution. It represents the application-level guard used after a result-save-before-ack failure.

## Threshold-Adjacent Next Event

```text
eventId=v3-phase4-phase4-after-result-threshold-20260824-001-4
partition=5
offset=24
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
v3-phase4-phase4-after-result-threshold-20260824-001-3  | 0          | LOW        | APPROVE  |
v3-phase4-phase4-after-result-threshold-20260824-001-4  | 30         | MEDIUM     | REVIEW   | RAPID_TRANSACTION_COUNT
```

Target result row count:

```text
v3-phase4-phase4-after-result-threshold-20260824-001-3 => 1
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
partition 5 current-offset=40 log-end-offset=40 lag=0
```

Conclusion: failure after result save but before ack caused Kafka redelivery. On redelivery, the existing fraud result was detected before Redis/rule/result sink execution, so stateful processing was not repeated for E3. The target result row remained unique, the threshold-adjacent next event produced the expected rule match and risk score, and final lag drained to zero.
