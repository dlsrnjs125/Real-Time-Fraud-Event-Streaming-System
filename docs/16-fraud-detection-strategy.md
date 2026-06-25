# Fraud Detection Strategy

## 1. 전략 결정

이번 프로젝트의 초기 구현 범위는 Rule 기반 이상거래 탐지로 제한합니다.

핵심 검증 대상은 Kafka 기반 실시간 처리, Consumer Lag, Retry/DLT, Redis 장애 대응, 탐지 지연 측정입니다. AI/ML 기반 탐지는 향후 확장 후보로 문서화합니다.

## 2. 이상거래 탐지 정의

이상거래 탐지는 단일 거래 금액만 보는 것이 아니라, 사용자별 최근 거래 패턴, 거래 빈도, 기기 변경, 위치 변화, 거래 금액, 실패 이력 등을 조합해 위험 점수를 계산하는 과정입니다.

처리 흐름:

```text
거래 이벤트 수신
-> 사용자별 최근 거래 window 조회
-> 개별 rule 평가
-> rule별 risk score 계산
-> 총 risk score 합산
-> LOW / MEDIUM / HIGH 분류
-> FraudResult 저장
-> HIGH면 fraud-alert-events 발행
```

초기 구현에서 HIGH risk는 거래 차단을 의미하지 않습니다. 이 프로젝트는 실제 승인/원장 시스템을 구현하지 않으므로, HIGH 이벤트는 운영자 검토 또는 알림 대상으로 분류합니다.

## 3. Risk Score 기준

Phase 5 Rule Engine v1 기준:

| Score | RiskLevel | Decision |
|---:|---|---|
| 0 ~ 29 | LOW | APPROVE |
| 30 ~ 69 | MEDIUM | REVIEW |
| 70 ~ 100 | HIGH | BLOCK |

Phase 5 rule:

- `AMOUNT_THRESHOLD`: `amount >= 1,000,000 KRW`, +50
- `NIGHT_TIME_TRANSACTION`: `eventTime` hour 0~5, +20
- `SUSPICIOUS_LOCATION`: `UNKNOWN`, `FOREIGN`, `HIGH_RISK`, +30

점수는 합산하되 최대 100으로 제한합니다. Phase 6부터 Redis Sliding Window 기반 사용자 최근 거래 패턴을 `RAPID_TRANSACTION_COUNT`, `WINDOW_AMOUNT_SUM` rule로 반영합니다.

Rule threshold는 Phase 5에서 코드 상수로 관리합니다. 이는 테스트 가능성과 구현 단순성을 우선한 선택이며, 운영 중 threshold 변경이 필요한 경우 application config, DB rule table, feature flag 방식으로 분리합니다.

| Score | RiskLevel |
|---:|---|
| 0 ~ 39 | LOW |
| 40 ~ 69 | MEDIUM |
| 70 이상 | HIGH |

예시:

- 고액 거래 + 새 기기 = 40 + 25 = 65 = MEDIUM
- 고액 거래 + 반복 거래 = 40 + 35 = 75 = HIGH
- 새 기기 + 위치 급변 + 실패 후 성공 = 25 + 30 + 30 = 85 = HIGH

`FraudResult`에는 판단 근거를 설명할 수 있도록 다음 필드를 남깁니다.

- `eventId`
- `userId`
- `riskLevel`
- `riskScore`
- `matchedRuleCodes`
- `skippedRuleCodes`
- `degraded`
- `detectedAt`
- `traceId`

예시:

```json
{
  "eventId": "evt-001",
  "riskLevel": "HIGH",
  "riskScore": 75,
  "matchedRuleCodes": [
    "AMOUNT_THRESHOLD",
    "RAPID_TRANSACTION_COUNT"
  ],
  "skippedRuleCodes": [],
  "degraded": false,
  "detectedAt": "2026-06-15T21:30:00+09:00"
}
```

## 4. 초기 Rule 후보

각 rule은 탐지 목적, 필요 데이터, Redis 필요 여부, 위험 점수, 오탐 가능성, 장애 시 동작, 측정 지표를 함께 정의합니다.

### 4.1 HighAmountRule

| 항목 | 내용 |
|---|---|
| Rule code | `HIGH_AMOUNT` |
| 목적 | 비정상적으로 큰 금액의 거래 탐지 |
| 조건 | `amount >= 1,000,000 KRW` |
| 점수 | +40 |
| 필요 데이터 | 현재 거래 이벤트 |
| Redis 필요 여부 | 필요 없음 |
| 장애 시 동작 | Redis 장애와 무관하게 수행 가능 |
| 오탐 가능성 | 고소득 사용자 또는 정상적인 고액 송금 |
| 측정 지표 | matched count, high amount distribution |

단독으로 HIGH 처리하지 않고 다른 rule과 조합합니다.

