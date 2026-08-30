# k6 Load Tests

Phase 13 load tests are local Docker Compose evidence scenarios. They are intentionally not part of the default CI gate because load results depend on local CPU, memory, Docker resources, and whether the API and Consumer are running.

## Preconditions

```bash
make infra-up
make topics
make api
make consumer
```

Run `make api` and `make consumer` in separate terminals.

## Environment Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `API_BASE_URL` | `http://localhost:8080` | app-api base URL |
| `EVENT_PREFIX` | `phase13` | synthetic eventId prefix |
| `USER_PREFIX` | `user-phase13` | synthetic userId prefix |

Do not point `API_BASE_URL` at a production environment. Payloads use synthetic account, user, device, and merchant identifiers only.

## Commands

| Scenario | Command | Purpose |
| --- | --- | --- |
| Smoke | `make k6-smoke` | 3-request syntax/connectivity check |
| Normal Load | `make k6-normal` | Stable event intake load |
| Peak Load | `make k6-peak` | Ramping arrival-rate pressure |
| Duplicate Replay | `make k6-duplicate` | Duplicate `eventId` consistency check |
| Duplicate Replay Check | `make k6-duplicate-check` | Duplicate replay plus DB count check |
| Redis Down Load | `make k6-redis-down` | Degraded mode load with Redis stopped by script |
| V3 Phase 0 Baseline | `make k6-v3-baseline` | Versioned 5 EPS normal HTTP baseline with arrival-time event timestamps |
| V3 Phase 1 Capacity Discovery | `make k6-v3-phase1-capacity` | Staged local capacity discovery workload |
| V3 Phase 1 Knee Confirmation | `make k6-v3-phase1-knee` | Narrower staged workload around the candidate knee |
| V3 Phase 1 Backlog Recovery | `make k6-v3-phase1-recovery` | Overload and drain workload for recovery measurement |
| V3 Phase 2 State Baseline | `make k6-v3-phase2-state-baseline` | Low per-user Redis window density |
| V3 Phase 2 State Pressure | `make k6-v3-phase2-state-pressure` | Same EPS/event count with higher per-user Redis window density |
| V3 Phase 3 Partition Balanced | `make k6-v3-phase3-partition-balanced` | Partition-affinity workload targeting balanced local partition shares |
| V3 Phase 3 Partition Skew | `make k6-v3-phase3-partition-skew` | Partition-affinity workload targeting a hot P2 partition |
| V3 Phase 4 Stateful Redelivery | `make k6-v3-phase4-stateful-redelivery` | Deterministic single-user stream for redelivery failure-point drills |
| V3 Phase 5 Late / Out-of-Order | `make k6-v3-phase5-late-out-of-order` | Controlled lateness workload for accepted-late, too-late, and event-time ordering |
| V3 Phase 6 Organic Burst | `make k6-v3-phase6-organic-burst` | Source-emulator workload for new activity burst with event-time rebased to dispatch |
| V3 Phase 6 Catch-up Burst | `make k6-v3-phase6-catch-up-burst` | Source-emulator workload with same EPS/event count and controlled upstream source delay |
| V3 Phase 7 Historical Replay | `make k6-v3-phase7-historical-replay` | Historical replay workload routed to replay topic, replay consumer group, and replay Redis namespace |

Duplicate replay consistency can be checked after the scenario with:

```bash
make k6-duplicate-check
```

`make k6-redis-down` checks that Redis degraded and detection degraded metrics increase when `app-consumer` metrics are reachable. It also starts Redis again and waits for `redis-cli ping` during cleanup.

Raw k6 result files should be written under `load-test/k6/results/` and must not be committed. Keep only `results/.gitkeep`.

## V3 Workload Contract

V3 manifests are stored under `load-test/workloads/v3` and validated with `make verify-v3-workload-manifests`. The committed Phase 0 baseline uses `driverType=HTTP_K6` and `eventTimeMode=REBASE_TO_ARRIVAL`; its PaySim-like transaction attributes do not imply PaySim hourly timestamps or observed five-minute density.

