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

Do not commit a production salt, local private salt, `.env` file, or report/manifest field that contains the salt value itself. Reports and manifests may record only `hashSaltSource`, `hashAlgorithm`, and `hashIdPrefixLength`.

## Phase Responsibilities

- V2 Phase 1: directory guardrails, `.gitignore`, documentation, and `check-data-policy.sh`
- V2 Phase 2: optional KaggleHub download helper and PaySim normalization script
- V2 Phase 3: validation, rejected rows, and sample generation
- V2 Phase 4: identifier hashing enforcement
- V2 Phase 5: replay pipeline

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
export PAYSIM_HASH_SALT="<local-private-salt-for-sample-regeneration>"
make prepare-paysim-smoke
make validate-paysim-strict
make generate-paysim-sample-strict
make data-policy-check
```

Generated sample files:

- `data/samples/paysim-events-sample.jsonl`
- `data/samples/paysim-labels-sample.jsonl`
- `data/samples/paysim-sample-manifest.json`

The sample manifest records dataset slug, raw filename, input SHA-256, sample counts, strategy, `hashAlgorithm`, `hashIdPrefixLength`, `hashSaltSource`, and replay collision notes. It must not contain raw identifiers or the salt value itself.

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
