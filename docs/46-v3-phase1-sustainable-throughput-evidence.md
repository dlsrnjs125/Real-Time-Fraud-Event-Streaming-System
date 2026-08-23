# V3 Phase 1 Sustainable Throughput and Backlog Recovery Evidence

## 1. Status

- Status: Complete for local Docker Phase 1 evidence
- Scope: HTTP k6 intake, Kafka publish, app-consumer processing, Consumer Lag, backlog recovery, and first bottleneck attribution
- Selected improvement: configure app-consumer listener concurrency explicitly for the local six-partition topology
- Completion boundary: this is local evidence, not production capacity or a direct-Kafka producer benchmark

## 2. Implementation Summary

- Added API intake stage Timers:
  - `fraud.api.intake.service.latency`
  - `fraud.api.intake.transaction.latency`
  - `fraud.api.receipt.persistence.latency`
  - `fraud.kafka.publish.wait.latency`
  - `fraud.api.receipt.status.update.latency`
- Added slow API intake diagnostic log with `type=SLOW_INTAKE`.
- Added staged Phase 1 manifests:
  - `capacity-discovery-v1.json`
  - `knee-confirmation-v1.json`
  - `backlog-recovery-v1.json`
- Added `load-test/k6/scenarios/v3-phase1-throughput.js`.
- Extended workload validation so staged workloads must keep `eventLimit` and maximum `targetEps` aligned with the stage contract.
- Added V3 dashboard panels for API intake, Kafka publish wait, receipt DB stages, Hikari connections, and separated resource units.
- Added configurable Consumer concurrency with repository default `fraud.consumer.concurrency=1`; local Phase 1 after-run uses `FRAUD_CONSUMER_CONCURRENCY=6`.
- API intake stage timers record durations measured with `System.nanoTime()` and discard negative durations before recording.

## 3. Experiment Fingerprint

| Field | Value |
|---|---|
| Branch | `feat/v3-phase1-sustainable-throughput-recovery` |
| Runtime verification SHA | `44639fc` |
| Host / OS | local macOS developer machine |
| Runtime | Docker Compose local Kafka, PostgreSQL, Redis, Prometheus, Grafana; Spring Boot apps on host |
| Kafka partitions | `transaction-events` has 6 partitions |
| Consumer before fix | one listener consumer assigned all six partitions |
| Consumer after fix | six listener consumers, one partition each |
| Prometheus scrape interval | 15s |
| Driver | `HTTP_K6` |
| Event-time mode | `REBASE_TO_ARRIVAL` |
| Result files | local ignored `load-test/k6/results/*phase1*summary.json` |

`44639fc` is the runtime code SHA used for the final before/after recovery evidence. Later documentation commits record the measured results but do not change the runtime behavior under test.

## 3-1. Visual and Terminal Evidence Artifacts

Selected local evidence captures are stored under `docs/evidence/v3-phase1/`.

| File | Purpose |
|---|---|
| `01-before-lag-growth.png` | Grafana evidence that total and partition Consumer Lag grew with one listener consumer |
| `02-before-bottleneck-isolation.png` | Grafana dashboard crop for Consumer-side bottleneck isolation |
| `02b-before-api-kafka-hikari.png` | Supplemental crop showing Kafka publish wait and Hikari panels |
| `03-consumer-assignment-before.txt` | Kafka consumer group evidence that one consumer owned all six partitions |
| `04-consumer-assignment-after.txt` | Kafka consumer group evidence that six consumers owned the six partitions |
| `05-after-lag-contained.png` | Grafana evidence that lag stayed bounded with `FRAUD_CONSUMER_CONCURRENCY=6` |
| `06-final-consistency-check.txt` | PostgreSQL row-count equality and final Consumer Lag 0 |

