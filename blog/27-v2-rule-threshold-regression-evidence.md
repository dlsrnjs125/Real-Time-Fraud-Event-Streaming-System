# V2 Phase 9 - Threshold를 올리거나 낮추기 전에 Regression Evidence부터 만들기

## 1. 왜 Phase 9를 했는가

Phase 7에서는 PaySim replay evaluation report를 만들었다.

Phase 8에서는 PaySim native type을 production transaction type처럼 과장하지 않도록 mapping contract를 정리했다.

그 다음 남은 문제는 rule이나 threshold가 바뀔 때 metric 변화가 무엇 때문인지 설명하는 것이다.

Threshold를 낮추면 recall이 좋아 보일 수 있다. 하지만 false positive와 review workload가 같이 늘 수 있다. Threshold를 높이면 precision은 좋아질 수 있지만 missed fraud risk가 늘 수 있다.

Phase 9의 목적은 threshold를 조정해 숫자를 좋아 보이게 만드는 것이 아니다. Rule/threshold 변경 영향을 같은 evaluation contract 위에서 재현 가능하게 비교하는 것이다.

## 2. 처음 의심한 문제

처음 의심한 질문은 단순했다.

- recall이 올라가면 무조건 좋은가?
- threshold를 낮추면 운영자가 처리해야 할 review queue는 얼마나 늘어나는가?
- precision/recall이 바뀐 이유가 rule 변경인지 threshold 변경인지 어떻게 구분하는가?
- fixture check가 통과하면 실제 운영 성능이 보장되는가?
- F1만 보고 threshold를 선택해도 되는가?

답은 모두 조심스럽다.

Fraud detection은 metric만의 문제가 아니다. False negative는 위험 거래를 놓치는 문제이고, false positive는 review workload와 사용자 영향으로 이어진다.

## 3. 설계 판단

Phase 9에서는 version field를 분리했다.

```text
mappingPolicyVersion
evaluationContractVersion
evaluationPolicyVersion
ruleVersion
thresholdVersion
```

`ruleVersion`은 rule logic의 의미가 바뀔 때 바뀐다. `thresholdVersion`은 risk/action boundary가 바뀔 때 바뀐다. 둘을 섞으면 metric 변화 원인을 설명하기 어렵다.

Evaluation report에는 workload summary도 넣었다.

```text
reviewCandidateEvents
reviewCandidateRate
blockedCandidateEvents
blockedCandidateRate
actionDecisionDistribution
operatorWorkloadSummary
```

CI-safe fixture regression과 local/manual evidence도 분리했다. Fixture check는 report contract와 semantic regression을 검증한다. Full PaySim replay와 detection result export는 local evidence로 남긴다.

## 4. 트러블슈팅

첫 번째 문제는 threshold down이 recall 개선처럼 보이는 경우다.

Threshold를 낮추면 더 많은 event가 positive가 된다. Fraud-labeled event를 더 잡을 수 있지만, non-fraud event도 같이 positive가 될 수 있다. 그래서 Phase 9 report는 false positive와 reviewCandidateRate를 함께 본다.

두 번째 문제는 F1만 보고 운영 최적화로 착각하는 것이다.

F1은 precision과 recall의 균형을 보는 지표다. 하지만 review queue가 감당 가능한지, block 후보가 과도한지는 알려주지 않는다. 그래서 actionDecisionDistribution과 operatorWorkloadSummary를 추가했다.

세 번째 문제는 fixture regression을 production 성능 보장으로 오해하는 것이다.

Fixture check는 작은 입력에서 expected metric과 report schema가 깨지지 않았는지 확인한다. 실제 운영 성능, Consumer Lag, p95 latency, DLQ, Redis degraded behavior는 별도 evidence가 필요하다.

## 5. 면접 답변으로 연결하기

질문: "이 단계에서 무엇을 구현했나요?"

답변:

> Phase 9에서는 rule이나 threshold를 바꿨을 때 precision, recall, F1이 왜 변했는지 설명할 수 있도록 `ruleVersion`, `thresholdVersion`, `evaluationPolicyVersion`을 evaluation report에 추가했습니다. 또한 threshold 변경이 operator workload에 주는 영향을 보기 위해 reviewCandidateRate, blockedCandidateRate, actionDecisionDistribution을 추가했습니다.

질문: "왜 threshold tuning을 바로 하지 않았나요?"

답변:

> Threshold tuning은 숫자를 좋아 보이게 만들 수 있지만 false positive, missed fraud, review workload를 함께 악화시킬 수 있습니다. 먼저 versioning과 regression evidence를 만든 뒤 같은 조건에서 변경 전후를 비교하는 것이 더 안전하다고 판단했습니다.

질문: "ruleVersion과 thresholdVersion을 왜 분리했나요?"

답변:

> Rule logic이 바뀐 것과 threshold boundary가 바뀐 것은 metric 변화 원인이 다릅니다. 둘을 같은 version으로 묶으면 precision/recall 변화가 rule 때문인지 threshold 때문인지 설명하기 어렵습니다.

질문: "precision/recall/F1 외에 어떤 지표를 봐야 하나요?"

답변:

> False positive, missed fraud, reviewCandidateRate, blockedCandidateRate, actionDecisionDistribution을 함께 봐야 합니다. 실제 replay에서는 Consumer Lag, detection latency, DLQ count, Redis degraded count도 같이 봐야 합니다.

질문: "CI regression check와 local/manual evidence를 왜 분리했나요?"

답변:

> CI에서는 raw PaySim이나 local DB export 없이 fixture 기반 contract와 semantic value를 검증합니다. Full replay와 detection result export는 환경 의존성이 크고 raw/full data를 커밋하면 안 되므로 local/manual evidence로 분리했습니다.

질문: "이 작업이 백엔드/DevOps 기술 설명에서 어떤 의미가 있나요?"

답변:

> 단순히 fraud score를 계산하는 것이 아니라, rule/threshold 변경이 시스템 지표와 운영 workload에 어떤 영향을 주는지 재현 가능한 evidence로 남겼다는 점이 중요합니다. 배포 전 regression gate, 데이터 정책, local/manual evidence 분리를 함께 설계한 작업입니다.

## 6. 남은 한계

- Consumer Rule Engine version과 evaluator ruleVersion은 아직 자동 연결되지 않았다.
- Full replay rejected eventId 전체 export는 후속 개선이다.
- Threshold policy는 evaluation evidence용 기준이며 실제 금융 fraud policy가 아니다.
- Fixture regression은 production fraud 성능 보장이 아니다.
