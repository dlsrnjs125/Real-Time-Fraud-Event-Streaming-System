# V3 Phase 6 Evidence - External Delay and Catch-up Burst

Phase 6 compares two paired 300 EPS workloads:

- Organic burst: new events are generated at dispatch time.
- Catch-up burst: the same runtime shape is used, but `eventTime` is held back by 270 seconds.

The purpose is delay attribution. This evidence does not claim source transport latency, because `sourceSentAt` is not persisted by app-api or included in the Kafka payload. The authoritative runtime signal for upstream catch-up is persisted pre-ingress event age:

```text
receivedAt - eventTime
```

## Accepted Runs

| Workload | Run ID | Commit | Target EPS | Emitted | HTTP Failure | Dropped Iterations | Final Lag |
|---|---|---:|---:|---:|---:|---:|---:|
| Organic burst | `phase6-organic-20260824-003` | `c7e348a` | 300 | 9000 | 0 | 0 | 0 |
| Catch-up burst | `phase6-catchup-20260824-001` | `c7e348a` | 300 | 9000 | 0 | 0 | 0 |

## Key Result

| Metric | Organic Burst | Catch-up Burst |
|---|---:|---:|
| Configured event age | 0s | 270s |
| Event-to-ingress age p50 | 0.002309s | 270.002635s |
| Event-to-ingress age p95 | 0.239387s | 270.127421s |
| Event-to-ingress age p99 | 0.645115s | 270.471195s |
| Too-late events | 0 | 0 |
| Final DB counts | 9000/9000/9000 | 9000/9000/9000 |

The catch-up workload entered the system with roughly 270 seconds of pre-ingress age while remaining inside the 5-minute allowed-lateness policy. Downstream Kafka/Consumer/Redis/Sink signals were recorded separately and must not be merged into the event-age number.

## Files

| File | Purpose |
|---|---|
| `01-paired-workload-contract.md` | Organic/Catch-up paired workload contract |
| `02-clean-state-preflight.txt` | Pre-run lag 0 and Redis clean-state evidence |
| `03-organic-ingress-age.md` | Organic persisted event-to-ingress age |
| `04-catchup-ingress-age.md` | Catch-up persisted event-to-ingress age |
| `05-event-age-attribution-comparison.md` | Organic vs Catch-up attribution comparison |
| `06-downstream-processing-comparison.md` | Kafka/Consumer/Redis/Sink/API metric comparison |
| `07-lag-recovery-comparison.md` | Peak lag and drain comparison |
| `08-final-consistency.txt` | DB count and final lag consistency |
| `09-grafana-delay-attribution.png` | Grafana delay attribution panel |
| `10-grafana-processing-health.png` | Grafana processing health panel |
| `phase6-grafana-dashboard.json` | Local Grafana dashboard used for screenshots |

## Interpretation Boundary

This phase distinguishes pre-ingress event age from internal processing delay. It does not prove source network latency, and it does not treat every transient lag spike at 300 EPS as caused by catch-up age. In this accepted local run, Catch-up also showed higher transient Kafka lag than Organic; that is recorded as a downstream observation, not folded into upstream age.
