# V2 PaySim Native Replay Contract

## 1. Purpose

V2 Phase 8 makes PaySim native transaction types explainable inside the replay and evaluation contract.

This phase is not a fraud detection performance improvement phase. It clarifies how PaySim native types enter or do not enter the evaluation denominator, which internal type they use for app-api replay, and which mapping policy version was applied.

Phase 7 evaluation evidence is only meaningful when the input type contract is explicit. A precision/recall number must be read together with the mapping policy that decided whether `CASH_OUT`, `CASH_IN`, or `DEBIT` was included, normalized, or excluded.

## 2. Problem

PaySim is a synthetic dataset with native transaction types:

- `PAYMENT`
- `TRANSFER`
- `CASH_OUT`
- `CASH_IN`
- `DEBIT`

The current application transaction enum is:

- `PAYMENT`
- `TRANSFER`
- `WITHDRAWAL`
- `DEPOSIT`

These sets are not identical. Treating every PaySim type as a production transaction type would overstate the domain semantics. Mapping every type to a nearby internal type can also distort evaluation results.

`CASH_OUT` is highly relevant to PaySim fraud patterns, but mapping it to `WITHDRAWAL` loses native context. `CASH_IN` is mostly inflow behavior and can affect false positive interpretation. `DEBIT` is currently too ambiguous for this project's transaction contract, so Phase 8 excludes it instead of silently treating it as low risk.

## 3. Type Mapping Policy

Mapping policy version:

```text
paysim-native-mapping-v1
```

| PaySim native type | Internal normalized type | Support level | Rule interpretation | Evaluation denominator | Excluded reason | Semantic loss risk | Next action |
|---|---|---|---|---|---|---|---|
| `PAYMENT` | `PAYMENT` | production-supported | normal payment behavior | included | - | low | keep |
| `TRANSFER` | `TRANSFER` | production-supported | transfer risk behavior | included | - | low | keep |
| `CASH_OUT` | `WITHDRAWAL` | replay-supported | cash-out mapped to withdrawal-like outflow for replay | included | - | high | review Rule V2 semantics before production use |
| `CASH_IN` | `DEPOSIT` | replay-supported | cash-in mapped to deposit-like inflow for replay | included | - | medium | review false positive impact before production use |
| `DEBIT` | - | unsupported | not interpreted by current transaction contract | excluded | `UNSUPPORTED_NATIVE_TYPE:DEBIT` | high | define separate debit semantics before inclusion |

Support levels:

- `production-supported`: current API and fraud rules already have a clear internal meaning for the normalized type.
- `replay-supported`: allowed for PaySim replay/evaluation after explicit mapping, but not claimed as production transaction semantics.
- `unsupported`: excluded or rejected explicitly. It must not fall through to default low risk.

## 4. API Contract Impact

Phase 8 does not add PaySim native values to the Java `TransactionEventType` enum.

The production API continues to receive the normalized type in `eventType`. PaySim-specific metadata stays in the normalized JSONL and replay/evaluation reports:

- `nativeEventType`
- `normalizedEventType`
- `typeSupportLevel`
- `typeMappingPolicyVersion`

The replay script drops these metadata fields before sending the app-api request, as it already does for other PaySim-only fields. This keeps the production API contract stable while making replay semantics auditable.

Replay validation recomputes the mapping from `nativeEventType`. A row is rejected if `eventType`, `normalizedEventType`, or `typeSupportLevel` does not match `paysim-native-mapping-v1`. This prevents an unsupported native type such as `DEBIT` from being accepted as a nearby normalized API type.

If any native mapping metadata is present, `typeMappingPolicyVersion` is mandatory and must equal `paysim-native-mapping-v1`. Legacy PaySim rows without mapping metadata are accepted for compatibility, but replay reports count them as `missingMappingMetadata` under `mappingMetadataPolicy=required_for_phase8_paysim_native_contract`.

## 5. Script Contract

Preprocessing:

- writes `eventType` as the internal normalized type
- writes `nativeEventType` as the original PaySim type
- writes `normalizedEventType`, `typeSupportLevel`, `typeMappingPolicyVersion`
- rejects unsupported native types with explicit reasons

Replay report:

