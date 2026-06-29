# V2 PaySim Replay Evaluation Baseline

## 1. 문제

V2 Phase 5에서 PaySim runtime event를 app-api로 replay할 수 있게 됐지만, replay 이후 생성된 fraud detection result가 PaySim label과 얼마나 맞는지는 아직 설명할 수 없었습니다.

Rule Engine V2를 바로 만들면 어떤 metric이 좋아졌는지 비교하기 어렵습니다. 먼저 current rule output과 PaySim label sidecar를 연결하는 baseline evaluation pipeline이 필요했습니다.

## 2. 초기 설계

Phase 6에서는 DB에 직접 연결하지 않습니다. Detection result는 local export JSONL input contract로만 정의합니다.

입력은 세 가지입니다.

- PaySim label sidecar
- detection result export JSONL
- optional replay report

Join key는 `eventId` 하나로 제한합니다. Prefix replay를 사용한 경우에는 detection result의 prefixed eventId에서 prefix를 제거한 뒤 original PaySim label eventId와 join합니다.

Risk level은 `LOW < MEDIUM < HIGH < CRITICAL` 순서로 보고, 기본 threshold는 `MEDIUM`입니다. 따라서 `MEDIUM`, `HIGH`, `CRITICAL`은 predicted positive, `LOW`는 predicted negative가 됩니다.

## 3. 구현

추가한 script:

```text
scripts/data/evaluate_paysim_replay_results.py
```

주요 출력:

- TP, FP, TN, FN
- precision
- recall
- false positive rate
- false negative rate
- accuracy
- risk level distribution
- fraud/non-fraud by risk level
- ruleCode distribution
- warnings

Makefile target:

```bash
make evaluate-paysim-sample
make evaluate-paysim-sample-no-replay-report
```

Evaluation report는 `data/processed/paysim-evaluation-report.json`에 생성됩니다. 이 파일은 local evidence이며 Git에 커밋하지 않습니다.

## 4. 트러블슈팅

첫 번째 경계는 missing detection result였습니다. Replay success와 Consumer result persistence는 다른 단계이므로 result가 없는 row가 생길 수 있습니다. 기본값은 missing result를 denominator에 포함합니다. Fraud label이면 FN, non-fraud label이면 TN으로 계산하고 `missingResults`를 증가시킵니다.

두 번째 경계는 eventId prefix였습니다. Prefix replay는 collision을 줄이지만 label sidecar의 original eventId와 바로 join되지 않습니다. `--event-id-prefix`로 prefix를 제거하도록 했습니다.

세 번째 경계는 replay rejected event입니다. HTTP 요청 전 rejected된 event는 Rule Engine에 도달하지 않았으므로 evaluation denominator에서 제외합니다. 단, Phase 5 replay report의 failure summary는 bounded이므로 모든 rejected eventId가 없을 수 있고, 이 경우 warning을 남깁니다.

네 번째 경계는 denominator 0입니다. Positive prediction이 하나도 없으면 precision은 0이 아니라 계산 불가입니다. 이런 metric은 `null`로 기록합니다.

다섯 번째 경계는 label leakage입니다. Detection result export에 `isFraud`가 들어가면 evaluation이 오염됩니다. Script는 label field가 result export에 있으면 실패합니다.

## 5. 검증

Fixture 기반 unittest로 다음을 확인했습니다.

- perfect prediction metric
- TP/FP/TN/FN 계산
- risk threshold 변경
- missing result 포함/제외
- eventId prefix join
- duplicate eventId strict failure
- unsupported riskLevel failure
- label leakage failure
- replay rejected event exclusion
- denominator 0 metric null
- report에 raw identifier/token/request/response body 미저장
- sampleEventIds 최대 10개 제한

실제 local evaluation은 detection result export가 있을 때만 실행합니다. CI에서는 DB/API를 기동하지 않고 fixture test와 data policy check만 실행합니다.

## 6. 남은 한계

Rule Engine V2는 다음 Phase에서 구현합니다.

1,000건 sample metric은 pipeline validation evidence입니다. 전체 PaySim 성능이나 production fraud model 성능으로 해석하지 않습니다.

Full PaySim evaluation은 local processed output, actual replay, detection result export가 준비된 뒤 별도 evidence 단계에서 기록합니다.
