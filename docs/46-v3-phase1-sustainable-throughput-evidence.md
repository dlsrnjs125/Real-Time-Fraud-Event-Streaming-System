# V3 Phase 1 Sustainable Throughput and Backlog Recovery Evidence

## 1. Status

- Status: Complete for local Docker Phase 1 evidence
- Scope: HTTP k6 intake, Kafka publish, app-consumer processing, Consumer Lag, backlog recovery, first bottleneck attribution
- Selected improvement: configure app-consumer listener concurrency to match the six `transaction-events` partitions in the local topology
- Completion boundary: this is local evidence, not production capacity or a direct-Kafka producer benchmark

## 2. Implementation Summary

- Added API intake stage Timers:
  - `fraud.api.intake.service.latency`
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
- Added configurable Consumer concurrency with `fraud.consumer.concurrency=6` for the local six-partition topic.
- API intake stage timers record durations measured with `System.nanoTime()` and discard negative durations before recording.

## 3. Experiment Fingerprint

| Field | Value |
|---|---|
| Branch | `feat/v3-phase1-sustainable-throughput-recovery` |
| Commit SHA in summaries | `748105f` / `748105f-dirty` before final commit |
| Host / OS | local macOS developer machine |
| Runtime | Docker Compose local Kafka, PostgreSQL, Redis, Prometheus, Grafana; Spring Boot apps on host |
| Kafka partitions | `transaction-events` has 6 partitions |
| Consumer before fix | one listener consumer assigned all six partitions |
| Consumer after fix | six listener consumers, one partition each |
| Prometheus scrape interval | 15s |
| Driver | `HTTP_K6` |
| Event-time mode | `REBASE_TO_ARRIVAL` |
| Result files | local ignored `load-test/k6/results/*phase1*summary.json` |

The first capacity summary contains `748105f` without a dirty suffix because the Makefile did not yet include dirty-state detection for uncommitted Phase 1 edits. The Makefile was fixed before the later knee and recovery summaries.

## 4. Capacity Discovery

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

## 5. Knee Confirmation Before Fix

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

## 6. Backlog Recovery Before Fix

Run ID: `phase1-recovery-20260821-001`

| Boundary | Value |
|---|---:|
| Configured event limit | 51,000 |
| Emitted events | 51,000 |
| Average achieved EPS across staged run | 140.11 |
| Max target EPS | 300 |
| HTTP failure rate | 0 |
| Dropped iterations | 0 / null |
| k6 HTTP p95 | 4.39 ms |
| Final cumulative receipt / result / log count | 133,500 / 133,500 / 133,500 |
| Final Consumer Lag | 0 |
| 20m observed peak Lag | 17,115 |
| API intake p99 | 5.98 ms |
| Kafka publish wait p99 | 2.36 ms |
| Consumer service p99 | 13.43 ms |

Interpretation: backlog eventually drained after overload ended, but the previous knee run already showed that one listener thread could not keep up with the highest stage while input was active.

## 7. Bottleneck Attribution

| Question | Evidence | Answer |
|---|---|---|
| app-api, Kafka, app-consumer, or load generator? | k6 had 0 HTTP failures and no dropped iterations; API p99 stayed single-digit ms; Kafka publish wait p99 stayed about 2-3 ms; Lag grew under 300 EPS | app-consumer throughput |
| If Consumer, which boundary? | Lag was spread across all six partitions while only one listener consumer processed them before the fix | listener concurrency, not partition skew |
| Is PostgreSQL connection starvation supported? | Hikari pending max was 0 for app-api and app-consumer | no |
| Is Kafka publish wait the primary bottleneck? | publish wait p99 was low except one warm-up slow sample | no sustained evidence |
| Is load generator saturation supported? | k6 maintained target stages and reported no dropped iterations | no |

## 8. Root Cause

