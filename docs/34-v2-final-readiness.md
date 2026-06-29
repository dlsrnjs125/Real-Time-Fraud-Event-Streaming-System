# V2 Final Readiness

## 1. Purpose

V2 Phase 10 is a readiness and documentation consistency phase for the PaySim-based data, replay, and evaluation workflow completed through V2 Phase 9.

It does not add a new fraud detection model or claim better detection performance. It checks that the repository separates README entry-point content from detailed evidence docs, keeps CI-safe checks distinct from local/manual checks, and describes implemented scope, limitations, and future work without overclaiming.

## 2. V2 Phase Summary

| Phase | Goal | Main Output | Verification | Evidence Location | Status | Limitations |
|---|---|---|---|---|---|---|
| V2 Phase 1 | Data provenance and raw data protection | `data/` guardrails and policy check | `make data-policy-check` | `docs/24-kaggle-paysim-data-provenance.md` | Done | raw PaySim is local-only |
| V2 Phase 2 | PaySim preprocessing normalization | normalized runtime events and label sidecar | `make test-data-scripts` | `docs/25-paysim-normalization-mapping.md`, `scripts/data/README.md` | Done | full dataset processing is local/manual |
| V2 Phase 3 | validation, rejected rows, and safe samples | validation report and committed sample set | `make validate-paysim`, `make generate-paysim-sample` | `scripts/data/README.md` | Done | full validation requires local processed outputs |
| V2 Phase 4 | identifier hash/salt policy | HMAC-based identifier contract | strict validation/sample commands | `docs/24-kaggle-paysim-data-provenance.md`, `docs/25-paysim-normalization-mapping.md` | Done | salt management remains local/operational |
| V2 Phase 5 | replay pipeline | replay script and replay report | `make replay-paysim-sample-dry-run` | `scripts/data/README.md` | Done | actual replay requires local app-api |
| V2 Phase 6 | replay result evaluation baseline | evaluation script and report | `make evaluate-paysim-replay` | `docs/29-v2-result-evidence.md` | Done | local detection result export required |
| V2 Phase 7 | replay evaluation evidence | fixture report contract | `make verify-paysim-evaluation-report-contract` | `docs/31-v2-replay-evaluation-evidence.md` | Done | not production fraud performance |
| V2 Phase 8 | native replay contract | mapping policy and denominator type distributions | `make verify-paysim-native-replay-contract` | `docs/32-v2-paysim-native-replay-contract.md` | Done | PaySim replay-supported types are not production semantics |
| V2 Phase 9 | rule/threshold regression evidence | rule/threshold/evaluation policy fields and workload summary | `make verify-paysim-rule-threshold-regression` | `docs/33-v2-rule-threshold-regression-evidence.md` | Done | consumer Rule Engine version integration remains follow-up |
| V2 Phase 10 | final readiness | README slimdown, final readiness matrix, evidence/troubleshooting links | `make final-check` | this document | This PR | readiness check is not a production model guarantee |

## 3. Completed Scope

- raw/full processed PaySim data exclusion policy
- PaySim normalization and committed safe sample workflow
- validation report and rejected row contract
- identifier hashing and salt provenance guardrails
- replay dry-run and local actual replay script
- replay/evaluation report contract
- native type mapping policy and unsupported type explicit exclusion
- `mappingPolicyVersion` and evaluation denominator distributions
- `evaluationContractVersion`
- `ruleVersion`, `thresholdVersion`, and `evaluationPolicyVersion`
- `riskScoreCoverage`
- `operatorWorkloadSummary`
- CI-safe fixture verifiers for Phase 7, Phase 8, and Phase 9
- local/manual evidence command separation
- README slimdown with details delegated to docs and `scripts/data/README.md`

## 4. Not Production Claims

- PaySim is a synthetic dataset.
- V2 evaluation results are not production fraud model performance guarantees.
- Rule/threshold regression checks validate report semantics and change impact, not real-world fraud prevention quality.
- replay-supported PaySim types are not production-supported transaction semantics.
- current `ruleVersion` is an evaluation evidence policy value unless directly integrated with the app-consumer Rule Engine.

## 5. Verification Matrix

