# V2 Final Portfolio Summary

## 1. Purpose

V2 Phase 15 closes the V2 evidence sequence from Phase 7 through Phase 14.

This phase is V2 Phase 15, separate from the core streaming pipeline Phase 14 already documented in the root README. It does not add a new API, DB migration, fraud rule, threshold, metric, dashboard, or rollback automation.

The goal is to make the problem definition, design decisions, verification evidence, operational limits, and future work easy to review without expanding the root README.

## 2. One-line Summary

V2 extends the Kafka-based fraud detection system with PaySim replay/evaluation contracts and ruleVersion traceability evidence, proving reproducible evaluation, version consistency, and operational readiness rather than production fraud model accuracy.

한국어 요약:

V2는 Kafka 기반 이상거래 탐지 시스템에 PaySim replay/evaluation 계약과 ruleVersion 추적성을 추가해, 모델 성능 과장이 아니라 재현 가능한 평가, 버전 정합성, 운영 준비도를 증명하는 단계입니다.

## 3. Problem Framing

V2 addressed these evidence gaps:

- A synthetic dataset without provenance and raw data policy weakens trust.
- Evaluation metrics without denominator, missing-result, and excluded-type policy are easy to misread.
- Treating PaySim native types as production transaction semantics overclaims the domain model.
- A report-level `ruleVersion` string alone cannot prevent Java/Python drift.
- Detection results without per-result `ruleVersion` provide weak row-level traceability.
- Confusing active runtime version with stored historical result version makes deployment diagnosis harder.
- A version change without a runbook leaves hold/rollback decisions subjective.

## 4. Phase Map

| Phase | Problem | Main Decision | Output | Verification | Evidence | Limitation |
|---|---|---|---|---|---|---|
| V2 Phase 7 | evaluation metric overclaim risk | report contract and denominator policy | replay evaluation evidence | `make verify-paysim-evaluation-report-contract` | `docs/31-v2-replay-evaluation-evidence.md` | not production model performance |
| V2 Phase 8 | PaySim native type overclaim risk | `mappingPolicyVersion` and unsupported type exclusion | native replay contract | `make verify-paysim-native-replay-contract` | `docs/32-v2-paysim-native-replay-contract.md` | PaySim types are replay-supported only |
| V2 Phase 9 | threshold tuning overclaim risk | `ruleVersion` / `thresholdVersion` separation and workload summary | rule/threshold regression evidence | `make verify-paysim-rule-threshold-regression` | `docs/33-v2-rule-threshold-regression-evidence.md` | consumer ruleVersion integration followed later |
| V2 Phase 10 | README bloat risk | README as entry point, docs/scripts as detail | final readiness docs | `make final-check` | `docs/34-v2-final-readiness.md` | no new runtime feature |
| V2 Phase 11 | Java/Python ruleVersion drift | app-consumer active version contract | rule version integration evidence | `make verify-paysim-rule-version-contract` | `docs/35-v2-rule-version-integration-evidence.md` | per-result persistence came later |
| V2 Phase 12 | report-level ruleVersion only | per-result propagation, coverage, and strict mode | result ruleVersion propagation | `make verify-paysim-result-rule-version-contract` | `docs/36-v2-result-rule-version-propagation-evidence.md` | legacy rows may be null |
| V2 Phase 13 | runtime/stored version confusion | Actuator info and stored summary semantics | ruleVersion observability evidence | `./gradlew test`, `make final-check` | `docs/37-v2-rule-version-observability-evidence.md` | dashboard hardening remains future work |
| V2 Phase 14 | no change runbook | pre/post checklist and hold/rollback criteria | ruleVersion change runbook | `make final-check`, manual drill template | `docs/38-v2-rule-version-change-runbook.md` | no automatic rollback |

## 5. Engineering Decisions

