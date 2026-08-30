# Live Only Baseline

Run ID: `phase7-live-only-20260831-002`

The live baseline reused the existing `v3-phase2-state-size-baseline` workload as a control workload. It ran against the live API and live Consumer only.

| Metric | Value |
|---|---:|
| Target EPS | 100 |
| Duration | 120s |
| Configured event limit | 12,000 |
| Emitted event count | 12,000 |
| Achieved EPS | 99.996 |
| HTTP failure rate | 0 |
| Checks rate | 1 |
| Dropped iterations | metric absent |
| HTTP p95 | 11.024 ms |
| Final live Consumer Lag | 0 |
| Replay Redis keys after run | 0 |
| Live-run keys in replay namespace | 0 |

Final DB consistency for the accepted run:

```text
receipts        = 12000
fraud_results   = 12000
processing_logs = 12000
```

Discarded run:

`phase7-live-only-20260831-001` emitted 11,993 of 12,000 events with `droppedIterations=7`. It was kept in final DB consistency output for traceability but is not used as accepted evidence.

For the accepted run, k6 did not serialize a `dropped_iterations` metric. Because the run emitted the full configured 12,000 events and the discarded run showed the metric when drops occurred, this is recorded as no dropped-iteration series observed.
