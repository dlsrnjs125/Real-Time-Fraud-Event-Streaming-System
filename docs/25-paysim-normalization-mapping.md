# PaySim Normalization Mapping

## 1. Purpose

이 문서는 Kaggle PaySim CSV를 현재 프로젝트의 transaction event 흐름에 맞는 normalized JSONL로 변환하는 설계를 정의합니다.

V2의 핵심은 PaySim 데이터를 단순히 보관하는 것이 아니라, Kafka event로 replay 가능한 입력으로 바꾸는 것입니다.

## 2. PaySim Input Columns

PaySim CSV는 일반적으로 다음 컬럼을 포함합니다.

| PaySim column | Meaning |
|---|---|
| `step` | 시간 step. 보통 1 step은 1 hour로 해석 |
| `type` | 거래 유형. `PAYMENT`, `TRANSFER`, `CASH_OUT`, `DEBIT`, `CASH_IN` 등 |
| `amount` | 거래 금액 |
| `nameOrig` | 송금 주체 account-like identifier |
| `oldbalanceOrg` | 송금 주체 거래 전 잔액 |
| `newbalanceOrig` | 송금 주체 거래 후 잔액 |
| `nameDest` | 수신 대상 account-like identifier |
| `oldbalanceDest` | 수신 대상 거래 전 잔액 |
| `newbalanceDest` | 수신 대상 거래 후 잔액 |
| `isFraud` | PaySim fraud label |
| `isFlaggedFraud` | PaySim source rule flag |

## 3. Normalized Event Contract

Normalized JSONL은 현재 `TransactionEventMessage`와 V2 fraud rule feature를 함께 담습니다.

예시:

```json
{
  "eventId": "paysim-000000001",
  "userId": "U-9f1a3c21e2b0",
  "accountId": "A-9f1a3c21e2b0",
  "destinationAccountId": "D-12ab8821cdae",
  "eventType": "PAYMENT",
  "amount": 9839.64,
  "currency": "PAYSIM",
  "eventTime": "2026-01-01T01:00:00Z",
  "receivedAt": null,
  "traceId": "trace-paysim-000000001",
  "schemaVersion": "v2-paysim",
  "source": "PAYSIM",
  "label": "NORMAL",
  "isFraud": false,
  "sourceFlaggedFraud": false,
  "features": {
    "oldBalanceOrig": 170136.0,
    "newBalanceOrig": 160296.36,
    "oldBalanceDest": 0.0,
    "newBalanceDest": 0.0,
    "step": 1
  }
}
```

`receivedAt`은 app-api가 이벤트를 접수할 때 생성하는 값이므로 normalized file 단계에서는 null이거나 생략 가능합니다.

## 4. Mapping Table

| PaySim column | V2 normalized field | Notes |
|---|---|---|
| `step` | `eventTime`, `features.step` | 기준 시작 시각 + step hour |
| `type` | `eventType` | 기존 enum과 호환 필요 |
| `amount` | `amount` | `BigDecimal`로 처리 |
| `nameOrig` | `userId` | user-based Kafka key 유지 |
| `nameOrig` | `accountId` | `ACC-` prefix 적용 가능 |
| `nameDest` | `destinationAccountId` | V2 field 확장 후보 |
| `oldbalanceOrg` | `features.oldBalanceOrig` | balance drain rule 입력 |
| `newbalanceOrig` | `features.newBalanceOrig` | zero balance rule 입력 |
| `oldbalanceDest` | `features.oldBalanceDest` | destination anomaly rule 입력 |
| `newbalanceDest` | `features.newBalanceDest` | destination anomaly rule 입력 |
| `isFraud` | `isFraud`, `label` | 평가용 정답. 탐지 rule 입력으로 직접 사용 금지 |
| `isFlaggedFraud` | `sourceFlaggedFraud` | PaySim source flag. 참고용 |

## 4.1 Output Files

V2 preprocessing script는 정상 결과만 만들지 않습니다. rejected row와 validation report를 함께 만들어야 전처리 결과를 신뢰할 수 있습니다.

```text
data/processed/paysim-normalized.jsonl
data/processed/paysim-rejected.jsonl
data/processed/paysim-validation-report.json
```

`paysim-normalized.jsonl`:

- replay 가능한 normalized event
- raw `nameOrig`, `nameDest` 미포함
- hash identifier 사용

`paysim-rejected.jsonl`:

```json
{
  "rowNumber": 12345,
  "reason": "INVALID_AMOUNT",
  "raw": {
    "type": "TRANSFER",
    "amount": "-100"
  }
}
```

`paysim-validation-report.json`:

```json
{
  "inputRows": "TBD",
  "normalizedRows": "TBD",
  "rejectedRows": "TBD",
  "fraudRows": "TBD",
  "normalRows": "TBD",
  "fraudRatio": "TBD",
  "typeDistribution": {
    "PAYMENT": "TBD",
    "TRANSFER": "TBD",
    "CASH_OUT": "TBD",
    "CASH_IN": "TBD",
    "DEBIT": "TBD"
  }
}
```

## 5. EventId and TraceId Policy

`eventId`는 row order 기반 deterministic id로 생성합니다.

```text
eventId = paysim-{rowNumber zero padded}
traceId = trace-paysim-{rowNumber zero padded}
```

목표:

- replay를 반복해도 같은 row는 같은 `eventId`를 갖습니다.
- Consumer idempotency와 duplicate replay 검증에 사용할 수 있습니다.
- PaySim row order가 바뀌면 eventId가 바뀔 수 있으므로 전처리 script는 input order를 보존합니다.

## 6. Time Policy

PaySim `step`은 normalized eventTime으로 변환합니다.

기본 기준:

```text
baseTime = 2026-01-01T00:00:00Z
eventTime = baseTime + step hours
```

이 기준은 실제 발생 시각이 아니라 replay와 latency 계산을 위한 deterministic synthetic time입니다.

## 7. Label Usage Policy

`isFraud`는 평가용 정답입니다.

금지:

- Rule Engine이 `isFraud`를 보고 risk score를 계산하지 않습니다.
- 운영 decision이 label을 직접 참조하지 않습니다.

허용:

- Rule 결과와 PaySim label을 비교해 precision/recall 후보를 계산합니다.
- missed fraud, false positive example을 문서화합니다.

## 8. Schema Boundary

V2 구현 시 선택지는 두 가지입니다.

1. 기존 `TransactionEventMessage`에 V2 optional fields를 추가
2. 별도 `PaySimTransactionEventMessage`를 만들고 app-api에서 공통 transaction event로 변환

권장:

- app-common의 핵심 transaction schema는 과도하게 PaySim 전용으로 오염시키지 않습니다.
- `features`처럼 V2 rule에 필요한 확장 필드는 명시적으로 문서화하고, API DTO와 consumer contract 변경 시 docs/05, docs/16을 함께 갱신합니다.

## 9. Validation Policy

전처리 단계에서 확인할 항목:

- required column 존재
- `amount >= 0`
- `step >= 0`
- `type in PAYMENT, TRANSFER, CASH_OUT, CASH_IN, DEBIT`
- `nameOrig` not blank
- `nameDest` not blank
- balance fields parseable
- `isFraud in 0, 1`
- `isFlaggedFraud in 0, 1`

잘못된 row는 skip하지 않고 rejected output 또는 error report에 기록합니다.

## 10. Sampling Policy

추가 script 후보:

```text
scripts/data/sample_paysim_dataset.py
```

출력:

```text
data/samples/paysim-normalized-sample.jsonl
data/samples/paysim-fraud-sample.jsonl
```

정책:

- sample은 100~1,000건 이하로 제한합니다.
- sample에는 raw identifier를 포함하지 않습니다.
- hash identifier만 포함합니다.
- label 분포를 README 또는 validation report에 명시합니다.
- sample은 테스트/문서용이며 성능 수치 근거로 사용하지 않습니다.

## 11. Replay Pipeline Contract

Replay script 후보:

```bash
python scripts/data/replay_paysim_to_api.py \
  --input data/processed/paysim-normalized.jsonl \
  --api-base-url http://localhost:8080 \
  --limit 1000 \
  --rate-per-second 50
```

기능 요구사항:

- JSONL 한 줄씩 읽기
- app-api `POST /api/v1/transactions/events` 호출
- rate limit 옵션
- 실패 응답 기록
- replay summary 출력
- eventId prefix 옵션
- fraud-only replay 옵션
- normal-only replay 옵션

Replay summary:

```json
{
  "inputRows": 1000,
  "sent": 1000,
  "success": 998,
  "failed": 2,
  "durationSeconds": 20.1,
  "ratePerSecond": 49.7
}
```