The 2026-08-23 before screenshot run used run ID `phase1-evidence-before-concurrency1-20260823-001` and emitted 50,993 of 51,000 configured HTTP events with 7 dropped iterations. It is kept as visual evidence of the bottleneck pattern, not as the authoritative final row-count run. The after evidence run used run ID `phase1-evidence-after-concurrency6-20260823-001`, emitted 51,000 of 51,000 events, and produced the final consistency artifact.

## 4. Workload Contract

`backlog-recovery-v1.json` defines four plateau stages:

| Stage | Target EPS | Duration | Expected events |
|---|---:|---:|---:|
| warmup | 50 | 60s | 3,000 |
| sustainable | 100 | 60s | 6,000 |
| overload | 300 | 120s | 36,000 |
| recovery | 50 | 120s | 6,000 |
| Total | - | 360s | 51,000 |

The Phase 1 k6 runner maps each manifest stage to a separate `constant-arrival-rate` scenario with a stage-level hard emission limit. `emittedEventCount` in the generated summary is the authoritative HTTP emission count. k6 may print an extra completed iteration at the boundary when a post-limit iteration returns before issuing an HTTP request; those iterations are not counted as emitted events.

## 5. Capacity Discovery

Run ID: `phase1-discovery-20260821-002`

| Boundary | Value |
|---|---:|
| Configured event limit | 22,500 |
| Emitted events | 22,500 |
| Average achieved EPS across staged run | 92.21 |
| Max target EPS | 200 |
| HTTP failure rate | 0 |
| Dropped iterations | 0 / null |
| k6 HTTP p95 | 9.65 ms |
| Final receipt / result / processing-log count | 22,500 / 22,500 / 22,500 |
| Final Consumer Lag | 0 |
| API intake p95 | 5.21 ms |
| Kafka publish wait p95 | 1.95 ms |
| Consumer service p95 | 11.82 ms |
| Hikari pending max | 0 |

Observed slow diagnostic sample:

```text
type=SLOW_INTAKE intakeServiceMs=506 receiptPersistenceMs=33 kafkaPublishWaitMs=413 statusUpdateMs=2 outcome=SUCCESS
```

Interpretation: the broad discovery run completed cleanly through the 200 EPS stage. The only notable slow intake was a warm-up style Kafka publish wait sample, not a sustained DB or API saturation signal.

## 6. Knee Confirmation Before Fix

Run ID: `phase1-knee-20260821-001`

| Boundary | Value |
|---|---:|
| Configured event limit | 60,000 |
| Emitted events | 60,000 |
| Average achieved EPS across staged run | 196.72 |
| Max target EPS | 300 |
| HTTP failure rate | 0 |
| Dropped iterations | 0 / null |
| k6 HTTP p95 | 3.81 ms |
| Immediate final receipt count | 82,500 cumulative |
| Immediate final result count | 68,440 cumulative |
| Immediate final processing-log count | 68,442 cumulative |
| Immediate Consumer Lag | 14,027 |
| Drain to zero | about 60s |

Lag by partition immediately after the run:

| Partition | Lag |
|---:|---:|
| 0 | 2,442 |
| 1 | 2,984 |
| 2 | 2,809 |
| 3 | 2,211 |
| 4 | 1,711 |
| 5 | 1,870 |

Interpretation: API intake accepted the workload without HTTP failures, while Consumer processing fell behind under the 300 EPS stage. The lag spread across all partitions pointed to insufficient consumer parallelism rather than one hot partition.

## 7. Backlog Recovery Before Fix

Run ID: `phase1-recovery-before-concurrency1-20260821-003`

| Boundary | Value |
|---|---:|
| Configured event limit | 51,000 |
| Emitted events | 51,000 |
| Average achieved EPS across staged run | 141.67 |
| Max target EPS | 300 |
| HTTP failure rate | 0 |
| Dropped iterations | null |
| k6 HTTP p95 | 6.35 ms |
| Final receipt / result / log count | 51,000 / 51,000 / 51,000 |
| Final Consumer Lag | 0 |
| API service p99 | 5.86 ms |
| API transaction p99 | 10.30 ms |
| Kafka publish wait p99 | 2.20 ms |
| Consumer service p99 | 13.29 ms |
| Hikari pending max | 0 |

