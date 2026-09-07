# 빠른 API를 만든 뒤, 느려지는 스트림을 보기 시작했다

처음 질문은 단순했다.

> 거래 이벤트를 빠르게 받아 이상거래를 놓치지 않으려면 어떤 구조가 필요한가?

그 질문에 답하면서 API와 Kafka Consumer를 분리했고, Redis 상태와 PostgreSQL 결과 저장소를 붙였다. 그러나 시스템이 동작한 뒤에는 더 어려운 질문이 남았다.

> 입력이 몰릴 때 어디서 밀리는가? 같은 사용자의 최근 상태를 유지하면서 얼마까지 처리할 수 있는가? 오래된 이벤트가 섞여도 그 지연의 출발점을 설명할 수 있는가?

V3는 이 두 번째 질문에서 시작했다.

## 접수 성공은 탐지 완료가 아니다

API가 `202 Accepted`를 반환했다는 사실은 요청 검증, 접수 기록, Kafka publish가 끝났다는 뜻이다. Redis 조회, rule 실행, 결과 저장까지 끝났다는 뜻은 아니다.

동기 호출로 모든 단계를 묶으면 탐지 지연이 그대로 API 지연이 된다. 이 프로젝트는 처리 특성이 다른 두 실행 단위를 분리했다.

```text
Client
  -> app-api
      -> PostgreSQL receipt
      -> Kafka(transaction-events, key=userId)
  -> app-consumer
      -> processing log
      -> Redis recent-user state
      -> fraud rule engine
      -> PostgreSQL fraud result
      -> manual ack
```

`app-common`에는 이벤트 계약만 두고, Kafka listener나 Redis 구현은 넣지 않았다. 완전한 MSA를 선택한 것도 아니다. 하나의 저장소와 공통 DB를 쓰되 API와 Consumer의 장애·확장·지표 경계만 분리한 modular monolith + event-driven worker 구조다.

## Kafka를 넣었다고 스트림 문제가 해결되지는 않았다

Kafka는 API와 탐지를 시간적으로 분리하고 burst를 backlog로 흡수한다. 하지만 backlog가 생겼다는 사실만으로 Kafka가 병목이라고 결론 낼 수는 없다.

Lag은 다음 원인 모두에서 커질 수 있다.

- listener 수가 partition 수보다 적다.
- 특정 partition에 트래픽이 집중된다.
- Redis 상태 조회 비용이 사용자별 window 크기와 함께 증가한다.
- 결과 저장소가 느려진다.
- 입력이 Consumer 처리량보다 빠르다.

그래서 V3의 중심을 기술 목록이 아니라 세 개의 측정 축으로 바꿨다.

| 축 | 답해야 할 질문 | 대표 신호 |
|---|---|---|
| Throughput | 입력과 처리가 어느 구간까지 균형을 이루는가 | ingress EPS, Consumer EPS, Lag growth/drain |
| Stateful Processing | 사용자별 최근 상태가 커질수록 비용이 어떻게 변하는가 | window count, Redis ops/event, Redis·Consumer p95 |
| Event Freshness | 이벤트가 언제 발생했고 어느 경계에서 늦어졌는가 | ingress age, Kafka 전달 지연, too-late count |

## `userId` key가 만든 이익과 빚

Kafka는 partition 안에서만 순서를 보장한다. 최근 거래 횟수와 금액을 사용자 단위로 계산하려면 같은 사용자의 이벤트가 같은 partition에 들어가야 한다. Producer가 `message.userId()`를 key로 보내는 이유다.

이 선택은 두 가지를 동시에 만든다.

1. 같은 사용자의 처리 순서를 한 partition에서 다룰 수 있다.
2. key 분포가 치우치면 한 partition만 밀릴 수 있다.

V3 Phase 3에서 60%의 이벤트를 P2에 보내자, concurrency가 6이어도 P2에만 12,554건의 즉시 Lag이 남고 다른 partition은 0이 되었다. 사용자 순서를 얻는 대가가 hot-partition 위험이라는 사실을 설계 설명이 아니라 실행 결과로 확인한 것이다.

## V1·V2 안전장치 위에 V3를 올렸다

V3가 처리량을 중심에 놓았다고 이전 기능을 버린 것은 아니다.

- Consumer는 auto commit을 끄고 필요한 처리 뒤 `MANUAL_IMMEDIATE` ack를 수행한다.
- `fraud_detection_results.event_id` unique constraint가 최종 중복 방어선이다.
- Redis 장애 시 상태 의존 rule을 skipped로 기록한 degraded result를 남긴다.
- DLT 재처리는 원본 `eventId`와 원본 live/replay topic을 보존한다.
- PaySim 원본은 커밋하지 않고, HMAC 식별자와 fixture 기반 검증 계약을 사용한다.

이 장치들은 V3의 주인공이 아니라 실험이 데이터를 망가뜨리지 않게 하는 바닥이다.

## 완성된 시스템보다 설명 가능한 시스템

이 저장소가 증명한 범위는 로컬 Docker 환경에서 재현한 스트림 동작이다. 300 EPS 이상 capacity, 실제 금융 데이터에 대한 탐지 품질, 물리적으로 분리된 replay 인프라, 자동 rollback은 증명하지 않았다.

대신 다음 질문에는 근거를 연결할 수 있게 되었다.

- 왜 API는 정상인데 탐지가 늦었는가?
- concurrency를 늘렸을 때 무엇이 좋아졌고 어디서 더 이상 늘지 않는가?
- Redis 메모리가 아니라 명령 수가 먼저 문제 후보가 된 이유는 무엇인가?
- 270초 오래된 이벤트와 내부 처리 지연을 어떻게 구분했는가?
- historical replay가 live state를 오염시키지 않았다는 것을 무엇으로 확인했는가?

이후 글은 이 질문을 실험 하나씩 분해한다.
