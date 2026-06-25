# Fraud Rule V2 Strategy

## 1. Purpose

V2에서는 AI/ML 모델을 구현하지 않습니다. 대신 PaySim 거래 유형과 잔액 변화 feature를 활용해 Rule 기반 이상거래 탐지를 더 도메인스럽게 확장합니다.

범위를 Rule 기반으로 제한하는 이유:

- ML 모델 성능, feature engineering, class imbalance, model serving, drift monitoring까지 포함하면 V2 범위가 과도하게 커집니다.
- 이 프로젝트의 핵심은 Kafka 기반 대량 이벤트 처리, 지연 관측, 재처리, 운영 조치입니다.
- PaySim label은 Rule 평가와 evidence 작성에 사용하고, 탐지 logic의 직접 입력으로 사용하지 않습니다.

## 2. V1 Rule 한계

V1은 운영 안정성에 강했습니다.

- Kafka 비동기 처리
- Redis Sliding Window
- PostgreSQL idempotency
- DLT/reprocess
- failure drill
- k6 load test
- Admin API protection and audit log

하지만 fraud detection 관점에서는 다음 한계가 있습니다.

- synthetic transaction 생성 근거가 약합니다.
- fraud label 기반 평가가 없습니다.
- balance drain, transfer/cash-out flow 같은 PaySim fraud pattern을 반영하지 않습니다.
- 탐지 후 action workflow가 약합니다.

## 3. V2 Initial Rule Scope

| Rule code | Condition | Score | Notes |
|---|---:|---:|---|
| `BALANCE_DRAIN` | `amount / oldBalanceOrig >= 0.8` | 40 | 계좌 잔액 대부분이 빠져나감 |
| `ZERO_BALANCE_AFTER_TRANSFER` | `oldBalanceOrig > 0 && newBalanceOrig == 0` | 35 | 거래 후 잔액 0 |
| `TRANSFER_CASHOUT_PATTERN` | `type in (TRANSFER, CASH_OUT) && amount >= configured threshold` | 25 | PaySim fraud 흐름 반영 |
| `HIGH_AMOUNT` | amount threshold 이상 | 30 | 기존 amount rule 유지 |
| `RAPID_TRANSACTION_COUNT` | window count threshold 초과 | 30 | Redis Sliding Window |
| `WINDOW_AMOUNT_SUM` | window amount sum threshold 초과 | 40 | Redis Sliding Window |

초기 V2 구현 우선순위는 PaySim 특화 rule 3개입니다.

1. `BALANCE_DRAIN`
2. `ZERO_BALANCE_AFTER_TRANSFER`
3. `TRANSFER_CASHOUT_PATTERN`

`DESTINATION_BALANCE_ANOMALY`, `NEW_DESTINATION`, `FAILED_THEN_SUCCESS`, `AMOUNT_Z_SCORE`는 후속 후보로 둡니다.

## 4. Rule Details

### BALANCE_DRAIN

```text
oldBalanceOrig > 0
amount / oldBalanceOrig >= 0.8
```

의미:

- 송금 주체의 잔액 대부분이 한 번에 빠져나가는 패턴입니다.
- PaySim fraud 설명의 account takeover 후 transfer/cash-out 흐름과 연결됩니다.

주의:

- oldBalanceOrig가 0이면 skip합니다.
- amount와 balance는 floating point가 아니라 `BigDecimal` 기준으로 계산합니다.

### ZERO_BALANCE_AFTER_TRANSFER

```text
oldBalanceOrig > 0
newBalanceOrig == 0
type in (TRANSFER, CASH_OUT)
```

의미:

- 거래 후 잔액이 0이 되는 고위험 패턴입니다.

### TRANSFER_CASHOUT_PATTERN

```text
type in (TRANSFER, CASH_OUT)
amount >= configured threshold
```

의미:

- PaySim fraud behavior는 transfer와 cash-out 흐름을 중심으로 설명됩니다.
- 단독으로는 강한 fraud signal이 아니므로 다른 rule과 합산해 risk score에 반영합니다.

### DESTINATION_BALANCE_ANOMALY 후속 후보

```text
expectedNewBalanceDest = oldBalanceDest + amount
abs(newBalanceDest - expectedNewBalanceDest) > tolerance
```

의미:

- 목적지 잔액 변화가 거래 금액과 맞지 않는 경우입니다.

주의:

- PaySim에서 merchant destination은 balance가 0으로 유지되는 경우가 있으므로 type/destination prefix를 함께 고려해야 합니다.
- false positive 가능성이 높으므로 초기 구현에서는 제외하고, label coverage 분석 이후 도입 여부를 판단합니다.

## 5. Risk Level and Decision

| Score | Risk Level | Detection Decision |
|---:|---|---|
| 0-29 | LOW | APPROVE |
| 30-59 | MEDIUM | REVIEW |
| 60-79 | HIGH | HOLD_CANDIDATE |
| 80-100 | CRITICAL | BLOCK_CANDIDATE |

`CRITICAL`이어도 실제 계좌 정지나 금융 제재를 자동 실행하지 않습니다.

## 6. Label-based Evaluation

PaySim `isFraud` label은 평가에만 사용합니다.

절대 금지:

- Rule Engine이 `isFraud`를 입력으로 사용하지 않습니다.
- Rule Engine이 `sourceFlaggedFraud`를 입력으로 사용하지 않습니다.

평가 정의:

```text
positive prediction = riskLevel in (HIGH, CRITICAL)
actual positive = isFraud == true
```

Confusion matrix:

|  | Predicted Fraud | Predicted Normal |
|---|---:|---:|
| Actual Fraud | TP | FN |
| Actual Normal | FP | TN |

지표:

```text
precision = TP / (TP + FP)
recall = TP / (TP + FN)
f1 = 2 * precision * recall / (precision + recall)
```

평가 후보:

- total events
- labeled fraud count
- detected high/critical count
- fraud label 중 high/critical로 잡힌 count
- missed fraud examples
- false positive examples
- rule match distribution

V2의 precision/recall은 production ML model 성능이 아니라, PaySim synthetic label에 대한 Rule coverage 분석 지표입니다. Rule 기반 탐지의 해석 가능성과 Kafka 처리 evidence를 중심으로 기록합니다.

## 7. Redis Degraded Behavior

Redis 기반 rule은 V1 정책을 유지합니다.

- Redis unavailable이면 stateful rule을 skipped 처리합니다.
- stateless PaySim balance/type rule은 계속 실행합니다.
- degraded fraud result에는 skipped rule code를 남깁니다.
- Redis degraded metric을 증가시킵니다.

## 8. Tests

V2 구현 시 최소 테스트:

- `BALANCE_DRAIN` true/false
- oldBalanceOrig 0일 때 skipped 또는 not matched
- `ZERO_BALANCE_AFTER_TRANSFER` true/false
- `TRANSFER_CASHOUT_PATTERN` type별 판정
- score to risk level mapping
- label은 rule input으로 사용하지 않음

후속 Rule 테스트:

- `DESTINATION_BALANCE_ANOMALY` merchant/account destination edge case
- `NEW_DESTINATION`
- `FAILED_THEN_SUCCESS`
- `AMOUNT_Z_SCORE`

## 9. Documentation Updates Required During Implementation

V2 Rule 구현 시 함께 갱신할 문서:

- `docs/04-data-model.md`
- `docs/05-api-design.md`
- `docs/08-observability.md`
- `docs/13-development-roadmap.md`
- `docs/16-fraud-detection-strategy.md`
- `docs/20-evidence-index.md`
