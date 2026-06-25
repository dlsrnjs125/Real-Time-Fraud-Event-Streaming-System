# PaySim Data Scripts

This directory contains V2 PaySim data workflow helpers.

V2 Phase 2 adds local dataset acquisition and preprocessing normalization. It does not implement sampling, replay, Java Rule Engine V2, schema changes, database migrations, or fraud action/case logic.

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
python3 scripts/data/download_paysim_dataset.py
python3 scripts/data/download_paysim_dataset.py --force
```

The helper uses `kagglehub` to download `ealaxi/paysim1` into the local KaggleHub cache and copies the expected CSV into `data/raw`. Install the optional dependency when needed:

```bash
pip install kagglehub
```

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

- It is a `.jsonl` or `.csv` file.
- It is 100 to 1,000 rows or less.
- It does not contain raw `nameOrig` or `nameDest`.
- It uses hashed identifiers only.
- It is small enough for repository review; the current policy check fails samples larger than 1MB.

`data/samples` is not a place for the original Kaggle CSV.

## Identifier Hashing

PaySim is synthetic, but `nameOrig` and `nameDest` look like account identifiers. Treat them as sensitive for repository and logging purposes.

The preprocessing script hashes identifiers before writing replayable events:

```text
userId = U-{hmac_sha256(raw=nameOrig, key=salt).substring(0, 16)}
accountId = A-{hmac_sha256(raw=nameOrig, key=salt).substring(0, 16)}
destinationAccountId = D-{hmac_sha256(raw=nameDest, key=salt).substring(0, 16)}
```

Do not commit a production salt or `.env` file.

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

Test the data scripts without the real Kaggle CSV:

```bash
make test-data-scripts
```

## Policy Check

Run:

```bash
make data-policy-check
```

The check scans files tracked or staged under `data/` and fails if raw or full processed data is about to be committed.
