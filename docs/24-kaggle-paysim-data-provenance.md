# Kaggle PaySim Data Provenance

## 1. Purpose

V2에서는 실제 개인정보가 포함된 금융 데이터를 사용하지 않고, Kaggle PaySim synthetic 금융 거래 데이터를 재현 가능한 방식으로 연동합니다.

목표는 다음과 같습니다.

- 원본 데이터 미커밋 원칙을 유지합니다.
- 누구나 같은 Kaggle dataset을 내려받아 같은 전처리 절차를 재현할 수 있게 합니다.
- PaySim 데이터를 현재 Kafka 기반 transaction event 흐름으로 replay할 수 있게 설계합니다.
- Rule 기반 탐지 결과를 PaySim fraud label과 비교할 수 있는 평가 기준을 남깁니다.

## 2. Dataset

- Dataset name: Synthetic Financial Datasets For Fraud Detection
- Common name: PaySim
- Source: Kaggle
- Dataset URL: `https://www.kaggle.com/datasets/ealaxi/paysim1`
- Original file name: `PS_20174392719_1491204439457_log.csv`
- Type: synthetic mobile money transaction dataset

PaySim은 실제 모바일 머니 서비스 로그 샘플을 기반으로 생성된 synthetic dataset으로 설명됩니다. 따라서 본 프로젝트는 실제 고객 개인정보나 실제 금융 원장을 사용하지 않습니다.

다만 user/account처럼 보이는 식별자, 금액, 거래 흐름, fraud label이 포함되므로 운영 시스템 설계 관점에서는 민감 데이터처럼 취급합니다.

## 3. Why Not Commit Raw Data

원본 Kaggle CSV는 repository에 커밋하지 않습니다.

이유:

- 파일 크기가 큽니다.
- Kaggle dataset license와 다운로드 절차를 repository가 대신하지 않습니다.
- raw data를 commit하면 재현 절차보다 결과 파일 자체에 의존하게 됩니다.
- synthetic dataset이더라도 거래 식별자, 금액, 잔액 흐름은 민감 데이터에 준해 관리합니다.

Repository에는 작은 sample JSONL만 제한적으로 둘 수 있습니다.

## 4. Directory Layout and Git Guardrail

V2 Phase 1에서 repository-level data guardrail을 추가했습니다.

```text
data/
  raw/
    .gitkeep
  processed/
    .gitkeep
  samples/
    .gitkeep
    paysim-events-sample.jsonl
    paysim-labels-sample.jsonl
    paysim-sample-manifest.json

scripts/
  data/
    README.md
    check-data-policy.sh
    download_paysim_dataset.py
    prepare_paysim_dataset.py
    validate_paysim_outputs.py
    generate_paysim_samples.py
```

V2 Phase 3에서 validation과 safe sampling script를 추가했습니다. Replay script는 후속 Phase에서 추가합니다.

`.gitignore` policy:

```gitignore
data/raw/*
data/processed/*
data/samples/*
!data/raw/.gitkeep
!data/processed/.gitkeep
!data/samples/.gitkeep
!data/samples/*.jsonl
!data/samples/*manifest*.json
```

정책:

- `data/raw`: Kaggle 원본 CSV 위치. `.gitkeep` 외 커밋 금지.
- `data/processed`: 전처리 전체 산출물 위치. `.gitkeep` 외 커밋 금지.
- `data/samples`: 검증된 작은 JSONL sample과 sample manifest만 제한적으로 커밋 가능.
- sample은 100~1,000건 이하, raw identifier 미포함, `.jsonl` 또는 `*manifest*.json`, 1MB 이하를 기준으로 둡니다.

`scripts/data/check-data-policy.sh`는 tracked 또는 staged 상태의 `data/` 파일을 검사합니다.

```bash
make data-policy-check
```

한계:

- Git ignore와 policy check는 이미 commit된 민감 데이터의 masking을 보장하지 않습니다.
- sample 안의 raw identifier 유무는 Phase 1 script가 완벽히 판별하지 못합니다.
- 후속 Phase 2~4에서 preprocessing, hashing, sample validation을 구현해야 합니다.

