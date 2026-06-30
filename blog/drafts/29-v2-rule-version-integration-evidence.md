# V2 Phase 11 - Rule Version Drift를 Evidence Gate로 막기

## 문제

V2 Phase 9에서 evaluation report는 `ruleVersion`, `thresholdVersion`, `evaluationPolicyVersion`을 기록하기 시작했다.

하지만 그 시점의 `ruleVersion`은 Python evaluator 쪽 evidence policy 값이었다. Report에는 `rule-v2-baseline-v1`이 남아도, Java app-consumer Rule Engine 쪽 baseline이 같은 값을 쓰는지 자동으로 증명하지는 못했다.

즉 metric report는 좋아졌지만, Java와 Python 사이의 version drift를 막는 gate는 아직 없었다.

## 설계

Phase 11의 목표는 새 rule 구현이 아니다.

이번 단계의 목표는 app-consumer Rule Engine baseline version과 Python evaluator report version을 같은 계약으로 묶는 것이다.

정리한 기준은 단순하다.

- `ruleVersion`은 rule logic 의미를 설명한다.
- `thresholdVersion`은 risk/action boundary를 설명한다.
- 둘은 섞지 않는다.
- Java baseline과 Python evaluator version이 다르면 CI-safe verifier에서 실패한다.

## 구현

app-consumer에 `FraudRuleVersions`를 추가해 현재 baseline을 노출했다.

```text
rule-v2-baseline-v1
```

Python 쪽에는 `verify_paysim_rule_version_contract.py`를 추가했다. 이 verifier는 Java source constant를 읽고, evaluator policy의 `RULE_VERSION`과 allowlist가 같은 값을 가리키는지 확인한다.

Evaluator도 per-result `ruleVersion`을 읽을 수 있게 했다.

- result row에 `ruleVersion`이 있으면 expected version과 반드시 같아야 한다.
- 다르면 evaluation이 fail-fast 된다.
- 없으면 evaluation은 contract-level version으로 계속 진행하지만, report에 coverage와 warning을 남긴다.

## 검증

CI-safe command:

```bash
make verify-paysim-rule-version-contract
make verify-v2-phase11
```

`verify-v2-phase11`은 기존 Phase 7/8/9 checks에 rule version contract check를 더한다.

`make final-check`도 이 Phase 11 aggregate check를 사용하도록 변경했다.

## 발견한 문제

Per-result `ruleVersion`이 없으면 event별로 어떤 Java rule version이 처리했는지 강하게 말할 수 없다.

그래서 Phase 11 report policy는 일부러 보수적으로 잡았다. Per-result version이 없으면 warning을 남기고, coverage를 별도로 기록한다. Contract-level version alignment는 검증하지만, per-event persistence/export는 후속 작업으로 남긴다.

## 남은 한계

- 새 PaySim-specific Java rule은 추가하지 않았다.
- `FraudResult`에 `ruleVersion`을 저장하지 않았다.
- DB detection result export 자동화는 아직 없다.
- Full PaySim replay/evaluation은 계속 local/manual evidence이다.
- 이번 gate는 version consistency를 검증할 뿐, production fraud model performance를 보장하지 않는다.