- `mappingMetadataPolicy`
- `missingMappingMetadata`
- `mappingPolicyVersion`
- `mappingPolicyVersions`
- `inputNativeTypeDistribution`
- `inputNormalizedTypeDistribution`
- `acceptedNativeTypeDistribution`
- `acceptedNormalizedTypeDistribution`
- `rejectedNativeTypeDistribution`
- `rejectedNormalizedTypeDistribution`
- `excludedByType`

Evaluation report:

- propagates mapping fields from replay report when available
- propagates `mappingMetadataPolicy` and `replayMissingMappingMetadata`
- separates replay input scope from evaluation denominator scope
- records `replayNativeTypeDistribution`, `evaluatedNativeTypeDistribution`, and `excludedNativeTypeDistribution`
- records `ruleVersion=null` and `thresholdVersion=null` placeholders for later Rule V2 evidence
- keeps Phase 7 fields backward compatible
- keeps missing/excluded/rejected buckets separate from denominator metrics

## 6. Evaluation Impact

Type mapping can change the denominator.

Including `CASH_OUT` as `WITHDRAWAL` can increase fraud label coverage because PaySim fraud often appears around transfer/cash-out behavior. That does not mean the system understands real cash-out operations in a production banking sense.

Excluding `DEBIT` can change precision and recall because some labels are not evaluated. Reports must be read with:

- `mappingPolicyVersion`
- `replayNativeTypeDistribution`
- `evaluatedNativeTypeDistribution`
- `acceptedNormalizedTypeDistribution`
- `excludedByType`
- `totalLabels`
- `evaluatedEvents`
- `missingResults`

Phase 8 fixes `mappingPolicyVersion` and `evaluationContractVersion`. `ruleVersion` and `thresholdVersion` remain null until Rule V2 evidence fills them. Evaluation metrics are compared only with compatible mapping, evaluation contract, rule, and threshold versions.

## 7. Rule Semantics

Rules must not default unknown types to low risk.

Phase 8 keeps rule threshold tuning out of scope. The important rule contract is:

- production-supported normalized types can use existing rule semantics
- replay-supported types must remain visible through `nativeEventType` and mapping reports
- unsupported types are rejected or excluded before they can become low-risk events
- rule hit distribution should be reviewed together with native type distribution

## 8. Operational Gate

Gate candidates:

- `mappingPolicyVersion` exists
- `missingMappingMetadata` is reviewed
- replay input type distribution exists
- accepted/rejected type distribution exists
- evaluated denominator type distribution exists
- `excludedByType` exists
- unsupported type is not silently accepted
- Phase 7 evaluation report contract remains backward compatible
- raw/full PaySim data is not staged
- smoke fixture contract check passes

Gate candidates for CI:

```bash
make verify-v2-phase8
```

Local/manual evidence:

```bash
make v2-phase8-evidence
```

Gate deferred:

- production fraud performance
- full PaySim threshold tuning
- Kafka lag/p95 performance guarantees
- real financial transaction type equivalence
- ML baseline comparison

## 9. Limitations

PaySim native types are synthetic dataset types, not real banking API types.

Mapping native types to internal types introduces semantic loss. `CASH_OUT -> WITHDRAWAL` is useful for replay but should not be described as production cash-out support.

Replay-supported types are separate from production-supported types.

Phase 8 verifies contract semantics with fixtures. Full replay and detection result export remain local/manual work.

## 10. AI Review Notes

AI-generated shortcuts to avoid:

- adding all PaySim native types directly to the production enum
- mapping `CASH_OUT` to `TRANSFER` without documenting semantic loss
- treating unsupported types as low risk
- comparing Phase 7 and Phase 8 metrics without checking `mappingPolicyVersion`
- committing raw/full PaySim data or local exports

Human review focus:

- domain semantics
- unsupported type handling
- default low-risk anti-pattern
- metric denominator changes
- mapping policy versioning
- data policy guardrails

Verification checklist:

- data script tests
- native replay contract check
- evaluation report contract check
- data policy check
- README remains minimal
- implemented behavior is separated from planned follow-up work

## 11. Next Steps

- Rule V2 threshold tuning
- rule version and mapping version comparison reports
- full local replay evidence with exported detection results
- Grafana dashboard candidate for native type distribution
- DLQ/replay failure analysis by native type
- production API and replay API separation review
- model-based baseline comparison