## 5. Reproduction Flow

1. Kaggle에서 PaySim CSV를 다운로드합니다. 로컬에서는 optional KaggleHub helper를 사용할 수 있습니다.

```bash
make data-env
make download-paysim
```

helper는 KaggleHub cache에 `ealaxi/paysim1` dataset을 다운로드하고, cache 안의 expected CSV를 아래 경로로 복사합니다.

```text
data/raw/PS_20174392719_1491204439457_log.csv
```

Kaggle token, API token, access token은 `.env`, docs, logs, validation report에 남기지 않습니다. CI에서는 download helper를 실행하지 않습니다.

KaggleHub는 project-managed `.venv-data`에서 실행합니다. 이 repository의 주 toolchain은 Java/Spring Boot와 Gradle이고, Python은 PaySim data helper에만 사용합니다. 따라서 개발자가 global `pip install kagglehub`를 직접 실행하지 않고 `make data-env`로 격리된 환경을 준비합니다.

이 분리의 이유:

- Java application runtime과 Python data helper 의존성을 분리합니다.
- 개발자별 Python interpreter와 package 상태 차이로 생기는 drift를 줄입니다.
- CI와 로컬 명령어가 같은 Makefile target을 사용합니다.
- Kaggle credential이나 token을 repository, `.venv-data`, docs, logs에 저장하지 않는 원칙을 유지합니다.

2. 전처리 script를 실행합니다.

```bash
make prepare-paysim
```

로컬 smoke 검증은 일부 row만 처리합니다.

```bash
make prepare-paysim-smoke
```

3. 후속 V2 Phase 3에서 작은 sample을 생성합니다.

4. 후속 V2 Phase 5에서 Replay script로 app-api에 이벤트를 주입합니다.

raw dataset acquisition과 preprocessing은 분리합니다. download helper는 raw CSV를 local에 준비하는 도구이고, preprocessing script는 raw CSV의 SHA-256과 `datasetSlug`를 validation report에 기록합니다.

## 6. Security and Privacy Notes

V2 문서와 구현에서는 다음 표현을 유지합니다.

> PaySim은 실제 모바일 머니 서비스 로그 샘플을 기반으로 생성된 synthetic dataset이다. 따라서 본 프로젝트는 실제 고객 개인정보나 실제 금융 원장을 사용하지 않는다. 다만 user/account 식별자, 금액, 거래 흐름이 포함되므로 운영 시스템 설계 관점에서는 민감 데이터로 간주하고 raw data를 저장소에 커밋하지 않는다.

금지:

- PaySim을 실제 고객 개인정보 dataset이라고 설명하지 않습니다.
- Kaggle 원본 CSV를 repository에 커밋하지 않습니다.
- `data/processed`의 normalized 전체 결과를 repository에 커밋하지 않습니다.
- raw payload나 전체 account-like identifier를 운영 log, metric tag, audit metadata에 남기지 않습니다.

## 7. Identifier Hashing Policy

PaySim은 synthetic dataset이지만 `nameOrig`, `nameDest`는 계좌형 식별자처럼 보입니다. V2에서는 raw identifier를 API, log, sample, result에 노출하지 않는 방향을 기본으로 둡니다.

전처리 script 옵션 후보:

```bash
python scripts/data/prepare_paysim_dataset.py \
  --input data/raw/PS_20174392719_1491204439457_log.csv \
  --events-output data/processed/paysim-events.jsonl \
  --labels-output data/processed/paysim-labels.jsonl \
  --hash-identifiers \
  --hash-salt local-dev-salt
```

Hashing 규칙:

```text
userId = U-{hmac_sha256(raw=nameOrig, key=salt).substring(0, 16)}
accountId = A-{hmac_sha256(raw=nameOrig, key=salt).substring(0, 16)}
destinationAccountId = D-{hmac_sha256(raw=nameDest, key=salt).substring(0, 16)}
```

기준:

