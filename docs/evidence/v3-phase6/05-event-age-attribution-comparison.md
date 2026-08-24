# Event Age Attribution Comparison

| Metric | Organic Burst | Catch-up Burst |
|---|---:|---:|
| Configured source delay | 0s | 270s |
| Event-to-ingress age p50 | 0.002309s | 270.002635s |
| Event-to-ingress age p95 | 0.239387s | 270.127421s |
| Event-to-ingress age p99 | 0.645115s | 270.471195s |
| Event-to-ingress age max | 1.182201s | 271.095674s |
| HTTP failure rate | 0 | 0 |
| Too-late result count | 0 | 0 |
| Matched-rule distribution | 5000 rapid / 4000 none | 5000 rapid / 4000 none |

Conclusion:

Organic events entered near dispatch time. Catch-up events entered with approximately 270 seconds of pre-ingress event age and remained within the 5-minute allowed-lateness policy. Because both runs produced too-late count 0, this Phase 6 result is separate from Phase 5 freshness rejection behavior.

Note:

The Grafana delay attribution screenshot uses `fraud_event_ingress_age_seconds_max` for visualizing the catch-up step because the current histogram bucket layout is capped for much smaller p95 values. Persisted DB timestamps are the authoritative p50/p95/p99 source for this evidence.
