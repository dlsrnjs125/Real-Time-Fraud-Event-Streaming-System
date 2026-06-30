# V2 Phase 15 - 기능을 더 만들지 않고 evidence를 닫은 이유

## 왜 Phase 15를 했는가

Phase 7부터 Phase 14까지 PaySim evaluation, native mapping, threshold regression, ruleVersion traceability, runtime observability, change runbook이 쌓였다.

여기서 바로 기능을 더 추가하면 문서와 evidence가 흩어질 수 있다. 그래서 Phase 15에서는 새 API나 rule을 만들지 않고, 지금까지 만든 evidence를 최종 요약으로 닫았다.

README를 다시 길게 만들지 않고, 최종 요약은 `docs/39-v2-final-evidence-closure.md`에 분리했다.

## 처음 의심한 문제

처음에는 여기서 Rule Engine V2 rule을 추가하는 것이 더 좋아 보일 수 있었다.

- 지금 더 기능을 추가하는 게 맞는가
- README에 최종 요약을 넣으면 다시 길어지지 않는가
- `make final-check`가 무엇을 보장하는지 충분히 명확한가
- implemented와 future work가 섞이지 않는가
- ruleVersion traceability를 detection quality로 오해하지 않게 설명했는가

결론은 기능 추가보다 evidence closure가 먼저라는 것이었다.

## 설계 판단

첫 번째 판단은 최종 요약을 README가 아니라 docs에 두는 것이었다.

README는 프로젝트 entry point다. Phase별 history, evidence map, 판단 기준, 한계까지 넣으면 다시 운영 매뉴얼처럼 길어진다.

두 번째 판단은 Phase Map에 problem, decision, output, verification, limitation을 함께 두는 것이었다.

단순히 "무엇을 만들었다"가 아니라 "왜 만들었고, 무엇을 검증했고, 무엇은 검증하지 않았는지"를 같이 보여주기 위해서다.

세 번째 판단은 Decision Notes를 문서에 넣는 것이었다.

이 프로젝트는 기능 목록보다 설계 판단을 설명할 수 있어야 한다. 그래서 PaySim 한계, denominator policy, ruleVersion/thresholdVersion 분리, final-check 한계를 짧게 답할 수 있도록 정리했다.

네 번째 판단은 AI-assisted 문서를 그대로 받아들이지 않는 것이다.

계속 기능을 추가하는 방식은 오히려 완성도를 낮출 수 있다. 이번 단계에서는 overclaim, implemented/future work 혼동, README 비대화, raw data commit 위험을 검토하는 쪽을 택했다.

## 트러블슈팅

첫 번째 문제는 README가 다시 비대해지는 것이다.

최종 요약을 README에 넣으면 README가 다시 phase history와 command matrix로 커진다. 그래서 README는 그대로 두고, 세부 내용은 docs와 blog에 분리했다.

두 번째 문제는 implemented와 future work가 섞이는 것이다.

Dashboard, automatic rollback, deployment changelog, historical backfill은 아직 future work다. Final summary에서는 implemented, local/manual, future work를 따로 나눴다.

세 번째 문제는 `make final-check`를 production 성능 보장으로 오해하는 것이다.

`make final-check`는 build/test/config/script/data verifier를 묶은 readiness guardrail이다. Production fraud model accuracy, local runtime curl, full PaySim replay를 보장하지 않는다.

네 번째 문제는 ruleVersion traceability를 detection quality로 오해하는 것이다.

ruleVersion이 잘 남는다는 것은 어떤 rule baseline이 사용됐는지 추적할 수 있다는 뜻이다. Precision, recall, false positive 개선과는 다른 문제다.

## 검증

이번 Phase는 문서 closure 단계다. Local app startup과 curl drill은 실행하지 않았다.

CI-safe 검증 후보는 다음이다.

```bash
make verify-v2-phase13
./gradlew test
make final-check
```

실제 실행 결과는 roadmap과 review 문서에 기록한다.

## 남긴 기준

이번 V2를 정리하면서 끝까지 유지한 기준은 세 가지였다.

첫째, PaySim 결과를 production fraud 성능으로 쓰지 않는다. PaySim은 synthetic dataset이므로 replay/evaluation contract와 version traceability evidence로 제한했다.

둘째, ruleVersion 추적성을 detection quality와 혼동하지 않는다. 어떤 rule baseline이 사용됐는지 추적할 수 있다는 것과 precision/recall이 개선됐다는 것은 다른 문제다.

셋째, final-check를 운영 인증이 아니라 repository readiness guardrail로 제한한다. build/test/config/script/data verifier는 통과할 수 있지만, local runtime curl, full PaySim replay, production latency를 보장하지 않는다.

문서 초안도 같은 기준으로 검토했다. README 비대화, implemented/future work 혼동, raw data commit 위험, final-check 의미 과장을 줄이는 쪽을 우선했다.
