# Troubleshooting Index

`docs/11-troubleshooting-log.md`와 `docs/18-runbook.md`가 길어졌기 때문에, 장애 유형별로 먼저 볼 위치를 정리합니다.

## Kafka / Consumer

- Manual ack timing: `docs/11-troubleshooting-log.md`, `docs/18-runbook.md#14-consumer-재시작-후-미처리-이벤트-재소비-확인`
- Consumer restart replay: `docs/18-runbook.md#18-phase-8-failure-drill`
- DLT publish failure: `docs/18-runbook.md#19-phase-9-dlt-운영-절차`
- Kafka unavailable drill: `scripts/failure_drills/kafka_unavailable_drill.md`

## Redis

- Redis degraded mode: `docs/06-redis-sliding-window.md`, `docs/18-runbook.md#3-redis-장애`
- Redis partial write: `docs/11-troubleshooting-log.md`
- Redis duplicate eventId window pollution: `docs/11-troubleshooting-log.md`
- Redis integration database isolation: `docs/18-runbook.md#17-redis-integration-test와-metric-확인`

## PostgreSQL / Consistency

- Fraud result unique constraint: `docs/04-data-model.md`, `docs/07-consistency-and-reprocessing.md`
- Processing log duplicate offset: `docs/04-data-model.md`, `docs/18-runbook.md#14-consumer-재시작-후-미처리-이벤트-재소비-확인`
- DLT source offset unique constraint: `docs/04-data-model.md`, `docs/07-consistency-and-reprocessing.md`
- DB failure no-ack policy: `docs/18-runbook.md#6-postgresql-장애`

## API / Admin

- DLT reprocess state conflict: `docs/05-api-design.md`, `docs/18-runbook.md#19-phase-9-dlt-운영-절차`
- DLT discard reason: `docs/05-api-design.md`, `docs/18-runbook.md#19-phase-9-dlt-운영-절차`
- Admin security limitation: `docs/14-security-and-privacy.md`

## CI / Script

- GitHub Actions minimum gate: `.github/workflows/ci.yml`
- Redis integration test separation: `Makefile`, `docs/18-runbook.md#17-redis-integration-test와-metric-확인`
- Failure drill script line ending issue: `docs/11-troubleshooting-log.md`
- Phase 11 evidence index: `docs/20-evidence-index.md`
- V2 Phase 7 replay evaluation evidence checks: `docs/31-v2-replay-evaluation-evidence.md`

## Load Test

- Phase 12 k6 scenarios: `load-test/k6/README.md`
- Load test result template: `docs/22-load-test-results.md`
- Duplicate replay interpretation: `docs/11-troubleshooting-log.md#phase-12-duplicate-replay와-k6-failure-기준`
- Redis down load recovery: `docs/18-runbook.md#1-2-phase-12-load-test-runbook`
- Phase 13 load/failure evidence: `docs/23-load-test-results.md`
- Phase 13 runbook: `docs/18-runbook.md#1-3-phase-13-load-test-runbook`
- Phase 13 duplicate replay interpretation: `docs/11-troubleshooting-log.md#phase-13-duplicate-replay와-k6-failure-기준`

## V2 PaySim Replay Evaluation

- Replay evaluation overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-7-replay-evaluation-결과를-탐지-성능으로-과대-해석할-위험`
- Detection quality vs operation metrics: `docs/11-troubleshooting-log.md#v2-phase-7-평가-지표와-운영-지표의-혼동`
- Raw/full processed PaySim commit risk: `docs/11-troubleshooting-log.md#v2-phase-7-대용량-paySim-결과-커밋-위험`
- Threshold tuning workload trade-off: `docs/11-troubleshooting-log.md#v2-phase-7-threshold-조정과-false-positive-증가`
- Evaluation field naming risk: `docs/11-troubleshooting-log.md#v2-phase-7-evaluation-report-field-이름이-운영-실패처럼-읽힐-위험`
- Missing result denominator risk: `docs/11-troubleshooting-log.md#v2-phase-7-missing-result를-true-negative로-세면-accuracy가-과대평가될-위험`
