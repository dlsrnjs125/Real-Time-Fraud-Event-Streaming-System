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

## 4. Proposed Directory Layout

```text
data/
  raw/
    .gitkeep
  processed/
    .gitkeep
  samples/
    .gitkeep
    paysim-normalized-sample.jsonl
    paysim-fraud-sample.jsonl

scripts/
  data/
    README.md
    prepare_paysim_dataset.py
    sample_paysim_dataset.py
    replay_paysim_to_api.py
```

Proposed `.gitignore` policy:

```gitignore
data/raw/*
data/processed/*
!data/raw/.gitkeep
!data/processed/.gitkeep
!data/samples/.gitkeep
!data/samples/*.jsonl
!data/samples/*.csv
```

## 5. Reproduction Flow

1. Kaggle에서 PaySim CSV를 다운로드합니다.
2. 파일을 아래 경로에 둡니다.

```text
data/raw/PS_20174392719_1491204439457_log.csv
```

3. 전처리 script를 실행합니다.

```bash
python scripts/data/prepare_paysim_dataset.py \
  --input data/raw/PS_20174392719_1491204439457_log.csv \
  --output data/processed/paysim-normalized.jsonl
```

4. 작은 sample을 생성합니다.

```bash
python scripts/data/sample_paysim_dataset.py \
  --input data/processed/paysim-normalized.jsonl \
  --output data/samples/paysim-normalized-sample.jsonl \
  --limit 1000
```

5. Replay script로 app-api에 이벤트를 주입합니다.

```bash
python scripts/data/replay_paysim_to_api.py \
  --input data/processed/paysim-normalized.jsonl \
  --api-base-url http://localhost:8080 \
  --limit 10000
```

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
  --output data/processed/paysim-normalized.jsonl \
  --hash-identifiers \
  --hash-salt local-dev-salt
```

Hashing 규칙:

```text
userId = U-{sha256(nameOrig + salt).substring(0, 16)}
accountId = A-{sha256(nameOrig + salt).substring(0, 16)}
destinationAccountId = D-{sha256(nameDest + salt).substring(0, 16)}
```

기준:

- 동일 원본 identifier는 동일 hash로 변환합니다.
- 사용자별 Redis Sliding Window 계산이 가능해야 합니다.
- sample data에는 raw `nameOrig`, `nameDest`를 포함하지 않습니다.
- salt는 운영 환경에서 secret으로 관리해야 합니다.
- local 문서 예시는 `local-dev-salt`를 사용할 수 있지만 운영용 값으로 간주하지 않습니다.

## 8. V2 Phase 1 Completion Criteria

- PaySim provenance 문서 작성
- raw/processed data 미커밋 정책 문서화
- sample data commit 허용 범위 문서화
- identifier hashing 정책 문서화
- PaySim CSV to normalized JSONL mapping 문서화
- replay 전제 조건과 재현 명령 문서화

구현은 후속 V2 Phase 1에서 진행합니다. 이 문서는 기획/설계 기준입니다.