### 4.2 RapidTransactionCountRule

| 항목 | 내용 |
|---|---|
| Rule code | `RAPID_TRANSACTION_COUNT` |
| 목적 | 짧은 시간 안에 반복적으로 발생한 거래 탐지 |
| 조건 | 최근 5분 내 거래 5건 이상 |
| 점수 | +30 |
| 필요 데이터 | 사용자별 최근 거래 이벤트 |
| Redis 필요 여부 | 필요 |
| Redis 구조 | `ZSET fraud:tx:user:{userId}:events` |
| 장애 시 동작 | SKIPPED 처리하고 `degraded=true` 기록 |
| 오탐 가능성 | 콘서트 예매, 명절 송금, 공동구매 |
| 측정 지표 | matched count, skipped count, Redis command latency |

ZSET score는 `eventTime` epoch millis, value는 `eventId`입니다.

### 4.3 WindowAmountSumRule

| 항목 | 내용 |
|---|---|
| Rule code | `WINDOW_AMOUNT_SUM` |
| 목적 | 짧은 시간 안에 누적된 고액 거래 패턴 탐지 |
| 조건 | 최근 5분 누적 거래 금액 3,000,000 KRW 이상 |
| 점수 | +40 |
| 필요 데이터 | 사용자별 최근 거래 이벤트와 amount metadata |
| Redis 필요 여부 | 필요 |
| Redis 구조 | `ZSET fraud:tx:user:{userId}:events` + `HASH fraud:tx:event:{eventId}` |
| 장애 시 동작 | SKIPPED 처리하고 `degraded=true` 기록 |
| 오탐 가능성 | 정상적인 분할 결제, 공동구매, 대량 송금 |
| 측정 지표 | matched count, skipped count, Redis degraded count |

### 4.4 NewDeviceRule

| 항목 | 내용 |
|---|---|
| Rule code | `NEW_DEVICE` |
| 목적 | 기존에 사용하지 않던 기기에서 발생한 거래 탐지 |
| 조건 | 최근 N일 내 사용 이력이 없는 `deviceId` |
| 점수 | +25 |
| 필요 데이터 | 사용자별 최근 device history |
| Redis 필요 여부 | 선택 |
| 장애 시 동작 | SKIPPED 또는 PostgreSQL fallback 중 구현 단계에서 선택 |
| 오탐 가능성 | 새 휴대폰 구매, 브라우저 변경 |
| 측정 지표 | matched count, skipped count, fallback count |

### 4.5 LocationChangeRule

| 항목 | 내용 |
|---|---|
| Rule code | `LOCATION_CHANGE` |
| 목적 | 짧은 시간 내 물리적으로 이동하기 어려운 위치 변경 탐지 |
| 조건 | 이전 거래 위치와 현재 거래 위치가 다른 국가이고 시간 차이가 30분 이하 |
| 점수 | +30 |
| 필요 데이터 | 사용자별 최근 location |
| Redis 필요 여부 | 선택 |
| 장애 시 동작 | SKIPPED 또는 PostgreSQL fallback 중 구현 단계에서 선택 |
| 오탐 가능성 | VPN, 해외 결제 대행, 온라인 가맹점 위치 차이 |
| 측정 지표 | matched count, skipped count |

### 4.6 FailedThenSuccessRule

| 항목 | 내용 |
|---|---|
| Rule code | `FAILED_THEN_SUCCESS` |
| 목적 | 여러 번 실패한 뒤 성공한 거래 탐지 |
| 조건 | 최근 5분 내 실패 거래 3회 이상 후 성공 거래 발생 |
| 점수 | +30 |
| 필요 데이터 | 최근 실패 거래 window |
| Redis 필요 여부 | 필요 |
| 장애 시 동작 | SKIPPED 처리하고 `degraded=true` 기록 |
| 오탐 가능성 | 사용자가 비밀번호를 여러 번 잘못 입력한 정상 케이스 |
| 측정 지표 | matched count, skipped count, degraded count |

## 5. Rule 실행 순서

1. 단건 이벤트 기반 Rule
   - `HIGH_AMOUNT`
   - Redis 장애와 무관하게 수행 가능

2. Redis Sliding Window 기반 Rule
   - `RAPID_TRANSACTION_COUNT`
   - `WINDOW_AMOUNT_SUM`
   - `FAILED_THEN_SUCCESS`
   - Redis 장애 시 `skippedRuleCodes`에 기록

3. 사용자 Context 기반 Rule
   - `NEW_DEVICE`
   - `LOCATION_CHANGE`
   - Redis 또는 PostgreSQL fallback 여부를 구현 단계에서 결정

이 순서로 실행하는 이유는 Redis 장애가 발생해도 단건 기반 Rule은 계속 수행하고, Redis 의존 Rule만 명확히 SKIPPED 처리하기 위함입니다.

