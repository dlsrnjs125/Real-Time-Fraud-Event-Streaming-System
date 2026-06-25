# PaySim Normalization Mapping

## 1. Purpose

이 문서는 Kaggle PaySim CSV를 현재 프로젝트의 transaction event 흐름에 맞는 normalized JSONL로 변환하는 설계를 정의합니다.

V2의 핵심은 PaySim 데이터를 단순히 보관하는 것이 아니라, Kafka event로 replay 가능한 입력으로 바꾸는 것입니다.

V2 Phase 2에서 `scripts/data/prepare_paysim_dataset.py`가 이 mapping의 첫 구현을 제공합니다. sample generation, API replay, Rule Engine V2 구현은 후속 Phase로 남깁니다.

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

## 3. Runtime Event Contract

Runtime event JSONL은 app-api/Kafka replay에 사용하는 입력입니다. 정답 label은 포함하지 않습니다.

예시:

```json
{
  "eventId": "paysim-000000001",
  "userId": "U-9f1a3c21e2b0",
  "accountId": "A-9f1a3c21e2b0",
  "destinationAccountId": "D-12ab8821cdae",
  "eventType": "PAYMENT",
  "amount": "9839.64",
  "currency": "KRW",
  "eventTime": "2026-01-01T01:00:00Z",
  "traceId": "trace-paysim-000000001",
  "schemaVersion": "v2-paysim",
  "source": "PAYSIM",
  "balanceFeatures": {
    "oldBalanceOrig": "170136.00",
    "newBalanceOrig": "160296.36",
    "oldBalanceDest": "0.00",
    "newBalanceDest": "9839.64",
    "sourceStep": 1
  }
}
```

`receivedAt`은 app-api가 이벤트를 접수할 때 생성하는 값이므로 normalized runtime event에는 포함하지 않습니다. Kafka message에는 app-api가 생성한 `receivedAt`이 포함됩니다.

`currency`는 현행 API validation과 맞추기 위해 `KRW`로 고정합니다. PaySim 금액은 synthetic amount이므로 실제 원화 거래 의미를 주장하지 않으며, 데이터 출처는 `source=PAYSIM`으로 표현합니다.

`amount`와 balance field는 Python `Decimal`로 읽고 JSON에는 문자열로 기록합니다. float 기반 직렬화는 사용하지 않습니다.

## 3.1 Evaluation Label Sidecar

PaySim label은 runtime event와 물리적으로 분리합니다.

`paysim-labels.jsonl` 예시:

```json
{
  "eventId": "paysim-000000001",
  "isFraud": false,
  "sourceFlaggedFraud": false,
  "sourceStep": 1,
  "sourceType": "TRANSFER"
}
```

이 분리는 Consumer가 정답 label을 볼 수 있는 구조를 피하기 위한 설계입니다. Rule Engine과 action decision은 `paysim-events.jsonl`을 통해 들어온 runtime event만 사용합니다. Runtime event와 label sidecar는 `eventId`로만 연결합니다.

## 4. Mapping Table

| PaySim column | V2 normalized field | Notes |
|---|---|---|
| `step` | `eventTime`, `balanceFeatures.sourceStep` | 기준 시작 시각 + step hour |
| `type` | `eventType` | 기존 enum과 호환 필요 |
| `amount` | `amount` | `BigDecimal`로 처리 |
| `nameOrig` | `userId` | user-based Kafka key 유지 |
| `nameOrig` | `accountId` | `ACC-` prefix 적용 가능 |
| `nameDest` | `destinationAccountId` | V2 field 확장 후보 |
| `oldbalanceOrg` | `balanceFeatures.oldBalanceOrig` | balance drain rule 입력 |
| `newbalanceOrig` | `balanceFeatures.newBalanceOrig` | zero balance rule 입력 |
| `oldbalanceDest` | `balanceFeatures.oldBalanceDest` | destination anomaly rule 입력 |
| `newbalanceDest` | `balanceFeatures.newBalanceDest` | destination anomaly rule 입력 |
| `isFraud` | `paysim-labels.jsonl.isFraud` | 평가용 sidecar. runtime event에 포함 금지 |
| `isFlaggedFraud` | `paysim-labels.jsonl.sourceFlaggedFraud` | 평가용 sidecar. runtime event에 포함 금지 |

## 4.1 Output Files

V2 preprocessing script는 정상 결과만 만들지 않습니다. rejected row와 validation report를 함께 만들어야 전처리 결과를 신뢰할 수 있습니다.