Set a unique run identifier before running V3 evidence workloads:

```bash
V3_RUN_ID=<run-id> make k6-v3-baseline
```

`V3_RUN_ID` is required. The generated summary under `load-test/k6/results` records `runId`, `workloadId`, `workloadVersion`, commit SHA, target EPS, and achieved EPS. User and partition distributions remain `null` until measured from an appropriate driver/report or Kafka evidence; the k6 HTTP driver does not infer Kafka partition placement.

Phase 1 workloads additionally require `V3_WORKLOAD_MANIFEST`, which the Makefile targets set automatically. The runner converts each manifest stage into a separate `constant-arrival-rate` plateau scenario and enforces a stage-level HTTP emission limit so `eventLimit` remains the sum of each stage's `targetEps * duration`. Raw summaries remain local ignored evidence.

Phase 2 state-size workloads keep EPS, duration, event amount, and total event count fixed while changing `userCardinality`. Compare `fraud.redis.window.event.count`, `fraud.redis.window.amount.sum`, `fraud.redis.state.latency`, Consumer service latency, Redis memory, and Consumer Lag over the same Grafana time range.

Phase 6 source-emulator workloads use the same `targetEps`, `duration`, `eventLimit`, `userCardinality`, and `eventAmount` for organic and catch-up bursts. The organic workload sets `eventTime` at source dispatch time. The catch-up workload sets `eventTime` 270 seconds before source dispatch, staying inside the 5-minute freshness policy so evidence can isolate pre-ingress event age from too-late rejection. `randomSeed` remains manifest metadata, but the Phase 6 runner uses deterministic modulo user assignment rather than seeded random selection.

The Phase 6 Makefile targets run `scripts/load_tests/prepare_v3_phase6_run.sh` before k6. That local-only preflight requires Consumer Lag 0 when the consumer group exists and flushes the local Redis DB so organic and catch-up accepted runs do not share sliding-window state. Do not point this helper at shared or production Redis.

Phase 7 historical replay requires a separate replay route. Start replay app-api with `make replay-api` and replay app-consumer with `make replay-consumer`. Keep the live app-api/app-consumer on `make api` and `make consumer`. The replay k6 scenario requires `REPLAY_API_BASE_URL`, for example `REPLAY_API_BASE_URL=http://localhost:8082 V3_RUN_ID=phase7-replay-001 make k6-v3-phase7-historical-replay`, and rejects the default live API port `8080`. The Phase 7 preflight checks live/replay group Lag when groups exist and deletes only Redis keys matching `fraud:tx:replay:*`; it must not flush the live namespace.

Phase 3 partition workloads use `PARTITION_AFFINITY` manifests. The k6 runner pre-generates synthetic `userId` values whose Kafka Murmur2 key hash maps to the configured local partitions, then emits events according to `targetPartitionDistribution`. User assignment uses a per-partition occurrence counter so partition skew is not accidentally implemented as hot-user pressure. Confirm achieved distribution with Kafka exporter metrics or processing logs after the run.

Phase 4 stateful redelivery workload emits a deterministic single-user event stream at 1 EPS. The manifest keeps `eventLimit = targetEps * duration seconds`, while `statefulWindowProfile.runtimeWindow` describes the Consumer's Redis sliding-window horizon. The k6 summary records `drillTargetEventId`; start `app-consumer` with `fraud.consumer.redelivery-drill.enabled=true`, that `event-id`, and one of `BEFORE_REDIS_UPDATE`, `AFTER_REDIS_UPDATE_BEFORE_RESULT`, or `AFTER_RESULT_SAVE_BEFORE_ACK` to reproduce stateful redelivery failure points. `fail-once` tracking is process-local and resets when `app-consumer` restarts.