Lag recovery calculation uses total Consumer Lag from Prometheus at 5s query step, with recovery start defined as overload-stage end.

| Recovery Field | Value |
|---|---:|
| Overload start offset | 120s |
| Overload end / recovery start offset | 240s |
| First positive lag offset | 140s |
| Lag at recovery start | 15,720 |
| Peak Lag | 17,857 at +245s |
| First zero lag after recovery start | +410s |
| Lag growth rate | 154.06 records/s |
| Lag drain rate from recovery start | 92.47 records/s |
| Recovery time | 170s |

Interpretation: the 300 EPS overload stage produced a real backlog with one listener consumer. API service, API transaction, Kafka publish wait, and Hikari pending stayed healthy, so the evidence does not support app-api, Kafka ACK wait, or DB connection starvation as the primary bottleneck.

## 8. Bottleneck Attribution

| Question | Evidence | Answer |
|---|---|---|
| app-api, Kafka, app-consumer, or load generator? | k6 had 0 HTTP failures; API transaction p99 was 10.30 ms; Kafka publish wait p99 was 2.20 ms; Hikari pending max was 0; Lag grew under 300 EPS | app-consumer throughput |
| If Consumer, which boundary? | Lag was spread across all six partitions while only one listener consumer processed them before the fix | Kafka listener partition-consumption parallelism |
| Is PostgreSQL connection starvation supported? | Hikari pending max was 0 for app-api and app-consumer | no |
| Is Kafka publish wait the primary bottleneck? | publish wait p99 was low except one warm-up slow sample | no sustained evidence |
| Is load generator saturation supported? | k6 maintained target stages and reported no dropped iterations | no |

## 9. Root Cause

```text
Symptom: Consumer Lag grew during the 300 EPS stage while API intake stayed healthy.
Metric Evidence: 51,000 emitted, HTTP failure rate 0, peak Lag 17,857, API transaction p99 10.30 ms, Kafka publish wait p99 2.20 ms, Hikari pending 0.
Initial Hypothesis: app-consumer could not consume and persist at the input rate.
Isolation Experiment: inspect consumer group assignment and partition lag.
Root Cause: Kafka listener partition-consumption parallelism was mismatched to topology: one consumer thread owned all six transaction-events partitions.
Fix Candidate: expose fraud.consumer.concurrency and set it explicitly to 6 for the local six-partition experiment.
Trade-off: higher local DB/Redis concurrency and more consumer clients; still bounded by partition count.
Selected Fix: keep repository default concurrency at 1, and use FRAUD_CONSUMER_CONCURRENCY=6 for the local Phase 1 after-run.
Same-Load Re-test: backlog-recovery workload re-run with the same 51,000 events and max 300 EPS.
```

## 10. Same-Load Recovery Re-test After Fix

Run ID: `phase1-recovery-after-concurrency6-20260821-001`

| Metric | Before | After |
|---|---:|---:|
| Workload | `backlog-recovery-v1` | `backlog-recovery-v1` |
| Runtime verification SHA | `44639fc` | `44639fc` |
| Consumer concurrency | 1 | 6 |
| Max target EPS | 300 | 300 |
| Configured event limit | 51,000 | 51,000 |
| Emitted events | 51,000 | 51,000 |
| HTTP failure rate | 0 | 0 |
| Dropped iterations | null | null |
| k6 HTTP p95 | 6.35 ms | 7.58 ms |
| Final Consumer Lag | 0 | 0 |
| Peak Lag | 17,857 | 86 |
| Lag at recovery start | 15,720 | 19 |
| Lag growth rate | 154.06 records/s | 0.40 records/s |
| Lag drain rate from recovery start | 92.47 records/s | 0.14 records/s |
| Recovery time | 170s | 139s to first zero after recovery start |
| Final receipt / result / log count | 51,000 / 51,000 / 51,000 | 51,000 / 51,000 / 51,000 |
| API service p99 | 5.86 ms | 8.91 ms |
| API transaction p99 | 10.30 ms | 15.85 ms |
| Kafka publish wait p99 | 2.20 ms | 3.70 ms |
| Consumer service p99 | 13.29 ms | 25.87 ms |
| Hikari pending max | 0 | 0 |

