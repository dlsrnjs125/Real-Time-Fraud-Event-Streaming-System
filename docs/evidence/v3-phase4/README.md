# V3 Phase 4 Runtime Evidence

Phase 4 verifies stateful Kafka redelivery semantics with local-only failure injection.

| Evidence | Failure point | Purpose |
| --- | --- | --- |
| `01-before-redis-redelivery.md` | `BEFORE_REDIS_UPDATE` | Failure before Redis state mutation. |
| `02-after-redis-redelivery.md` | `AFTER_REDIS_UPDATE_BEFORE_RESULT` | Failure after Redis update and before result save. |
| `03-after-result-before-ack.md` | `AFTER_RESULT_SAVE_BEFORE_ACK` | Failure after result save and before ack. |
| `04-redis-state-comparison.txt` | all | Redis state comparison across accepted runs. |
| `05-next-event-risk-stability.md` | all | Threshold-adjacent next-event window and risk decision stability. |
| `06-final-consistency.txt` | all | Final DB counts and Consumer Lag after drain. |
| `07-grafana-lag-throughput.png` | all | Grafana stream boundary, total lag, partition lag, and queue delay view. |
| `08-grafana-latency-panels.png` | all | Grafana Consumer, Redis, rule, sink, duplicate guard, and partition incoming rate view. |

Accepted runtime runs:

| Run | Failure point | Target | Next event | Events | HTTP failures | Final lag |
| --- | --- | --- | --- | ---: | ---: | ---: |
| `phase4-before-redis-threshold-20260824-002` | `BEFORE_REDIS_UPDATE` | E3 | E4 | 20 | 0 | 0 |
| `phase4-after-redis-threshold-20260824-001` | `AFTER_REDIS_UPDATE_BEFORE_RESULT` | E3 | E4 | 20 | 0 | 0 |
| `phase4-after-result-threshold-20260824-001` | `AFTER_RESULT_SAVE_BEFORE_ACK` | E3 | E4 | 20 | 0 | 0 |

Threshold-adjacent next-event expectation:

```text
E4 transaction count = 5
E4 amount sum = 500000
E4 matched rule = RAPID_TRANSACTION_COUNT
E4 risk score = 30
E4 risk level = MEDIUM
E4 decision = REVIEW
```

This evidence distinguishes stateful redelivery semantics from result-row duplicate prevention alone.
