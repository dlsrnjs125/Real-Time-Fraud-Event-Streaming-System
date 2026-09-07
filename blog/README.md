# V3 Blog Series

현재 구현과 `docs/`의 accepted runtime evidence를 기준으로 다시 구성한 12편의 기술 기록입니다. Core/V1의 신뢰성 기반, V2의 PaySim·evaluation 계약, V3 Phase 0~7의 처리량·상태·시간 실험을 하나의 흐름으로 연결합니다.

이 시리즈는 기능 소개 순서가 아니라 질문이 깊어진 순서로 읽도록 설계했습니다.

```text
시스템 경계
  -> 실험 계약
  -> 관측 경계
  -> 처리량
  -> 상태 비용
  -> 파티션 편향
  -> 재전달
  -> 이벤트 시간
  -> 외부 지연
  -> replay 격리
  -> 안전장치
  -> 회고
```

## 시리즈 순서

| # | 글 | 중심 질문 | 전개 방식 |
|---:|---|---|---|
| 1 | [빠른 API를 만든 뒤, 느려지는 스트림을 보기 시작했다](series/01-from-fraud-api-to-stateful-stream.md) | 왜 CRUD/API 프로젝트에서 stateful stream processing으로 관점이 바뀌었는가 | 문제 정의와 아키텍처 진화 |
| 2 | [600만 행 데이터셋은 6,000 EPS가 아니다](series/02-dataset-workload-time-contract.md) | dataset, workload, timestamp를 왜 별도 계약으로 관리했는가 | 오해 교정과 계약 설계 |
| 3 | [Lag 하나를 아홉 개의 처리 경계로 분해하기](series/03-stream-observability-boundaries.md) | backlog의 원인을 어떤 metric 경계로 좁혔는가 | 관측 지도와 조사 순서 |
| 4 | [300 EPS에서 생긴 17,857건의 Lag을 추적한 기록](series/04-sustainable-throughput-backlog-recovery.md) | concurrency 1의 병목을 어떻게 찾고 같은 부하로 검증했는가 | 실험 일지와 가설 기각 |
| 5 | [Redis 메모리보다 먼저 HGET 횟수가 문제였다](series/05-redis-state-size-cost.md) | 사용자별 window가 커질 때 실제 비용은 어디서 증가했는가 | 예상과 측정 결과의 대조 |
| 6 | [Consumer를 여덟 개 띄워도 한 partition은 빨라지지 않는다](series/06-partition-skew-consumer-parallelism.md) | `userId` ordering과 hot partition의 trade-off는 무엇인가 | 트래픽 지도 비교 |
| 7 | [중복 row가 없다고 Redis 상태도 안전한 것은 아니다](series/07-stateful-redelivery-idempotency.md) | redelivery 뒤 다음 이벤트의 판단까지 안정적인가 | failure-point별 검증 |
| 8 | [4분 59초는 받고 10분은 상태에서 제외했다](series/08-late-out-of-order-event-time.md) | late/out-of-order event를 live state와 durable result에서 어떻게 다뤘는가 | 정책 경계와 사례 |
| 9 | [같은 300 EPS라도 270초 묵은 이벤트는 다른 사고다](series/09-organic-vs-catchup-burst.md) | organic burst와 upstream catch-up을 어떻게 분리했는가 | 두 개의 시계 비교 |
| 10 | [하루치 replay를 live Kafka topic에 넣지 않은 이유](series/10-historical-replay-isolation.md) | historical replay가 live routing/state를 오염시키지 않는가 | 격리 계층과 3개 실행 장면 |
| 11 | [처리량 실험이 데이터를 망치지 않게 만든 안전장치들](series/11-safety-rails-and-reproducible-evidence.md) | manual ack, DB constraint, degraded, DLT, ruleVersion은 어떤 바닥을 만드는가 | 안전장치 해부 |
| 12 | [회고: 거래 이벤트 한 건에서 설명 가능한 스트림 시스템까지](series/12-v3-retrospective.md) | Core/V1부터 V3까지 무엇을 선택하고 배우고 남겼는가 | 프로젝트 전체 회고 |

## 디렉터리

```text
blog/
  README.md        # 현재 12편의 순서와 범위
  series/          # V3 기준 publication candidate
  drafts/          # Core/V2 작성 과정의 보존 자료
  images/          # 기존에 확보한 sanitized evidence 이미지
  image-plan.md    # V3 시리즈용 시각 자료 선택 기준
```

`drafts/`는 현재 시리즈가 아닙니다. 과거 판단과 작성 과정의 원자료로만 보존하며, 공개 순서는 `series/`의 12편을 기준으로 합니다.

## 작성 원칙

- 같은 `문제-설계-검증-한계` 제목을 12번 반복하지 않습니다.
- 수치는 accepted run과 연결하고 discarded run은 탈락 이유를 함께 남깁니다.
- local Docker evidence를 production capacity나 fraud accuracy로 확대하지 않습니다.
- 실제로 구현한 것, local/manual로 검증한 것, future work를 구분합니다.
- raw PaySim row, token, account/device identifier, local salt를 노출하지 않습니다.
- 상세 실행 명령과 전체 evidence는 블로그에 복제하지 않고 `docs/`를 기준 자료로 둡니다.

이미지 후보와 개인정보 경계는 [Blog Image Plan](image-plan.md)에 정리했습니다.
