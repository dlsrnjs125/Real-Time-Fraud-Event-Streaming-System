# V2 Rule Version Change Runbook

## 1. Purpose

V2 Phase 14 defines the operating checks for a `ruleVersion` change.

This phase is V2 Phase 14, separate from the core streaming pipeline Phase 14 already documented elsewhere in the repository. It is about change management, rollback readiness, and evidence capture for Rule Engine version changes.

It does not improve fraud detection performance, tune thresholds, train a model, implement automatic rollback, or complete deployment automation. It connects the Phase 11 ruleVersion contract, Phase 12 per-result propagation, and Phase 13 runtime/admin observability into a practical runbook.

## 2. Scope

Completed in this phase:

- active ruleVersion source confirmation through app-consumer runtime metadata
- per-result ruleVersion propagation and strict-mode evaluator checks as pre-change evidence
- app-consumer Actuator info check procedure
- app-api stored ruleVersion summary check procedure
- CI-safe contract checks and local/manual runtime drill separation
- rollback and release hold decision criteria
- evidence template for manual change records

Not completed in this phase:

- automatic rollback
- rule deployment automation
- persisted rule deployment changelog
- Grafana dashboard or production alerting
- historical `rule_version` backfill automation
- full PaySim local evidence automation
- new fraud rules, threshold tuning, or ML model training

## 3. Pre-change Checklist

Before changing `FraudRuleVersions.ACTIVE_RULE_VERSION`, record:

- expected new `ruleVersion`
- whether the change is rule logic, threshold boundary, or both
- if rule logic changes, `ruleVersion` must change
- if only threshold boundary changes, `thresholdVersion` must change instead
- if rule logic and threshold boundary change in one PR, document the reason and expected metric interpretation impact
- Java/Python ruleVersion contract check passes
- per-result ruleVersion strict-mode fixture passes
- app-api Flyway migration state is compatible with app-consumer JPA validate
- app-consumer expects the API-owned `rule_version` migration to be applied before startup
- raw/full PaySim data, local DB exports, and large local reports are not staged
- `make final-check` passes
- docs/blog wording separates implemented behavior from future work

