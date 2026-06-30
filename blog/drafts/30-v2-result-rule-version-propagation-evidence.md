# V2 Phase 12 - report-level ruleVersion에서 per-result ruleVersion으로

## 왜 했는가

Phase 11에서는 Java app-consumer Rule Engine의 active `ruleVersion`과 Python evaluator의 `RULE_VERSION`이 drift 나지 않는지 확인했다.

그런데 이건 contract-level evidence다. Report에 `ruleVersion=rule-v2-baseline-v1`이 있어도, 각 detection result row가 실제로 어떤 rule version으로 만들어졌는지는 row 자체에 version이 없으면 강하게 말하기 어렵다.

Phase 12의 목적은 이 gap을 줄이는 것이다. 새 fraud rule을 만들거나 threshold를 조정한 것이 아니라, detection result 단위 추적 가능성을 강화했다.

## 처음 의심한 문제

처음에는 report-level `ruleVersion`만으로 충분한지부터 의심했다.

- report ruleVersion이 있으면 모든 row가 같은 ruleVersion이라고 말해도 되는가
- legacy export에 `ruleVersion`이 없으면 평가를 막아야 하는가
- 일부 row만 `ruleVersion`을 가질 때 distribution에 missing 값을 넣어도 되는가
- strict mode를 기본값으로 두면 기존 fixture와 local evidence가 깨지지 않는가
- DB column을 추가하면 API response와 테스트까지 같이 따라오는가

결론은 단순했다. 기본 모드는 legacy compatibility를 유지하고, 더 강한 evidence가 필요한 경우에만 strict mode를 켠다.

## 구현 판단

app-consumer에서는 `FraudRuleEngineResult`에 `ruleVersion`을 추가했다. `FraudRuleEngine`은 매 평가 결과에 `FraudRuleVersions.ACTIVE_RULE_VERSION`을 넣는다.

저장 경로도 이어 붙였다.

- `fraud_detection_results.rule_version` nullable column 추가
- 신규 detection result 저장 시 active ruleVersion 저장
- admin fraud result response에 nullable `ruleVersion` 노출

Column을 nullable로 둔 이유는 기존 row와 fixture 호환성 때문이다. 신규 생성 경로는 테스트에서 non-null을 확인하지만, historical backfill은 이번 범위가 아니다.

Python evaluator 쪽에서는 report schema를 `2026-06-v2-phase12`로 올리고 `--require-per-result-rule-version`을 추가했다.

- default: missing per-result `ruleVersion` 허용, warning과 coverage 기록
- strict: evaluated result row에 `ruleVersion`이 없으면 fail-fast
- mismatch: default/strict와 무관하게 fail-fast
- distribution: present version만 집계하고 missing/null은 넣지 않음

`evaluationContractVersion`은 유지했다. denominator, missing-result 처리, replay rejected exclusion 의미는 바뀌지 않았기 때문이다.

## 검증

새 verifier는 fixture 기반이다.

```bash
make verify-paysim-result-rule-version-contract
make verify-v2-phase12
```

검증하는 내용은 다음과 같다.

- 모든 result row에 `ruleVersion`이 있으면 `per_result_verified`
- legacy missing row는 default mode에서 coverage/warning으로 허용
- mixed present/missing row는 distribution에 present version만 포함
- mismatched per-result `ruleVersion`은 fail-fast
- strict mode는 missing per-result `ruleVersion`에서 fail-fast
- report schema version은 Phase 12 값

`make final-check`도 Phase 12 aggregate gate를 타도록 바꿨다. 이 경로는 raw PaySim, local app-api, detection result export를 요구하지 않는다.

## 트러블슈팅

첫 번째 문제는 report-level version을 row-level consistency처럼 과장하는 것이었다.

Report version은 evaluation 기준을 설명한다. 하지만 row마다 어떤 consumer rule version으로 생성됐는지는 row field가 있어야 더 강하게 말할 수 있다. 그래서 `ruleVersionCoverage`와 `ruleVersionReadiness`를 추가했다.

두 번째 문제는 legacy export와 strict mode 충돌이었다.

기존 export에는 `ruleVersion`이 없을 수 있다. 그래서 strict mode를 기본값으로 두지 않았다. 기본 모드는 warning과 coverage로 legacy를 읽고, strict mode는 새 evidence를 요구할 때만 사용한다.

세 번째 문제는 mixed distribution 오염이었다.

Missing version을 distribution에 넣으면 `null` 또는 `None` bucket이 생긴다. 이건 실제 rule version 분포가 아니다. Missing은 coverage에서만 세고, distribution은 present version만 세도록 했다.

## 남은 한계

- Historical row backfill은 하지 않았다.
- DB detection result export 자동화는 아직 없다.
- Full PaySim replay/evaluation result는 여전히 local/manual evidence다.
- 이번 작업은 traceability evidence이지 fraud detection quality 개선이 아니다.

## 남긴 기준

기준: 이 단계에서 무엇을 구현했나요?

정리: Contract-level ruleVersion alignment를 detection result 단위 추적 가능성으로 확장했습니다. Java result/persistence/API response에 `ruleVersion`을 전파하고, Python evaluator에는 coverage/readiness와 strict mode를 추가했습니다.

기준: 왜 report-level ruleVersion만으로는 부족하다고 봤나요?

정리: Report-level version은 평가 기준을 설명하지만, row별 생성 기준까지 증명하지는 못합니다. Rule deployment나 export window가 섞이면 per-result version이 있어야 원인 분석이 쉬워집니다.

기준: per-result ruleVersion이 없을 때는 어떻게 처리했나요?

정리: 기본 모드에서는 legacy export로 보고 warning과 coverage를 남깁니다. Strict mode에서는 missing row를 fail-fast 처리합니다.

기준: 이 작업이 성능 개선과 다른 이유는 무엇인가요?

정리: Rule logic이나 threshold를 바꾸지 않았습니다. Precision, recall, F1을 높이는 작업이 아니라 결과가 어떤 rule baseline에서 생성됐는지 설명 가능하게 만드는 작업입니다.
