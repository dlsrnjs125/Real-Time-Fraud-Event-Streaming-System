# V2 Phase 8 - PaySim Native Type Replay Contract 정리

## 1. 왜 Phase 8을 했는가

V2 Phase 7까지는 PaySim replay evaluation report의 metric contract를 정리했다. 하지만 한 가지 큰 질문이 남아 있었다.

> PaySim의 `CASH_OUT`, `CASH_IN`, `DEBIT`를 현재 시스템의 거래 타입으로 어떻게 해석할 것인가?

이 질문을 건너뛰고 precision/recall만 보면 숫자가 좋아 보여도 입력 의미가 흔들릴 수 있다. PaySim native type은 synthetic dataset의 타입이고, 현재 production API enum은 `PAYMENT`, `TRANSFER`, `WITHDRAWAL`, `DEPOSIT`이다.

Phase 8은 fraud 성능을 올리는 단계가 아니라, native type을 어디까지 replay/evaluation에 포함할지 명시하는 contract 단계다.

## 2. 처음 의심한 문제

가장 위험한 선택은 Java enum에 PaySim native type을 그대로 추가하는 것이었다.

그렇게 하면 구현은 빨라진다. 하지만 `CASH_OUT`을 production API가 진짜 지원하는 것처럼 보일 수 있다. 이 프로젝트의 API는 실제 core banking transaction type contract가 아니고, PaySim은 synthetic dataset이다. 따라서 native type을 그대로 API 의미로 확장하는 것은 과한 주장이다.

반대로 모든 native type을 기존 타입으로 아무 설명 없이 바꾸는 것도 위험하다. `CASH_OUT -> WITHDRAWAL`은 replay에는 유용하지만, native context가 사라진다. `DEBIT`를 그냥 LOW risk로 흘려보내면 false negative가 숨을 수 있다.

## 3. 설계 판단

Phase 8에서는 production API enum을 확장하지 않았다.

대신 mapping policy를 만들었다.

```text
paysim-native-mapping-v1
```

핵심 판단은 다음과 같다.

- `PAYMENT -> PAYMENT`: production-supported
- `TRANSFER -> TRANSFER`: production-supported
- `CASH_OUT -> WITHDRAWAL`: replay-supported
- `CASH_IN -> DEPOSIT`: replay-supported
- `DEBIT`: unsupported, excluded

`replay-supported`는 중요한 표현이다. 이것은 app-api replay와 evaluation을 위해 허용한 mapping이지, production 거래 의미가 완전히 확정됐다는 뜻이 아니다.

## 4. 구현한 것

전처리 script는 이제 runtime event에 다음 필드를 남긴다.

```text
nativeEventType
normalizedEventType
typeSupportLevel
typeMappingPolicyVersion
```

app-api로 보낼 때는 current API가 이해하는 normalized `eventType`만 body에 넣는다. PaySim 전용 metadata는 replay/evaluation report에 남기고 HTTP request body에서는 제외한다.

Replay report와 evaluation report에는 다음 분포를 추가했다.

```text
mappingPolicyVersion
inputNativeTypeDistribution
acceptedNormalizedTypeDistribution
rejectedNativeTypeDistribution
replayNativeTypeDistribution
evaluatedNativeTypeDistribution
excludedByType
```

중요한 보강은 replay validation이 `nativeEventType`을 기준으로 mapping policy를 다시 계산한다는 점이다. `nativeEventType=DEBIT`를 `eventType=DEPOSIT`으로 위장한 row는 current API enum을 통과하더라도 Phase 8 validation에서 reject된다.

Native mapping metadata가 있는 row는 `typeMappingPolicyVersion`도 반드시 가져야 한다. 반대로 오래된 PaySim replay fixture처럼 metadata가 전혀 없는 row는 compatibility 때문에 허용하되 `missingMappingMetadata`로 report에 남긴다.

CI-safe 확인용 command도 추가했다.

```bash
make verify-paysim-native-replay-contract
make verify-v2-phase8
```

