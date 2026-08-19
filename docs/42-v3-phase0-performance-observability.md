# V3 Phase 0 Performance Observability Foundation

## 1. Status

`In Progress`

Phase 0 application metrics, HikariCP experiment variables, and local Grafana panels are implemented. Unit tests and static dashboard validation are complete. A local runtime baseline and bottleneck reproduction evidence are still required before this phase can be marked `Done`.

## 2. Problem

An overall Consumer latency value cannot identify the component that caused a delay. The actual current success path is more detailed than the simplified Kafka/Redis/Rule/DB flow:

```text
Kafka record delivery
  -> processing log PostgreSQL operation
  -> fraud result duplicate lookup
  -> Redis sliding window
  -> Rule Engine
  -> fraud result PostgreSQL operation
  -> manual ack
```

If Consumer processing p95 is 300 ms, that value alone cannot distinguish Redis delay, rule execution, PostgreSQL execution, or HikariCP connection waiting. Kafka lag is therefore treated as an accumulated outcome, while stage latency and dependency metrics are used to isolate the cause.

## 3. Metric Contract

| Metric | Boundary | Recorded population |
|---|---|---|
| `fraud.kafka.queue.latency` | Kafka record timestamp to listener entry | records with a valid, non-future Kafka timestamp |
| `fraud.consumer.processing.latency` | listener entry to successful new fraud result persistence | newly persisted, non-duplicate results |
| `fraud.redis.window.latency` | complete `recordAndGetWindow` call, including degraded failure handling | records that enter the Redis window path |
| `fraud.rule.processing.latency` | complete Rule Engine evaluation | Rule Engine attempts, including failed attempts |
| `fraud.db.persistence.latency` | one synchronous PostgreSQL call in the Consumer path | `operation=processing_log`, `fraud_result_lookup`, or `fraud_result` |
| `fraud.event.e2e.latency` | business `eventTime` to successful new fraud result persistence | newly persisted, non-duplicate results |

All six metrics are Micrometer Timers with histogram and p50/p95/p99 publication enabled. Negative clock-based durations are discarded.

### Kafka queue timestamp decision

Phase 0 uses `ConsumerRecord.timestamp()` as the queue latency source. It does not add `producerPublishedAt` to `TransactionEventMessage`, so observability does not create a Kafka event contract change.

The metric describes time between Kafka record creation and Consumer listener entry. It includes broker residence, accumulated Consumer lag, fetch/poll delay, and clock skew between producer and Consumer hosts. It is not broker append acknowledgement latency.

### DB operation tag decision

`fraud.db.persistence.latency` uses only a bounded `operation` tag:

```text
processing_log
fraud_result_lookup
fraud_result
```

Although `fraud_result_lookup` is a read, it is included because it is a synchronous PostgreSQL call on the persistence path and can wait for the same HikariCP pool. `eventId`, `traceId`, `userId`, topic, partition, and offset are not metric labels.

### Event E2E interpretation

`fraud.event.e2e.latency` uses `processingEnd - eventTime`. It is valid as a live business-event age signal, but it must not be used as the processing SLA for PaySim or historical replay. Replay events can intentionally contain old `eventTime` values. For replay experiments, use queue latency and Consumer processing latency, and label business E2E evidence as not applicable.

Manual acknowledgement is issued after the measured processing boundary. The Timer does not claim to measure broker-side offset commit completion.

## 4. HikariCP Experiment Variables

The Consumer exposes its pool configuration through environment variables:

| Spring property | Environment variable | Local default |
|---|---|---:|
| `spring.datasource.hikari.maximum-pool-size` | `CONSUMER_DB_POOL_MAX_SIZE` | 10 |
| `spring.datasource.hikari.minimum-idle` | `CONSUMER_DB_POOL_MIN_IDLE` | 2 |
| `spring.datasource.hikari.connection-timeout` | `CONSUMER_DB_CONNECTION_TIMEOUT_MS` | 30000 ms |

Keep `minimum-idle <= maximum-pool-size`. Every experiment must record these values in its fingerprint.

Actuator/Prometheus exposes the pool signals used with DB latency:

```text
hikaricp_connections_active
hikaricp_connections_idle
hikaricp_connections_pending
hikaricp_connections_timeout_total
```

High DB operation latency with increasing `pending` means connection acquisition is a likely contributor. It does not by itself prove that PostgreSQL query execution is slow.

## 5. Kafka Metric Ownership

| Signal | Source | Purpose |
|---|---|---|
| total/partition lag | `kafka-exporter` | backlog and partition skew |
| records consumed/sec | Kafka Consumer client metric | current Consumer throughput |
| assigned partitions | Kafka Consumer client metric | assignment state |
| rebalance total | Kafka Consumer client metric | assignment churn |
| queue latency | application Timer | delay observed by each consumed record |

