# Runbook

## 1. 목적

이 문서는 장애 발생 시 운영자가 무엇을 보고, 어떤 순서로 확인하며, 어떻게 복구 여부를 판단할지 정리합니다.

형식:

- 장애 상황
- 탐지 지표
- 영향
- 확인 명령
- 대응 방법
- 복구 확인
- 재발 방지

## 1-1. 최신 로컬 운영 명령

현재 Makefile 기준으로 반복 검증과 로컬 drill은 아래 명령을 사용합니다.

| 목적 | Command | 비고 |
|---|---|---|
| lightweight CI | `make ci-check` | `./gradlew test`, `./gradlew assemble` |
| Redis integration test | `make redis-integration-test` | Docker Compose Redis DB 15 사용 |
| Redis down drill | `make failure-drill-redis` | 자동 script |
| Consumer restart drill | `make failure-drill-consumer` | app-consumer 재시작은 별도 터미널에서 수행 |
| Kafka topics 생성 | `make topics` | `scripts/create-topics.sh` 실행 |
| script syntax check | `make scripts-check` | failure drill script 포함 |
| final static check | `make final-check` | build, compose config, script syntax check |

Kafka unavailable drill은 자동화하지 않고 `scripts/failure_drills/kafka_unavailable_drill.md` runbook으로 확인합니다. Broker stop/start는 로컬 환경에 미치는 영향이 커서 수동 절차로 유지합니다.

## 1-2. Phase 12 Load Test Runbook

Phase 12 load test는 로컬 Docker Compose 환경에서만 실행합니다. 운영 환경 URL이나 외부 공유 환경을 대상으로 실행하지 않습니다.

### Precondition

```bash
make infra-up
make topics
make api
make consumer
```

`make api`와 `make consumer`는 각각 별도 터미널에서 실행합니다.

### Smoke

```bash
make k6-smoke
```

Smoke는 부하 측정이 아니라 k6 실행 가능 여부와 API 접수 경로 확인을 위한 1~3회 요청 테스트입니다.

### Normal Load

```bash
make k6-normal
```

### Peak Load

```bash
make k6-peak
```

### Duplicate Replay

```bash
make k6-duplicate
scripts/load_tests/check_duplicate_result_count.sh phase12-duplicate-fixed-event-id
```

확인 항목:

1. duplicate `eventId` 기록
2. API response policy 확인
3. `fraud_detection_results` count 1건 유지 여부 확인
4. 409 응답을 단순 장애로 해석하지 않음

### Redis Down Load

```bash
make k6-redis-down
docker compose -f infra/docker-compose.yml ps redis
```

`scripts/load_tests/run_redis_down_load.sh`는 `trap`으로 Redis start를 시도하고 `redis-cli ping` readiness를 확인합니다. 테스트 실패 후에도 반드시 Redis container 상태를 확인합니다. app-consumer metric endpoint가 reachable이면 Redis degraded, detection degraded, skipped rule metric before/after 값을 출력합니다.

### Failure Check

1. app-api health
2. app-consumer health
3. Kafka topic status
4. Redis container status
5. PostgreSQL connection
6. Prometheus metric endpoint

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus | grep fraud
```

## 1-3. Phase 13 Load Test Runbook

Phase 13 load/failure test는 로컬 Docker Compose 환경에서만 실행합니다. `API_BASE_URL`을 운영 환경이나 외부 공유 환경 URL로 지정하지 않습니다.

### Precondition

```bash
make infra-up
make topics
make api
make consumer
```

`make api`와 `make consumer`는 각각 별도 터미널에서 실행합니다.

### Smoke

```bash
make k6-smoke
```

Smoke는 부하 측정이 아니라 k6 실행 가능 여부와 API 접수 경로 확인을 위한 1~3회 요청 테스트입니다.

### Normal Load

```bash
make k6-normal
```

### Peak Load

```bash
make k6-peak
```

### Duplicate Replay

```bash
make k6-duplicate-check
```

확인 항목:

1. duplicate `eventId` 기록
2. API response policy 확인
3. `fraud_detection_results` count 1건 유지 여부 확인
4. 409 응답을 단순 장애로 해석하지 않음

### Redis Down Load

```bash
make k6-redis-down
docker compose -f infra/docker-compose.yml ps redis
```

`scripts/load_tests/run_redis_down_load.sh`는 `trap`으로 Redis start를 시도하고 `redis-cli ping` readiness를 확인합니다. app-consumer metric endpoint가 reachable이면 Redis degraded와 detection degraded metric 증가를 검증하고, skipped rule metric before/after 값을 출력합니다. 테스트 실패 후에도 반드시 Redis container 상태를 확인합니다.

### Failure Check

1. app-api health
2. app-consumer health
3. Kafka topic status
4. Redis container status
5. PostgreSQL connection
6. Prometheus metric endpoint

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus | grep fraud
```

