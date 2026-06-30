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
- Fraud label denominator naming risk: `docs/11-troubleshooting-log.md#v2-phase-7-fraud-label-count가-전체-label-수처럼-과대-해석될-위험`
- Native type production overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-8-paysim-native-type을-운영-transaction-type으로-과도하게-해석하는-문제`
- Unsupported type default low-risk risk: `docs/11-troubleshooting-log.md#v2-phase-8-unsupported-type을-default-low-risk로-처리하는-문제`
- Mapping version comparison risk: `docs/11-troubleshooting-log.md#v2-phase-8-type-mapping-변경-후-phase-7-평가-수치와-직접-비교하는-문제`
- Replay-supported production wording risk: `docs/11-troubleshooting-log.md#v2-phase-8-replay-supported-type을-production-supported처럼-문서화하는-문제`
- Native type distribution blind spot: `docs/11-troubleshooting-log.md#v2-phase-8-native-type-분포를-보지-않고-precisionrecall만-보는-문제`
- Native mapping consistency risk: `docs/11-troubleshooting-log.md#v2-phase-8-nativeeventtype과-eventtype-mapping-consistency-검증-누락`
- Type distribution scope confusion: `docs/11-troubleshooting-log.md#v2-phase-8-type-distribution-scope가-replay-input과-evaluation-denominator를-혼동시키는-문제`
- Threshold down workload risk: `docs/11-troubleshooting-log.md#v2-phase-9-threshold를-낮춰-recall이-좋아진-것처럼-보이지만-false-positive가-증가하는-문제`
- Missing threshold version risk: `docs/11-troubleshooting-log.md#v2-phase-9-thresholdversion-없이-metric을-비교하는-문제`
- F1-only optimization risk: `docs/11-troubleshooting-log.md#v2-phase-9-f1-score만-보고-운영-최적화로-오해하는-문제`
- Fixture performance overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-9-fixture-regression을-production-성능-보장으로-오해하는-문제`
- Rule and threshold version coupling risk: `docs/11-troubleshooting-log.md#v2-phase-9-ruleversion과-thresholdversion을-하나로-뭉치는-문제`
- README bloat from phase details: `docs/11-troubleshooting-log.md#v2-phase-10-readme가-phase별-구현-상세로-비대해지는-문제`
- CI-safe and local/manual command confusion: `docs/11-troubleshooting-log.md#v2-phase-10-ci-safe-command와-localmanual-command가-readme에서-섞이는-문제`
- Final readiness production claim risk: `docs/11-troubleshooting-log.md#v2-phase-10-final-readiness를-production-fraud-성능-보장으로-오해하는-문제`
- Implemented vs planned wording risk: `docs/11-troubleshooting-log.md#v2-phase-10-implemented-vs-planned-표현이-섞이는-문제`
- final-check meaning drift: `docs/11-troubleshooting-log.md#v2-phase-10-evidence-command가-늘어나면서-final-check-의미가-불명확해지는-문제`
- Rule version not connected to consumer baseline: `docs/11-troubleshooting-log.md#v2-phase-11-evaluation-report-ruleversion이-실제-consumer-rule과-연결되지-않는-문제`
- Rule/threshold version confusion: `docs/11-troubleshooting-log.md#v2-phase-11-ruleversion과-thresholdversion을-섞는-문제`
- Java/Python version drift: `docs/11-troubleshooting-log.md#v2-phase-11-javapython-version-drift-문제`
- Per-result ruleVersion overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-11-per-result-ruleversion-없이-이벤트별-rule-consistency를-보장하는-것처럼-말하는-문제`
- Rule version integration performance overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-11-rule-version-integration을-fraud-성능-개선으로-오해하는-문제`
- Report-level ruleVersion row-level overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-12-report-level-ruleversion만-보고-row-level-consistency를-과장하는-문제`
- Legacy export strict mode failure: `docs/11-troubleshooting-log.md#v2-phase-12-legacy-export에-ruleversion이-없어-strict-mode가-실패하는-문제`
- Mixed ruleVersion distribution pollution: `docs/11-troubleshooting-log.md#v2-phase-12-mixed-presentmissing-ruleversion에서-distribution이-오염되는-문제`
- Persistence/export scope overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-12-ruleversion-persistence-범위를-과장하는-문제`
- Report schema version drift: `docs/11-troubleshooting-log.md#v2-phase-12-reportschemaversion을-올리지-않고-새-필드를-추가하는-문제`
- Active vs stored ruleVersion confusion: `docs/11-troubleshooting-log.md#v2-phase-13-active-ruleversion과-stored-result-ruleversion을-같은-의미로-오해하는-문제`
- RuleVersion observability performance overclaim: `docs/11-troubleshooting-log.md#v2-phase-13-ruleversion-observability를-fraud-성능-개선으로-과장하는-문제`
- RuleVersion metric cardinality risk: `docs/11-troubleshooting-log.md#v2-phase-13-metric-tag-cardinality-폭증-문제`
- Actuator endpoint overexposure risk: `docs/11-troubleshooting-log.md#v2-phase-13-actuator-endpoint-과다-노출-문제`
- RuleVersion summary/list backward compatibility risk: `docs/11-troubleshooting-log.md#v2-phase-13-ruleversion-summary나-filter가-기존-admin-query를-깨뜨리는-문제`
- RuleVersion and thresholdVersion mixed change risk: `docs/11-troubleshooting-log.md#v2-phase-14-ruleversion-변경과-thresholdversion-변경을-한-번에-섞는-문제`
- Active/stored ruleVersion mismatch overreaction: `docs/11-troubleshooting-log.md#v2-phase-14-active-ruleversion과-stored-result-summary가-다른-것을-무조건-장애로-오해하는-문제`
- Rollback readiness overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-14-automatic-rollback을-구현하지-않았는데-rollback-readiness라고-과장하는-문제`
- Runtime curl check CI-safe confusion: `docs/11-troubleshooting-log.md#v2-phase-14-runtime-curl-check를-ci-safe로-오해하는-문제`
- All-time ruleVersion summary dashboard risk: `docs/11-troubleshooting-log.md#v2-phase-14-all-time-ruleversion-summary를-production-dashboard로-사용하는-문제`
- Final summary README bloat risk: `docs/11-troubleshooting-log.md#v2-phase-15-최종-요약이-readme를-다시-비대하게-만드는-문제`
- final-check production performance overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-15-final-check를-production-fraud-성능-보장으로-오해하는-문제`
- Implemented/future work closure risk: `docs/11-troubleshooting-log.md#v2-phase-15-implemented와-future-work가-섞이는-문제`
- RuleVersion traceability quality overclaim risk: `docs/11-troubleshooting-log.md#v2-phase-15-ruleversion-traceability를-fraud-detection-quality로-과장하는-문제`
- Core Phase 14 and V2 Phase 15 numbering confusion: `docs/11-troubleshooting-log.md#v2-phase-15-core-phase-14와-v2-phase-15-numbering이-혼동되는-문제`

## Final Docs / Blog Closure

- Blog text vs image evidence confusion: `blog/image-plan.md`
- Broken screenshot link risk: `blog/image-plan.md`
- Blog series final order: `blog/README.md`, `blog/series/README.md`
- Final closure scope and anti-overclaim boundary: `docs/39-v2-final-evidence-closure.md`
