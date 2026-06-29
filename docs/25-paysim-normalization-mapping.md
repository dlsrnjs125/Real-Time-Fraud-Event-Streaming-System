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
  "userId": "U-9f1a3c21e2b0abcd",
  "accountId": "A-9f1a3c21e2b0abcd",
  "destinationAccountId": "D-12ab8821cdaefeed",
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
  "scriptVersion": "v2-phase-4",
  "datasetSlug": "ealaxi/paysim1",
  "rawFileName": "PS_20174392719_1491204439457_log.csv",
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
  "hashAlgorithm": "HMAC-SHA256",
  "hashIdPrefixLength": 16,
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
- `eventId`는 `inputSha256`과 raw row number 맥락에서 해석합니다. 서로 다른 input file에 같은 row number가 있으면 같은 eventId 규칙을 사용할 수 있으므로 validation report의 `inputSha256`을 함께 보존합니다.

후속 Phase 5 replay에서는 다른 dataset/sample을 같은 API/DB에 주입할 때 충돌을 피할 수 있도록 `--event-id-prefix` 옵션을 둡니다. 기본 preprocessing output은 idempotency 검증에 유용하도록 row number 기반 deterministic id를 유지합니다. Phase 4 sample manifest는 이 결정을 `eventIdPolicy=row-number-deterministic`과 replay collision note로 남깁니다.

## 5.1 Identifier Hash Contract

V2 Phase 4 기준 identifier format은 다음과 같습니다.

```text
userId = U- + 16 lowercase hex chars
accountId = A- + 16 lowercase hex chars
destinationAccountId = D- + 16 lowercase hex chars
```

Hash contract:

```text
algorithm = HMAC-SHA256
hashIdPrefixLength = 16
saltSourceOnly = true
saltValueRecorded = false
```

`nameOrig`는 `userId`와 `accountId` 생성에 사용하고, `nameDest`는 `destinationAccountId` 생성에 사용합니다. 동일 raw identifier는 같은 salt에서 같은 pseudonym으로 변환되어 사용자 단위 grouping과 Kafka key 정책을 유지합니다.

Salt source policy:

- `--hash-salt`를 지정하면 `hashSaltSource=cli`
- `--hash-salt-env PAYSIM_HASH_SALT` 또는 기본 env 값이 있으면 `hashSaltSource=env:PAYSIM_HASH_SALT`
- salt가 없으면 local smoke/debug 용도로 `hashSaltSource=default-local`
- `--require-non-default-salt`가 있으면 `default-local` 사용 시 실패

Report와 manifest에는 salt 값 자체를 기록하지 않습니다. `--hash-salt`는 shell history에 남을 수 있으므로 로컬 재현성 검증용으로만 사용하고, 공유/커밋 sample 재생성에는 `PAYSIM_HASH_SALT` env salt를 권장합니다.

`--require-non-default-salt`는 `default-local` source를 막는 정책입니다. Salt entropy, age, rotation, secret-manager storage까지 자동 검증하지는 않습니다.

V2 Phase 4부터 validation report contract에 `hashAlgorithm`, `hashIdPrefixLength`, `hashSaltSource`가 필수로 포함됩니다. V2 Phase 2/3에서 생성한 기존 `data/processed/*` 산출물이 있다면 `make prepare-paysim-smoke` 또는 `.venv-data/bin/python scripts/data/prepare_paysim_dataset.py --force`로 report를 재생성해야 합니다.

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

Rejected row 비율 정책은 V2 Phase 3에서 구현합니다. Phase 2는 `row-level`과 `fail-fast` reject policy만 제공합니다.

Phase 3 후보:

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
- `--limit`은 output row 수만 제한하며, input provenance를 위해 SHA-256은 전체 raw file 기준으로 계산

CLI 예시:

```bash
make data-env
make prepare-paysim-smoke
make prepare-paysim
```

`make prepare-paysim-smoke`와 `make prepare-paysim`은 `data-env`를 먼저 실행하고 `.venv-data/bin/python`으로 preprocessing script를 실행합니다. Python toolchain은 PaySim data helper에만 사용하며 Java application runtime에는 포함하지 않습니다.

직접 옵션을 조정해야 할 때는 venv Python을 사용합니다.

