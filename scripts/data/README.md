# PaySim Data Scripts

This directory contains V2 PaySim data workflow helpers.

V2 Phase 2 adds local dataset acquisition and preprocessing normalization. V2 Phase 4 hardens identifier hashing and salt policy before replay is implemented. It does not implement replay, Java Rule Engine V2, schema changes, database migrations, or fraud action/case logic.

This repository is primarily a Java/Spring Boot project. Python is used only for V2 PaySim data acquisition and preprocessing helpers. To avoid global `pip install` and local environment drift, data scripts run inside a repository-local virtual environment at `.venv-data`.

Create or refresh the data Python environment with:

```bash
make data-env
```

The environment is generated from `scripts/data/requirements.txt`. Do not commit `.venv-data`.

`urllib3<2` is pinned for local macOS/Python compatibility observed during KaggleHub bootstrap with system Python SSL libraries. Revisit this constraint when upgrading KaggleHub or standardizing on a newer Python/OpenSSL runtime.

## Dataset Location

Download the Kaggle PaySim CSV manually or use the optional KaggleHub helper and place it here:

```text
data/raw/PS_20174392719_1491204439457_log.csv
```

Dataset page:

```text
https://www.kaggle.com/datasets/ealaxi/paysim1
```

Optional helper:

```bash
make download-paysim
DATA_VENV_DIR=.venv-data make download-paysim
```

The helper uses `kagglehub` to download `ealaxi/paysim1` into the local KaggleHub cache and copies the expected CSV into `data/raw`. `make download-paysim` runs `make data-env` first, so developers do not install KaggleHub with global `pip`.

Do not commit Kaggle tokens, API tokens, `.env`, or raw CSV files.

## Kaggle Authentication

`download_paysim_dataset.py` is a local-only helper.

Recommended options:

- OAuth: `kaggle auth login`
- API token: create a token from Kaggle account settings
- Legacy credentials: store `kaggle.json` under `~/.kaggle/kaggle.json`

Do not commit:

- `kaggle.json`
- `.env`
- access tokens
- `KAGGLE_API_TOKEN`
- downloaded raw CSV files

## Data Directory Policy

`data/raw` is for the original Kaggle CSV. Raw CSV files must not be committed.

`data/processed` is for full preprocessing outputs such as JSONL files and validation reports. Full processed outputs must not be committed because they can be large and can contain account-like identifiers or derived transaction flows.

`data/samples` is for small samples only. A sample may be committed only when all of these are true:

- It is one of `paysim-events-sample.jsonl`, `paysim-labels-sample.jsonl`, or `paysim-sample-manifest.json`.
- It is 100 to 1,000 rows or less.
- It does not contain raw `nameOrig` or `nameDest`.
- It uses hashed identifiers only.
- It is small enough for repository review; the current policy check fails samples larger than 1MB.

`data/samples` is not a place for the original Kaggle CSV.

Phase 3 does not generate CSV samples because CSV can accidentally preserve raw PaySim columns. The data policy check rejects CSV samples, arbitrary JSONL samples, generic JSON samples, full/processed sample names, and sample files larger than 1MB.

`check-data-policy.sh` also performs lightweight grep-based sample content scans against staged files. This is a Git commit guardrail for common mistakes, not a full JSON schema validator. Structural validation remains the responsibility of `validate_paysim_outputs.py` and `generate_paysim_samples.py`.

Phase 4 additionally blocks a committed `data/samples/paysim-sample-manifest.json` that uses `hashSaltSource=default-local` or salt value fields such as `hashSaltValue`, `saltValue`, `salt`, or `rawSalt`. The field name `hashSaltSource` is allowed because it records provenance without exposing the secret.

## Identifier Hashing

PaySim is synthetic, but `nameOrig` and `nameDest` look like account identifiers. Treat them as sensitive for repository and logging purposes.

The preprocessing script hashes identifiers before writing replayable events. V2 Phase 4 treats this as a contract, not a best-effort transformation:

