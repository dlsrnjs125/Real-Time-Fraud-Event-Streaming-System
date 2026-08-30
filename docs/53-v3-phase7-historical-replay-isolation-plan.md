# V3 Phase 7 Historical Replay Isolation Plan

## Goal

Verify that PaySim or synthetic historical replay can run without contaminating live stream processing.

Phase 7 isolates:

- Kafka topic
- Consumer group offset
- Redis sliding-window namespace
- replay rate
- live vs replay Lag interpretation

This phase does not claim production replay throughput until local evidence records workload, host fingerprint, Lag, p95/p99, and final consistency checks.

## Runtime Separation

Live path:

```text
app-api FRAUD_STREAM_MODE=LIVE
app-consumer fraud.consumer.topic=transaction-events
app-consumer spring.kafka.consumer.group-id=fraud-event-consumer
app-consumer fraud.sliding-window.namespace=live
Redis keys fraud:tx:live:*
```

Replay path:

```text
app-api FRAUD_STREAM_MODE=REPLAY
app-consumer FRAUD_CONSUMER_TOPIC=transaction-events-replay
app-consumer SPRING_KAFKA_CONSUMER_GROUP_ID=fraud-event-replay-consumer
app-consumer FRAUD_SLIDING_WINDOW_NAMESPACE=replay
Redis keys fraud:tx:replay:*
```

The replay topic still uses `userId` as the Kafka key. Historical replay should preserve same-user ordering semantics and partition behavior instead of switching to `eventId` distribution.

Startup guard:

- live app-api must publish to `transaction-events`.
- replay app-api must publish to `transaction-events-replay`.
- live app-consumer must consume `transaction-events` with group `fraud-event-consumer` and namespace `live`.
- replay app-consumer must consume `transaction-events-replay` with group `fraud-event-replay-consumer` and namespace `replay`.

Replay mode does not apply the live `allowed-lateness` Redis skip policy. A 24-hour-old replay event should build `fraud:tx:replay:*` state against the replay timeline. Live mode keeps the existing freshness policy and skips too-late live events.

## Workload

Committed manifest:

```text
load-test/workloads/v3/historical-replay-v1.json
```

Shape:

- role: `HISTORICAL_REPLAY`
- driver: `HTTP_K6`
- event time mode: `PRESERVE_SOURCE_TIME`
- source profile: `HISTORICAL`
- target/replay rate: 150 EPS
- duration: 30 seconds
- event limit: 4,500 events
- user cardinality: 500
- historical age: 24 hours

Run command:

```bash
make replay-api
make replay-consumer
REPLAY_API_BASE_URL=http://localhost:8082 V3_RUN_ID=phase7-replay-001 make k6-v3-phase7-historical-replay
```

The Makefile target runs `scripts/load_tests/prepare_v3_phase7_run.sh` first. The preflight checks live and replay Consumer Lag when those groups exist, then deletes only `fraud:tx:replay:*` keys. The k6 scenario requires `REPLAY_API_BASE_URL` and rejects the default live port `8080`.

The preflight also calls `${REPLAY_API_BASE_URL}/actuator/info` and requires:

```text
fraudStream.mode=REPLAY
fraudStream.producerTopic=transaction-events-replay
```

Port `8082` alone is not accepted as proof that the process is a replay API.

k6 success is strict for Phase 7 correctness evidence:

```text
http_req_failed == 0
checks rate == 1
dropped_iterations == 0
http_reqs == eventLimit
```

## Scope Boundary

Phase 7 historical replay means a workload with historical `eventTime` routed through isolated replay stream state.

It does not mean replaying an event already processed by live with the same `eventId` for backfill or re-evaluation. PostgreSQL receipt and fraud result uniqueness remain global by `eventId`. Supporting repeated processing scopes such as `LIVE` and `REPLAY:{runId}` would require a later data-model change to composite uniqueness.

PostgreSQL remains shared by live and replay in Phase 7. Therefore the correct claim is not full physical isolation. The claim to prove is: Kafka backlog, Consumer group offset, Redis state, and live/replay application metrics are separated; any indirect impact through shared PostgreSQL resource contention must be measured in the Live + Replay experiment.

## Experiments

### Live Only

Run a small live workload against the default app-api and live app-consumer.

Record:

- live topic Lag
- live p95/p99 API latency
- live Consumer service p95/p99
- live Redis key count under `fraud:tx:live:*`

### Replay Only

Run Phase 7 historical replay with only replay app-api and replay app-consumer.

Record:

- replay topic Lag
- replay Consumer service p95/p99
- replay Redis key count under `fraud:tx:replay:*`
- live topic/group Lag remains unchanged or zero
- live Redis key count remains unchanged

### Live + Replay

Run a live workload and the historical replay workload concurrently.

Record:

- live topic Lag and p99 before/during/after replay
- replay topic Lag and recovery
- live Redis namespace key count before/after replay
- replay Redis namespace key count after replay
- PostgreSQL receipt/result/processing-log counts for each run prefix

Grafana panel requirement:

- Phase 7 Live vs Replay Lag
- Phase 7 Live vs Replay Consumer p99
- Phase 7 Live vs Replay Redis p99
- Phase 7 Live vs Replay Event Ingress Age p99

## Completion Criteria

- `transaction-events-replay` is created by the topic script.
- app-api can publish to a configured topic while preserving `userId` key.
- app-consumer can consume a configured topic with a separate group id.
- Redis keys include a configurable namespace and default to `live`.
- Replay mode creates replay Redis state for historical event times instead of classifying every 24-hour-old event as too-late.
- Replay k6 workload cannot run against the default live API URL by omission.
- Replay k6 workload validates replay API routing through `/actuator/info` before sending traffic.
- k6 rejects any run with failed requests, failed checks, dropped iterations, or event count drift.
- DLT reprocess republishes to the original source topic after allowlist validation.
- Historical replay manifest is CI-validated.
- k6 summary records replay routing requirements and workload fingerprint.
- Runtime evidence shows live Lag, live latency, and live Redis state are not contaminated by replay traffic.

## Current Implementation Status

Implemented:

- configurable API producer topic
- configurable Consumer topic
- stream mode startup validation for live/replay topic, group, and Redis namespace combinations
- replay topic constant and topic creation script entry
- configurable Redis sliding-window namespace
- replay-mode historical event Redis state semantics
- DLT reprocess source-topic routing
- Phase 7 workload manifest
- Phase 7 k6 replay driver
- Phase 7 preflight script
- unit and integration test coverage for configured replay topic and Redis namespace separation

Local runtime evidence is still required before marking V3 Phase 7 `Done`.
