# V3 Phase 4 Redelivery and Stateful Processing Semantics Plan

## 1. Status

- Status: Done
- Scope: controlled redelivery failure injection, Redis state idempotency checks, result-sink duplicate guard verification, runtime evidence plan
- Completion boundary: this phase is complete only after local evidence records all required failure points and verifies Redis window count, Redis amount sum, fraud result count, next-event decision, and final Consumer Lag after drain.

## 2. Goal

Phase 4 verifies stateful processing semantics under Kafka redelivery. The key question is not only whether `fraud_results.event_id` prevents duplicate rows, but whether Redis user state and subsequent fraud decisions remain explainable when the same Kafka record is delivered again after a failure.

## 3. Processing Path

```text
Kafka record delivery
↓
processing log persistence
↓
fraud result duplicate precheck
↓
Redis sliding-window update
↓
rule evaluation
↓
fraud result sink
↓
manual ack
```

The Consumer still acknowledges only after required processing succeeds. A failure before ack must leave Kafka free to redeliver the record.

## 4. Failure Points

Phase 4 adds a local-only redelivery drill injector controlled by:

```yaml
fraud.consumer.redelivery-drill.enabled
fraud.consumer.redelivery-drill.event-id
fraud.consumer.redelivery-drill.failure-point
fraud.consumer.redelivery-drill.fail-once
```

Supported failure points:

| Failure point | Meaning | Expected redelivery behavior |
| --- | --- | --- |
| `BEFORE_REDIS_UPDATE` | Crash before Redis state mutation | Redis window is unchanged until redelivery succeeds. |
| `AFTER_REDIS_UPDATE_BEFORE_RESULT` | Crash after Redis mutation and before result save | Redis ZSET member uses `eventId`, so redelivery must not increase count for the same event. |
| `AFTER_RESULT_SAVE_BEFORE_ACK` | Crash after result save and before ack | Redelivery should hit the result duplicate precheck and skip Redis/rule/result sink. |

The injector is disabled by default and should only be enabled for local drills.
`fail-once` tracking is process-local and resets when `app-consumer` restarts. Do not combine this drill with a process-restart test unless the expected repeated injection is documented in the evidence.

## 5. Workload

Use the deterministic Phase 4 workload. This is a semantics drill, not a throughput test, so it emits 20 events over 20 seconds at 1 EPS while the Redis runtime window remains the normal 5-minute sliding window.

```bash
V3_RUN_ID=phase4-after-redis-001 make k6-v3-phase4-stateful-redelivery
```

The k6 summary records `drillTargetEventId`. Start `app-consumer` with that event id and one failure point at a time.

Example:

```bash
FRAUD_CONSUMER_CONCURRENCY=1 \
./gradlew :app-consumer:bootRun --args='\
--fraud.consumer.redelivery-drill.enabled=true \
--fraud.consumer.redelivery-drill.event-id=v3-phase4-phase4-after-redis-001-0 \
--fraud.consumer.redelivery-drill.failure-point=AFTER_REDIS_UPDATE_BEFORE_RESULT \
--fraud.consumer.redelivery-drill.fail-once=true'
```

## 6. Required Evidence

Record each accepted drill under `docs/evidence/v3-phase4/`:

- failure point and target `eventId`
- first delivery failure log
- redelivery success log
- Redis ZSET `ZCARD` for the target user
- Redis amount/hash evidence for the target event
- `fraud_results` count for target `eventId`
- next event's fraud result/risk score after redelivery
- final Consumer Lag after drain

## 7. Completion Criteria

Phase 4 can be marked complete only when evidence answers:

- Does a failure before Redis leave no Redis state mutation?
- Does a failure after Redis but before result save avoid double-counting the same event after redelivery?
- Does a failure after result save but before ack skip Redis and result sink on redelivery?
- Does the next event see an explainable Redis window count and fraud decision?
- Do final durable counts and final Consumer Lag align after drain?

## 8. Known Constraints

- The drill injector is a local reproducibility tool, not a production failure mechanism.
- This phase does not change the Kafka partition key or retry/DLT policy.
- Redis state remains a short-lived detection state store; PostgreSQL remains the durable result authority.
- Accepted runtime evidence is recorded under `docs/evidence/v3-phase4/`. The evidence is local Docker topology evidence and should not be generalized as production failure behavior without rerunning on the target topology.
