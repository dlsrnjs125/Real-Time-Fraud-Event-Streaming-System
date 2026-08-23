# V3 Phase 2 Evidence

This directory stores evidence for the V3 Phase 2 stateful sliding-window scaling experiment.

## Files

- `01-baseline-window-latency.png`: baseline Grafana evidence for Redis window size and latency.
- `02-pressure-window-latency.png`: high-density Grafana evidence for Redis window size and latency.
- `03-redis-commandstats-before-after.txt`: Redis commandstats and per-event command scaling.
- `04-redis-state-shape.txt`: Redis ZSET/Hash shape for baseline and pressure runs.
- `05-partition-distribution.txt`: partition distribution and peak partition Lag.
- `06-final-consistency.txt`: PostgreSQL row-count and final Consumer Lag consistency.

## Accepted Runs

- Baseline: `phase2-state-baseline-clean-20260823-003`
- Pressure: `phase2-state-pressure-clean-20260823-001`

## Main Finding

Increasing per-user window members from 12 to 120 did not increase clean Redis memory delta. The stronger signal was command scaling: `HGET` calls per event increased from 6.5 to 60.5, Redis state p95 increased from 6.20 ms to 32.84 ms, and Consumer service p95 increased from 13.18 ms to 38.47 ms.