```text
algorithm = HMAC-SHA256
hashIdPrefixLength = 16 lowercase hex characters
userId = U-{hmac_sha256(raw=nameOrig, key=salt).substring(0, 16)}
accountId = A-{hmac_sha256(raw=nameOrig, key=salt).substring(0, 16)}
destinationAccountId = D-{hmac_sha256(raw=nameDest, key=salt).substring(0, 16)}
```

Use the `PAYSIM_HASH_SALT` environment variable for shared or committed sample regeneration. `--hash-salt` exists for local reproducibility, but it can be captured in shell history. `default-local` salt is allowed only for local smoke/debug output.

Use `--hash-salt` only for local reproducibility checks. For shared or committed sample regeneration, prefer `PAYSIM_HASH_SALT` so the salt value is not captured in shell history.

Do not commit a production salt, local private salt, `.env` file, or report/manifest field that contains the salt value itself. Reports and manifests may record only `hashSaltSource`, `hashAlgorithm`, and `hashIdPrefixLength`.

## Phase Responsibilities

- V2 Phase 1: directory guardrails, `.gitignore`, documentation, and `check-data-policy.sh`
- V2 Phase 2: optional KaggleHub download helper and PaySim normalization script
- V2 Phase 3: validation, rejected rows, and sample generation
- V2 Phase 4: identifier hashing enforcement
- V2 Phase 5: replay pipeline
- V2 Phase 6: replay result evaluation baseline
- V2 Phase 7: evaluation evidence command alias and CI-safe verification checks
- V2 Phase 8: PaySim native type replay contract, mapping policy metadata, and CI-safe native contract check
- V2 Phase 9: rule/threshold/evaluation policy versioning and fixture-based regression check

## Preprocessing

Run a limited local smoke conversion after the raw CSV exists:

```bash
make prepare-paysim-smoke
```

Run the full conversion locally:

```bash
make prepare-paysim
```

Generated full outputs are written under `data/processed` and must not be committed:

- `paysim-events.jsonl`
- `paysim-labels.jsonl`
- `paysim-rejected.jsonl`
- `paysim-validation-report.json`

The preprocessing script streams the CSV with Python `csv.DictReader`. It does not load the full file into memory.

Runtime events never include `isFraud`, `isFlaggedFraud`, `nameOrig`, `nameDest`, or `receivedAt`. Labels are written only to the sidecar file and joined by `eventId`.

`--limit` only limits output row processing. The script still computes SHA-256 for the full raw input file so the validation report keeps file-level provenance.

Phase 2 writes output files directly. If `fail-fast` stops during processing, partial files can remain under `data/processed`. Do not replay processed output until Phase 5 adds replay-specific safety checks. Atomic temp-file writes are a Phase 5 follow-up.

Phase 3 sample generation must not use the `default-local` salt for committed samples. Use `PAYSIM_HASH_SALT` or an explicit `--hash-salt`, and never write the salt value to reports or manifests.
Phase 4 enforces this more strongly with `--require-non-default-salt`, `make validate-paysim-strict`, `make generate-paysim-sample-strict`, fixture tests, and data policy checks against committed sample manifests.

V2 Phase 4 changes the validation report contract. Reports must include `hashAlgorithm`, `hashIdPrefixLength`, and `hashSaltSource`. If `data/processed/*` was generated during V2 Phase 2 or V2 Phase 3, regenerate it before running validation:

```bash
make prepare-paysim-smoke
```

For full local output, rerun the preprocessing script with `--force` through the data venv:

```bash
.venv-data/bin/python scripts/data/prepare_paysim_dataset.py --force
```

## Validation and Sampling

Validate processed outputs after preprocessing:

```bash
make validate-paysim
```

For shared or committed processed outputs, require a non-default salt source:

```bash
make validate-paysim-strict
```

The validator checks:

- event/label/rejected/report count consistency
- eventId join consistency between runtime events and label sidecar
- label leakage in runtime events
- raw `nameOrig`, `nameDest`, and PaySim identifier pattern leakage
- `userId`, `accountId`, and `destinationAccountId` hash ID format
- report `hashAlgorithm=HMAC-SHA256`, `hashIdPrefixLength=16`, and `hashSaltSource`
- optional non-default salt policy when `--require-non-default-salt` is used
- rejected reason allowlist
- reject ratio threshold
- report provenance and count fields