```text
Symptom: Consumer Lag grew during the 300 EPS knee stage while API intake stayed healthy.
Metric Evidence: 60,000 emitted, HTTP failure rate 0, immediate Lag 14,027, API p99 low, Kafka publish wait p99 low, Hikari pending 0.
Initial Hypothesis: app-consumer could not consume and persist at the input rate.
Isolation Experiment: inspect consumer group assignment and partition lag.
Root Cause: Spring Kafka listener concurrency defaulted to one, so one consumer thread owned all six transaction-events partitions.
Fix Candidate: expose fraud.consumer.concurrency and set local default to 6.
Trade-off: higher local DB/Redis concurrency and more consumer clients; still bounded by partition count.
Selected Fix: set factory concurrency from fraud.consumer.concurrency, minimum 1, with local value 6.
Same-Load Re-test: knee workload re-run with 60,000 events and max 300 EPS.
```

## 9. Same-Load Re-test After Fix

Run ID: `phase1-knee-after-concurrency6-20260821-001`

| Metric | Before | After |
|---|---:|---:|
| Workload | `knee-confirmation-v1` | `knee-confirmation-v1` |
| Max target EPS | 300 | 300 |
| Emitted events | 60,000 | 60,000 |
| HTTP failure rate | 0 | 0 |
| Dropped iterations | 0 / null | 0 / null |
| k6 HTTP p95 | 3.81 ms | 5.30 ms |
| Immediate Consumer Lag | 14,027 | 0 |
| Prometheus max Lag over 10m | not captured for exact window | 6 |
| Final receipt / result / log count | eventually aligned | 60,000 / 60,000 / 60,000 |
| API intake p99 | about 6 ms in related recovery window | 5.92 ms |
| Kafka publish wait p99 | 2.36 ms in related recovery window | 2.40 ms |
| Consumer service p99 | 13.43 ms in related recovery window | 20.41 ms |
| Hikari pending max | 0 | 0 |

Consumer assignment after the fix:

```text
consumer-fraud-event-consumer-1 -> transaction-events-0
consumer-fraud-event-consumer-2 -> transaction-events-1
consumer-fraud-event-consumer-3 -> transaction-events-2
consumer-fraud-event-consumer-4 -> transaction-events-3
consumer-fraud-event-consumer-5 -> transaction-events-4
consumer-fraud-event-consumer-6 -> transaction-events-5
```

Interpretation: with six listener consumers on six partitions, the same 300 EPS knee workload completed with final Lag 0 and all 60,000 events persisted through receipt, fraud result, and processing log boundaries.

## 10. Local Sustainable Range

For the current local HTTP-driven setup and the committed staged workloads:

- 200 EPS completed cleanly before any tuning.
- 300 EPS caused Lag growth with one listener consumer.
- 300 EPS completed cleanly after setting consumer concurrency to 6.

The local sustainable range for this topology is therefore at least the tested 300 EPS under `knee-confirmation-v1`. This does not prove the next knee. A higher-rate workload must be introduced before claiming capacity above 300 EPS.

## 11. Verification

Implementation verification:

```bash
./gradlew :app-api:test
./gradlew :app-consumer:test
python3 -m unittest scripts/data/test_validate_v3_workload_manifest.py
make test-data-scripts-ci
make verify-v3-workload-manifests
make observability-check
python3 -m json.tool infra/grafana/dashboards/v3-stream-foundation.json >/dev/null
git diff --check
```

Runtime verification:

```bash
make final-check
V3_RUN_ID=phase1-discovery-20260821-002 make k6-v3-phase1-capacity
V3_RUN_ID=phase1-knee-20260821-001 make k6-v3-phase1-knee
V3_RUN_ID=phase1-recovery-20260821-001 make k6-v3-phase1-recovery
V3_RUN_ID=phase1-knee-after-concurrency6-20260821-001 make k6-v3-phase1-knee
```

## 12. Known Limitations

- This evidence is local Docker evidence only.
- Results use the HTTP k6 driver and must not be merged with future direct Kafka producer results.
- The first capacity run before the plateau-stage fix was discarded and is not used as capacity evidence.
- `CreateTime` producer-to-Consumer delay is not broker queue latency.
- Source processing and transport delay remain unavailable without source emulator ownership.
- Phase 1 does not solve controlled hot-key, state-size, late-event, or replay isolation behavior.
- Capacity above 300 EPS remains unmeasured.
