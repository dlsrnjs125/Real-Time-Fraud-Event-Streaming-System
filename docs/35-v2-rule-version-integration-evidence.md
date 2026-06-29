# V2 Rule Version Integration Evidence

## 1. Purpose

V2 Phase 11 connects the app-consumer Rule Engine baseline version with the PaySim evaluation report `ruleVersion`.

The goal is version drift detection. It does not add a new fraud rule, tune thresholds, or claim improved fraud detection quality.

## 2. Problem

V2 Phase 9 added `ruleVersion` to the evaluation report, but it was still a Python-side evidence policy value. That made the report useful for regression checks, but left one important gap: the Java Rule Engine baseline and the Python evaluator could drift silently.

Phase 11 closes that gap by adding an app-consumer rule version source and a CI-safe verifier that compares it with the evaluator policy.

## 3. Source Of Truth

| Contract | Source Area | Source File | Report / Verifier Use | Drift Risk | CI-safe Check |
|---|---|---|---|---|---|
| `ruleVersion` | app-consumer | `app-consumer/src/main/java/com/example/fraud/consumer/rule/FraudRuleVersions.java` | evaluation report `ruleVersion`, per-result ruleVersion parser, rule version verifier | Java Rule Engine and Python evaluator disagree | `make verify-paysim-rule-version-contract` |
| `thresholdVersion` | scripts/data | `scripts/data/paysim_evaluation_policy.py` | threshold policy, risk-level fallback, workload summary | metrics compared under different threshold boundaries | `make verify-paysim-rule-threshold-regression` |
| `mappingPolicyVersion` | scripts/data | `scripts/data/paysim_native_type_mapping.py` | native type mapping, replay/evaluation denominator fields | unsupported/native type denominator mismatch | `make verify-paysim-native-replay-contract` |
| `evaluationContractVersion` | scripts/data | `scripts/data/evaluate_paysim_replay_results.py` | report parser/verifier schema contract | report consumer reads incompatible schema | `make verify-paysim-evaluation-report-contract` |

`ruleVersion` and `thresholdVersion` are intentionally separate. A rule logic change and a threshold boundary change can both alter metrics, but they are different causes and should be reviewed independently.

## 4. Rule Version Policy

The current app-consumer baseline rule version is:

```text
rule-v2-baseline-v1
```

Policy:

- Java exposes the baseline through `FraudRuleVersions.RULE_V2_BASELINE_V1`.
- Java exposes the active app-consumer baseline through `FraudRuleVersions.ACTIVE_RULE_VERSION`.
- Python evaluator policy `RULE_VERSION` must match the Java baseline.
- Python `RULE_VERSIONS` allowlist must contain only the active Java baseline.
- Unsupported values such as `rule-v2-drift-v1` fail fast.
- The verifier reads the Java source directly, so a Java/Python version mismatch is caught without raw PaySim data, local app-api, or a detection result export.

## 5. Detection Result Export Policy

Detection result rows may include a per-result `ruleVersion`:

```json
{"eventId": "paysim-000000001", "riskLevel": "HIGH", "riskScore": 85, "ruleVersion": "rule-v2-baseline-v1", "ruleCodes": ["AMOUNT_THRESHOLD"]}
```

Evaluator behavior:

- If a result row has `ruleVersion`, it must exactly match the expected contract-level `ruleVersion`.
- If a result row has a mismatched `ruleVersion`, evaluation fails fast.
- If result rows omit `ruleVersion`, evaluation still runs with the contract-level `ruleVersion`, but the report records `ruleVersionCoverage` and a warning.
- Per-result app-consumer persistence/export of `ruleVersion` remains future work. Phase 11 proves contract alignment, not per-event Java result persistence.

Report fields added or strengthened in this phase:

- `ruleVersionCoverage`
- `ruleVersionDistribution`
- warning when evaluated rows omit per-result `ruleVersion`

## 6. Verification Gate

CI-safe command:

```bash
make verify-paysim-rule-version-contract
make verify-v2-phase11
```

`make verify-paysim-rule-version-contract` checks:

- Java rule version source exists and has the expected format.
- Python evaluator policy matches the Java active version.
- Unsupported rule versions are rejected.
- Fixture reports with per-result `ruleVersion` produce full coverage and distribution.
- Fixture reports without per-result `ruleVersion` produce missing coverage and warning.
- Fixture reports with mismatched per-result `ruleVersion` fail fast.

`make verify-v2-phase11` runs the existing Phase 7/8/9 CI-safe checks plus the Phase 11 rule version contract.

`make final-check` uses `verify-v2-phase11` as its V2 PaySim readiness component.

## 7. Limitations

- Phase 11 does not implement new PaySim-specific Java fraud rules.
- Phase 11 does not persist `ruleVersion` on `FraudResult`.
- Phase 11 does not create a DB detection result export.
- Full PaySim replay/evaluation remains local/manual.
- Fixture checks validate contract consistency, not production fraud model performance.
- `thresholdVersion` still belongs to evaluator threshold policy, not the Java Rule Engine version source.

## 8. Next Steps

- Persist or export per-result `ruleVersion` from app-consumer detection results.
- Add rule change examples once Rule Engine V2 patterns are implemented.
- Add before/after reports that compare rule changes separately from threshold changes.
- Keep full replay rejected event id export separate from bounded replay report summaries.