| Command | Scope | CI-safe | Requires local infra | Requires raw/full PaySim | Requires detection result export | Pass Criteria | Notes |
|---|---|---:|---:|---:|---:|---|---|
| `make data-policy-check` | data commit guardrail | Yes | No | No | No | raw/full PaySim data is not tracked or staged | scans tracked/staged `data/` files |
| `make test-data-scripts` | Python data script unit tests | Yes | No | No | No | all fixture tests pass | does not need Kaggle CSV |
| `make verify-paysim-evaluation-report-contract` | Phase 7 report contract | Yes | No | No | No | required report fields and fixture metrics match | creates temp files only |
| `make verify-paysim-native-replay-contract` | Phase 8 native mapping contract | Yes | No | No | No | mapping/distribution/exclusion contract matches | creates temp files only |
| `make verify-paysim-rule-threshold-regression` | Phase 9 threshold regression contract | Yes | No | No | No | version fields, fallback, workload, and fixture metrics match | creates temp files only |
| `make verify-v2-phase9` | aggregate V2 Phase 7/8/9 checks | Yes | No | No | No | data tests, policy check, and all verifiers pass | current V2 CI-safe gate |
| `make final-check` | representative repository readiness gate | Yes | No | No | No | Gradle build, Docker config, script syntax, and `verify-v2-phase9` pass | requires Java/Python/Docker tooling |
| `make validate-paysim` | local processed output validation | No | No | Yes | No | local processed output/report contract is valid | fails if local processed outputs are stale |
| `make replay-paysim-sample` | actual sample replay | No | Yes | No | No | local app-api accepts/rejects according to replay contract | requires local infra and app-api |
| `make evaluate-paysim-replay` | local detection result evaluation | No | No | No | Yes | strict evaluation report is generated | does not export DB rows itself |
| `make evaluate-paysim-threshold-policy-report` | local threshold policy report | No | No | No | Yes | report includes selected threshold policy and workload summary | depends on local detection export |

## 6. Evidence Map

| Area | Location | Scope |
|---|---|---|
| Evidence index | `docs/20-evidence-index.md` | phase evidence and command lookup |
| Troubleshooting index | `docs/21-troubleshooting-index.md` | quick entry points into long troubleshooting records |
| PaySim command reference | `scripts/data/README.md` | CI-safe and local/manual command matrix |
| Data provenance | `docs/24-kaggle-paysim-data-provenance.md` | dataset source and raw data exclusion |
| Normalization contract | `docs/25-paysim-normalization-mapping.md` | event/label/rejected output semantics |
| Result evidence plan | `docs/29-v2-result-evidence.md` | evaluation report and metric interpretation |
| Replay evaluation evidence | `docs/31-v2-replay-evaluation-evidence.md` | Phase 7 report contract |
| Native replay contract | `docs/32-v2-paysim-native-replay-contract.md` | Phase 8 mapping policy and denominator scope |
| Rule threshold regression evidence | `docs/33-v2-rule-threshold-regression-evidence.md` | Phase 9 threshold/version/workload evidence |
| Blog drafts | `blog/25-*` through `blog/28-*` | narrative review and troubleshooting story |
| Generated local reports | `data/processed/*.json` | local/manual output, not committed |
| Committed fixtures/samples | `data/samples/*`, script tests | small safe samples and fixture-based checks |

## 7. README Policy

- README remains an entry point for project summary, architecture, local start commands, representative verification, and docs links.
- Phase-specific implementation details stay in docs/blog.
- PaySim command matrices stay in `scripts/data/README.md`.
- README should link out instead of accumulating troubleshooting, metric interpretation, or local/manual evidence procedures.
- `make final-check` is the representative repository readiness check.

## 8. Release / Merge Readiness Criteria

- `make final-check` passes.
- data policy check passes.
- Python data script tests pass.
- Phase 7/8/9 fixture verifiers pass.
- Gradle tests pass as part of `make final-check`.
- README keeps V2 details minimal and links to docs/scripts README.
- docs/blog links point to existing files.
- raw/full processed PaySim data is not staged or tracked beyond allowed sample files.
- implemented, local/manual, and future work are clearly separated.
- local/manual failures are recorded with their environment dependency.

## 9. Known Limitations

- Full PaySim replay is local/manual.
- Full raw dataset is not stored in this repository.
- Detection results without `riskScore` use threshold policy risk-level fallback.
- `ruleVersion` can currently be an evaluation evidence policy value.
- app-consumer Rule Engine version and evaluation report direct integration remains follow-up.
- Full rejected event id export remains a future hardening item.
- Grafana/dashboard integration for V2 evidence remains future work.
- Model-based baseline comparison remains future work.

## 10. Next Steps

- app-consumer Rule Engine version integration
- full replay rejected ids output
- threshold before/after comparison report
- Grafana dashboard integration
- DLQ/replay failure and evaluation correlation
- model baseline comparison
- final summary page after V2 evidence stabilizes
- PR review checklist automation
