# V3 Phase 7 Isolation Workload Contract

Captured at: 2026-08-31 KST

## Code and Host

| Field | Value |
|---|---|
| Commit SHA | `d5f425b` |
| Host CPU | Apple M1 Pro |
| Host logical CPUs | 10 |
| Host memory | 16 GiB |
| Docker observed memory limit | 7.75 GiB |
| Kafka partitions | 6 |

## Live Route

| Field | Value |
|---|---|
| API port | `8080` |
| Consumer port | `8081` |
| Topic | `transaction-events` |
| Consumer group | `fraud-event-consumer` |
| Redis namespace | `fraud:tx:live:*` |

## Replay Route

| Field | Value |
|---|---|
| API port | `8082` |
| Consumer port | `8083` |
| Topic | `transaction-events-replay` |
| Consumer group | `fraud-event-replay-consumer` |
| Redis namespace | `fraud:tx:replay:*` |

## Workloads

| Workload | Scenario | Target EPS | Duration | Event Limit | Event Time |
|---|---|---:|---:|---:|---|
| Live control | `v3-phase2-state-size-baseline` | 100 | 120s | 12,000 | arrival time |
| Historical replay | `v3-phase7-historical-replay` | 150 | 30s | 4,500 | 24h before arrival |

The live control reuses the existing Phase 2 state-size baseline workload to avoid adding Phase 7-only load-test driver code. Phase 7 evidence compares logical routing/state isolation and records concurrent-run latency observations separately.

## Acceptance Criteria

| Check | Expected |
|---|---|
| Phase 7 replay HTTP failures | 0 |
| Phase 7 replay failed checks | 0 |
| Phase 7 replay dropped iterations | 0 |
| Phase 7 replay emitted events | 4,500 |
| Replay final Consumer Lag | 0 |
| Live final Consumer Lag | 0 |
| Replay Redis state | `fraud:tx:replay:user:{userId}:events` ZSET keys created |
| Cross namespace contamination | 0 replay-prefixed users under live namespace, 0 live-prefixed users under replay namespace |
| Final DB consistency | receipt/result/processing-log counts align by eventId prefix |
