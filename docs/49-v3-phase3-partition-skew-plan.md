# V3 Phase 3 Kafka Partition Skew and Consumer Parallelism Plan

## 1. Status

- Status: In progress
- Scope: controlled partition-affinity workload, Consumer concurrency comparison, partition-level evidence
- Completion boundary: this phase is complete only after local runtime evidence records balanced and hot-partition runs across selected Consumer concurrency settings.

## 2. Goal

Phase 3 measures whether Consumer scale-out improves throughput when Kafka traffic is evenly distributed, and where that improvement stops when one partition receives a disproportionate share of traffic.

This phase does not change the Kafka partition key. The normal transaction topic still uses `userId` as the Kafka key to preserve same-user ordering.

## 3. Experiment Contract

The experiment uses pre-generated synthetic `userId` values whose Kafka Murmur2 key hash maps to known local partitions. This avoids treating user concentration as partition-skew evidence.

| Workload | Target distribution | EPS | Duration | Events | Purpose |
|---|---:|---:|---:|---:|---|
| `partition-balanced-v1.json` | P0~P5 each about 16.67% | 300 | 120s | 36,000 | balanced partition-affinity baseline |
| `partition-skew-hot-p2-v1.json` | P2 60%, others 8% each | 300 | 120s | 36,000 | hot-partition pressure |

Both workloads keep `driverType=HTTP_K6`, `eventTimeMode=REBASE_TO_ARRIVAL`, `sourceProfile=NORMAL`, `userCardinality=600`, and `randomSeed=630519`.

## 4. Execution Matrix

Run the same workload against the same local topic partition count while changing only `FRAUD_CONSUMER_CONCURRENCY`.

| Consumer concurrency | Reason |
|---:|---|
| 1 | one thread owns all six partitions |
| 2 | partial parallelism |
| 3 | partial parallelism |
| 6 | one thread per local partition |
| 8 | confirms no useful scale-out beyond six partitions and records idle Consumers |

The `8`-consumer run is not expected to process more partitions than the topic has. It is evidence for the partition-count ceiling and idle consumer behavior.

## 5. Required Signals

Record these signals for each accepted run:

- target partition distribution from manifest
- generated expected partition distribution from k6 summary
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

Repeat with `1`, `2`, `3`, `6`, and `8` for the accepted evidence matrix.

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
- Results must not be generalized as production capacity without rerunning on the target topology and resource limits.