- 동일 원본 identifier는 동일 hash로 변환합니다.
- 사용자별 Redis Sliding Window 계산이 가능해야 합니다.
- sample data에는 raw `nameOrig`, `nameDest`를 포함하지 않습니다.
- salt는 운영 환경에서 secret으로 관리해야 합니다.
- local 문서 예시는 `local-dev-salt`를 사용할 수 있지만 운영용 값으로 간주하지 않습니다.

## 8. V2 Phase 1 Completion Criteria

- `data/raw/.gitkeep`, `data/processed/.gitkeep`, `data/samples/.gitkeep` 추가
- raw/processed data 미커밋 `.gitignore` 정책 적용
- sample data commit 허용 범위 문서화
- `scripts/data/README.md` 작성
- `scripts/data/check-data-policy.sh` 추가
- `make data-policy-check` 제공
- README와 blog index에 V2 Phase 1 링크 추가

V2 Phase 1은 data guardrail 구현 범위입니다. PaySim normalization, sample generation, replay, Java Rule Engine V2 구현은 후속 Phase에서 진행합니다.

## 9. V2 Phase 2 Acquisition and Preprocessing

V2 Phase 2에서 추가한 구현:

- `scripts/data/download_paysim_dataset.py`
- `scripts/data/prepare_paysim_dataset.py`
- `scripts/data/test_prepare_paysim_dataset.py`
- Makefile target: `data-env`, `download-paysim`, `prepare-paysim`, `prepare-paysim-smoke`, `test-data-scripts`

CI에서는 KaggleHub download와 full preprocessing을 실행하지 않습니다. CI는 `.venv-data` 기반의 작은 fixture unittest와 data policy check만 실행합니다.

## 10. V2 Data Python Toolchain

V2 Data Python Toolchain 보강에서 추가한 구현:

- `scripts/data/requirements.txt`
- `scripts/data/bootstrap-data-env.sh`
- `.venv-data/` gitignore
- `make data-env`
- venv 기반 `download-paysim`, `prepare-paysim`, `prepare-paysim-smoke`, `test-data-scripts`

`scripts/data/requirements.txt`는 PaySim data helper에 필요한 Python dependency만 관리합니다. KaggleHub API 변화가 있으면 이 파일에서 version range를 조정하거나 더 좁게 고정합니다.

## 11. V2 Phase 3 Validation and Safe Sampling

V2 Phase 3에서는 full processed output을 replay나 Rule V2 입력으로 넘기기 전에 검증합니다.

검증 대상:

- `data/processed/paysim-events.jsonl`
- `data/processed/paysim-labels.jsonl`
- `data/processed/paysim-rejected.jsonl`
- `data/processed/paysim-validation-report.json`

검증 후 커밋 가능한 파일은 `data/samples` 아래의 작은 JSONL sample과 manifest로 제한합니다. `data/samples`에는 raw CSV, full processed JSONL, full validation summary를 넣지 않습니다.

Phase 3 sample policy:

- JSONL sample만 생성합니다.
- CSV sample은 raw column leakage 위험 때문에 생성하지 않습니다.
- `data/samples/*.csv`와 일반 `data/samples/*.json`은 data policy check에서 막습니다.
- `data/samples/*manifest*.json`만 제한적으로 허용합니다.
- sample manifest에는 dataset slug, raw filename, input SHA-256, sample count, strategy, `hashSaltSource`만 기록합니다.
- sample manifest에 raw identifier나 salt 값 자체를 기록하지 않습니다.
- local smoke sample에는 `default-local` salt source가 허용되지만, 공유/커밋 sample에는 `--require-non-default-salt` 사용을 권장합니다.

V2 Phase 3 검증 결과:

- `make prepare-paysim-smoke`: accepted 1,000 rows
- `make validate-paysim`: events=1000, labels=1000, rejected=0, fraud=9, flagged=0, rejectRatio=0.0000
- `make generate-paysim-sample-strict`: events=1000, labels=1000, fraud=9
- `make data-policy-check`: PASS
