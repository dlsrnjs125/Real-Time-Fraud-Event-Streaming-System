# Blog Series Writing Guide

Status: V3 Phase 0~7 기준으로 재작성한 publication candidate 12편.

## 시리즈의 한 줄 흐름

시스템이 동작한다는 설명에서 출발해, 같은 workload로 병목·상태·시간·격리를 재현하고 해석하는 과정으로 이동합니다.

각 글은 독립적으로 읽을 수 있지만 역할은 겹치지 않습니다.

- 01~03: 왜 이 시스템을 만들었고 어떻게 측정 가능하게 했는가
- 04~06: throughput, Redis state, partition skew의 서로 다른 병목
- 07~10: redelivery와 event-time/replay가 state에 미치는 영향
- 11: 실험 아래에서 correctness를 지키는 Core/V2 안전장치
- 12: 결과, 잘못된 가설, 남은 범위를 통합한 회고

## 구조 규칙

모든 글은 핵심 질문, 실제 판단, 근거, 주장 한계를 포함합니다. 다만 독자에게 같은 템플릿으로 보이지 않도록 주제에 맞는 구조를 사용합니다.

| 글 유형 | 적용 글 | 구조적 특징 |
|---|---|---|
| 관점 전환 | 01, 12 | 질문 변화와 배운 점 중심 |
| 계약 설명 | 02 | 오해 → 계약 분리 → accepted 기준 |
| 관측 해부 | 03 | pipeline boundary와 조사 순서 |
| 실험 기록 | 04, 05, 09 | 통제 변수, 수치, 기각한 해석 |
| 비교 분석 | 06, 10 | balanced/hot 또는 live/replay 대조 |
| 장애 검증 | 07 | failure point별 state/result 확인 |
| 정책 결정 | 08 | 시간 경계와 durable 의미 |
| 기반 정리 | 11 | correctness safety rail별 책임 |

## 사실 확인 기준

우선순위는 다음과 같습니다.

1. 실제 `app-api`, `app-consumer`, `app-common` 코드와 configuration
2. `docs/evidence/v3-phase*/`의 accepted runtime artifact
3. Phase별 evidence/plan 문서
4. roadmap와 README
5. 기존 draft

문서와 코드가 다르면 현재 코드의 동작을 기준으로 하고 불일치를 별도 수정 대상으로 남깁니다. 숫자는 accepted evidence에 있는 값만 사용합니다.

## 표현 제한

다음 표현은 피합니다.

- production-ready, exactly-once, 실시간 보장
- “대용량 처리 성공”처럼 workload와 환경이 없는 문장
- PaySim 결과를 실제 fraud detection 성능으로 해석하는 문장
- Kafka `CreateTime` 차이를 broker queue latency만으로 단정하는 문장
- `eventTime`과 `receivedAt` 차이를 source network latency로 단정하는 문장
- logical replay isolation을 physical resource isolation로 확대하는 문장

accepted evidence 이미지는 해당 수치를 해석하는 문단 바로 뒤에 배치하고, 캡션에서 확인 가능한 사실과 해석 한계를 함께 설명합니다. 별도의 문서 경로 목록을 글 끝에 반복하지 않으며, 블로그 본문도 docs 전체를 복제하지 않습니다.
