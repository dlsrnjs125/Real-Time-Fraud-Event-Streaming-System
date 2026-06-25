# PaySim Data Scripts

This directory is reserved for V2 PaySim data workflow helpers.

V2 Phase 1 only adds directory, Git ignore, documentation, and data policy check guardrails. It does not implement preprocessing, sampling, replay, Java Rule Engine V2, schema changes, database migrations, or fraud action/case logic.

## Dataset Location

Download the Kaggle PaySim CSV manually and place it here:

```text
data/raw/PS_20174392719_1491204439457_log.csv
```

Dataset page:

```text
https://www.kaggle.com/datasets/ealaxi/paysim1
```

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

Future preprocessing phases should hash identifiers before writing replayable events or samples:

```text
userId = U-{sha256(nameOrig + salt).substring(0, 16)}
accountId = A-{sha256(nameOrig + salt).substring(0, 16)}
destinationAccountId = D-{sha256(nameDest + salt).substring(0, 16)}
```

Do not commit a production salt or `.env` file.

## Phase Responsibilities

- V2 Phase 1: directory guardrails, `.gitignore`, documentation, and `check-data-policy.sh`
- V2 Phase 2: PaySim normalization script
- V2 Phase 3: validation, rejected rows, and sample generation
- V2 Phase 4: identifier hashing enforcement
- V2 Phase 5: replay pipeline

## Policy Check

Run:

```bash
make data-policy-check
```

The check scans files tracked or staged under `data/` and fails if raw or full processed data is about to be committed.