- PostgreSQL remains the source of durable detection result and audit truth.
- Kafka remains the asynchronous ingestion and replay backbone.
- Redis remains short-term detection state, not correctness authority.
- Raw/full PaySim data stays outside the repository.
- CI-safe fixture verifiers are separated from full local/manual replay.
- `mappingPolicyVersion`, `evaluationContractVersion`, `ruleVersion`, and `thresholdVersion` are separate version dimensions.
- Active runtime `ruleVersion` and stored historical result `ruleVersion` are separate operational meanings.
- README stays minimal; detailed evidence lives in docs, blog drafts, and `scripts/data/README.md`.
- `make final-check` is a readiness guardrail, not production fraud quality certification.

## 6. Verification Map

| Command | What It Validates | CI-safe | What It Does Not Validate | Evidence Link |
|---|---|---:|---|---|
| `make final-check` | Gradle build/tests, Docker Compose config, shell syntax, V2 data/evaluation guardrails | Yes | production fraud quality, local curl evidence, raw PaySim replay | `docs/34-v2-final-readiness.md` |
| `./gradlew test` | Java unit/slice contracts, including ruleVersion persistence and runtime/admin tests | Yes | local app deployment, actual actuator/admin curl drill | `docs/37-v2-rule-version-observability-evidence.md` |
| `make test-data-scripts` | Python data helper fixture tests | Yes | raw PaySim full processing | `scripts/data/README.md` |
| `make data-policy-check` | committed/staged `data/` guardrail | Yes | semantic quality of local ignored data | `docs/24-kaggle-paysim-data-provenance.md` |
| `make verify-paysim-evaluation-report-contract` | Phase 7 report schema and denominator contract | Yes | model accuracy | `docs/31-v2-replay-evaluation-evidence.md` |
| `make verify-paysim-native-replay-contract` | native mapping policy and unsupported type exclusion | Yes | production transaction semantics | `docs/32-v2-paysim-native-replay-contract.md` |
| `make verify-paysim-rule-threshold-regression` | threshold policy, workload summary, regression fields | Yes | production staffing capacity or fraud prevention quality | `docs/33-v2-rule-threshold-regression-evidence.md` |
| `make verify-paysim-rule-version-contract` | Java/Python ruleVersion drift and mismatch fail-fast | Yes | per-row persistence by itself | `docs/35-v2-rule-version-integration-evidence.md` |
| `make verify-paysim-result-rule-version-contract` | per-result ruleVersion coverage, distribution, strict mode | Yes | historical backfill or DB export automation | `docs/36-v2-result-rule-version-propagation-evidence.md` |
| `make verify-v2-phase13` | V2 data/evaluation guardrails through Phase 12 | Yes | Phase 13 Java tests by itself, local runtime curl checks | `scripts/data/README.md` |

Local/manual actuator and admin curl checks are documented in `docs/37-v2-rule-version-observability-evidence.md` and `docs/38-v2-rule-version-change-runbook.md`. They are not part of `final-check`.

## 7. Implemented

- replay evaluation report contract
- PaySim native mapping contract
- threshold regression report semantics
- Java/Python ruleVersion drift check
- per-result ruleVersion propagation, coverage, and strict mode
- app-consumer active ruleVersion info
- app-api stored ruleVersion summary
- ruleVersion change runbook with hold/rollback criteria
- README slimdown and docs/scripts evidence separation

## 8. Local/manual

- full PaySim replay/evaluation using local raw data and local app-api
- local app-api/app-consumer runtime curl checks
- admin ruleVersion summary manual check
- runtime evidence capture for an actual ruleVersion change
- local DB detection result export
- production-like load or failure drills beyond fixture checks

## 9. Future Work

- rule deployment changelog persistence
- automatic rollback
- ruleVersion summary time range filter
- `(rule_version, detected_at)` index
- Grafana dashboard
- unexpected ruleVersion alert
- historical `rule_version` backfill
- model baseline comparison
- full PaySim local evidence automation
- PaySim-specific Java Rule Engine V2 rules

## 10. Anti-overclaim Guardrails

- PaySim is a synthetic dataset.
- V2 evaluation is not production fraud model performance.
- RuleVersion traceability is not detection accuracy.
- `make final-check` is a readiness guardrail, not production certification.
- The Phase 14 runbook is rollback readiness, not automatic rollback.
- app-consumer active version and stored result version have different meanings.
- Local/manual checks are not CI-safe unless explicitly automated.
- README intentionally omits detailed V2 phase history.