```text
data/processed/paysim-events.jsonl
data/processed/paysim-labels.jsonl
data/processed/paysim-rejected.jsonl
data/processed/paysim-validation-report.json
```

`paysim-events.jsonl`:

- replay 가능한 normalized event
- `label`, `isFraud`, `sourceFlaggedFraud` 미포함
- raw `nameOrig`, `nameDest` 미포함
- hash identifier 사용

`paysim-labels.jsonl`:

- evaluation용 sidecar
- `eventId`, `isFraud`, `sourceFlaggedFraud`, `sourceStep`, `sourceType`만 포함
- hash identifier도 evaluation join에 필요하지 않으므로 포함하지 않음
- app-api/Kafka replay 입력으로 사용하지 않음

`paysim-rejected.jsonl`:

```json
{
  "rowNumber": 12345,
  "reason": "INVALID_AMOUNT",
  "rawType": "TRANSFER",
  "message": "amount must be >= 0"
}
```

Rejected row에는 `nameOrig`, `nameDest`, raw row 전체를 저장하지 않습니다.

`paysim-validation-report.json`:

```json
{
  "scriptVersion": "v2-phase-2",
  "inputPath": "data/raw/PS_20174392719_1491204439457_log.csv",
  "inputSha256": "TBD",
  "baseTime": "2026-01-01T00:00:00Z",
  "startedAt": "TBD",
  "finishedAt": "TBD",
  "totalRows": "TBD",
  "acceptedRows": "TBD",
  "rejectedRows": "TBD",
  "fraudRows": "TBD",
  "flaggedFraudRows": "TBD",
  "eventTypeCounts": {
    "PAYMENT": "TBD",
    "TRANSFER": "TBD",
    "CASH_OUT": "TBD",
    "CASH_IN": "TBD",
    "DEBIT": "TBD"
  },
  "hashSaltSource": "default-local",
  "outputFiles": {
    "events": "data/processed/paysim-events.jsonl",
    "labels": "data/processed/paysim-labels.jsonl",
    "rejected": "data/processed/paysim-rejected.jsonl"
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
- `eventId`는 `inputSha256`과 `sourceRowNumber` 맥락에서 해석합니다. 서로 다른 input file에 같은 row number가 있으면 같은 eventId 규칙을 사용할 수 있으므로 validation report의 `inputSha256`을 함께 보존합니다.

## 6. Time Policy

PaySim `step`은 normalized eventTime으로 변환합니다.

기본 기준:

```text
baseTime = 2026-01-01T00:00:00Z
eventTime = baseTime + step hours
```

이 기준은 실제 발생 시각이 아니라 replay와 latency 계산을 위한 deterministic synthetic time입니다.

## 7. Label Usage Policy

`isFraud`와 `sourceFlaggedFraud`는 `paysim-labels.jsonl`에만 저장하는 평가용 정답입니다.

금지:

- Rule Engine이 `isFraud`를 보고 risk score를 계산하지 않습니다.
- Rule Engine이 `sourceFlaggedFraud`를 보고 risk score를 계산하지 않습니다.
- 운영 decision이 label을 직접 참조하지 않습니다.
- app-api/Kafka replay payload에 label을 포함하지 않습니다.

허용:

- Rule 결과와 PaySim label을 비교해 precision/recall 후보를 계산합니다.
- missed fraud, false positive example을 문서화합니다.

## 8. Runtime Schema Decision

V2 초기 구현은 Rule V2가 balance 기반 feature를 안정적으로 읽을 수 있도록 typed optional field를 추가합니다.

결정:

- `TransactionEventMessage`와 API request DTO에 `TransactionBalanceFeatures` optional field를 추가합니다.
- generic `Map<String, Object> features`는 사용하지 않습니다.
- `TransactionBalanceFeatures`에는 PaySim label이나 source flag를 포함하지 않습니다.
- field 이름은 PaySim 전용이 아니라 balance 기반 rule 입력임을 드러내는 이름을 사용합니다.
- JSON field 이름은 `sourceStep`으로 통일합니다. `step`은 PaySim raw column 이름으로만 사용합니다.
- `TransactionEventType` enum에는 PaySim type인 `CASH_OUT`, `CASH_IN`, `DEBIT`를 추가합니다. Rule V2에서 `CASH_OUT` 구분이 필요하므로 기존 type으로 강제 normalize하지 않습니다.
- `currency`는 `KRW`를 사용하고 PaySim 출처는 `source=PAYSIM`으로 보존합니다.

후보 Java contract:

```java
public record TransactionBalanceFeatures(
        BigDecimal oldBalanceOrig,
        BigDecimal newBalanceOrig,
        BigDecimal oldBalanceDest,
        BigDecimal newBalanceDest,
        Integer sourceStep
) {
}
```

이 결정의 이유:

- Rule V2 구현 시 타입 안정성이 필요합니다.
- `BigDecimal` 기반 금액/잔액 처리를 명확히 합니다.
- Map 기반 형변환, 누락, 타입 오류를 줄입니다.
- label sidecar와 runtime payload 분리를 강제하기 쉽습니다.

구현 시 함께 갱신할 문서:

- `docs/04-data-model.md`
- `docs/05-api-design.md`
- `docs/16-fraud-detection-strategy.md`

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

## 9.1 Fail-fast and Row-level Reject Policy

전처리 script는 실패 유형을 구분합니다.

Fail-fast:

- input file missing
- required column missing
- CSV header parse 실패
- output directory not writable
- `--hash-identifiers`가 true인데 salt가 없음

Row-level reject:

- `amount < 0`
- `step < 0`
- balance parse 실패
- `nameOrig` 또는 `nameDest` blank
- `isFraud` 또는 `isFlaggedFraud`가 0/1이 아님

Rejected row 비율 정책:

- 기본 `--max-reject-ratio 0.01`
- reject ratio가 max reject ratio를 초과하면 report와 rejected output은 생성하되 exit code 2로 종료합니다.
- 이 정책은 CI나 local 검증에서 데이터 품질 문제를 빠르게 드러내기 위한 것입니다.

## 9.2 Large CSV Processing Policy

PaySim은 수백만 row 규모로 사용될 수 있으므로 전처리 script는 streaming 처리를 기본으로 합니다.

기준:

- Python `csv.DictReader` 기반 streaming 처리
- 한 row씩 validate -> normalize -> write
- 전체 input을 memory에 올리지 않음
- progress log는 N건마다 출력
- 개발 중 일부 row만 처리할 수 있도록 `--limit` 옵션 제공
- `nameOrig`, `nameDest`, label 값은 progress log에 출력하지 않음

CLI 예시:

```bash
python scripts/data/prepare_paysim_dataset.py \
  --input data/raw/PS_20174392719_1491204439457_log.csv \
  --output-dir data/processed \
  --base-time 2026-01-01T00:00:00Z \
  --hash-salt-env PAYSIM_HASH_SALT \
  --limit 100000 \
  --reject-policy row-level \
  --force
