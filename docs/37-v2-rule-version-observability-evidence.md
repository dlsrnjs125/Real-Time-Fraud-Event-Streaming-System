# V2 Runtime Rule Version Observability Evidence

## 1. Purpose

V2 Phase 13 extends Phase 12 per-result `ruleVersion` propagation into runtime and admin observability.

The goal is operational traceability, not fraud detection performance improvement. Operators need to distinguish the rule version currently active in app-consumer from the rule version stored on historical detection results.

README remains minimal. Detailed commands, endpoint contracts, troubleshooting, and verification records stay in docs, blog drafts, and `scripts/data/README.md`.

## 2. Problem

Persisting `ruleVersion` on a detection result is useful, but it does not answer every operational question.

- app-consumer runtime may be running a different active `ruleVersion` from older stored results.
- stored result `ruleVersion` represents the version used when the result was created.
- rule deployment windows can produce mixed stored result versions.
- high-cardinality metric tags such as `eventId`, `userId`, `transactionId`, and `traceId` can damage Prometheus cardinality.
- expanding admin or actuator endpoints without scope control increases operational exposure.

Phase 13 keeps app-consumer runtime metadata and app-api stored-result queries separate.

## 3. Completed Scope

- app-consumer exposes active rule version metadata through Actuator info.
- `RuleVersionInfoContributor` reports `activeRuleVersion`, `versionSource`, and `scope`.
- Unit tests verify Actuator info payload values and absence of high-cardinality identifiers.
- app-api adds an admin ruleVersion summary endpoint for stored fraud detection results.
- The summary endpoint counts non-null stored `ruleVersion` values and reports legacy null rows separately.
- Existing event fraud result response keeps nullable `ruleVersion` for single-result traceability.
- `verify-v2-phase13` is added as a CI-safe aggregate alias for existing V2 data verifiers.
- `final-check` now points at `verify-v2-phase13`.

Not implemented:

- ruleVersion list filtering on the existing `/fraud-results` list stub
- Grafana panel or Prometheus ruleVersion metric
- deployment changelog or rollback automation
- historical `rule_version` backfill

## 4. Runtime vs Historical Result Semantics

| Source | Meaning | Owner | Example | Caveat |
|---|---|---|---|---|
| app-consumer `activeRuleVersion` | currently running Rule Engine baseline | app-consumer | `rule-v2-baseline-v1` | describes current runtime, not old rows |
| `fraud_detection_results.rule_version` | ruleVersion used when a result was created | app-consumer write, app-api read | `rule-v2-baseline-v1` | legacy rows may be null |
| PaySim evaluation report `ruleVersion` | evaluation contract-level ruleVersion | scripts/data | `rule-v2-baseline-v1` | not a production performance guarantee |
| `thresholdVersion` | evaluation threshold policy | scripts/data | `threshold-v1` | independent from `ruleVersion` |

## 5. API / Actuator Contract

app-consumer local/manual runtime check:

```bash
curl http://localhost:8081/actuator/info
```

Expected detail shape:

```json
{
  "fraudRule": {
    "activeRuleVersion": "rule-v2-baseline-v1",
    "versionSource": "app-consumer",
    "scope": "fraud-rule-engine-baseline"
  }
}
```

app-api stored result detail:

```bash
curl -H "X-Admin-Token: <local-admin-token>" \
  http://localhost:8080/api/v1/admin/events/{eventId}/fraud-result
```

`ruleVersion` is nullable for legacy rows.

app-api stored result ruleVersion summary:

```bash
curl -H "X-Admin-Token: <local-admin-token>" \
  http://localhost:8080/api/v1/admin/fraud-results/rule-version-summary
```

Response shape:

```json
{
  "resultCounts": {
    "rule-v2-baseline-v1": 120
  },
  "legacyMissingResults": 3
}
```

Admin APIs require the existing local admin token filter. These curl commands are local/manual checks and are not part of CI-safe verification.

## 6. Metrics Cardinality Decision

No new ruleVersion metric is added in Phase 13.

Decision:

- `ruleVersion` is bounded and may be a future metric tag candidate.
- `riskLevel` and `decision` are also bounded if a future result counter needs them.
- `eventId`, `userId`, `accountId`, `deviceId`, `transactionId`, and `traceId` must not be metric tags.
- Actuator info and admin summary are enough for this phase's traceability evidence.

Future metric candidate:

```text
fraud_rule_active_info{rule_version="rule-v2-baseline-v1"} 1
```

Only add it if dashboard or alerting requirements need it.

## 7. Verification Matrix

| Command | Scope | CI-safe | Requires local infra | Requires auth | Pass Criteria |
|---|---|---:|---:|---:|---|
| `./gradlew test` | Java API/consumer unit and slice tests | Yes | No | No | Actuator info and admin summary tests pass |
| `make verify-v2-phase13` | V2 data/evaluation contract guardrails | Yes | No | No | Phase 7/8/9/11/12 verifiers pass; Java runtime/admin contracts are covered by Gradle tests |
| `make final-check` | representative repository readiness | Yes | No | No | build, Docker config, scripts, Gradle tests, and V2 Phase 13 checks pass |
| `curl http://localhost:8081/actuator/info` | local runtime active ruleVersion check | No | Yes | No | `fraudRule.activeRuleVersion` is visible |
| `curl /api/v1/admin/fraud-results/rule-version-summary` | local stored result ruleVersion summary check | No | Yes | Yes | counts non-null versions and legacy null rows |

## 8. Failure Modes

- active `ruleVersion` is not exposed, so current consumer baseline is unclear
- active `ruleVersion` and stored result `ruleVersion` are treated as the same meaning
- legacy null `ruleVersion` rows are interpreted as a new-result propagation failure
- summary or filter changes break existing admin query behavior
- high-cardinality values are added as metric tags
- actuator endpoints are over-exposed beyond local operational needs
- app-consumer starts with JPA validate before app-api Flyway migrations are applied

## 9. Limitations

- Runtime active ruleVersion exposure is not a deployment audit log.
- Stored result ruleVersion is traceability evidence, not fraud performance evidence.
- Legacy rows may have null `ruleVersion`.
- RuleVersion list filtering remains future work because the current fraud result list API is still a stub.
- Grafana panels and alerts remain future work.
- Admin security follows the existing local-token development scope.

## 10. Next Steps

- Add rule deployment changelog.
- Add ruleVersion filter when the fraud result list API becomes a real query.
- Add dashboard panel for active/stored ruleVersion distribution.
- Add alert on unexpected active ruleVersion after deployment.
- Define historical ruleVersion backfill policy.
- Document rollback checks for rule deployment.