## 2. Consumer Lag 지속 증가

장애 상황:

- Consumer Lag이 지속적으로 증가합니다.

탐지 지표:

현재 Phase에서 확인 가능한 metric:

- `fraud_redis_window_record_latency_seconds_*`
- `fraud_redis_window_degraded_total`
- `fraud_rule_skipped_total`
- `fraud_detection_degraded_total`

후속 Observability Phase 후보:

- `kafka_consumer_lag`
- `fraud_consumer_processing_duration_seconds`
- `db_insert_latency`
- `publish_failure_count`

영향:

- 거래 이벤트 접수는 가능하지만 이상거래 탐지가 지연됩니다.

확인 명령:

```bash
docker compose -f infra/docker-compose.yml ps
docker logs fraud-kafka --tail 100
```

Kafka UI에서 consumer group lag을 확인하고, Grafana Consumer dashboard에서 processing duration을 확인합니다.

대응 방법:

- app-consumer 재시작
- Consumer concurrency 증가 검토
- DB connection pool 확인
- Redis latency 확인
- DLQ 증가 여부 확인

복구 확인:

- Lag이 감소합니다.
- detection latency가 목표 범위로 돌아옵니다.

재발 방지:

- rule별 execution time metric 추가
- partition hot spot 테스트 추가

## 3. Redis 장애

장애 상황:

- Redis timeout 또는 connection 실패가 발생합니다.

탐지 지표:

- `fraud_redis_window_record_latency_seconds_*`
- `fraud_redis_window_degraded_total`
- `fraud_rule_skipped_total`
- `fraud_detection_degraded_total`

영향:

- Redis 기반 rule이 SKIPPED 처리됩니다.
- FraudResult에 `degraded=true`가 기록됩니다.

확인 명령:

```bash
docker exec fraud-redis redis-cli ping
docker logs fraud-redis --tail 100
```

대응 방법:

- Redis 상태 확인
- Redis 재시작
- app-consumer degraded metric 확인

복구 확인:

- Redis ping 성공
- skipped rule count 증가 중단
- degraded count 증가 중단

재발 방지:

- Redis command timeout 조정
- Redis unavailable fallback 정책 검토

## 4. Kafka Publish 실패

장애 상황:

- app-api가 `transaction-events` publish에 실패합니다.

탐지 지표:

현재 API 응답과 receipt 상태를 우선 확인합니다.

후속 Observability Phase 후보:

- Kafka publish failure count
- API error rate
- API p95/p99 latency

영향:

- 거래 이벤트가 접수되지 않습니다.
- Kafka publish 성공 전에는 `ACCEPTED`를 반환하지 않습니다.

확인 명령:

```bash
docker exec fraud-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
docker logs fraud-kafka --tail 100
```

대응 방법:

- Kafka broker 상태 확인
- topic 존재 여부 확인
- app-api Kafka bootstrap 설정 확인

복구 확인:

- publish success rate 회복
- API error rate 정상화

재발 방지:

- publish timeout metric 추가
- topic 생성 스크립트와 readiness check 강화

## 5. DLT 증가

장애 상황:

- `transaction-events-dlt` 이벤트가 증가합니다.

탐지 지표:

- DLT count
- DLQ event count
- failure_reason 분포

영향:

- 일부 이벤트가 자동 처리되지 못하고 운영자 확인이 필요합니다.

