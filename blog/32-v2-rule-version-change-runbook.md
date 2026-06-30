# V2 Phase 14 - ruleVersion 변경 전에 확인해야 할 것들

## 왜 Phase 14를 했는가

Phase 11에서는 Java Rule Engine baseline과 Python evaluator `ruleVersion`이 drift 나지 않도록 했다.

Phase 12에서는 detection result 단위로 `ruleVersion`을 저장하고, evaluator strict mode로 per-result coverage를 확인할 수 있게 했다.

Phase 13에서는 현재 app-consumer active ruleVersion과 app-api에 저장된 historical result ruleVersion을 운영자가 구분해서 볼 수 있게 했다.

하지만 버전이 보인다고 해서 변경 관리가 끝나는 것은 아니다. 실제 운영에서는 ruleVersion을 바꾸기 전 무엇을 확인해야 하는지, 바꾼 뒤 어떤 evidence를 봐야 하는지, 어떤 조건이면 hold 또는 rollback 판단을 해야 하는지가 필요하다.

V2 Phase 14는 자동 rollback 구현이 아니다. RuleVersion 변경에 대한 runbook, evidence template, rollback/hold decision criteria를 정리한 단계다.

## 처음 의심한 문제

처음에는 ruleVersion observability 다음에 바로 Rule Engine V2 rule을 추가해도 되는지 의심했다.

- ruleVersion만 바꾸면 바로 배포해도 되는가
- active ruleVersion과 stored result summary가 다르면 장애인가
- thresholdVersion 변경과 ruleVersion 변경을 같이 해도 되는가
- local/manual curl check를 CI-safe evidence라고 말해도 되는가
- all-time summary endpoint를 운영 dashboard로 바로 써도 되는가
- rollback readiness라고 쓰면 automatic rollback을 구현한 것처럼 보이지 않는가

결론은 rule을 늘리기 전에 변경 관리 기준부터 고정하는 것이었다.

## 설계 판단

첫 번째 판단은 pre-change와 post-change checklist를 분리하는 것이었다.

Pre-change에서는 Java/Python version drift, per-result strict mode, data policy, final-check를 본다. 이 단계는 local app startup 없이 확인할 수 있어야 한다.

Post-change에서는 runtime evidence를 본다. app-consumer `/actuator/info`의 activeRuleVersion, app-api stored ruleVersion summary, 신규 detection result의 ruleVersion 저장 여부를 확인한다. 이 check는 local app, network, admin token이 필요할 수 있으므로 CI-safe로 넣지 않았다.

두 번째 판단은 active runtime version과 stored historical version을 분리하는 것이다.

배포 직후 stored summary에 old version과 new version이 함께 보이는 것은 정상일 수 있다. Stored result는 과거 결과가 만들어진 시점의 version이고, activeRuleVersion은 현재 consumer runtime의 version이다. 문제는 expected version이 보이지 않거나, 예상하지 않은 version이 새 row에 저장되거나, 신규 result에서 ruleVersion이 빠지는 경우다.

세 번째 판단은 all-time summary endpoint의 운영 비용을 제한해서 설명하는 것이다.

현재 endpoint는 local/admin traceability evidence다. Production dashboard로 쓰려면 bounded time range와 `(rule_version, detected_at)` index 후보가 필요하다.

## 트러블슈팅

첫 번째 문제는 active ruleVersion과 stored summary mismatch를 장애로 오해하는 것이다.

배포 직후에는 stored result에 이전 version이 남아 있을 수 있다. 이것은 historical result의 의미와 runtime metadata의 의미가 다르기 때문에 자연스러운 상태다. Runbook에서는 정상 mixed state와 unexpected version을 구분한다.

두 번째 문제는 rollback readiness를 automatic rollback처럼 쓰는 것이다.

이번 단계는 자동 배포 되돌리기 기능이 아니다. 어떤 evidence가 실패하면 hold하거나 rollback 준비를 해야 하는지 decision criteria를 정리한 것이다. 그래서 Completed와 Future Work를 분리했다.

세 번째 문제는 all-time summary endpoint를 production dashboard로 바로 쓰는 것이다.

전체 `fraud_detection_results`를 group by 하는 query는 데이터가 커질수록 비용이 커질 수 있다. 운영 dashboard로 바꾸려면 기간 조건과 index 전략이 먼저 필요하다.

네 번째 문제는 ruleVersion과 thresholdVersion을 한 번에 바꾸는 것이다.

Rule logic change와 threshold boundary change가 섞이면 precision/recall 변화 원인을 설명하기 어렵다. 둘을 분리하거나, 같은 PR에서 바꾼다면 이유와 기대 영향을 명확히 기록해야 한다.

## 검증 경계

CI-safe check:

```bash
make verify-paysim-rule-version-contract
make verify-paysim-result-rule-version-contract
make verify-v2-phase13
./gradlew test
make final-check
```

Local/manual runtime drill:

```bash
curl http://localhost:8081/actuator/info
curl -H "X-Admin-Token: <local-admin-token>" \
  http://localhost:8080/api/v1/admin/fraud-results/rule-version-summary
```

이번 문서 작업에서는 local app startup과 curl drill을 실행하지 않았다. Runbook에 실행 절차와 evidence template만 남겼다.

## Review Q&A

질문: 이 단계에서 무엇을 했나요?

답변: V2 Phase 14에서는 ruleVersion 변경 전후 운영자가 확인해야 할 evidence를 runbook으로 정리했습니다. Pre-change, post-change, hold, rollback criteria를 나누고, CI-safe check와 local/manual runtime drill을 분리했습니다.

질문: 왜 ruleVersion observability 다음에 runbook이 필요했나요?

답변: Version을 볼 수 있는 것과 안전하게 변경을 판단하는 것은 다릅니다. 배포 전 verifier, 배포 후 runtime/admin evidence, unexpected version 조사 기준이 있어야 운영 변경 관리가 가능합니다.

질문: rollback readiness와 automatic rollback은 어떻게 다른가요?

답변: Rollback readiness는 어떤 조건에서 hold 또는 rollback을 판단할지 기준과 evidence template을 갖춘 상태입니다. Automatic rollback은 배포 시스템이 자동으로 이전 version으로 되돌리는 기능인데, 이번 Phase에서는 구현하지 않았습니다.

질문: active ruleVersion과 stored result ruleVersion은 어떻게 다르게 해석하나요?

답변: active ruleVersion은 현재 app-consumer runtime 기준입니다. Stored result ruleVersion은 각 fraud result가 생성된 당시의 기준입니다. 배포 직후 둘이 섞여 보일 수 있으며, 예상하지 않은 version이나 신규 missing version이 조사 대상입니다.

질문: CI-safe check와 local/manual drill을 왜 분리했나요?

답변: CI-safe check는 raw PaySim data, local app startup, admin token, DB export 없이 실행되어야 합니다. Actuator/admin curl check는 network와 auth, 실행 중인 app에 의존하므로 local/manual evidence로만 둡니다.

질문: 이 작업이 백엔드/DevOps engineering record에서 어떤 의미가 있나요?

답변: 단순히 API나 metric을 추가하는 데서 멈추지 않고, 변경 전후 확인 절차와 rollback 판단 기준까지 문서화했습니다. 비동기 fraud pipeline에서 version traceability를 운영 변경 관리 evidence로 연결한 작업입니다.