```bash
.venv-data/bin/python scripts/data/prepare_paysim_dataset.py \
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
- `--require-non-default-salt`
- `--force`

## 10. Sampling Policy

구현 script:

```text
scripts/data/generate_paysim_samples.py
```

출력:

```text
data/samples/paysim-events-sample.jsonl
data/samples/paysim-labels-sample.jsonl
data/samples/paysim-sample-manifest.json
```

정책:

- sample은 100~1,000건 이하로 제한합니다.
- sample에는 raw identifier를 포함하지 않습니다.
- hash identifier만 포함합니다.
- label 분포를 sample manifest에 명시합니다.
- sample은 테스트/문서용이며 성능 수치 근거로 사용하지 않습니다.
- Phase 3에서는 CSV sample을 생성하지 않습니다.

## 10.1 Phase 3 Validation Contract

`scripts/data/validate_paysim_outputs.py`는 Phase 2 processed output 네 종류를 함께 검증합니다.

검증 기준:

- runtime event 필수 field와 `balanceFeatures` field 존재
- `eventId`, `traceId` 중복 없음
- `eventType`은 `TRANSFER`, `CASH_OUT`, `PAYMENT`, `CASH_IN`, `DEBIT` 중 하나
- `amount`와 balance field는 finite Decimal 문자열
- `currency=KRW`, `source=PAYSIM`, `schemaVersion=v2-paysim`
- `eventTime`은 UTC ISO-8601 형식
- identifier는 `U-`/`A-`/`D-` + 16 lowercase hex 형식
- runtime event에 `receivedAt`, `isFraud`, `isFlaggedFraud`, `sourceFlaggedFraud` 없음
- event/label sidecar는 `eventId` set이 일치
- rejected row는 reason allowlist만 사용
- report의 accepted/rejected/fraud/flagged/type count가 실제 line count와 일치
- `rejectRatio = rejectedRows / totalRows`가 기본 0.01을 초과하면 실패
- raw `nameOrig`, `nameDest`, `C12345`, `M12345` 형태 값이 output에 있으면 실패
- report에는 `hashAlgorithm=HMAC-SHA256`, `hashIdPrefixLength=16`, `hashSaltSource`가 있어야 함
- report에는 salt 값 자체가 없어야 함
- `--require-non-default-salt` 사용 시 `hashSaltSource=default-local`이면 실패

Validator는 full JSONL payload 전체를 메모리에 올리지 않고 line-by-line으로 읽습니다. 다만 event/label 정합성 검증을 위해 eventId set과 traceId set은 메모리에 보관합니다. 전체 PaySim full output 검증 시 이 set 크기는 accepted row 수에 비례합니다.

## 10.2 Phase 3 Sample Output Contract

`scripts/data/generate_paysim_samples.py`는 processed events/labels/report에서 작은 sample을 생성합니다.

출력:

```text
data/samples/paysim-events-sample.jsonl
data/samples/paysim-labels-sample.jsonl
data/samples/paysim-sample-manifest.json
```

sample contract:

- sample event count <= 1,000
- event sample과 label sample의 eventId set 일치
- event sample에 label field나 `receivedAt` 없음
- event/label/manifest에 raw identifier pattern 없음
- manifest에는 source dataset slug, raw filename, input SHA-256, sample count, strategy, hashSaltSource 기록
- manifest에는 `hashAlgorithm`, `hashIdPrefixLength`, `eventIdPolicy`, replay collision note 기록
- manifest에 salt 값 자체를 기록하지 않음
- 각 sample file은 1MB 이하
- Phase 3에서는 CSV sample을 생성하지 않음
- committed sample은 salt 값을 노출하지 않는 것을 우선하므로 동일한 private salt 없이는 byte-for-byte 재생성을 보장하지 않음

Sampling strategy:

- `head`: event 앞 N건과 matching label 추출
- `balanced`: fraud label true event를 우선 포함하고 나머지를 non-fraud로 채움

`balanced`는 deterministic first-N-per-class 방식입니다. 복잡한 reservoir sampling은 아직 구현하지 않습니다. Fraud row가 매우 적은 dataset에서는 가능한 fraud row만 포함하고 sample size 나머지는 non-fraud로 채웁니다.

## 10.3 Phase 4 Handoff

Phase 4에서는 hash/salt policy를 더 강화했습니다.

- committed/shared sample 생성 시 `--require-non-default-salt` 사용
- `make validate-paysim-strict`
- `make generate-paysim-sample-strict`
- committed sample manifest의 `default-local` salt source 차단
- validation script의 identifier format, hash metadata, non-default salt option 검증
- replay 단계에서 dataset/sample 충돌을 피하기 위한 eventId prefix policy 문서화
- committed sample JSONL을 재생성하지 않는 경우 manifest에 `generatedByScriptVersion`과 `policyHardenedByPhase`를 분리해 기록

Java replay path와 API payload validation 연결은 Phase 5 replay pipeline 범위입니다.

## 11. Replay Pipeline Contract

V2 Phase 5 구현 script:

```text
scripts/data/replay_paysim_events.py
```

기본 replay 대상은 app-api transaction intake endpoint입니다.

```text
POST http://localhost:8080/api/v1/transactions/events
```

Replay input은 events JSONL만 사용합니다.

```text
data/samples/paysim-events-sample.jsonl
data/processed/paysim-events.jsonl
```

`paysim-labels.jsonl`은 HTTP replay input이 아닙니다. Label sidecar는 replay 이후 fraud result와 join해 offline/online evaluation을 계산하기 위한 파일입니다.

### 11.1 Runtime Event to API Request Mapping

Current app-api `TransactionEventRequest` accepts:

```text
eventId
userId
accountId
eventType
amount
currency
merchantId
deviceId
location
eventTime
```

PaySim runtime event에서 API request body로 보내는 field:

| PaySim runtime event | app-api request | Notes |
|---|---|---|
| `eventId` | `eventId` | preserve 또는 prefix policy 적용 |
| `userId` | `userId` | Kafka key와 user grouping 유지 |
| `accountId` | `accountId` | HMAC pseudonym |
| `eventType` | `eventType` | current enum과 호환 |
| `amount` | `amount` | decimal string 그대로 전송 |
| `currency` | `currency` | `KRW` |
| `eventTime` | `eventTime` | server가 `receivedAt` 생성 |
| `traceId` | `X-Trace-Id` header | body에는 넣지 않음 |

API DTO에 없는 field는 HTTP body에서 제외하고 replay report의 `droppedFields`에 집계합니다.

```text
balanceFeatures
source
schemaVersion
destinationAccountId
```

금지:

- `receivedAt`: app-api가 접수 시각으로 생성합니다.
- `isFraud`, `isFlaggedFraud`, `sourceFlaggedFraud`: label leakage입니다.
- `nameOrig`, `nameDest`, `C12345`, `M12345` 형태 raw identifier: replay payload와 report에 남기지 않습니다.

### 11.2 EventId Collision and Idempotency

`idempotency-mode=preserve`는 PaySim deterministic `eventId`를 그대로 사용합니다. 같은 API/DB에 다시 replay하면 `409 DUPLICATE_TRANSACTION_EVENT`가 발생할 수 있으며, replay report에서는 `httpDuplicateOrConflict`로 집계합니다.

`idempotency-mode=prefix`는 `--event-id-prefix`를 요구합니다.

```bash
.venv-data/bin/python scripts/data/replay_paysim_events.py \
  --input data/samples/paysim-events-sample.jsonl \
  --idempotency-mode prefix \
  --event-id-prefix local-smoke \
  --max-events 100 \
  --force