The after-run showed small scrape-sampled lag spikes rather than a sustained backlog. Because lag at recovery start was only 19 records and peak lag was 86 records, the 139s first-zero timestamp is not evidence of a large backlog drain; it reflects intermittent scrape visibility while the system kept up with the workload.

Consumer assignment after the fix:

```text
consumer-fraud-event-consumer-1 -> transaction-events-0
consumer-fraud-event-consumer-2 -> transaction-events-1
consumer-fraud-event-consumer-3 -> transaction-events-2
consumer-fraud-event-consumer-4 -> transaction-events-3
consumer-fraud-event-consumer-5 -> transaction-events-4
consumer-fraud-event-consumer-6 -> transaction-events-5
```

Interpretation: with six listener consumers on six partitions, the same overload/recovery workload completed with final Lag 0 and all 51,000 events persisted through receipt, fraud result, and processing log boundaries. The bottleneck improvement is therefore Consumer partition-consumption parallelism, not API publish or database connection availability.

## 11. Local Sustainable Range

For the current local HTTP-driven setup and the committed staged workloads:

- 200 EPS completed cleanly before any tuning.
- 300 EPS caused sustained Lag growth with one listener consumer.
- 300 EPS completed without sustained backlog after explicitly setting Consumer concurrency to 6 for the six-partition topic.

The local sustainable range for this topology is therefore at least the tested 300 EPS under the Phase 1 HTTP workloads after the concurrency/topology fix. This does not prove the next knee. A higher-rate workload must be introduced before claiming capacity above 300 EPS.

## 12. Verification

Implementation verification:

```bash
./gradlew :app-api:test
./gradlew :app-consumer:test
k6 inspect -e V3_RUN_ID=phase1-inspect -e V3_WORKLOAD_MANIFEST=backlog-recovery-v1.json load-test/k6/scenarios/v3-phase1-throughput.js >/tmp/v3-phase1-recovery.inspect
python3 -m json.tool infra/grafana/dashboards/v3-stream-foundation.json >/tmp/v3-stream-foundation.json.check
git diff --check
```

Runtime verification:

```bash
bash scripts/reset-local-env.sh
V3_RUN_ID=phase1-recovery-before-concurrency1-20260821-003 make k6-v3-phase1-recovery
bash scripts/reset-local-env.sh
FRAUD_CONSUMER_CONCURRENCY=6 ./gradlew :app-consumer:bootRun
V3_RUN_ID=phase1-recovery-after-concurrency6-20260821-001 make k6-v3-phase1-recovery
docker exec fraud-postgres psql -U fraud -d fraud -c "select (select count(*) from transaction_event_receipts) receipts, (select count(*) from fraud_detection_results) results, (select count(*) from event_processing_logs) logs;"
docker exec fraud-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group fraud-event-consumer
```

Full repository verification is recorded in the PR checklist after documentation updates.

## 13. Known Limitations

- This evidence is local Docker evidence only.
- Results use the HTTP k6 driver and must not be merged with future direct Kafka producer results.
- The first capacity run before the stage-contract fix was discarded and is not used as capacity evidence.
- `CreateTime` producer-to-Consumer delay is not broker queue latency.
- Source processing and transport delay remain unavailable without source emulator ownership.
- Phase 1 does not solve controlled hot-key, state-size, late-event, or replay isolation behavior.
- Capacity above 300 EPS remains unmeasured.
