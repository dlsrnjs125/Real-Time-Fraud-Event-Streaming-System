# V3 Phase 3 Kafka Partition Skew and Consumer Parallelism Plan

## 1. Status

- Status: Done
- Scope: controlled partition-affinity workload, Consumer concurrency comparison, partition-level evidence
- Completion boundary: this phase is complete after local runtime evidence records the required runtime proof set: balanced single-consumer baseline, balanced one-consumer-per-partition run, hot-partition run at one-consumer-per-partition, and an assignment check above partition count.

## 2. Goal

Phase 3 measures whether Consumer scale-out improves throughput when Kafka traffic is evenly distributed, and where that improvement stops when one partition receives a disproportionate share of traffic.

This phase does not change the Kafka partition key. The normal transaction topic still uses `userId` as the Kafka key to preserve same-user ordering.

## 3. Experiment Contract

The experiment uses pre-generated synthetic `userId` values whose Kafka Murmur2 key hash maps to known local partitions. The k6 runner then assigns users by per-partition event occurrence, not by global cycle number. This keeps partition skew separate from hot-user and Redis state-density pressure.

| Workload | Target distribution | EPS | Duration | Events | Purpose |
|---|---:|---:|---:|---:|---|
| `partition-balanced-v1.json` | P0~P5 each about 16.67% | 300 | 120s | 36,000 | balanced partition-affinity baseline |
| `partition-skew-hot-p2-v1.json` | P2 60%, others 8% each | 300 | 120s | 36,000 | hot-partition pressure |

Both workloads keep `driverType=HTTP_K6`, `eventTimeMode=REBASE_TO_ARRIVAL`, `sourceProfile=NORMAL`, `userCardinality=600`, and `randomSeed=630519`.

## 4. Execution Matrix

Run the same workload against the same local topic partition count while changing only `FRAUD_CONSUMER_CONCURRENCY`.

Required runtime proof:

| Consumer concurrency | Reason |
|---:|---|
| 1 | one thread owns all six partitions |
| 6 | one thread per local partition |
| 8 | assignment-only check proving idle Consumers above partition count |

The accepted evidence uses:

- balanced c1 as the single-consumer baseline;
- balanced c6 as the one-consumer-per-partition comparison;
- hot P2 c6 as the hot-partition ceiling test;
- concurrency 8 assignment evidence to show the partition-count ceiling and idle consumer behavior.

Exploratory intermediate settings:

| Consumer concurrency | Requirement level | Reason |
|---:|---|---|
| 2 | Optional | intermediate partial parallelism signal |
| 3 | Optional | intermediate partial parallelism signal |

The c2/c3 runs are useful for drawing a smoother scaling curve, but they are not required for Phase 3 completion because the required evidence already answers the scale-out boundary questions.

## 5. Required Signals

Record these signals for each accepted runtime run when applicable:

- target partition distribution from manifest
- generated expected partition distribution from k6 summary
- generated unique users, per-partition unique users, events/user p50/p95/p99/max, and top-user share from k6 summary
- achieved partition distribution from Kafka/exporter or processing logs
- total Consumer Lag and per-partition Lag
- partition incoming rate
- Consumer consumed records rate
- Consumer assigned partition count
- idle Consumer evidence when concurrency exceeds partition count
- Consumer service p95/p99
- Redis state p95/p99
- final receipt/result/processing-log counts

## 6. Commands

```bash
V3_RUN_ID=phase3-balanced-c6-001 make k6-v3-phase3-partition-balanced
V3_RUN_ID=phase3-hot-p2-c6-001 make k6-v3-phase3-partition-skew
```

Set Consumer concurrency when starting `app-consumer`:

```bash
FRAUD_CONSUMER_CONCURRENCY=6 ./gradlew :app-consumer:bootRun
```

Use c1 and c6 for accepted balanced runtime proof, hot P2 c6 for partition-ceiling proof, and c8 for assignment-only idle consumer proof. c2/c3 are optional exploratory settings and are not part of the required completion matrix.

## 7. Completion Criteria

Phase 3 can be marked complete only when the evidence answers:

- Does balanced partition traffic scale as concurrency approaches the six local partitions?
- Does a hot partition create partition-specific Lag even when other partitions remain healthy?
- Does adding Consumers beyond partition count create idle Consumers rather than additional throughput?
- Are API/Kafka publish/Redis/sink signals separated from partition-level backlog?
- Do final durable row counts still align after each accepted run?

## 8. Known Constraints

- The HTTP k6 driver measures API-intake path plus Kafka publish, not direct Kafka producer capacity.
- Local Kafka uses six partitions, so results are local topology evidence.
- Partition-affinity user generation depends on Kafka's Murmur2 key hashing and the documented six-partition local topic.
- The generated user-distribution summary is a contract check that partition skew is not accidentally implemented as hot-user pressure.
- `make verify-v3-phase3-partition-assignment` verifies the committed Phase 3 manifests generate all 600 users evenly and keep top-user share at the expected `1 / userCardinality` level.
- Results must not be generalized as production capacity without rerunning on the target topology and resource limits.
