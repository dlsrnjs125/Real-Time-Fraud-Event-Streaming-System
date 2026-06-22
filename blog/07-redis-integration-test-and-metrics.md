# Phase 7. Redis Sliding Window 통합 검증과 Metric 보강

## 1. 이번 Phase에서 풀려는 문제

Phase 6에서는 Redis Sliding Window를 구현했다. Phase 7의 목표는 실제 Redis 자료구조가 의도대로 동작하는지 검증하고, Redis 장애와 degraded mode를 metric으로 관측 가능하게 만드는 것이다.

## 2. Mock 테스트만으로 부족한 이유

Mock 테스트는 호출 순서와 fallback 정책을 빠르게 검증할 수 있지만, Redis ZSET score, Hash 저장, TTL, cleanup 같은 실제 자료구조 동작을 보장하지는 못한다. Sliding Window의 핵심은 Redis 자료구조이므로 실제 Redis 기준 검증이 필요했다.

## 3. Redis ZSET/Hash를 실제 Redis로 검증한 항목

- ZSET `fraud:tx:user:{userId}:events` 저장
- Hash `fraud:tx:event:{eventId}` metadata 저장
- 같은 eventId 재기록 시 count 중복 방어
- eventTime 기준 window cleanup
- TTL 설정
- metadata 없는 ZSET member count/sum 제외
- eventTime 기준 window 조회

## 4. Integration Test를 기본 CI와 분리한 이유

Docker 기반 integration test는 CI 시간을 늘리고 실행 환경에 영향을 받는다. Phase 7에서는 기본 `make ci-check`는 빠른 unit/slice test로 유지하고, Redis integration test는 `make redis-integration-test`로 분리했다.

Testcontainers Redis도 시도했지만 로컬 Docker Desktop provider API 호환 문제로 실패했다. 최종적으로 Docker Compose Redis를 띄운 뒤 실제 Redis에 연결하는 방식으로 검증했다.

통합 테스트는 테스트 전용 Redis database index `15`를 사용한다. 테스트 시작 전 초기화도 해당 DB에서만 수행해, 개발자가 같은 Redis 인스턴스의 다른 DB를 사용하고 있더라도 전체 Redis 데이터를 지우지 않도록 했다.

## 5. Redis command latency를 측정한 이유

Redis 장애가 Consumer 전체 실패로 이어지지는 않더라도, Redis 응답이 느리면 Consumer thread가 묶이고 처리 지연이 커질 수 있다. 그래서 `recordAndGetWindow` 전체 시간을 `fraud.redis.window.record.latency` Timer로 기록한다.

## 6. degraded count와 skipped rule count가 필요한 이유

Redis 장애 중에도 Consumer는 ack를 계속할 수 있다. 이때 API와 Consumer health는 정상처럼 보일 수 있지만, Redis 기반 rule은 skip된다. `fraud.redis.window.degraded.total`, `fraud.detection.degraded.total`, `fraud.rule.skipped.total`은 탐지 민감도 저하를 관측하기 위한 신호다.

`fraud.redis.window.degraded.total`은 Redis window store가 degraded result를 반환한 횟수이고, `fraud.detection.degraded.total`은 degraded fraud result가 신규 저장된 횟수다. Duplicate fraud result path에서는 신규 탐지 결과가 저장되지 않으므로 detection degraded/skipped rule metric을 증가시키지 않는다.

## 7. Metric tag에 eventId/userId를 넣지 않은 이유

Metric은 집계용이고, 개별 이벤트 추적은 log/trace의 역할이다. eventId, traceId, userId는 cardinality가 높고 운영 환경에서는 식별자 노출 위험이 있다. Phase 7 metric은 rule 같은 낮은 cardinality tag만 사용한다.

## 8. 테스트와 검증 결과

실행한 명령:

```bash
./gradlew :app-consumer:test
make redis-integration-test
```

결과:

- app-consumer test PASS
- Redis integration test PASS
- Testcontainers Redis 시도는 Docker provider 호환 문제로 실패, Docker Compose Redis fallback 선택

## 9. 이번 Phase의 한계

Kafka + Redis + PostgreSQL 전체 E2E 검증은 아직 없다. Grafana dashboard와 alert rule도 아직 연결하지 않았다. k6 기반 Redis down/Consumer Lag 부하 검증도 후속 Phase 범위다.

## 10. 다음 Phase에서 보완할 점

Prometheus/Grafana dashboard에 Phase 7 metric을 연결하고, Redis down failure scenario에서 degraded metric과 Consumer Lag을 함께 측정한다. 이후 k6 부하 테스트로 Redis latency와 skipped rule count가 부하 상황에서 어떻게 변하는지 확인한다.