확인 명령:

```bash
docker exec fraud-kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic transaction-events-dlt
```

대응 방법:

- DLQ 조회 API로 failure_reason 확인
- schemaVersion 오류인지 payload 오류인지 분류
- 재처리 가능 이벤트만 reprocess
- 재처리 불가능 이벤트는 reason과 함께 discard

복구 확인:

- DLQ status가 `REPROCESSED` 또는 `DISCARDED`로 변경
- 중복 FraudResult 미생성

재발 방지:

- request validation 강화
- schema compatibility test 추가

## 6. PostgreSQL 장애

장애 상황:

- 탐지 결과 또는 처리 로그 저장 실패가 발생합니다.

탐지 지표:

- DB connection error count
- DB insert latency
- DB constraint violation count
- retry count
- DLT count

영향:

- Consumer 처리가 지연되거나 retry/DLT로 이동할 수 있습니다.

확인 명령:

```bash
docker exec fraud-postgres pg_isready -U fraud -d fraud
docker logs fraud-postgres --tail 100
```

대응 방법:

- PostgreSQL connection 확인
- connection pool saturation 확인
- unique constraint conflict인지 일시 장애인지 분류

복구 확인:

- DB 저장 성공
- retry 이벤트 처리 완료
- Consumer Lag 감소

재발 방지:

- DB index와 insert latency 확인
- duplicate handling path 테스트 강화

## 7. 개인정보 로그 노출

장애 상황:

- 로그 또는 DLQ 조회 결과에 accountId, deviceId, ipAddress 원문이 노출됩니다.

탐지 지표:

- 로그 샘플 검토
- DLQ 조회 응답 검토
- security review issue count

영향:

- 민감정보 노출 위험이 있습니다.

확인 명령:

```bash
grep -R "accountId" logs/ || true
grep -R "deviceId" logs/ || true
```

대응 방법:

- raw payload 로그 제거
- masking utility 적용
- DLQ response에서 masked payload만 반환
- 노출 범위와 접근자를 기록

복구 확인:

- 신규 로그에 민감정보 원문이 남지 않음
- DLQ 조회 응답에 masked payload 또는 payload_hash만 노출

재발 방지:

- logging field allowlist 적용
- 보안 리뷰 체크리스트에 로그 샘플 검토 추가

## 8. Hot Partition 발생

장애 상황:

- 특정 `userId`에 이벤트가 집중되어 일부 partition lag이 급증합니다.

탐지 지표:

- partition별 consumer lag
- partition별 message count
- hot userId load-test 결과

영향:

- 사용자별 순서는 유지되지만 해당 partition의 탐지 지연이 증가합니다.

확인 명령:

```bash
docker exec fraud-kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic transaction-events
```

대응 방법:

- Kafka UI에서 partition별 lag 확인
- 특정 userId 집중 여부 확인
- Consumer concurrency가 partition 수 이하인지 확인

복구 확인:

- hot partition lag이 감소합니다.
- detection latency가 목표 범위로 돌아옵니다.

재발 방지:

- hot partition 부하 테스트 추가
- key 전략 변경 필요 시 사용자별 순서 보장 영향 재검토

## 9. Invalid schemaVersion 이벤트 유입

장애 상황:

- Consumer가 지원하지 않는 `schemaVersion` 이벤트를 수신합니다.

탐지 지표:

- DLT count
- unsupported schema version failure_reason
- schema validation error count

영향:

- 해당 이벤트는 자동 처리되지 않고 운영자 확인 대상이 됩니다.

확인 명령:

```bash
docker exec fraud-kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic transaction-events-dlt
```

대응 방법:

- schemaVersion 확인
- 호환 가능한 변환인지 판단
- 임의 변환하지 않고 재처리 가능 여부를 운영자가 판단

복구 확인:

- DLT 이벤트 status가 `REPROCESSED`, `DISCARDED`, `FAILED_PERMANENT` 중 하나로 정리됩니다.

재발 방지:

- schema compatibility test 추가
- producer/consumer schemaVersion 변경 절차 문서화

## 10. Consumer Rebalance 후 중복 처리 증가