## 6. Rule Test Matrix

| Rule | 정상 케이스 | 탐지 케이스 | 장애 케이스 | 기대 결과 |
|---|---|---|---|---|
| `HIGH_AMOUNT` | 50,000원 거래 | 1,000,000원 이상 거래 | Redis 장애 | Redis와 무관하게 평가 |
| `RAPID_TRANSACTION_COUNT` | 5분 내 2건 | 5분 내 5건 이상 | Redis down | `skippedRuleCodes` 기록 |
| `WINDOW_AMOUNT_SUM` | 5분 누적 100,000원 | 5분 누적 3,000,000원 이상 | Redis down | `skippedRuleCodes` 기록 |
| `NEW_DEVICE` | 기존 deviceId | 신규 deviceId | Redis down | skipped 또는 fallback |
| `LOCATION_CHANGE` | 동일 국가 | 30분 내 국가 변경 | Redis down | skipped 또는 fallback |
| `FAILED_THEN_SUCCESS` | 실패 1회 후 성공 | 실패 3회 후 성공 | Redis down | `skippedRuleCodes` 기록 |

## 7. Rule 기반 탐지 선택 이유

장점:

- 탐지 근거가 명확합니다.
- 테스트가 쉽습니다.
- 장애 시 어떤 rule이 skip됐는지 기록할 수 있습니다.
- 운영자가 threshold를 이해하기 쉽습니다.
- 초기 구현과 검증이 빠릅니다.

단점:

- 새로운 사기 패턴에 약합니다.
- threshold 튜닝이 필요합니다.
- 오탐이 발생할 수 있습니다.
- 사용자별 정상 패턴 차이를 충분히 반영하기 어렵습니다.

## 8. ML 기반 탐지 확장 후보

ML 기반 탐지는 이번 구현 범위에서 제외합니다.

이유:

- 실제 이상거래 데이터셋 확보가 어렵습니다.
- 금융 사기 데이터는 불균형 데이터라 평가가 어렵습니다.
- 모델 학습이 Kafka/Consumer/DLQ/운영 검증 범위를 흐릴 수 있습니다.
- 설명 가능성, 편향, 오탐/미탐 평가, 개인정보 보호까지 함께 다뤄야 합니다.

확장 시 검토 항목:

- 학습 데이터 품질
- 오탐/미탐 평가 기준
- explainability와 interpretability
- model drift
- 개인정보 보호
- 모델 버전 관리
- `model_version`
- feature store 후보
- batch scoring 또는 stream scoring 구조
- false positive / false negative 평가
- human review feedback label
- 모델 서빙 장애 시 fallback rule

## 9. 확장 구조

ML 기반 탐지는 다음 방식 중 하나로 확장합니다.

- batch scoring: 과거 거래와 탐지 결과를 기반으로 주기적 위험 점수 계산
- stream scoring: Consumer 처리 중 모델 서빙 API 또는 embedded model로 score 계산
- hybrid scoring: Rule score와 ML score를 함께 사용

초기 Rule Engine은 `matchedRuleCodes`, `skippedRuleCodes`, `riskScore`를 남기므로 향후 ML score를 추가하더라도 운영자가 판단 근거를 비교할 수 있습니다.

## 10. V2 PaySim Rule Strategy Planning

V2에서는 PaySim synthetic dataset을 사용하지만 AI/ML 모델은 구현하지 않습니다. Rule 기반 탐지를 유지하고, PaySim의 거래 유형과 잔액 변화 feature를 활용합니다.

### Runtime feature contract

Rule V2는 `TransactionBalanceFeatures` typed optional field를 입력으로 사용합니다.

필드:

- `oldBalanceOrig`
- `newBalanceOrig`
- `oldBalanceDest`
- `newBalanceDest`
- `sourceStep`

금지:

- `isFraud`를 Rule 입력으로 사용하지 않습니다.
- `sourceFlaggedFraud`를 Rule 입력으로 사용하지 않습니다.
- PaySim label을 Kafka runtime payload에 포함하지 않습니다.

### Initial V2 rules

초기 V2 구현 rule:

- `BALANCE_DRAIN`
- `ZERO_BALANCE_AFTER_TRANSFER`
- `TRANSFER_CASHOUT_PATTERN`

후속 후보:

- `DESTINATION_BALANCE_ANOMALY`
- `NEW_DESTINATION`
- `FAILED_THEN_SUCCESS`
- `AMOUNT_Z_SCORE`

### Evaluation policy

Offline evaluation과 online replay evaluation은 같은 Java Rule Engine과 같은 `ruleVersion`을 사용해야 합니다. Python으로 rule logic을 별도 재구현하지 않습니다.

PaySim label 기반 precision/recall/f1은 production ML model 성능이 아니라 Rule coverage 분석 지표입니다.