Primary Prometheus series:

```text
kafka_consumergroup_lag
kafka_consumer_fetch_manager_records_consumed_rate
kafka_consumer_coordinator_assigned_partitions
kafka_consumer_coordinator_rebalance_total
```

Kafka client series must be confirmed from the running `/actuator/prometheus` endpoint because binder and client versions can affect exported names. A missing panel is not evidence that the runtime value is zero.

## 6. Grafana and PromQL

The local dashboard contains:

- Redis window p95 latency
- Consumer, Rule Engine, and DB operation p95 latency
- Kafka queue and business event E2E p95 latency
- total Consumer group lag
- HikariCP active, idle, pending, and timeout signals
- Kafka consumed records/sec, assigned partitions, and rebalance count

Representative p95 query:

```promql
histogram_quantile(
  0.95,
  sum by (le, operation) (
    rate(fraud_db_persistence_latency_seconds_bucket[1m])
  )
)
```

Do not calculate a percentile from `_seconds_max`. Use histogram buckets for p50/p95/p99 and keep the aggregation labels needed for comparison.

## 7. Local Verification

Static/CI-safe checks:

```bash
./gradlew :app-consumer:test
make observability-check
```

Local runtime check:

```bash
docker compose -f infra/docker-compose.yml up -d
make api
make consumer
curl http://localhost:8081/actuator/prometheus | grep -E 'fraud_(kafka_queue|consumer_processing|redis_window|rule_processing|db_persistence|event_e2e)_latency'
curl http://localhost:8081/actuator/prometheus | grep -E 'hikaricp_connections_(active|idle|pending|timeout)'
curl http://localhost:8081/actuator/prometheus | grep -E 'kafka_consumer_(fetch_manager_records_consumed_rate|coordinator_assigned_partitions|coordinator_rebalance_total)'
```

Generate transaction traffic before checking Timer series. A Timer or Kafka client series can be absent until the corresponding path has executed.

## 8. Troubleshooting Drill

The following is a diagnosis template, not a measured result.

### Symptom

Kafka Consumer Lag rises rapidly during a fixed burst workload.

### Initial hypothesis

Kafka or Consumer throughput is insufficient.

### Observation example

```text
Rule p95                 7 ms
Redis p95               11 ms
DB fraud_result p95    840 ms
Hikari pending          increasing
Kafka queue p95         increasing after DB p95
```

### Interpretation

Kafka lag is the result signal. The first correlated saturation appears in PostgreSQL/HikariCP, so the working hypothesis becomes connection pool waiting or slow DB execution rather than Kafka broker throughput.

### Isolation order

1. Confirm whether input TPS exceeds consumed records/sec.
2. Compare queue p95 with Consumer processing p95.
3. Compare Redis, Rule, and DB operation p95 over the same interval.
4. Check Hikari active, idle, pending, and timeout signals.
5. Separate connection waiting from query execution with PostgreSQL evidence before changing pool size.
6. Re-run the identical workload after one controlled change.

### Avoided false conclusions

- Lag increase alone does not prove a Kafka broker bottleneck.
- A larger Hikari pool can move saturation to PostgreSQL and is not automatically an improvement.
- Low Rule/Redis latency does not prove the full Consumer is healthy when DB calls dominate.
- Historical replay E2E latency does not represent current processing speed.

## 9. Evidence Template

| Evidence | Baseline | Bottleneck run | Same-load re-test |
|---|---:|---:|---:|
| input TPS | TBD | TBD | TBD |
| consumed records/sec | TBD | TBD | TBD |
| peak total group lag | TBD | TBD | TBD |
| queue latency p95/p99 | TBD | TBD | TBD |
| Consumer processing p95/p99 | TBD | TBD | TBD |
| Redis p95/p99 | TBD | TBD | TBD |
| Rule p95/p99 | TBD | TBD | TBD |
| DB p95/p99 by operation | TBD | TBD | TBD |
| Hikari peak active/pending | TBD | TBD | TBD |
| Hikari timeout increase | TBD | TBD | TBD |

Record commit SHA, workload version, hardware/container limits, Kafka partition count, Consumer concurrency, pool settings, component versions, and dataset/seed with this table.

## 10. Completion Criteria

Phase 0 can be marked `Done` only after:

- all six application Timers are visible after local traffic
- HikariCP and Kafka runtime series are confirmed from the actual endpoint
- Grafana panels render non-empty values for an executed path
- one controlled bottleneck run separates the symptom from the likely cause
- the same workload is rerun after one controlled improvement or the trade-off is intentionally accepted
- measured values and evidence locations replace `TBD`