## 11. Interview Answer Pack

### Why did you add V2?

V2 was added to make evaluation and evidence reproducible around the existing Kafka fraud pipeline. Instead of claiming better detection quality, it defines PaySim data provenance, replay/evaluation contracts, denominator policy, and version traceability so reviewers can see exactly what was measured and what was not.

### What limits did you consider when using PaySim?

PaySim is synthetic, so I treated it as replay/evaluation evidence rather than production fraud truth. Raw and full processed data are excluded from Git, native PaySim types are mapped carefully, and unsupported types are explicit instead of being silently interpreted as production behavior.

### How did you avoid overclaiming evaluation metrics?

The evaluator records denominator policy, missing-result treatment, replay-rejected handling, native type distribution, threshold policy, and workload summary. Fixture verifiers check these fields, but the docs repeatedly state that these metrics are not production fraud model performance.

### Why separate ruleVersion and thresholdVersion?

`ruleVersion` identifies rule logic baseline. `thresholdVersion` identifies evaluation decision boundaries. If they are combined, a metric change could be caused by either rule logic or threshold policy, which makes regression interpretation weak.

### How is Java/Python ruleVersion drift prevented?

`make verify-paysim-rule-version-contract` reads the Java `FraudRuleVersions` source and compares it with the Python evaluator policy. It also checks that unsupported and mismatched ruleVersion values fail fast.

### Why did per-result ruleVersion matter?

Report-level ruleVersion only says which evaluation contract was selected. Per-result ruleVersion shows which rule baseline produced each stored detection result, while coverage/readiness fields keep legacy missing rows honest.

### How are active runtime and stored result ruleVersion different?

Active runtime ruleVersion is the currently running app-consumer Rule Engine baseline. Stored result ruleVersion is the version used when a specific result was created. After deployment, old and new stored versions may coexist normally.

### What does final-check guarantee?

`make final-check` validates repository readiness guardrails: Gradle build/tests, Docker Compose config, shell syntax, data policy, and V2 fixture verifiers. It does not guarantee production fraud model accuracy, production latency, local runtime curl evidence, or full PaySim replay.

### How does this project show backend and DevOps engineering skill?

It combines asynchronous Kafka processing, idempotent PostgreSQL persistence, Redis degraded-mode handling, operational metrics, DLQ/reprocessing flow, data guardrails, CI-safe contract verifiers, and runbooks. V2 adds evidence discipline around evaluation, versioning, and release readiness.

### How was AI used and verified?

AI-assisted drafts were treated as candidates, not accepted blindly. The final docs separate implemented, local/manual, and future work; preserve README minimalism; keep raw/full data excluded; and verify the repository with final-check, data policy, ruleVersion verifiers, evidence index, troubleshooting index, and roadmap updates.

## 12. Final Limitations

- Production fraud model validation is out of scope.
- Full PaySim local/manual evidence requires raw data and local infrastructure.
- Automatic rollback is not implemented.
- Production alerting is not implemented.
- Deployment changelog persistence is not implemented.
- Full historical backfill is not implemented.
- Time-bounded summary query and index strategy remain future work.
- Dashboard integration remains future work.

## 13. Portfolio Links

- Evidence Index: `docs/20-evidence-index.md`
- Troubleshooting Index: `docs/21-troubleshooting-index.md`
- V2 Final Readiness: `docs/34-v2-final-readiness.md`
- V2 Rule Version Integration: `docs/35-v2-rule-version-integration-evidence.md`
- V2 Per-result Rule Version Propagation: `docs/36-v2-result-rule-version-propagation-evidence.md`
- V2 Rule Version Observability: `docs/37-v2-rule-version-observability-evidence.md`
- V2 Rule Version Change Runbook: `docs/38-v2-rule-version-change-runbook.md`
- PaySim Data Scripts: `scripts/data/README.md`
- Blog Drafts: `blog/25-v2-paysim-replay-evaluation-evidence.md` through `blog/33-v2-final-portfolio-summary.md`