Generate commit-safe JSONL samples after validation:

```bash
make generate-paysim-sample
```

For shared or committed samples, prefer strict salt-source enforcement:

```bash
make generate-paysim-sample-strict
```

Strict local regeneration example:

```bash
export PAYSIM_HASH_SALT="replace-with-local-private-salt"
make prepare-paysim-smoke
make validate-paysim-strict
make generate-paysim-sample-strict
make data-policy-check
```

Do not paste the actual salt value into Git, documentation, PR descriptions, terminal logs, or shared chat.

`--require-non-default-salt` blocks `default-local`; it does not validate salt entropy, age, rotation, or secret-manager storage. Keep those as separate operational controls.

Generated sample files:

- `data/samples/paysim-events-sample.jsonl`
- `data/samples/paysim-labels-sample.jsonl`
- `data/samples/paysim-sample-manifest.json`

The sample manifest records dataset slug, raw filename, input SHA-256, sample counts, strategy, `hashAlgorithm`, `hashIdPrefixLength`, `hashSaltSource`, generation/policy phase metadata, and replay collision notes. It must not contain raw identifiers or the salt value itself.

Committed samples prioritize not exposing raw identifiers or salt values. Without the same private salt, byte-for-byte regeneration of the pseudonymized sample is not guaranteed; reproducibility is described through `sourceInputSha256`, the sample manifest, and the generation/validation scripts.

CI runs fixture-based data script tests only. Full validation and sample generation require local `data/processed` output and are not part of the default CI path.

Test the data scripts without the real Kaggle CSV:

```bash
make test-data-scripts
```

`make prepare-paysim`, `make prepare-paysim-smoke`, and `make test-data-scripts` use `.venv-data/bin/python`. CI runs the fixture-based data script tests and data policy check, but does not download the Kaggle dataset or run full preprocessing.

## Policy Check

Run:

```bash
make data-policy-check
```

The check scans files tracked or staged under `data/` and fails if raw or full processed data is about to be committed.

## Replay

V2 Phase 5 adds `scripts/data/replay_paysim_events.py` to replay normalized runtime events into app-api.

Dry-run validates replay payloads and writes a report without HTTP requests:

```bash
make replay-paysim-sample-dry-run
```

Actual sample replay requires local infrastructure and app-api to be running:

```bash
make replay-paysim-sample
```

Processed-output smoke replay uses local full processed events and is not part of CI:

```bash
make replay-paysim-processed-smoke
```

Replay rules:

- Input is events JSONL only, normally `data/samples/paysim-events-sample.jsonl` or `data/processed/paysim-events.jsonl`.
- If replay input JSONL itself cannot be parsed, replay fails as input corruption. Row-level `payloadRejected` is only for parsed JSON object rows that violate the replay contract.
- Labels JSONL is not a replay input. It is reserved for evaluation joins.
- Replay payloads never include `isFraud`, `isFlaggedFraud`, `sourceFlaggedFraud`, `nameOrig`, `nameDest`, or `receivedAt`.
- `X-Trace-Id` is populated from the PaySim event `traceId`.
- Fields not accepted by the current app-api DTO, such as `balanceFeatures`, `source`, `schemaVersion`, and `destinationAccountId`, are omitted and counted in `droppedFields`.
- Phase 8 preprocessing writes `eventType` as the internal normalized type and preserves the original PaySim type in `nativeEventType`.
- Phase 8 mapping metadata fields are `nativeEventType`, `normalizedEventType`, `typeSupportLevel`, and `typeMappingPolicyVersion`.
- Mapping policy `paysim-native-mapping-v1` keeps `PAYMENT` and `TRANSFER` as production-supported, maps `CASH_OUT -> WITHDRAWAL` and `CASH_IN -> DEPOSIT` as replay-supported, and rejects `DEBIT` as unsupported.
- Replay validation recomputes the mapping from `nativeEventType` and rejects mismatched `eventType`, `normalizedEventType`, or `typeSupportLevel`. For example, `nativeEventType=DEBIT` cannot be smuggled into the API as `eventType=DEPOSIT`.
- If any native mapping metadata is present, `typeMappingPolicyVersion` is required and must equal `paysim-native-mapping-v1`.
- Legacy PaySim rows without mapping metadata are still accepted for compatibility, but replay reports count them in `missingMappingMetadata` under `mappingMetadataPolicy=required_for_phase8_paysim_native_contract`.
- The default `--event-type-policy current-api` validates that the HTTP body only uses current app-api enum values. Unsupported values are rejected before HTTP requests are sent.
- `--event-type-policy preserve` is dry-run only and reserved for contract inspection. It must not be used against current app-api actual replay.
- `idempotency-mode=preserve` keeps original `eventId` values and is useful for duplicate/idempotency checks.
- `idempotency-mode=prefix` requires `--event-id-prefix` and avoids collisions when mixing datasets or replaying into the same API/database repeatedly.
- `--rate-per-second` limits request pace; avoid unbounded replay against local Kafka/PostgreSQL.
- `--retry-count` retries timeout and 5xx attempts. Connection errors are not retried unless `--retry-connection-error` is explicitly set.
- Final outcome counters such as `httpSuccess`, `timeout`, and `connectionError` are event-level final outcomes. Retry attempt details are recorded separately as `retryTimeoutAttempts`, `retryServerErrorAttempts`, and `retryConnectionErrorAttempts`.
- `--auth-token` and `--auth-token-env` can send an `Authorization: Bearer ...` header for non-default deployments. The normal local transaction ingest API does not require it, and token values are not written to reports.
- Replay reports are written under `data/processed`, defaulting to `data/processed/paysim-replay-report.json`, and must not be committed.
- Reports store counts and sampled eventIds only. They do not store request bodies, response bodies, tokens, labels, or raw identifiers.
- Phase 2/3 validation is dataset-oriented, while Phase 5 replay validation follows the current app-api request contract. For example, an amount that normalized successfully can still be rejected during replay if the app-api contract disallows it.

Direct dry-run example:

```bash
.venv-data/bin/python scripts/data/replay_paysim_events.py \
  --input data/samples/paysim-events-sample.jsonl \
  --max-events 100 \
  --dry-run \
  --force
```

## Replay Result Evaluation

V2 Phase 6 adds `scripts/data/evaluate_paysim_replay_results.py` to compare a local detection result export with the PaySim label sidecar.

Local sample evaluation requires a detection result export under `data/processed`:

```bash
make evaluate-paysim-sample
make evaluate-paysim-replay
```

`make evaluate-paysim-replay` evaluates an existing PaySim label sidecar and local detection result export. It does not run app-api replay by itself.

The default Make target runs with `--strict`, so duplicate label/result eventIds, unsupported risk levels, label leakage, raw identifier leakage, and invalid label sidecar metadata fail the evaluation.

If no replay report is available, evaluate labels and results only:

```bash
make evaluate-paysim-sample-no-replay-report
```

`make evaluate-paysim-sample` passes `--replay-report`; that file must exist or evaluation fails. Use the no-replay target when intentionally evaluating labels and results without replay rejected exclusion.

For Phase 7 evidence checks that do not require full PaySim raw data, local DB exports, or actual app-api replay:

```bash
make verify-v2-phase7
```

This target runs fixture-based data script tests, data policy checks, and `verify_paysim_evaluation_report_contract.py`, which creates a temporary report and verifies required Phase 7 report fields.

For Phase 8 native type replay contract checks that do not require full PaySim raw data, local DB exports, or actual app-api replay:

```bash
make verify-paysim-native-replay-contract
make verify-v2-phase8
```

`make verify-paysim-native-replay-contract` creates temporary fixture events and verifies that mapping metadata, input/accepted/rejected type distributions, unsupported-type exclusion, evaluated denominator type distribution, and evaluation report propagation remain intact.

`make verify-v2-phase8` runs fixture data tests, data policy checks, Phase 7 evaluation report contract verification, and the Phase 8 native replay contract verification.

For Phase 9 rule/threshold regression checks that do not require full PaySim raw data, local DB exports, or actual app-api replay:

```bash
make verify-paysim-rule-threshold-regression
make verify-v2-phase9
```

`make verify-paysim-rule-threshold-regression` verifies fixture metrics, rule/threshold/evaluation policy versions, action workload summary, missing-result policy, and Phase 8 native type compatibility.

Local/manual Phase 8 evidence uses an existing detection result export and replay report:

```bash
make evaluate-paysim-native-replay
make evaluate-paysim-threshold-policy-report
make v2-phase8-evidence
make v2-phase9-evidence
```

These commands do not create the DB export by themselves. They require local `data/processed/paysim-detection-results.jsonl` and relevant replay/evaluation inputs.

Detection result export contract:

```json
{"eventId": "paysim-000000001", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": [], "detectedAt": "2026-01-01T00:00:01Z"}
```

Evaluation rules:

- Labels JSONL is evaluation input only. It is never replay payload.
- Join key is `eventId`. If replay used an eventId prefix, pass `--event-id-prefix` so detection result ids can be normalized back to original PaySim ids.
- The selected `thresholdVersion` is the source of truth for fraud-positive and action fallback decisions. `--positive-risk-level` is a legacy compatibility option and must match `thresholdPolicy.positiveRiskLevelFallback` when provided.
- Missing detection results are excluded from denominator metrics by default. The report records `missingResultTreatment=missing_results_excluded_from_denominator`.
- Use `--include-missing-results` only for explicit sensitivity checks. In that mode, fraud labels without a result count as FN; non-fraud labels without a result count as TN and increment `missingResults`.
- With a replay report, pre-HTTP payload rejects recorded in the bounded `failures` summary are excluded from the denominator by default. The report records `replayReportUsed`, `replayPayloadRejected`, `replayRejectedEventIdsAvailable`, and `replayRejectedExclusionComplete`; if `payloadRejected` is greater than available rejected eventIds, the evaluation report warns that the denominator may still include replay-rejected events.
- Detection results that do not match any label eventId are not used in metrics. The report records `matchedResults` and `unmatchedResults`, and warns when result ids appear mismatched.
- Evaluation reports are written under `data/processed`, defaulting to `data/processed/paysim-evaluation-report.json`, and must not be committed.
- Reports store counts, metrics, distributions, warnings, and at most 10 sample eventIds. They do not store raw identifiers, label/result payload dumps, request/response bodies, or tokens.
- Phase 8 separates replay input type distribution from evaluation denominator type distribution. `replayNativeTypeDistribution` is replay-report input scope, while `evaluatedNativeTypeDistribution` is label/result denominator scope.
- Evaluation reports propagate `mappingMetadataPolicy` and `replayMissingMappingMetadata` from replay reports.
- Phase 9 fills `ruleVersion`, `thresholdVersion`, `evaluationPolicyVersion`, `thresholdPolicy`, `riskScoreCoverage`, `thresholdRegressionReliability`, `reviewCandidateEvents`, `reviewCandidateRate`, `blockedCandidateEvents`, `blockedCandidateRate`, `actionDecisionDistribution`, and `operatorWorkloadSummary`.
- Current `ruleVersion` is an evaluation evidence policy value. Direct app-consumer Rule Engine version integration remains a follow-up.
- `totalFraudLabels` is the full fraud count in the label sidecar. `evaluatedFraudLabeledEvents` is the fraud count inside the evaluation denominator after replay-rejected and missing-result policy is applied.
- `missedFraudEvents` is denominator-scoped. Missing fraud labels excluded by default are counted in `missingFraudLabels`, not in `missedFraudEvents`.
- `misclassifiedEvents` means `FP + FN`. `unmatchedResultEvents` means result rows that do not join to a label. `failedRecords` and `invalidRecords` are reserved for future non-fatal pipeline/schema failures and remain separate from detection quality mismatches. Phase 7 invalid input fails fast before report generation.
- CI runs fixture tests only. Actual DB/API export evaluation is local-only and requires a prepared `data/processed/paysim-detection-results.jsonl`.
- Phase 8 mapping report fields are contract metadata. They explain the denominator and type semantics; they are not production fraud performance guarantees.
