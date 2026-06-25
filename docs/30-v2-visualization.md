# V2 Visualization Plan

## 1. Purpose

V2는 PaySim synthetic 거래 replay 결과를 표와 이미지로 남겨야 합니다. 이 문서는 구현 후 어떤 시각화 산출물을 만들지 정의합니다.

시각화는 운영/평가 evidence를 설명하기 위한 보조 자료이며, production 성능 보장을 의미하지 않습니다.

## 2. Visualization Candidates

| Artifact | Question | Output |
|---|---|---|
| Risk level distribution | 전체 거래가 어떻게 분류됐나 | `docs/images/v2-risk-level-distribution.png` |
| Rule match distribution | 어떤 rule이 가장 많이 발동했나 | `docs/images/v2-rule-match-distribution.png` |
| Action decision distribution | 탐지 결과가 어떤 후속 조치로 이어졌나 | `docs/images/v2-action-decision-distribution.png` |
| Rule-label confusion matrix | PaySim label과 Rule 결과가 얼마나 맞나 | `docs/images/v2-rule-label-confusion-matrix.png` |
| Case summary | 운영자 검토 case가 얼마나 생겼나 | `docs/29-v2-result-evidence.md` table |

## 3. Data Sources

후속 구현에서 다음 source를 사용합니다.

- `paysim-validation-report.json`
- `fraud_detection_results`
- `fraud_action_decisions`
- `fraud_cases`
- app-api/app-consumer Prometheus metrics

## 4. Privacy Rules

시각화에는 다음 값을 포함하지 않습니다.

- raw PaySim `nameOrig`
- raw PaySim `nameDest`
- full `userId`
- full `accountId`
- full `destinationAccountId`
- event-level raw payload

Charts는 집계값만 사용합니다.

## 5. Interpretation Rules

허용:

- Rule V2가 PaySim synthetic label을 기준으로 어떤 패턴을 잡고 놓쳤는지 설명
- Kafka replay 이후 action/case 분포를 설명
- 병목 후보와 한계를 문서화

금지:

- production fraud model 성능으로 주장
- 실제 금융 사기 차단 성능으로 주장
- 실제 계좌 차단/정지 결과로 표현

## 6. Completion Criteria

- risk level distribution chart 작성
- rule match distribution chart 작성
- action decision distribution chart 작성
- confusion matrix chart 또는 table 작성
- chart 생성 명령과 입력 source 문서화