장애 상황:

- Consumer scale out 또는 재시작 중 rebalance가 발생하고 같은 메시지가 다시 소비됩니다.

탐지 지표:

- duplicate skip count
- DB unique constraint conflict count
- consumer rebalance count

영향:

- 처리량이 일시적으로 흔들릴 수 있지만 FraudResult 중복 생성은 없어야 합니다.

확인 명령:

```bash
docker logs fraud-kafka --tail 100
```

대응 방법:

- duplicate skip이 정상적으로 기록되는지 확인
- unique constraint conflict가 FraudResult 중복 생성으로 이어지지 않는지 확인

복구 확인:

- Consumer Lag이 정상화됩니다.
- Duplicate FraudResult count가 0입니다.

재발 방지:

- idempotency test 추가
- rebalance 상황에서 manual ack 동작 검증

## 11. DLT 재처리 중 Duplicate FraudResult Conflict

장애 상황:

- 이미 처리된 `eventId`를 가진 DLT 이벤트를 재처리합니다.

탐지 지표:

- DB unique constraint conflict count
- duplicate reprocess count
- `dead_letter_events.reprocess_attempts`

영향:

- 중복 FraudResult가 생성되면 안 됩니다.

확인 명령:

```bash
docker exec fraud-postgres psql -U fraud -d fraud -c "select event_id, count(*) from fraud_detection_results group by event_id having count(*) > 1;"
```

대응 방법:

- 재처리 결과를 duplicate 또는 already processed로 기록
- DLQ status와 `reprocess_attempts`, `last_reprocessed_at` 확인

복구 확인:

- 중복 FraudResult row가 없습니다.
- 재처리 이력이 남아 있습니다.

재발 방지:

- `fraud_detection_results.event_id` unique constraint 유지
- reprocess API 중복 요청 테스트 추가

## 12. Prometheus Target Down

장애 상황:

- Prometheus에서 app-api 또는 app-consumer target이 DOWN으로 표시됩니다.

탐지 지표:

- Prometheus target status
- missing application metrics
- Grafana no data panel

영향:

- 비즈니스 처리는 계속될 수 있지만 운영자가 지표를 볼 수 없습니다.

확인 명령:

```bash
docker compose -f infra/docker-compose.yml ps prometheus grafana
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8081/actuator/health
```

대응 방법:

- 애플리케이션 health 확인
- Prometheus scrape target 설정 확인
- host.docker.internal 접근 가능 여부 확인

복구 확인:

- Prometheus target이 UP으로 돌아옵니다.
- Grafana dashboard에 데이터가 표시됩니다.

재발 방지:

- smoke test에 prometheus target 확인 추가

## 13. Future eventTime 이벤트 유입

장애 상황:

- `eventTime`이 `receivedAt`보다 허용 범위를 초과해 미래입니다.

탐지 지표:

- validation failure count
- future eventTime DLT count
- negative ingest_delay count

영향:

- Redis sliding window 계산이 왜곡될 수 있습니다.

확인 명령:

```bash
docker logs fraud-kafka --tail 100
```

대응 방법:

- 허용 가능한 clock skew인지 확인
- 초과 시 validation failure 또는 DLT로 분류
- 원본 producer의 clock 설정 확인

복구 확인:

- 음수 ingest_delay가 더 이상 발생하지 않습니다.
- Future eventTime 이벤트가 DLT 또는 validation failure로 분류됩니다.

재발 방지:

- producer clock skew 모니터링
- eventTime validation test 추가

## 14. Consumer 재시작 후 미처리 이벤트 재소비 확인

장애 상황:

- Consumer가 중지된 동안 `transaction-events`에 이벤트가 쌓입니다.
- Consumer 재시작 후 ack되지 않은 이벤트가 다시 소비되는지 확인해야 합니다.

탐지 지표:

- Kafka UI consumer group lag
- `event_processing_logs` row count
- app-consumer structured log의 `traceId`, `eventId`, `topic`, `partition`, `offset`

확인 명령:

```bash
make infra-up
make topics
make api
```

Consumer startup prerequisite:

app-consumer는 runtime Flyway를 실행하지 않으므로, 로컬에서 빈 DB를 사용할 때는 app-api를 먼저 실행하거나 migration owner를 먼저 실행해 schema를 생성해야 합니다. schema가 없는 상태에서 app-consumer를 먼저 실행하면 JPA validate 단계에서 실패하는 것이 정상입니다.

Consumer를 중지한 상태에서 `POST /api/v1/transactions/events`로 이벤트를 발행한 뒤 Consumer를 시작합니다.

```bash
make consumer
curl http://localhost:8080/api/v1/admin/events/{eventId}/processing-log
```

복구 확인:

- app-consumer 로그에 해당 `eventId`, `traceId`, `topic`, `partition`, `offset`이 출력됩니다.
- `GET /api/v1/admin/events/{eventId}/processing-log` 응답에 status `PROCESSED` log가 표시됩니다.
- 동일 offset이 재소비되어도 `(topic, partition_no, offset_no)` unique constraint 때문에 duplicate processing log가 계속 쌓이지 않습니다.

남은 한계:

- Retry/DLT는 Phase 9 범위입니다.
- FraudResult 저장과 eventId 기준 business idempotency는 Phase 5 이후 범위입니다.

## 15. Fraud Result와 Rule Engine v1 수동 검증

전제 조건:

- app-api가 schema owner이므로 빈 DB에서는 app-api를 먼저 실행해 migration을 적용합니다.
- app-consumer는 runtime Flyway를 실행하지 않고 JPA validate만 수행합니다.

확인 명령:

```bash
make infra-up
make topics
make api
make consumer
```

저위험 이벤트 발행:

```bash
curl -X POST http://localhost:8080/api/v1/transactions/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-phase5-low-001","userId":"user-1001","accountId":"acc-1001","amount":10000,"currency":"KRW","merchantId":"merchant-001","deviceId":"device-001","location":"SEOUL","eventType":"PAYMENT","eventTime":"2026-06-19T10:00:00Z"}'
```

고위험 이벤트 발행:

```bash
curl -X POST http://localhost:8080/api/v1/transactions/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-phase5-high-001","userId":"user-1001","accountId":"acc-1001","amount":1500000,"currency":"KRW","merchantId":"merchant-001","deviceId":"device-001","location":"HIGH_RISK","eventType":"PAYMENT","eventTime":"2026-06-19T02:00:00Z"}'
```

조회:

```bash
curl http://localhost:8080/api/v1/admin/events/evt-phase5-high-001/processing-log
curl http://localhost:8080/api/v1/admin/events/evt-phase5-high-001/fraud-result
```

기대 결과:

- processing log status가 `PROCESSED`로 조회됩니다.
- fraud result가 저장됩니다.
- 고위험 이벤트는 `riskLevel=HIGH`, `decision=BLOCK`입니다.
- 같은 eventId가 재소비되어도 `fraud_detection_results` row는 중복 생성되지 않습니다.

## 16. Redis Sliding Window 수동 검증

전제 조건:

- app-api가 schema owner이므로 빈 DB에서는 app-api를 먼저 실행해 migration을 적용합니다.
- app-consumer는 runtime Flyway를 실행하지 않고 JPA validate만 수행합니다.
- Redis는 Docker Compose의 `redis` service를 사용합니다.

확인 명령:

```bash
make infra-up
make topics
make api
make consumer
```

같은 사용자에게 5분 window 안의 이벤트를 5건 발행합니다.

```bash
for i in 1 2 3 4 5; do
  curl -X POST http://localhost:8080/api/v1/transactions/events \
    -H "Content-Type: application/json" \
    -d "{\"eventId\":\"evt-phase6-rapid-00${i}\",\"userId\":\"user-phase6-rapid\",\"accountId\":\"acc-phase6\",\"amount\":10000,\"currency\":\"KRW\",\"merchantId\":\"merchant-001\",\"deviceId\":\"device-001\",\"location\":\"SEOUL\",\"eventType\":\"PAYMENT\",\"eventTime\":\"2026-06-19T10:0${i}:00Z\"}"
done
```

