# V2 Rule Threshold Regression Evidence

## 1. Purpose

V2 Phase 9 adds regression evidence for rule and threshold changes on top of the Phase 7 replay evaluation contract and the Phase 8 PaySim native replay contract.

This phase is not a production fraud performance claim. It makes evaluation reports explain which rule version, threshold version, mapping policy, and evaluation contract produced a metric change.

Phase boundaries:

- Phase 7: replay evaluation report contract
- Phase 8: PaySim native type mapping and denominator contract
- Phase 9: rule/threshold regression contract
- Phase 11: app-consumer/evaluator `ruleVersion` drift check

## 2. Problem

Fraud rules are threshold-sensitive.

Lowering a threshold can increase detected fraud and recall, but it can also increase false positives and review workload. Raising a threshold can reduce false positives, but it can increase missed fraud risk.

Metric changes are hard to explain without versions:

- rule logic can change
- threshold boundaries can change
- PaySim native type mapping can change
- missing/excluded denominator policy can change

Therefore precision, recall, F1, and workload counts must be read with explicit version fields.

## 3. Versioning Policy

| Version Field | Meaning | Changes When | Example | Comparison Rule |
|---|---|---|---|---|
| `mappingPolicyVersion` | PaySim native type mapping | `CASH_OUT` mapping or `DEBIT` handling changes | `paysim-native-mapping-v1` | Different versions can change denominator composition |
| `evaluationContractVersion` | Report schema and denominator policy | Missing/excluded handling or report schema changes | `v2-phase9-evaluation-contract-v1` | Direct comparison is not valid across versions |
| `ruleVersion` | Rule logic meaning | Rule scoring or matched rule semantics change | `rule-v2-baseline-v1` | Separate rule effects from threshold effects |
| `thresholdVersion` | Risk/action boundary | MEDIUM/HIGH threshold or action boundary changes | `threshold-v1` | Precision, recall, F1, and workload can change |
| `evaluationPolicyVersion` | Evaluation script interpretation | Workload summary or action decision aggregation changes | `evaluation-policy-v1` | Report interpretation can change |

Phase 9 fills these fields in the evaluation report. Phase 11 connects the contract-level `ruleVersion` to the app-consumer baseline source; per-result persistence/export remains a follow-up.

Threshold policy is the source of truth for threshold-related decisions. The report-level `positiveRiskLevel` is derived from `thresholdPolicy.positiveRiskLevelFallback`, and the legacy `--positive-risk-level` option is accepted only when it matches the selected threshold policy fallback.

## 4. Threshold Trade-Off

Threshold down:

- recall may increase
- false positives may increase
- review candidate rate may increase
- operator workload may become unrealistic

Threshold up:

- precision may improve
- false positives may decrease
- missed fraud risk may increase
- blocked/review volume may decrease

F1 is useful, but it is not the only operational decision metric. A threshold with a better F1 can still create too many review candidates or block too many normal transactions.

## 5. Evaluation Regression Gate

CI-safe gate:

```bash
make verify-v2-phase9
```

The CI-safe gate uses fixture data only. It verifies:

- report schema fields exist
- `ruleVersion`, `thresholdVersion`, `evaluationPolicyVersion` exist
- `mappingPolicyVersion` and `evaluationContractVersion` exist
- expected precision/recall/F1 fixture values match
- expected review/block workload values match
- expected riskScore coverage and fallback behavior match
- workload budget status is present
- unsupported native type remains excluded
- missing result default policy remains explicit
- raw/full PaySim data is not staged

Local/manual gate:

```bash
make evaluate-paysim-threshold-policy-report
make v2-phase9-evidence
```

These commands require local processed labels, replay report, and detection result export. They do not download PaySim data or export database rows by themselves.

## 6. Report Fields

Implemented Phase 9 fields:

- `reportSchemaVersion`
- `evaluationContractVersion`
- `evaluationPolicyVersion`
- `mappingPolicyVersion`
- `ruleVersion`
- `thresholdVersion`
- `thresholdPolicy`
- `positiveRiskLevel`
- `mediumRiskThreshold`
- `highRiskThreshold`
- `decisionPolicy`
- `riskScoreCoverage`
- `thresholdRegressionReliability`
- `reviewCandidateEvents`
- `reviewCandidateRate`
- `blockedCandidateEvents`
- `blockedCandidateRate`
- `actionDecisionDistribution`
- `operatorWorkloadSummary`
- Phase 7 metric fields
- Phase 8 native/replay/evaluated type distribution fields

Future fields:

- per-result consumer-provided rule version export
- consumer threshold config snapshot
- production action decision distribution
- automated before/after threshold comparison file

When `riskScore` is missing for an evaluated result, threshold decisions fall back to the risk-level fallback sets defined by `thresholdPolicy`. The report records this with `riskScoreCoverage`, `thresholdRegressionReliability`, and a warning.

`operatorWorkloadSummary.budgetStatus` records whether review/block candidate rates are within the selected threshold policy's candidate workload budget. This is a warning signal, not a release gate.

## 7. Operational Use

Regression evidence is a review signal, not an absolute release decision.

Review together:

- precision
- recall
- F1
- false positives
- missed fraud events
- review candidate rate
- blocked candidate rate
- Consumer Lag and p95 latency when running actual replay
- DLQ and Redis degraded counts when relevant

## 8. Limitations

PaySim is synthetic and does not represent real production fraud behavior.

The current threshold policy is an evaluation evidence policy. It is not a real financial fraud policy or staffing model.

Fixture regression checks validate report semantics and expected metric calculations. They do not guarantee production fraud performance.

Full replay rejected event exclusion can still be incomplete when the bounded replay failure summary does not contain every rejected eventId. A future `--rejected-output` and evaluator `--rejected-events` option should make full local denominator exclusion exact.

## 9. Next Steps

- connect app-consumer Rule Engine version to evaluation report
- add threshold before/after comparison report
- add Grafana panel candidates for rule/threshold version
- connect DLQ/replay failure summary to regression evidence
- generate local full PaySim evidence summary without committing raw/full data
- compare rule baseline with model-based baseline
- define operator workload budget policy
