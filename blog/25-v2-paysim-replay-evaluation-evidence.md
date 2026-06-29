# V2 Phase 7 - PaySim Replay Evaluation을 운영 Evidence로 바꾸기

## 1. 왜 Phase 7을 했는가

V2 Phase 6에서는 PaySim label sidecar와 detection result export를 join해서 confusion matrix와 precision/recall을 계산하는 baseline을 만들었다.

하지만 여기서 멈추면 결과 파일 하나만 생긴다. 리뷰어가 더 궁금해하는 것은 숫자 자체보다 다음 질문이다.

- 어떤 입력으로 평가했는가?
- 어떤 command로 재현할 수 있는가?
- 이 숫자를 어디까지 해석해도 되는가?
- false positive와 false negative를 어떻게 판단했는가?
- 운영 배포 전 gate로 삼아도 되는 기준과 안 되는 기준은 무엇인가?

Phase 7은 이 질문에 답하기 위해 replay evaluation을 운영 evidence로 정리한 단계다.

## 2. 처음 의심한 문제

가장 먼저 의심한 것은 "이 결과를 실제 fraud detection 성능처럼 말해도 되는가?"였다.

답은 아니다.

PaySim은 synthetic dataset이다. 현재 Rule Engine도 실제 금융기관 fraud model이 아니라 프로젝트의 rule baseline이다. 따라서 precision/recall을 실제 운영 fraud detection 성능 보장값처럼 말하면 과장이다.

대신 이렇게 말할 수 있다.

> 같은 PaySim sample, 같은 label sidecar, 같은 detection result export를 입력으로 사용하면, 현재 rule baseline이 어떤 event를 TP/FP/TN/FN으로 분류했는지 재현할 수 있다.

이 차이를 문서에 남기는 것이 Phase 7의 핵심이었다.

## 3. 설계 판단

README에는 긴 설명을 넣지 않았다. README는 현재 범위, 핵심 command, 상세 문서 링크만 남기는 위치로 유지했다.

상세한 판단은 `docs/31-v2-replay-evaluation-evidence.md`에 두었다.

그 문서에는 다음을 분리했다.

- 현재 report가 실제로 산출하는 metric
- replay report에서 따로 얻는 throughput metric
- 아직 구현되지 않은 future metric
- gate로 사용할 수 있는 기준
- gate로 삼으면 안 되는 기준

특히 detection quality metric과 streaming operation metric을 분리했다.

Precision/recall은 label과 탐지 결과의 agreement를 설명한다. Consumer Lag, detection latency, DLQ count, Redis degraded count는 운영 상태를 설명한다. 둘은 같이 봐야 하지만 같은 지표처럼 섞으면 안 된다.

## 4. 구현한 것

Phase 7에서 새로 정리한 command는 두 가지다.

```bash
make evaluate-paysim-replay
make verify-v2-phase7
```

`make evaluate-paysim-replay`는 local detection result export가 있을 때 evaluation report를 만든다.

`make verify-v2-phase7`은 전체 PaySim raw data나 local DB export 없이 실행 가능한 check다. CI나 리뷰 환경에서는 이 경로가 더 안전하다. 이 target은 fixture 기반 report contract check까지 실행해서 report 생성, required field, 민감 필드 미포함, missing result 기본 정책을 확인한다.

Evaluator report에는 Phase 7 문서와 맞도록 `f1Score`, `totalEvents`, `fraudLabeledEvents`, `detectedFraudEvents`, `missedFraudEvents`, `falsePositiveEvents`, `truePositiveEvents`, `trueNegativeEvents`, `misclassifiedEvents`, `unmatchedResultEvents`, `evaluationExcludedRecords`를 추가했다.

`failedRecords`와 `invalidRecords`는 탐지 오분류가 아니라 pipeline/schema failure 의미로 분리했다. Missing result도 기본적으로 denominator에서 제외한다. 누락된 non-fraud row를 true negative로 세면 accuracy가 좋아 보일 수 있기 때문이다.

기존 confusion matrix와 precision/recall 구조는 유지했다.

## 5. 트러블슈팅

첫 번째 문제는 replay evaluation 결과를 탐지 성능처럼 과대 해석할 위험이었다.

해결은 숫자를 숨기는 것이 아니라 해석 범위를 제한하는 것이었다. Synthetic dataset, rule-based baseline, local export contract라는 한계를 문서에 명시했다.

두 번째 문제는 평가 지표와 운영 지표가 섞이는 것이었다.

Precision/recall이 좋아도 Consumer Lag이 쌓이면 실시간 탐지는 지연된다. 반대로 Consumer Lag이 낮아도 rule이 fraud label을 놓치면 detection quality는 낮다. 그래서 Phase 7 문서에서는 quality metric과 operation metric을 별도 섹션으로 나눴다.

세 번째 문제는 threshold 조정이었다.

Threshold를 낮추면 recall은 좋아질 수 있다. 하지만 false positive가 늘면 운영자는 더 많은 review candidate를 처리해야 한다. 그래서 threshold 변경은 precision, recall, false positive count, operator workload를 함께 봐야 한다고 정리했다.

## 6. 면접 답변으로 연결하기

질문: "이 단계에서 무엇을 구현했나요?"

답변:

> Phase 6에서 만든 PaySim replay evaluation baseline을 Phase 7에서 재현 가능한 evidence로 정리했습니다. Makefile command, report metric, 해석 기준, gate 가능/불가 기준, troubleshooting 기록을 추가했고, evaluator report에는 f1Score와 주요 event count alias 및 fixture 기반 report contract check를 보강했습니다.

질문: "왜 PaySim 결과를 그대로 성능이라고 말하지 않았나요?"

답변:

> PaySim은 synthetic dataset이고 현재 rule은 production model이 아니기 때문입니다. 그래서 precision/recall을 실제 금융 fraud 성능으로 주장하지 않고, rule baseline이 label sidecar와 얼마나 맞는지 보는 재현 가능한 평가 evidence로 제한했습니다.

질문: "이 평가 결과를 배포 전에 어떻게 활용할 수 있나요?"

답변:

> Script가 정상 종료되는지, report가 생성되는지, required fields가 있는지, label/result contract가 strict하게 통과하는지는 gate로 볼 수 있습니다. 하지만 precision/recall 절대값이나 운영 fraud 성능은 아직 gate로 삼지 않습니다. 그 기준은 운영 데이터와 human review 결과가 필요합니다.

질문: "False positive와 false negative를 어떻게 해석했나요?"

답변:

> False negative는 fraud-labeled event를 놓치는 것이므로 위험합니다. False positive는 실제 운영에서는 review workload와 사용자 영향으로 이어질 수 있습니다. 그래서 threshold tuning은 recall만 높이는 방향이 아니라 precision, recall, action workload를 함께 보는 방향으로 정리했습니다.

## 7. 남은 한계

Phase 7은 evaluation evidence 체계를 만든 단계다.

아직 남은 일은 다음과 같다.

- Rule Engine V2 threshold tuning
- rule version과 config snapshot을 report에 저장
- ActionDecision 구현 후 `action_decision_distribution` 산출
- Grafana dashboard와 replay evaluation report 연결
- model-based baseline과 rule-based baseline 비교
