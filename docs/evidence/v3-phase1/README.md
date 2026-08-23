# V3 Phase 1 Evidence Capture Guide

This folder stores local/manual evidence for V3 Phase 1 sustainable throughput and backlog recovery.

Do not commit raw k6 result JSON, large video recordings, or unrelated screenshots. Keep only the selected evidence that explains the bottleneck and before/after result.

## Target Files

| No. | File | Owner | Purpose |
|---:|---|---|---|
| 01 | `01-before-lag-growth.png` | Manual or Chrome capture | Shows total and partition Consumer Lag growing with concurrency 1 |
| 02 | `02-before-bottleneck-isolation.png` | Manual or Chrome capture | Shows API/Kafka/DB staying healthy while Consumer is the bottleneck |
| 02b | `02b-before-api-kafka-hikari.png` | Chrome capture | Supplemental lower-dashboard crop for Kafka publish wait and Hikari panels |
| 03 | `03-consumer-assignment-before.txt` | Codex | Terminal evidence that one consumer owns six partitions |
| 04 | `04-consumer-assignment-after.txt` | Codex | Terminal evidence that six consumers own one partition each |
| 05 | `05-after-lag-contained.png` | Manual or Chrome capture | Shows lag contained under the same workload with concurrency 6 |
| 06 | `06-final-consistency-check.txt` | Codex | PostgreSQL row counts and final Consumer Lag 0 |

## Captured Run Notes

These local evidence files were captured on 2026-08-23 from the Grafana `V3 Stream Processing Foundation` dashboard and Kafka/PostgreSQL terminal checks.

| Evidence | Run ID | Notes |
|---|---|---|
| Before screenshots | `phase1-evidence-before-concurrency1-20260823-001` | Used for visual lag-growth and bottleneck-isolation evidence. k6 emitted 50,993 of the configured 51,000 HTTP events and dropped 7 iterations, so this run is not used as the final row-count contract. |
| After screenshot and final consistency | `phase1-evidence-after-concurrency6-20260823-001` | k6 emitted 51,000 of 51,000 events with HTTP failure rate 0. Final `receipts`, `fraud_results`, and `processing_logs` were all 51,000, and Consumer Lag was 0. |

The main measured before/after Phase 1 result remains documented in `docs/46-v3-phase1-sustainable-throughput-evidence.md`. This folder stores the selected visual and terminal artifacts for review.

## Screenshot 01 - Before Lag Growth

Capture the Grafana dashboard `V3 Stream Processing Foundation`.

Use these panels in one screenshot:

- `Total Consumer Lag`
- `Partition Consumer Lag`
- optional: `Lag Growth / Drain Rate`

Required state:

- app-consumer is running with default concurrency 1
- workload is `backlog-recovery-v1`
- screenshot is taken during or just after the 300 EPS overload stage
- total lag is visibly increasing
- partition lag shows lag spread across most or all partitions

Recommended time range:

- Use the run window around the before run.
- If using relative time, use `Last 10 minutes` right after the run.
- If comparing manually, keep the same panel layout and similar time range for screenshot 05.

## Screenshot 02 - Before Bottleneck Isolation

Capture the same Grafana dashboard around the same before-run time window.

Use these panels in one screenshot:

- API service/transaction p99
- Kafka publish wait p99
- Hikari pending
- Consumer service p99

What the screenshot should prove:

- API transaction p99 remains low
- Kafka publish wait p99 remains low
- Hikari pending stays at 0
- Consumer service and/or lag is the limiting side

Avoid cropping away the panel titles, legends, y-axis values, and time range selector.

## Screenshot 03/04 - Consumer Assignment

Codex can produce these as terminal text files using:

```bash
scripts/load_tests/capture_v3_phase1_terminal_evidence.sh before
scripts/load_tests/capture_v3_phase1_terminal_evidence.sh after
```

The underlying command is:

```bash
docker exec fraud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group fraud-event-consumer
```

Expected before signal:

- one `CONSUMER-ID`
- all six `transaction-events` partitions assigned to that consumer

Expected after signal:

- six `CONSUMER-ID` values
- each consumer owns one `transaction-events` partition

## Screenshot 05 - After Lag Contained

Capture the same Grafana dashboard and same panel layout as screenshot 01.

Required state:

- app-consumer is running with `FRAUD_CONSUMER_CONCURRENCY=6`
- workload is the same `backlog-recovery-v1`
- final Consumer Lag is 0
- peak lag is tiny compared with the before run

Recommended comparison:

- Use the same dashboard panels as screenshot 01.
- Keep the visible y-axis scale comparable if Grafana allows it.
- If Grafana auto-scales, make sure the panel title, legend, peak value, and time range remain visible.

## Screenshot 06 - Final Consistency Check

Codex can produce this as a terminal text file using:

```bash
docker exec fraud-postgres psql -U fraud -d fraud -c \
  "select (select count(*) from transaction_event_receipts) receipts, \
          (select count(*) from fraud_detection_results) results, \
          (select count(*) from event_processing_logs) logs;"

docker exec fraud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group fraud-event-consumer
```

Expected result:

- `receipts = 51000`
- `results = 51000`
- `logs = 51000`
- all partition `LAG = 0`

## Manual Capture Rules

- Capture the full browser window or enough of the dashboard to include panel titles, legends, axes, and the time range.
- Do not crop out the Grafana time range.
- Do not include unrelated browser tabs or personal information.
- Prefer PNG.
- Keep filenames exactly as listed in the target table.

## Review Checklist

- [ ] Before screenshot shows lag growth, not only final lag.
- [ ] Before isolation screenshot shows API/Kafka/Hikari are not the bottleneck.
- [ ] Before assignment proves one consumer thread owned six partitions.
- [ ] After screenshot uses the same workload and similar dashboard layout.
- [ ] Final consistency check proves row counts align and lag is 0.
