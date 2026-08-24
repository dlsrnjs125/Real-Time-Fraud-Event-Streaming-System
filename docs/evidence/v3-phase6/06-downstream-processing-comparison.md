# Downstream Processing Comparison

Downstream values were collected from Prometheus after each accepted run. App processes were restarted between Organic and Catch-up metric collection so cumulative histograms were not intentionally shared between the accepted metric snapshots.

| Metric | Organic Burst | Catch-up Burst |
|---|---:|---:|
| Event-to-ingress age p95, DB | 0.239387s | 270.127421s |
| Kafka producer-to-consumer delay p95 | 2.880065s | 6.500886s |
| Consumer service p95 | 0.032839s | 0.045766s |
| Redis state p95 | 0.016052s | 0.021152s |
| Rule processing p95 | 0.000951s | 0.000951s |
| Result sink p95 | 0.007784s | 0.009828s |
| API intake service p95 | 0.012378s | 0.016459s |
| k6 HTTP request duration p95 | 268.586ms | 152.728ms |

Interpretation:

The pre-ingress age difference is roughly 270 seconds. Internal processing metrics were recorded separately and stayed in millisecond-to-single-digit-second ranges. The Catch-up run also showed higher transient Kafka producer-to-consumer delay than Organic in this local run, so the evidence does not claim downstream behavior was identical. It separates the upstream age component from measured downstream delay instead of folding both into one latency number.
