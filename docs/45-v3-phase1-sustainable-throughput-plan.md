# V3 Phase 1 Sustainable Throughput and Backlog Recovery Plan

## 1. Status

- Phase: V3 Phase 1
- Scope: Sustainable throughput, knee point, backlog recovery, and first-bottleneck attribution
- Status: Complete for the committed local Phase 1 evidence
- Runtime completion: local capacity discovery, knee confirmation, backlog recovery, bottleneck attribution, targeted fix, and same-load re-test are recorded in the evidence document

Phase 1 must not start by assuming Kafka, PostgreSQL, Redis, app-api, app-consumer, HikariCP, or k6 is the bottleneck. The phase first measures the pipeline, then selects the smallest justified improvement only if evidence identifies a primary bottleneck.

## 2. Goal

Answer these questions with measured local evidence:

- What local EPS range can the HTTP intake and async Consumer pipeline sustain without continuously growing Consumer Lag?
- Where does `Input EPS > Effective Consumer EPS` first appear?
- What happens to API, Kafka publish, Consumer service, stage latency, total Lag, and partition Lag immediately before and after the knee point?
- After overload ends, how quickly does backlog drain?
- Which service and stage first limit throughput?

All numbers are local Docker evidence, not production capacity.

## 3. Non-Goals

- Do not tune Kafka partitions, Consumer concurrency, Hikari pool size, Redis operations, batch inserts, outbox, or duplicate-guard logic before evidence points there.
- Do not mix HTTP k6 capacity with future direct-Kafka producer capacity.
- Do not implement Phase 2 state-size, Phase 3 hot-partition scale-out, Phase 5 lateness semantics, or Phase 6 source emulator behavior.
- Do not mark Phase 1 complete from unit tests alone.

## 4. Definitions

### Sustainable EPS

An EPS range is locally sustainable only when all of these hold over a long enough plateau:

- achieved EPS is close to target EPS
- HTTP failure and dropped iterations remain explainable and within the accepted run criteria
- receipt persisted EPS, Kafka publish success EPS, and Consumer delivery EPS remain aligned
- total and partition Lag do not continuously grow
- unexpected DLT, duplicate-result, or degraded behavior does not explain the run

### Knee Point

The knee point candidate is the first range where one or more of these begins:

- achieved EPS stops increasing with target EPS
- Consumer delivery EPS plateaus while input continues to increase
- total or partition Lag grows with a positive sustained slope
- API, Kafka publish wait, Consumer service, or a Consumer stage p95/p99 rises sharply

### Backlog Recovery Time

Recovery starts when the overload stage ends and input is reduced. Recovery completes when total Consumer Lag returns to 0 or to a documented low threshold. The same threshold must be used within the experiment.

## 5. Workloads

Committed manifests:

| Manifest | Role | Purpose |
|---|---|---|
| `capacity-discovery-v1.json` | `NORMAL_CAPACITY` | broad staged discovery from low EPS upward |
| `knee-confirmation-v1.json` | `NORMAL_CAPACITY` | narrower staged confirmation around the candidate knee |
| `backlog-recovery-v1.json` | `ORGANIC_BURST` | overload, Lag growth, and drain measurement |

All Phase 1 workloads use:

- `driverType=HTTP_K6`
- `eventTimeMode=REBASE_TO_ARRIVAL`
- synthetic transaction attributes
- explicit `V3_RUN_ID`
- summary files under ignored `load-test/k6/results/`

The k6 runner converts each manifest stage to a short 1s ramp followed by a plateau for the declared stage duration. This keeps the manifest's `targetEps * duration` event-limit contract aligned with the generated workload.

## 6. Metrics

Stream boundaries:

- `fraud.stream.intake.receipt.persisted.total`
- `fraud.stream.kafka.publish.success.total`
- `fraud.stream.kafka.publish.failure.total`
- `fraud.stream.consumer.delivery.total`

API intake:

- `fraud.api.intake.service.latency`
- `fraud.api.receipt.persistence.latency`
- `fraud.kafka.publish.wait.latency`
- `fraud.api.receipt.status.update.latency`

Consumer stages:

- `fraud.consumer.service.latency`
- `fraud.processing.log.latency`
- `fraud.result.precheck.latency`
- `fraud.redis.state.latency`
- `fraud.rule.processing.latency`
- `fraud.result.sink.latency`

Kafka and resource:

- `kafka_consumergroup_lag`
- partition lag and partition incoming rate from kafka-exporter
- Kafka client consumed records, assignment, and rebalance metrics
- Hikari active, pending, max
- app-api/app-consumer CPU and JVM memory
- Redis memory and Redis exporter resource signals

## 7. Slow Diagnostics

`app-consumer` emits `type=SLOW_EVENT` for slow Consumer service attempts.

`app-api` emits `type=SLOW_INTAKE` for slow intake attempts and records:

- `intakeServiceMs`
- `receiptPersistenceMs`
- `kafkaPublishWaitMs`
- `statusUpdateMs`
- `outcome`

These logs are event-level diagnostics and must not become metric tags.

## 8. Experiment Order

1. Phase 0 preflight: `make final-check`
2. Clean local infrastructure startup
3. app-api migration and readiness
4. app-consumer readiness and partition assignment
5. capacity discovery run
6. candidate knee selection
7. knee confirmation run
8. backlog recovery run
9. bottleneck attribution from metrics, logs, and resource signals
10. one targeted improvement if evidence supports it
11. same-load re-test for the knee and recovery workloads

## 9. Bottleneck Attribution Format

Use this structure:

```text
Symptom
Metric Evidence
Initial Hypothesis
Isolation Experiment
Root Cause
Fix Candidate
Trade-off
Selected Fix
Same-Load Re-test
```

## 10. Correctness Guardrails

Every performance change must preserve:

- PostgreSQL idempotency and unique constraints
- duplicate FraudResult prevention
- processing log contract
- manual ack after required processing and persistence
- DLT/retry behavior
- Redis degraded behavior
- ruleVersion propagation

## 11. Completion Criteria

Phase 1 is complete only when the evidence document contains:

- capacity discovery results
- local sustainable EPS range
- knee point candidate and confirmation
- overload and backlog recovery evidence
- Lag growth and drain rates
- primary bottleneck service and stage
- metric and slow-log evidence
- selected fix or documented no-fix decision
- same-load before/after if a fix is applied
- verification commands and results
- known limitations and next-phase boundary
