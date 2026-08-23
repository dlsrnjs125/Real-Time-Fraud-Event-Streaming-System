# AFTER_RESULT_SAVE_BEFORE_ACK Redelivery Evidence

Run ID: `phase4-after-result-20260824-001`

Failure point: `AFTER_RESULT_SAVE_BEFORE_ACK`

Target event: `v3-phase4-phase4-after-result-20260824-001-0`

Next event: `v3-phase4-phase4-after-result-20260824-001-1`

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

First delivery injected a local drill failure after result save and before ack:

```text
stateful redelivery drill failure injected
eventId=v3-phase4-phase4-after-result-20260824-001-0
failurePoint=AFTER_RESULT_SAVE_BEFORE_ACK
```

Spring Kafka then sought the same offset after the listener exception:

```text
KafkaException: Seek to current after exception
StatefulRedeliveryDrillException: Injected stateful redelivery failure at AFTER_RESULT_SAVE_BEFORE_ACK
```

Redelivery hit the result duplicate fast path and skipped stateful processing:

```text
transaction event duplicate fraud result skipped
eventId=v3-phase4-phase4-after-result-20260824-001-0
partition=5
offset=0
processingDuplicateSkipped=true
```

This log line is emitted before Redis/rule/result sink execution and represents the application-level guard used after a result-save-before-ack failure.

## Next Event Stability

The next event saw the expected two-event state after E0's saved result and redelivery fast path:

```text
eventId=v3-phase4-phase4-after-result-20260824-001-1
partition=5
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
v3-phase4-phase4-after-result-20260824-001-0  | 0          | LOW        | APPROVE  |
v3-phase4-phase4-after-result-20260824-001-1  | 0          | LOW        | APPROVE  |
```

Target result row count:

```text
v3-phase4-phase4-after-result-20260824-001-0 => 1
```

Final run counts:

```text
receipts        = 20
fraud_results   = 20
processing_logs = 20
```

## Redis Evidence

```text
ZCARD fraud:tx:user:v3-phase4-stateful-redelivery-user-phase4-after-result-20260824-001:events
20
```

First members:

```text
v3-phase4-phase4-after-result-20260824-001-0
v3-phase4-phase4-after-result-20260824-001-1
v3-phase4-phase4-after-result-20260824-001-2
```

## Final Lag

```text
partition 5 current-offset=20 log-end-offset=20 lag=0
```

Conclusion: failure after result save but before ack caused Kafka redelivery. On redelivery, the existing fraud result was detected before Redis/rule/result sink execution, so stateful processing was not repeated for E0. The target result row remained unique, the next event observed stable state, and final lag drained to zero.
