# V2 Phase 13 - ruleVersion을 운영자가 확인할 수 있게 만든 이유

## 왜 했는가

Phase 12에서는 detection result에 `ruleVersion`을 남겼다.

하지만 저장된 result에 version이 있다는 것과 현재 consumer가 어떤 version으로 실행 중인지 아는 것은 다른 문제다. 운영자는 rule 배포 전후에 현재 active version과 과거 stored result version을 구분해서 봐야 한다.

Phase 13은 성능 개선이 아니라 운영 추적성과 진단 가능성을 높이는 단계다.

## 처음 의심한 문제

처음에는 result에 `ruleVersion`이 저장되어 있으면 충분한지부터 의심했다.

- 현재 runtime active ruleVersion과 stored result ruleVersion은 같은 의미인가
- ruleVersion을 metric tag로 넣어도 되는가
- actuator info를 열면 endpoint exposure가 커지지 않는가
- app-api가 app-consumer runtime memory를 알아야 하는가
- admin query에 summary를 추가해도 기존 조회가 깨지지 않는가

결론은 runtime metadata와 stored result query를 분리하는 것이었다.

## 설계 판단

app-consumer는 active ruleVersion의 source다.

그래서 새 custom endpoint를 만들기보다 이미 노출 중인 Actuator info에 작은 metadata를 추가했다.

```json
{
  "fraudRule": {
    "activeRuleVersion": "rule-v2-baseline-v1",
    "versionSource": "app-consumer",
    "scope": "fraud-rule-engine-baseline"
  }
}
```

app-api는 stored result query의 owner다.

그래서 app-api에는 stored fraud result의 ruleVersion summary를 추가했다. 여기서도 legacy null row를 version distribution에 섞지 않고 `legacyMissingResults`로 분리했다. 이 endpoint는 full list query나 ruleVersion filter가 아니라 local/admin traceability evidence이며, production dashboard로 쓰려면 bounded time range와 `(rule_version, detected_at)` index 후보가 필요하다.

Metric은 추가하지 않았다. `ruleVersion` 자체는 bounded라 tag 후보가 될 수 있지만, 이번 Phase에서는 Actuator info와 admin summary로 충분했다. 특히 `userId`, `eventId`, `traceId` 같은 값은 metric tag에 넣으면 cardinality가 폭증하므로 명확히 금지 기준으로 남겼다.

## 검증

CI-safe 검증은 local app startup이나 curl 없이 돌아간다.

```bash
./gradlew test
make verify-v2-phase13
make final-check
```

`make verify-v2-phase13`은 data/evaluation guardrail alias다. Phase 13 Java observability tests는 `./gradlew test`와 `make final-check`에서 실행된다.

Java test는 다음을 확인한다.

- Actuator info payload가 active ruleVersion을 포함한다.
- Info payload에 high-cardinality identifier가 없다.
- Admin ruleVersion summary가 non-null version과 legacy missing row를 분리한다.
- 기존 eventId 기반 fraud result 조회는 계속 동작한다.

Local/manual check는 별도다.

```bash
curl http://localhost:8081/actuator/info
curl -H "X-Admin-Token: <local-admin-token>" \
  http://localhost:8080/api/v1/admin/fraud-results/rule-version-summary
```

이 명령은 local app startup과 admin token이 필요하므로 final-check에는 넣지 않았다.

## 트러블슈팅

첫 번째 문제는 active ruleVersion과 stored result ruleVersion을 혼동하는 것이다.

Active version은 현재 실행 중인 consumer 기준이다. Stored result version은 그 result가 생성된 시점의 기준이다. 두 값이 다르다고 항상 장애는 아니다. Rule 배포 전후의 정상적인 mixed state일 수 있다.

두 번째 문제는 metric cardinality다.

`ruleVersion`은 bounded 값이라 future metric tag 후보가 될 수 있다. 하지만 `eventId`, `userId`, `traceId`는 절대 tag로 넣으면 안 된다. Prometheus cardinality가 데이터 건수만큼 늘어날 수 있기 때문이다.

세 번째 문제는 actuator endpoint 과다 노출이다.

이번에는 기존 `info` endpoint에 낮은 민감도의 static metadata만 추가했다. 새 admin endpoint를 consumer에 열지 않았고, API와 Consumer 책임도 섞지 않았다.

## 남은 한계

- Actuator info는 deployment audit log가 아니며, public exposure 전 network-level control 또는 Spring Security hardening이 필요하다.
- Stored result summary는 traceability evidence이지 fraud quality evidence가 아니다.
- RuleVersion filter는 기존 list API가 실제 query로 바뀐 뒤 추가하는 편이 낫다.
- Grafana panel과 alert는 future work다.

## 남긴 기준

기준: 이 단계에서 무엇을 구현했나요?

정리: app-consumer Actuator info에 현재 active ruleVersion을 노출했고, app-api admin API에 stored fraud result ruleVersion summary를 추가했습니다. Runtime version과 historical result version을 구분하는 운영 추적성 evidence입니다.

기준: 왜 result ruleVersion 저장만으로는 부족하다고 봤나요?

정리: Stored result version은 과거 결과의 생성 기준이고, active version은 현재 consumer runtime 기준입니다. Rule 배포 전후를 분석하려면 두 관점을 따로 봐야 합니다.

기준: metric cardinality는 어떻게 고려했나요?

정리: `ruleVersion`은 bounded라 future metric tag 후보가 될 수 있지만, 이번 Phase에서는 metric을 추가하지 않았습니다. `userId`, `eventId`, `traceId` 같은 high-cardinality 값은 tag로 금지했습니다.

기준: endpoint 보안은 어떻게 고려했나요?

정리: consumer에는 새 custom endpoint를 만들지 않고 기존 Actuator info를 활용했습니다. app-api summary는 기존 admin token 보호 범위의 `/api/v1/admin` 아래에 두었습니다.
