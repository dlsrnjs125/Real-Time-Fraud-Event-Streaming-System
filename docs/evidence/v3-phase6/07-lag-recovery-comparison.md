# Lag Recovery Comparison

Kafka lag was queried from Prometheus using:

```promql
sum(kafka_consumergroup_lag{job="kafka-exporter", consumergroup="fraud-event-consumer", topic="transaction-events"})
```

| Metric | Organic Burst | Catch-up Burst |
|---|---:|---:|
| Peak total lag | 268 | 1198 |
| First observed zero after peak | 2026-08-24 10:32:20 UTC | 2026-08-24 10:35:35 UTC |
| Final lag from consumer group describe | 0 | 0 |

Organic lag samples:

```text
10:31:15 0
10:31:20 0
10:31:25 0
10:31:30 0
10:31:35 94
10:31:40 94
10:31:45 94
10:31:50 268
10:31:55 268
10:32:00 268
10:32:05 207
10:32:10 207
10:32:15 207
10:32:20 0
```

Catch-up lag samples:

```text
10:34:25 0
10:34:30 0
10:34:35 0
10:34:40 0
10:34:45 0
10:34:50 590
10:34:55 590
10:35:00 590
10:35:05 1198
10:35:10 1198
10:35:15 1198
10:35:20 217
10:35:25 217
10:35:30 217
10:35:35 0
10:35:40 0
10:35:45 0
10:35:50 0
10:35:55 0
10:36:00 0
```

Interpretation:

Both runs drained to final lag 0. Catch-up had a higher transient lag peak in this local execution, so absolute lag should be interpreted as a downstream capacity observation under the same 300 EPS shape, not as proof that pre-ingress age itself causes Kafka backlog.