이 검증은 raw PaySim CSV, local DB export, actual app-api replay 없이 fixture로 실행된다. 즉, contract가 깨졌는지 빠르게 확인하는 용도다.

실제 local evidence command는 별도로 둔다.

```bash
make evaluate-paysim-native-replay
make v2-phase8-evidence
```

이 command는 detection result export가 준비된 local 환경에서 사용한다.

## 5. 트러블슈팅

첫 번째 문제는 native type을 production type처럼 과대 해석하는 것이었다.

해결은 support level을 나누는 것이었다. `production-supported`, `replay-supported`, `unsupported`를 분리하니 문서와 report에서 무엇을 주장할 수 있는지 선이 생겼다.

두 번째 문제는 unsupported type을 default low risk로 처리하는 위험이었다.

`DEBIT`는 current contract에서 의미가 애매하다. 그래서 LOW로 처리하지 않고 excluded bucket에 넣는다. Report에는 `excludedByType`을 남겨 denominator에서 빠진 타입을 볼 수 있게 했다.

세 번째 문제는 replay input 분포와 evaluation denominator 분포가 섞이는 것이었다.

Replay input에는 rejected된 `DEBIT`가 있을 수 있지만, evaluation denominator에는 없을 수 있다. 그래서 report에서는 `replayNativeTypeDistribution`과 `evaluatedNativeTypeDistribution`을 분리했다.

네 번째 문제는 Phase 7 metric과 Phase 8 metric을 직접 비교하는 것이었다.

Mapping policy가 달라지면 denominator가 달라진다. 그래서 precision/recall을 비교할 때는 반드시 `mappingPolicyVersion`과 native type distribution을 함께 봐야 한다.

## 6. 면접 답변으로 연결하기

질문: "왜 PaySim type을 Java enum에 바로 추가하지 않았나요?"

답변:

> PaySim native type은 synthetic dataset의 타입이고, 현재 production API transaction type과 의미가 완전히 같지 않습니다. 그래서 enum을 확장해 운영 계약을 넓히기보다 replay/evaluation mapping policy로 분리했습니다. `CASH_OUT`과 `CASH_IN`은 replay-supported로 매핑하고, `DEBIT`는 unsupported로 제외해 과대 해석과 default low-risk 처리를 피했습니다.

질문: "이번 단계에서 실제로 검증한 것은 무엇인가요?"

답변:

> Raw PaySim이나 local DB export 없이 fixture 기반으로 native type mapping consistency, input/accepted/rejected distribution, evaluated denominator distribution, unsupported type exclusion, evaluation report propagation을 검증했습니다. `make verify-v2-phase8`가 CI-safe contract check 역할을 합니다.

질문: "이 평가 지표를 이전 Phase와 비교할 수 있나요?"

답변:

> 같은 mapping policy version일 때만 직접 비교할 수 있습니다. Phase 8부터는 `mappingPolicyVersion`, `replayNativeTypeDistribution`, `evaluatedNativeTypeDistribution`, `excludedByType`을 함께 보고 denominator가 바뀌었는지 확인해야 합니다. Rule version과 threshold version도 후속 evidence에서 함께 비교해야 합니다.

질문: "ruleVersion과 thresholdVersion이 왜 null인가요?"

답변:

> Phase 8에서는 mappingPolicyVersion과 evaluationContractVersion만 고정했습니다. 실제 Rule Engine V2와 threshold tuning은 후속 evidence 단계에서 채울 값이라, 지금은 null placeholder로 두고 비교 기준에 들어갈 자리를 먼저 만든 것입니다.

## 7. 남은 한계

Phase 8은 native type contract를 정리한 단계다.

아직 남은 일은 다음과 같다.

- Rule Engine V2에서 native type별 rule hit 분포 확인
- `CASH_OUT -> WITHDRAWAL` mapping의 semantic loss 검토
- full PaySim replay와 detection result export 기반 evidence 생성
- mapping version과 rule version을 함께 비교하는 report 구성
- production API와 replay-only contract 분리 기준 재검토