```

Prefix mode는 같은 dataset을 같은 API/DB에 여러 번 넣거나 sample/full dataset을 섞어 replay할 때 collision을 줄이기 위한 옵션입니다. 반대로 duplicate/idempotency 검증에는 preserve mode가 더 적합합니다.

### 11.3 Replay Report

Replay report 기본 경로:

```text
data/processed/paysim-replay-report.json
```

이 파일은 Git ignore 대상이며 commit하지 않습니다. Report는 success, duplicate/conflict, client error, server error, timeout, connection error, retry attempts, dropped fields, sampled eventIds, bounded failure summary를 기록합니다. Request body, response body, token, label field, raw identifier는 저장하지 않습니다.

## 12. Partial Output Safety

V2 Phase 2 script는 output file을 직접 씁니다. `fail-fast` 중단이나 사용자 interrupt가 발생하면 partial output이 남을 수 있습니다. 현재는 `data/processed`가 Git ignore 대상이고 replay pipeline이 아직 없으므로 Phase 2 blocker로 보지 않습니다.

후속 Phase 5에서 replay pipeline을 연결하기 전에는 다음 atomic write 방식을 검토합니다.

```text
paysim-events.jsonl.tmp
paysim-labels.jsonl.tmp
paysim-rejected.jsonl.tmp
paysim-validation-report.json.tmp
```

정상 완료 시에만 final filename으로 rename해 partial output replay 위험을 줄입니다.

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
