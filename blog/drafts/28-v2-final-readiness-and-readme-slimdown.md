# V2 Phase 10 - README를 줄이고 Evidence를 정리한 이유

## 1. 왜 Phase 10을 했는가

V2 Phase 7에서는 PaySim replay evaluation baseline을 evidence로 정리했고, Phase 8에서는 PaySim native type을 production transaction semantics로 과장하지 않도록 mapping contract를 만들었다. Phase 9에서는 ruleVersion, thresholdVersion, evaluationPolicyVersion, riskScoreCoverage, operator workload summary를 추가해 threshold 변경을 성능 자랑이 아니라 regression evidence로 해석할 수 있게 했다.

그 결과 기능보다 문서와 evidence가 더 많이 쌓였다. 이 상태에서 README에 Phase별 세부 설명과 command를 계속 붙이면 README가 entry point가 아니라 운영 매뉴얼처럼 변한다.

Phase 10은 새로운 detection 성능 개선 단계가 아니다. README, docs, blog, scripts README의 역할을 분리하고, final readiness가 무엇을 보장하고 무엇을 보장하지 않는지 정리하는 단계다.

## 2. 처음 의심한 문제

- README에 Phase 7/8/9 상세 설명이 들어가도 되는가?
- 처음 보는 사람이 README에서 `thresholdVersion`, `mappingPolicyVersion`까지 읽어야 하는가?
- CI-safe command와 local/manual command가 한 곳에 섞이면 어떤 문제가 생기는가?
- final readiness가 production fraud 성능 보장처럼 보일 위험은 없는가?
- future work가 구현 완료처럼 읽히지는 않는가?

## 3. 설계 판단

README는 프로젝트 진입점으로 줄였다. 한 줄 설명, 문제 정의, 아키텍처, 대표 실행/검증 명령, 문서 링크만 빠르게 보여주는 것이 목적이다.

`scripts/data/README.md`는 PaySim command matrix를 담당한다. 어떤 command가 raw PaySim을 요구하는지, local app-api가 필요한지, detection result export가 필요한지, CI-safe인지 이 문서에서 구분한다.

docs는 evidence와 contract의 source로 둔다. Phase 7/8/9의 report field, mapping policy, threshold policy, denominator 해석은 README가 아니라 docs에 있어야 한다.

blog는 트러블슈팅과 설명용 스토리를 담당한다. 왜 이런 분리를 했는지, 어떤 오해를 막으려 했는지, 어떤 기준으로 설명할 수 있는지를 남긴다.

`make final-check`는 대표 readiness gate로 둔다. Gradle build, Docker Compose config, script syntax, data policy, Phase 7/8/9 verifier를 묶어 재현성과 contract guardrail을 확인한다. 이 명령은 production fraud 성능을 보장하지 않는다.

## 4. 트러블슈팅

### README가 Phase별 상세 구현으로 비대해지는 문제

문제는 README가 너무 친절해질 때 생긴다. Phase 7/8/9의 command와 metric 해석을 모두 넣으면 처음 보는 사람은 프로젝트의 핵심 흐름보다 세부 evidence 용어를 먼저 만나게 된다.

해결은 README를 줄이는 것이었다. README에는 V2 PaySim Evaluation이 무엇인지, raw/full processed data를 커밋하지 않는다는 점, 상세 문서 링크만 남겼다.

### CI-safe command와 local/manual command가 섞이는 문제

PaySim workflow에는 fixture만으로 돌릴 수 있는 command와 raw dataset, local app-api, detection result export가 필요한 command가 섞여 있다.

이 둘을 README에 같이 나열하면 사용자는 어떤 명령이 바로 통과해야 하는지 알기 어렵다. 그래서 command matrix를 `scripts/data/README.md`로 옮기고, README에는 대표 검증 경로만 남겼다.

### final readiness를 production fraud 성능 보장으로 오해하는 문제

`final readiness`라는 말은 완성도 높은 느낌을 준다. 하지만 여기서의 readiness는 production fraud model quality가 아니라 evidence/readiness/documentation consistency다.

그래서 `docs/34-v2-final-readiness.md`에 "PaySim is synthetic", "not production fraud model performance guarantee", "replay-supported types are not production-supported semantics"를 분리해서 명시했다.

## 5. 설계 판단으로 정리

질문: "왜 README를 줄였나요?"

답변:

> README는 프로젝트 진입점이기 때문입니다. Phase 7~9에서 evaluation report, native type mapping, threshold regression evidence가 쌓이면서 README가 운영 매뉴얼처럼 길어질 위험이 있었습니다. 그래서 README는 문제 정의, 아키텍처, 대표 검증 명령, 문서 링크만 남기고 상세 내용은 docs와 scripts README로 분리했습니다.

질문: "상세한 Phase 7/8/9 내용은 어디에 두었나요?"

답변:

> Phase 7 replay evaluation evidence는 `docs/31`, Phase 8 native replay contract는 `docs/32`, Phase 9 rule/threshold regression evidence는 `docs/33`에 두었습니다. 실제 command matrix는 `scripts/data/README.md`에서 CI-safe와 local/manual로 구분했습니다.

질문: "final-check는 무엇을 보장하나요?"

답변:

> `make final-check`는 Gradle build, Docker Compose config, shell script syntax, data policy check, Phase 7/8/9 fixture verifier를 실행하는 대표 readiness gate입니다. 재현성과 contract guardrail을 확인하지만, production fraud model 성능을 보장하지는 않습니다.

질문: "final readiness가 실제 fraud detection 성능 보장과 다른 이유는 무엇인가요?"

답변:

> PaySim은 synthetic dataset이고, fixture verifier는 report semantics와 regression impact를 검증합니다. 실제 운영 fraud 성능을 말하려면 production-like data, operational metrics, model validation, alert outcome feedback이 필요합니다.

질문: "docs/blog를 어떻게 역할 분리했나요?"

답변:

> docs는 contract와 evidence의 source로 두고, blog는 설계 판단과 트러블슈팅 스토리로 두었습니다. README는 그 둘로 들어가는 entry point 역할만 하게 했습니다.

질문: "이 작업이 백엔드/DevOps 기술 설명에서 어떤 의미가 있나요?"

답변:

> 기능 구현만큼 중요한 것은 검증 경계와 운영 가능 범위를 명확히 하는 것입니다. Phase 10에서는 CI-safe check, local/manual evidence, future work를 분리해 재현 가능한 검증 체계를 정리했습니다.

## 6. 남은 한계

- full PaySim replay는 local/manual 영역이다.
- local detection result export는 evaluator가 직접 만들지 않는다.
- app-consumer Rule Engine version과 evaluation report direct integration은 후속 작업이다.
- threshold before/after comparison report는 아직 별도 자동 산출물이 아니다.
- dashboard integration과 model baseline comparison은 future work다.