마지막 이벤트의 fraud result를 조회합니다.

```bash
curl http://localhost:8080/api/v1/admin/events/evt-phase6-rapid-005/fraud-result
```

기대 결과:

- `matchedRules`에 `RAPID_TRANSACTION_COUNT`가 포함됩니다.
- `degraded`는 `false`입니다.
- `skippedRules`는 빈 배열입니다.

누적 금액 rule 확인:

```bash
for i in 1 2 3; do
  curl -X POST http://localhost:8080/api/v1/transactions/events \
    -H "Content-Type: application/json" \
    -d "{\"eventId\":\"evt-phase6-sum-00${i}\",\"userId\":\"user-phase6-sum\",\"accountId\":\"acc-phase6\",\"amount\":1000000,\"currency\":\"KRW\",\"merchantId\":\"merchant-001\",\"deviceId\":\"device-001\",\"location\":\"SEOUL\",\"eventType\":\"PAYMENT\",\"eventTime\":\"2026-06-19T11:0${i}:00Z\"}"
done
curl http://localhost:8080/api/v1/admin/events/evt-phase6-sum-003/fraud-result
```

기대 결과:

- `matchedRules`에 `WINDOW_AMOUNT_SUM`이 포함됩니다.
- 총 risk score는 stateless rule 점수와 stateful rule 점수를 합산하되 100을 넘지 않습니다.

Redis 장애 degraded mode 확인:

```bash
docker compose -f infra/docker-compose.yml stop redis
curl -X POST http://localhost:8080/api/v1/transactions/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-phase6-redis-down-001","userId":"user-phase6-down","accountId":"acc-phase6","amount":10000,"currency":"KRW","merchantId":"merchant-001","deviceId":"device-001","location":"SEOUL","eventType":"PAYMENT","eventTime":"2026-06-19T12:00:00Z"}'
curl http://localhost:8080/api/v1/admin/events/evt-phase6-redis-down-001/fraud-result
docker compose -f infra/docker-compose.yml start redis
```

기대 결과:

- Consumer가 Redis 장애만으로 중단되지 않습니다.
- fraud result가 저장되고 `degraded=true`입니다.
- `skippedRules`에 Redis 의존 rule이 포함됩니다.
- reason에 Redis degraded mode가 남습니다.

남은 한계:

- Consumer Lag metric, Grafana dashboard, alert rule은 후속 Observability Phase에서 구성합니다.
- Redis down 부하 테스트와 Kafka lag 영향 검증은 후속 Load/Failure Test Phase에서 진행합니다.

## 17. Redis Integration Test와 Metric 확인

Redis integration test 실행:

```bash
make redis-integration-test
```

기대 결과:

- Docker Compose Redis가 기동됩니다.
- Redis readiness 확인 후 Gradle integration test가 실행됩니다.
- 테스트 전용 Redis database index `15`만 초기화합니다.
- 실제 Redis 기준 ZSET/Hash 저장, TTL, cleanup, duplicate eventId, metadata 없는 ZSET member 제외가 검증됩니다.

기본 CI 검증:

```bash
make ci-check
```

Metric endpoint 확인:

```bash
make infra-up
make topics
make api
make consumer
curl http://localhost:8081/actuator/prometheus | grep fraud
```

확인할 metric 후보:

- `fraud_redis_window_record_latency_seconds_count`
- `fraud_redis_window_record_latency_seconds_sum`
- `fraud_redis_window_record_latency_seconds_max`
- `fraud_redis_window_degraded_total`
- `fraud_rule_skipped_total`
- `fraud_detection_degraded_total`

`fraud.redis.window.record.latency`는 Micrometer Timer이므로 Prometheus에서는 `_seconds_*` suffix가 붙은 시계열로 노출될 수 있습니다. Prefix grep을 사용할 때는 `fraud_redis_window_record_latency`로 확인할 수 있습니다.

Redis degraded metric 확인 절차:

1. app-api와 app-consumer를 실행합니다.
2. Redis를 중지합니다.
3. 거래 이벤트를 발행합니다.
4. fraud result 조회에서 `degraded=true`와 `skippedRules`를 확인합니다.
5. `/actuator/prometheus`에서 Redis degraded/skipped metric 증가를 확인합니다.
6. Redis를 다시 시작합니다.

