# Redis Sliding Window Risk Detection

Phase 6에서는 Rule Engine v1을 단일 이벤트 기반 판단에서 사용자별 최근 거래 패턴 판단으로 확장했다.

핵심 목표는 Redis를 빠른 보조 상태 저장소로 사용하되, Redis 장애가 Consumer 전체 장애로 번지지 않게 만드는 것이었다. 최종 fraud result 저장과 중복 방어는 계속 PostgreSQL `fraud_detection_results.event_id` unique constraint가 담당한다.

## 구현 범위

- `fraud:tx:user:{userId}:events` ZSET 기반 최근 거래 window
- `fraud:tx:event:{eventId}` Hash 기반 amount metadata 저장
- eventTime 기준 최근 5분 window 계산
- 최근 5분 거래 5건 이상이면 `RAPID_TRANSACTION_COUNT` +30
- 최근 5분 누적 금액 3,000,000 KRW 이상이면 `WINDOW_AMOUNT_SUM` +40
- Redis 장애 시 stateful rule skip, `degraded=true` 저장
- fraud result 조회 API의 `skippedRules`, `degraded` 응답

## Redis 자료구조 선택

ZSET member는 `eventId`, score는 `eventTime` epoch millis로 두었다.

금액 합산은 ZSET member에 amount를 넣는 대신 이벤트별 Hash를 별도로 저장했다.

```text
fraud:tx:user:{userId}:events
  score = eventTime epoch millis
  member = eventId

fraud:tx:event:{eventId}
  amount
  currency
  eventTime
  userId
```

이 방식은 Redis key 수가 늘어나지만, member parsing을 피하고 향후 metadata 확장이 쉽다. TTL은 10분으로 두어 window 5분보다 조금 길게 유지한다.

## Consumer Ack 판단

Redis 장애를 처리 실패로 보면 Redis 장애 중 Kafka Lag이 빠르게 증가한다. 하지만 Redis는 최종 정합성 저장소가 아니다. Redis가 하는 일은 최근 거래 패턴 탐지를 보조하는 것이다.

그래서 Phase 6에서는 Redis 장애 시 다음 흐름을 선택했다.

```text
processing log 저장
-> Redis window 실패
-> degraded window result 반환
-> stateless rule만 평가
-> fraud result 저장
-> ack
```

반대로 PostgreSQL fraud result 저장 실패, processing log 저장 실패, Rule Engine 자체 예외는 ack하지 않는다.

이미 PostgreSQL에 같은 `eventId`의 fraud result가 있으면 Redis window를 갱신하지 않고 ack한다. 같은 eventId에 다른 payload가 들어오는 conflict replay가 Redis Hash metadata를 덮어쓰는 일을 막기 위한 fast path다.

## 중복 eventId

Kafka message는 재소비될 수 있다. 같은 `eventId`가 다시 Redis에 기록될 때 window count가 늘어나거나 metadata가 덮어써지면 탐지 결과가 왜곡될 수 있다.

fraud result가 이미 있으면 Redis를 건드리지 않는다. 아직 fraud result가 없는 재소비라면 ZSET member를 `eventId`로 두어 같은 eventId의 `ZADD`가 member를 새로 추가하지 않게 한다. Redis 상태가 완전한 정합성 기준은 아니지만, 재소비 시 count 중복 증가를 줄이는 데 충분한 형태다.

최종 중복 FraudResult 방어는 여전히 PostgreSQL unique constraint가 담당한다.

## Redis 부분 실패

Redis window 갱신은 여러 명령으로 구성된다.

```text
HSET event metadata
ZADD user window
ZREMRANGEBYSCORE cleanup
EXPIRE
ZRANGEBYSCORE
HGET amount
```

Phase 6에서는 Lua나 Redis transaction을 쓰지 않았다. 대신 Hash metadata를 먼저 저장한 뒤 ZSET에 추가하고, window 계산에서는 amount metadata가 있는 eventId만 count와 sum에 포함한다.

이렇게 하면 과거 부분 실패로 ZSET에 eventId만 남아 있어도 거래 횟수가 부풀려지지 않는다.

## 구현 중 이슈

`fraud_detection_results`에 `skipped_rules`, `degraded` 컬럼을 추가하면서 V4 migration을 만들었다. 처음에는 하나의 `alter table` 문에 두 컬럼을 comma로 연결했지만 H2 테스트에서 syntax error가 났다.

해결은 단순했다. 컬럼별로 `alter table ... add column` 문을 분리했다.

## 검증

실행한 명령:

```bash
./gradlew :app-consumer:test
./gradlew :app-api:test
```

검증한 내용:

- Redis window count/sum 계산
- 같은 eventId 재기록 시 count 중복 방어
- duplicate fraud result 시 Redis 갱신 생략
- metadata 없는 ZSET member count/sum 제외
- window 밖 이벤트 제외
- Redis 예외 시 degraded result 반환
- stateful rule score 반영과 100점 cap
- Redis degraded 시 skipped rule 기록
- Redis degraded 시에도 fraud result 저장 후 ack
- fraud result 조회 API의 `skippedRules`, `degraded` 응답

## 남은 한계

실제 Redis integration test는 아직 없다. Phase 6에서는 Redis store logic과 degraded policy를 unit test로 먼저 고정했다.

다음 단계에서는 실제 Redis 기반 integration test, Redis command latency metric, Redis degraded count metric, Redis down failure scenario를 보강한다.
