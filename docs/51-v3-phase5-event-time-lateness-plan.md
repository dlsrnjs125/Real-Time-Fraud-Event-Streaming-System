# V3 Phase 5 Event-Time Lateness Semantics Plan

Status: Implementation ready; runtime evidence pending.

## 1. Objective

Phase 5 defines how the Redis sliding-window path handles events whose `eventTime` is older than arrival time, including allowed-late, out-of-order, boundary-late, and too-late events.

The goal is to separate freshness semantics from throughput or partition-skew behavior:

- accepted late events should update live Redis state by `eventTime`
- out-of-order accepted events should be stored with their original event-time score
- too-late events should not mutate live Redis state
- too-late events should be observable without being misclassified as Redis infrastructure failure

Runtime evidence is intentionally deferred to the evidence collection step.

## 2. Policy

Default runtime policy:

| Case | Condition | Redis state behavior | Rule behavior |
|---|---|---|---|
| On time | `receivedAt - eventTime <= allowedLateness` | write Hash and ZSET member | Redis rules evaluate normally |
| Allowed late | `receivedAt - eventTime <= allowedLateness` | write Hash and ZSET member using `eventTime` score | Redis rules evaluate normally |
| Boundary late | exactly `allowedLateness` | accepted | Redis rules evaluate normally |
| Too late | `receivedAt - eventTime > allowedLateness` | skip Hash/ZSET mutation | Redis-dependent rules are skipped via degraded window result |
| Future event | `receivedAt - eventTime < 0` | not classified as too late here | handled by existing validation/future-time policy |

`fraud.sliding-window.allowed-lateness` defaults to `5m`, aligned with the current runtime window.

## 3. Event-Time Window Semantics

Accepted events are stored in Redis as:

```text
ZSET fraud:tx:user:{userId}:events
score = eventTime epoch millis
member = eventId
```

The returned window is also evaluated by event time:

```text
[eventTime - window, eventTime]
```

This means an event that arrives after a newer event can still be inserted behind that newer event in Redis ordering. Its own decision uses the event-time window ending at that event's `eventTime`, not arrival order.

## 4. Workload Contract

Committed workload:

```text
load-test/workloads/v3/late-out-of-order-v1.json
```

Runtime driver:

```text
V3_RUN_ID=phase5-late-out-of-order-001 make k6-v3-phase5-late-out-of-order
```

Manifest contract:

- `workloadRole=LATE_OUT_OF_ORDER`
- `eventTimeMode=CONTROLLED_LATENESS`
- `sourceProfile=SLOW_SOURCE`
- `allowedLateness=5m`
- buckets: on-time, 30 seconds late, 2 minutes late, 5-minute boundary, 10 minutes too-late, and a 1-minute return bucket
- `expectedTooLateEvents=50`
- `expectedAcceptedLateEvents=250`

The validator rejects lateness profiles on non-late workloads and checks expected too-late counts from the bucket pattern.

The k6 runner applies `outOfOrderPattern` to each user's deterministic event plan. Bucket selection uses the user's event index plus user index, so the whole run keeps the expected bucket counts while each user receives a non-monotonic event-time sequence.

## 5. Metrics

Phase 5 adds:

```text
fraud.redis.window.too_late.total
```

This counter tracks events skipped because they exceeded the allowed-lateness policy. It is separate from:

```text
fraud.redis.window.degraded.total
```

Redis infrastructure failure and event freshness rejection are different causes and should not be interpreted as the same operational signal.

## 6. Completion Criteria

Implementation completion:

- Redis store skips too-late live-state mutation
- accepted out-of-order events are stored by event-time score
- unit and integration tests cover too-late and out-of-order behavior
- Phase 5 manifest validates through the V3 workload validator
- k6 script can inspect the Phase 5 workload contract

Runtime completion, to be collected next:

- accepted late bucket count matches manifest expectation
- too-late skip count matches manifest expectation
- per-user accepted out-of-order sequence is present in the runtime summary
- Redis state excludes too-late event IDs
- final DB result/log counts match accepted API emissions
- final Consumer Lag returns to 0 after the run drains

## 7. Known Limitations

- Phase 5 does not add `sourceSentAt` to the event schema. Source transport delay remains manifest/report metadata until a source emulator owns that timestamp.
- The returned Redis window is event-time scoped for the current event. It is not a global watermark implementation.
- Too-late events keep the fraud result `degraded=true` because Redis-dependent rules are intentionally skipped, but the window status and reason identify this as `TOO_LATE` / freshness policy behavior rather than Redis infrastructure failure.