```bash
docker compose -f infra/docker-compose.yml stop redis
curl -X POST http://localhost:8080/api/v1/transactions/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-phase7-redis-down-001","userId":"user-phase7-down","accountId":"acc-phase7","amount":10000,"currency":"KRW","merchantId":"merchant-001","deviceId":"device-001","location":"SEOUL","eventType":"PAYMENT","eventTime":"2026-06-19T12:30:00Z"}'
curl http://localhost:8081/actuator/prometheus | grep fraud
docker compose -f infra/docker-compose.yml start redis
```

남은 한계:

- Grafana dashboard와 alert rule은 후속 Observability Phase에서 구성합니다.
- Redis integration test는 기본 `make ci-check`와 분리되어 있습니다.

## 18. Phase 8 Failure Drill

### Redis Down Drill

사전 조건:

- `make infra-up`
- `make topics`
- `make api`
- `make consumer`

실행:

```bash
make failure-drill-redis
```

확인 항목:

- Redis 중지 중에도 Consumer가 중단되지 않습니다.
- fraud result가 저장되고 `degraded=true`입니다.
- `skippedRules`에 `RAPID_TRANSACTION_COUNT`, `WINDOW_AMOUNT_SUM`이 포함됩니다.
- `/actuator/prometheus`에서 Redis degraded/skipped/latency metric 증가가 확인됩니다.
- Drill script는 tag가 붙은 Prometheus sample을 metric name 기준으로 합산해 전체 증가 여부를 확인합니다.
- Redis 재시작 후 신규 이벤트는 `degraded=false`로 처리됩니다.

### Consumer Restart Drill

이 저장소의 app-consumer는 Docker Compose service가 아니라 로컬 Gradle process로 실행합니다. 따라서 restart drill은 아래 순서로 수행합니다.

```bash
make infra-up
make topics
make api
# app-consumer를 실행 중이면 중지합니다.
make failure-drill-consumer
# 스크립트가 안내하면 다른 터미널에서 app-consumer를 다시 시작합니다.
make consumer
```

확인 항목:

- Consumer 중지 중 이벤트가 API를 통해 Kafka에 publish됩니다.
- Consumer 재시작 후 fraud result가 생성됩니다.
- processing log에 `PROCESSED` 기록이 남습니다.
- `fraud_detection_results` row count가 1건입니다.

### Kafka Unavailable Drill

Kafka unavailable drill은 자동 script가 아니라 runbook으로 분리했습니다.

```bash
cat scripts/failure_drills/kafka_unavailable_drill.md
```

핵심 확인 항목:

- Kafka broker 중지 중 API가 `202 Accepted`로 성공 응답하지 않습니다. 로컬 검증에서는 `503 Service Unavailable`을 기준으로 보되, timeout/exception type에 따라 status가 달라질 수 있어 non-2xx를 핵심 PASS 기준으로 둡니다.
- Kafka 복구 후 topic 조회가 가능합니다.
- Consumer reconnect log를 확인합니다.
- 복구 후 신규 이벤트를 발행하고 fraud result와 processing log를 확인합니다.

### Event Consistency Check

특정 이벤트의 fraud result와 processing log를 함께 확인합니다.

```bash
scripts/failure_drills/check_event_consistency.sh evt-phase8-redis-down-123
```

### 실패 시 확인 순서

1. `docker compose -f infra/docker-compose.yml ps`
2. `curl http://localhost:8080/actuator/health`
3. `curl http://localhost:8081/actuator/health`
4. `./scripts/wait-for-kafka.sh`
5. `curl http://localhost:8081/actuator/prometheus | grep fraud`
6. fraud result API 조회
7. processing log API 조회
8. app-api/app-consumer 로그에서 `traceId`, `eventId`, topic/partition/offset 확인

### 남은 한계

- Retry/DLT 자동 복구는 후속 Phase에서 구현합니다.
- Kafka + Redis + PostgreSQL full E2E 부하 검증은 후속 Phase에서 수행합니다.
- Grafana dashboard와 alert rule은 후속 Observability Phase에서 구성합니다.

