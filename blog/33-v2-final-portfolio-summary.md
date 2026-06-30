# V2 Phase 15 - 기능을 더 만들지 않고 evidence를 닫은 이유

## 왜 Phase 15를 했는가

Phase 7부터 Phase 14까지 PaySim evaluation, native mapping, threshold regression, ruleVersion traceability, runtime observability, change runbook이 쌓였다.

여기서 바로 기능을 더 추가하면 문서와 evidence가 흩어질 수 있다. 그래서 Phase 15에서는 새 API나 rule을 만들지 않고, 지금까지 만든 evidence를 최종 요약으로 닫았다.

README를 다시 길게 만들지 않고, 최종 요약은 `docs/39-v2-final-portfolio-summary.md`에 분리했다.

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

README는 프로젝트 entry point다. Phase별 history, evidence map, Q&A, 한계까지 넣으면 다시 운영 매뉴얼처럼 길어진다.

두 번째 판단은 Phase Map에 problem, decision, output, verification, limitation을 함께 두는 것이었다.

단순히 "무엇을 만들었다"가 아니라 "왜 만들었고, 무엇을 검증했고, 무엇은 검증하지 않았는지"를 같이 보여주기 위해서다.

세 번째 판단은 Interview Answer Pack을 문서에 넣는 것이었다.

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

## Review Q&A

질문: 왜 여기서 기능 추가를 멈췄나요?

답변: Phase 7~14에서 evidence가 충분히 쌓였기 때문에, 바로 새 rule을 추가하면 implemented/future work 경계가 흐려질 수 있었습니다. 그래서 먼저 evidence를 닫고 final-check와 문서 링크를 정리했습니다.

질문: V2에서 가장 중요한 설계 판단은 무엇인가요?

답변: PaySim 결과를 production fraud 성능으로 과장하지 않고, replay/evaluation contract와 version traceability evidence로 제한한 것입니다.

질문: 어떤 검증 evidence가 있나요?

답변: Phase 7/8/9/11/12 fixture verifiers, Java tests, data policy check, final-check가 있습니다. Local/manual full replay와 runtime curl은 별도 evidence로 분리했습니다.

질문: 무엇을 과장하지 않으려고 했나요?

답변: PaySim metric을 production model accuracy로, ruleVersion traceability를 detection quality로, rollback readiness를 automatic rollback으로, final-check를 production certification으로 과장하지 않으려고 했습니다.

질문: 이 프로젝트가 백엔드/DevOps 역량을 어떻게 보여주나요?

답변: Kafka 비동기 처리, PostgreSQL idempotency, Redis degraded mode, DLQ/reprocessing, Actuator/metrics, CI-safe verifier, data guardrail, runbook과 change readiness를 함께 다룹니다.

질문: AI는 어떻게 활용하고 검증했나요?

답변: AI는 문서 구조와 체크리스트 초안을 만드는 데 활용했지만, 최종 반영 전 overclaim, README 비대화, implemented/future work 혼동, raw data commit 위험, final-check 의미를 사람이 검토했습니다.