CI-safe pre-change commands:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/paysim-pycache .venv-data/bin/python -m py_compile scripts/data/*.py
make test-data-scripts
make data-policy-check
make verify-paysim-rule-version-contract
make verify-paysim-result-rule-version-contract
make verify-v2-phase13
./gradlew test
make final-check
```

`make verify-v2-phase13` is a V2 data/evaluation guardrail alias. It does not start local apps and does not run actuator/admin curl checks.

## 4. Post-change Checklist

After deploying or locally starting the changed app-consumer, record:

- app-consumer `/actuator/info` exposes expected `fraudRule.activeRuleVersion`
- `fraudRule.versionSource` remains `app-consumer`
- `fraudRule.scope` remains bounded to the Rule Engine baseline
- app-api stored result detail exposes expected `ruleVersion` for newly generated detection results
- app-api stored ruleVersion summary contains expected non-null version counts
- legacy null rows are separated from newly generated missing-version rows
- no unexpected stored `ruleVersion` appears in the summary
- PaySim evaluation report `ruleVersion` matches Java active ruleVersion when evaluation evidence is generated
- `thresholdVersion` remains independent from `ruleVersion`
- `make final-check` passes after the change
- local/manual commands that were not run are explicitly marked as not run

Local/manual runtime checks:

```bash
curl http://localhost:8081/actuator/info
```

Expected shape:

```json
{
  "fraudRule": {
    "activeRuleVersion": "rule-v2-baseline-v1",
    "versionSource": "app-consumer",
    "scope": "fraud-rule-engine-baseline"
  }
}
```

Stored result summary:

```bash
curl -H "X-Admin-Token: <local-admin-token>" \
  http://localhost:8080/api/v1/admin/fraud-results/rule-version-summary
```

Admin APIs require the existing local admin token. These curl checks require local app startup and are not CI-safe.

Actuator info exposure is intended for local/internal operational checks. Do not expose it publicly without network-level controls or Spring Security hardening.

## 5. Rollback / Hold Decision Criteria

Hold the release or prepare rollback when any of these occur:

- Java active ruleVersion and Python evaluator `RULE_VERSION` mismatch
- newly generated detection result misses `ruleVersion`
- stored result summary shows an unexpected `ruleVersion`
- app-consumer Actuator info does not expose expected `fraudRule.activeRuleVersion`
- app-api migration is not applied and app-consumer JPA validate fails
- admin summary endpoint is too slow or all-time group by creates unacceptable DB load
- `make final-check` fails
- strict per-result ruleVersion mode fails on missing values for new result evidence
- ruleVersion and thresholdVersion changed together without documented reason and expected impact
- local/manual curl evidence is claimed without actually running the local drill

Rollback readiness in this phase means decision criteria and evidence capture are defined. It does not mean automatic rollback has been implemented.

## 6. Runtime vs Historical Semantics

| Source | Meaning | Normal Mixed State | Investigate When |
|---|---|---|---|
| app-consumer `activeRuleVersion` | current runtime Rule Engine baseline | after deployment, active version may differ from old stored rows | expected version is missing or wrong |
| stored result `ruleVersion` | version used when each result was created | old and new versions may coexist during deployment windows | unexpected version appears or new rows omit version |
| PaySim evaluation `ruleVersion` | evaluator contract-level version | should match intended Java baseline for evidence runs | Java/Python drift verifier fails |
| `thresholdVersion` | evaluation decision boundary | may remain unchanged during rule logic changes | threshold and rule changes are mixed without explanation |

Active runtime version and stored historical result version are intentionally different evidence dimensions. Mixed stored versions immediately after a rule deployment can be normal. Unexpected versions or missing version on new rows require investigation.

## 7. Evidence Template

Copy this template into a PR comment, release note, or local evidence record.

```text
Date:
Branch / Commit:
Expected activeRuleVersion:
Change type: rule logic / threshold boundary / both

Pre-check commands:
- py_compile:
- make test-data-scripts:
- make data-policy-check:
- make verify-paysim-rule-version-contract:
- make verify-paysim-result-rule-version-contract:
- make verify-v2-phase13:
- ./gradlew test:
- make final-check:

Post-check commands:
- app-consumer /actuator/info:
- app-api rule-version-summary:
- new detection result detail:

Actuator info result:
Stored result summary:
Legacy null rows:
Unexpected versions:
Strict per-result ruleVersion result:

Decision: proceed / hold / rollback
Reason:
Follow-up:
```

## 8. CI-safe vs Local/manual Boundary

CI-safe checks:

- `PYTHONPYCACHEPREFIX=/private/tmp/paysim-pycache .venv-data/bin/python -m py_compile scripts/data/*.py`
- `make test-data-scripts`
- `make data-policy-check`
- `make verify-paysim-rule-version-contract`
- `make verify-paysim-result-rule-version-contract`
- `make verify-v2-phase13`
- `./gradlew test`
- `make final-check`

Local/manual checks:

- app-consumer `/actuator/info`
- app-api `/api/v1/admin/fraud-results/rule-version-summary`
- app-api single detection result detail lookup
- full PaySim replay/evaluation with local detection result export
- raw/full PaySim preprocessing or validation

Local/manual checks are useful evidence, but they are not part of `final-check` because they may require local apps, network access, admin token, raw PaySim data, or DB exports.

## 9. Summary Endpoint Cost Boundary

The current ruleVersion summary endpoint is all-time local/admin traceability evidence. It should not be used as a high-volume production dashboard query without additional design.

Before dashboard or alert usage, add:

- bounded time range parameters, such as `from` and `to`
- an index candidate such as `(rule_version, detected_at)`
- expected latency and query cost measurements
- security review for admin endpoint exposure

## 10. Limitations

- This runbook is not automatic rollback.
- Actuator and admin checks are local/internal by default.
- Production exposure requires network controls or Spring Security hardening.
- All-time ruleVersion summary is not ready as a production dashboard source.
- Bounded summary query, index strategy, deployment changelog, and alerting remain future work.
- RuleVersion traceability is not a fraud performance guarantee.
- Historical rows may have null `rule_version`.

## 11. Next Steps

- rule deployment changelog
- ruleVersion summary time range filter
- `(rule_version, detected_at)` index review
- admin ruleVersion filter on the real fraud result list query
- Grafana dashboard for active/stored ruleVersion
- unexpected ruleVersion alert
- rollback automation
- final V2 evidence summary