## 19. Phase 9 DLT 운영 절차

### DLT 이벤트 조회

```bash
curl "http://localhost:8080/api/v1/admin/dlt-events?status=PENDING&page=0&size=20"
curl "http://localhost:8080/api/v1/admin/dlt-events/{id}"
```

확인 항목:

- `failureStage`
- `errorType`, `errorMessage`
- `sourceTopic`, `sourcePartition`, `sourceOffset`
- `status`
- `payloadJson`

단건 조회는 payload를 포함합니다. 현재 local 검증은 synthetic identifier 기준이지만 운영 환경에서는 masking과 접근 권한 분리가 필요합니다.

### DLT 재처리

```bash
curl -X POST "http://localhost:8080/api/v1/admin/dlt-events/{id}/reprocess"
```

기대 결과:

- `PENDING` 또는 `REPROCESS_FAILED`만 재처리됩니다.
- API 응답 status가 `REPROCESSED`이면 원본 payload가 `transaction-events`로 재발행된 상태입니다.
- Kafka publish 실패 시 status는 `REPROCESS_FAILED`로 남고 API는 `503 KAFKA_PUBLISH_FAILED`를 반환합니다.
- Consumer가 다시 처리할 때 원본 `eventId`가 유지됩니다.

재처리 후 확인:

```bash
curl "http://localhost:8080/api/v1/admin/events/{eventId}/fraud-result"
curl "http://localhost:8080/api/v1/admin/events/{eventId}/processing-log"
```

`fraud_detection_results.event_id` unique constraint 때문에 같은 eventId의 FraudResult는 중복 생성되지 않아야 합니다.

### DLT 폐기

```bash
curl -X POST "http://localhost:8080/api/v1/admin/dlt-events/{id}/discard" \
  -H "Content-Type: application/json" \
  -d '{"operatorId":"local-admin","reason":"invalid payload cannot be reprocessed"}'
```

기대 결과:

- `PENDING` 또는 `REPROCESS_FAILED`만 폐기됩니다.
- `discardReason`과 `discardedAt`이 저장됩니다.
- `REPROCESSED` 또는 `DISCARDED` 상태에서 재처리/폐기를 다시 요청하면 `409 DLT_STATE_CONFLICT`가 반환됩니다.

### 잘못된 재처리 시 확인 항목

1. DLT row의 `status`, `reprocessAttempts`, `lastReprocessedAt`
2. app-api 로그의 Kafka publish 실패 여부
3. app-consumer 로그의 `traceId`, `eventId`, topic/partition/offset
4. fraud result row count가 eventId 기준 1건인지 여부
5. processing log에 재처리 offset이 새로 기록되었는지 여부

### 남은 한계

- Phase 9 admin API는 local/development-only입니다.
- batch reprocess, rate limit, operator audit log는 후속 Phase에서 보강합니다.
- Kafka publish와 DB 상태 변경의 완전한 atomic transaction은 이번 Phase에서 구현하지 않았습니다.

## 20. Phase 11 Final Readiness Review

Phase 11에서는 새로운 운영 기능을 추가하지 않고, Phase 1~10에서 남긴 evidence를 찾기 쉽게 정리합니다.

확인 문서:

- `docs/19-final-readiness-checklist.md`
- `docs/20-evidence-index.md`
- `docs/21-troubleshooting-index.md`
- `docs/19-phase-10-final-readiness.md`

운영 준비도 판단 순서:

1. README에서 현재 구현 범위와 링크를 확인합니다.
2. `docs/20-evidence-index.md`에서 build/test, consistency, failure drill, observability evidence 위치를 확인합니다.
3. `docs/19-final-readiness-checklist.md`에서 완료 항목과 follow-up 항목을 분리해 봅니다.
4. 장애 유형별 상세 대응은 `docs/18-runbook.md`와 `docs/21-troubleshooting-index.md`에서 찾습니다.
5. 구현되지 않은 보안/관측/부하 항목은 후속 운영 고도화 후보로 기록합니다.