```

CLI options:

- `--input`
- `--output-dir`
- `--base-time`
- `--limit`
- `--reject-policy row-level|fail-fast`
- `--hash-salt`
- `--hash-salt-env`
- `--force`

## 10. Sampling Policy

추가 script 후보:

```text
scripts/data/sample_paysim_dataset.py
```

출력:

```text
data/samples/paysim-events-sample.jsonl
data/samples/paysim-labels-sample.jsonl
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
  --input data/processed/paysim-events.jsonl \
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
- 기본 replay는 `paysim-events.jsonl`만 읽고 label에 접근하지 않음
- fraud-only/normal-only replay는 명시적으로 `--labels-input`을 전달한 경우에만 허용
- filtered replay에서도 API/Kafka payload에는 label을 포함하지 않음

fraud-only replay 예시:

```bash
python scripts/data/replay_paysim_to_api.py \
  --input data/processed/paysim-events.jsonl \
  --labels-input data/processed/paysim-labels.jsonl \
  --filter fraud-only \
  --api-base-url http://localhost:8080 \
  --limit 1000
```

Replay summary:

```json
{
  "inputRows": 1000,
  "sent": 1000,
  "success": 998,
  "failed": 2,
  "durationSeconds": 20.1,
  "ratePerSecond": 49.7,
  "latencyMs": {
    "p50": "TBD",
    "p95": "TBD",
    "p99": "TBD"
  }
}
```

Replay failure report 후보:

```json
{
  "eventId": "paysim-000000123",
  "status": 409,
  "errorCode": "DUPLICATE_TRANSACTION_EVENT",
  "message": "duplicate eventId",
  "attemptedAt": "2026-01-01T01:03:00Z"
}
```
