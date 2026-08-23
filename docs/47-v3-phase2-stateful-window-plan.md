# V3 Phase 2 Stateful Sliding-Window Scaling Plan

## 1. Goal

V3 Phase 2 measures how Redis sliding-window state size changes Consumer cost under a controlled same-load experiment.

Primary question:

```text
When EPS and total event count stay fixed, how much does per-user runtime-window density affect Redis latency, Consumer latency, Redis memory, and Consumer Lag?
```

## 2. Scope

In scope:

- Synthetic HTTP k6 workloads that control events/user inside the runtime Redis window
- Redis window event-count and amount-sum metrics
- Grafana panels for state-size evidence
- Same-load comparison between low-density and high-density Redis windows
- Documentation of the measurement boundary and limitations

Out of scope:

- Redis data-structure optimization
- Lua, pipelining, batching, aggregate-state replacement, or multi-get optimization
- Kafka partition skew experiments
- late/out-of-order semantics
- replay isolation

## 3. Controlled Variable

The first Phase 2 experiment keeps these values fixed:

| Field | Value |
|---|---:|
| Driver | `HTTP_K6` |
| EPS | 100 |
| Duration | 120s |
| Total events | 12,000 |
| Event amount | 250,000 KRW |
| Event-time mode | `REBASE_TO_ARRIVAL` |
| Redis runtime window | 5m |

Only `userCardinality` changes:

| Workload | Users | Expected events/user/window | Expected amount/user/window |
|---|---:|---:|---:|
| `state-size-baseline-v1` | 1,000 | 12 | 3,000,000 KRW |
| `state-size-high-density-v1` | 100 | 120 | 30,000,000 KRW |

Because each run lasts 120 seconds and the Redis window is 5 minutes, emitted events are intended to remain inside the runtime window for the comparison.

## 4. Evidence Signals

Use the same Grafana time range for both runs.

Required signals:

- `fraud.redis.window.event.count`
- `fraud.redis.window.amount.sum`
- `fraud.redis.state.latency`
- `fraud.consumer.service.latency`
- `fraud.rule.processing.latency`
- `fraud.result.sink.latency`
- Redis memory start/final delta from a clean Redis instance
- Redis commandstats delta per event for state-sensitive commands
- total and partition Consumer Lag
- partition-level consumed record distribution
- final PostgreSQL receipt/result/processing-log equality

Interpretation rule:

```text
Do not attribute latency to Redis state size unless the state-size metrics rose and the same time range shows Redis or Consumer-stage latency movement.
```

Redis memory must not be compared across separate logical databases on the same long-lived Redis instance. Each memory comparison run starts from a clean Redis instance or an equivalent `FLUSHALL`, records start/final memory, and uses the delta as the comparable signal.

Because Kafka uses `userId` as the partition key, reducing `userCardinality` can also change partition distribution. Phase 2 records partition-level input distribution and partition lag so Redis state-size effects are not confused with a hot-partition experiment. Deliberate partition-skew mitigation remains out of scope for this phase.

## 5. Commands

```bash
V3_RUN_ID=phase2-state-baseline-001 make k6-v3-phase2-state-baseline
V3_RUN_ID=phase2-state-pressure-001 make k6-v3-phase2-state-pressure
```

The Makefile selects the corresponding workload manifest and k6 scenario.

## 6. Completion Criteria

- Workload manifests pass `make verify-v3-workload-manifests`.
- k6 runner emits the configured event count with HTTP failure rate 0.
- Final receipt/result/processing-log counts align for each run.
- Final Consumer Lag is 0 for each run.
- Evidence records Redis window size, Redis latency, Consumer latency, Redis memory, and Lag on the same time range.
- Evidence records clean Redis memory start/final delta for each workload.
- Evidence records Redis commandstats per-event comparison for state-sensitive commands.
- Evidence records partition-level consumed distribution and peak partition Lag so state-density results are not over-attributed when user cardinality changes Kafka key distribution.
- Any optimization is deferred until the state-size bottleneck is actually measured.
