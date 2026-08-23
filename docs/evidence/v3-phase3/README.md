# V3 Phase 3 Runtime Evidence

Phase 3 validates Kafka partition-affinity behavior under balanced traffic and a controlled hot-partition workload.

Accepted runtime runs:

| Run | Workload | Consumer concurrency | Events | Target EPS | Achieved EPS | Dropped iterations | HTTP failures |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `phase3-balanced-c1-20260823-002` | balanced | 1 | 36,000 | 300 | 299.93 | 0 | 0 |
| `phase3-balanced-c6-20260823-001` | balanced | 6 | 36,000 | 300 | 300.13 | 0 | 0 |
| `phase3-hot-p2-c6-20260823-001` | hot P2 | 6 | 36,000 | 300 | 299.94 | 0 | 0 |

Accepted completion scope uses balanced c1 for the single-consumer baseline, balanced c6 for one-consumer-per-partition behavior, hot P2 c6 for partition-local ceiling behavior, and concurrency 8 assignment evidence for idle consumers above partition count. c2/c3 are optional exploratory settings, not required Phase 3 completion criteria.

Discarded runtime runs:

| Run | Reason |
| --- | --- |
| `phase3-balanced-c1-20260823-001` | k6 emitted 35,860/36,000 events due to dropped iterations. |
| `phase3-balanced-c2-20260823-001` | k6 emitted 35,627/36,000 events due to dropped iterations. |
| `phase3-balanced-c2-20260823-002` | aborted during VU initialization. |
| `phase3-balanced-c2-20260823-003` | k6 emitted 35,878/36,000 events due to dropped iterations. |

The accepted evidence focuses on the questions that require runtime proof:

- balanced partition-affinity generation produces equal achieved partition distribution;
- hot P2 generation produces the intended 60% partition pressure without hot-user pressure;
- increasing consumer concurrency to match partition count suppresses balanced backlog;
- a hot partition creates partition-local lag even when other partitions are idle;
- consumer concurrency above partition count creates idle consumers;
- final database counts remain consistent after drain.

Screenshot evidence:

| File | Evidence |
| --- | --- |
| `01-balanced-partition-distribution.png` | Balanced workload expected-vs-achieved partition distribution. |
| `02-hot-p2-partition-distribution.png` | Hot P2 workload expected-vs-achieved partition distribution. |
| `03-balanced-concurrency-scaling.png` | Balanced workload concurrency scaling summary. |
| `04-hot-p2-partition-lag.png` | Grafana partition-local lag under hot P2 pressure. |
| `04b-hot-p2-partition-incoming-rate.png` | Grafana partition incoming rate and assigned partition view. |
| `05-concurrency8-idle-consumers.png` | Consumer group evidence for idle consumers when concurrency exceeds partition count. |
| `06-balanced-vs-hot-summary.png` | Balanced vs hot workload comparison at concurrency 6. |
| `07-final-consistency.png` | Final DB count and consumer lag consistency evidence. |
