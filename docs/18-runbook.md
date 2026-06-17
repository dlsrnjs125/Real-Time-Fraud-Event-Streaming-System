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

## 2. Consumer Lag 지속 증가

장애 상황:

- Consumer Lag이 지속적으로 증가합니다.

탐지 지표:

- `kafka_consumer_lag`
- `fraud_consumer_processing_duration_seconds_p95`
- `db_insert_latency`
- `redis_command_latency`

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

- Redis error count
- Redis command latency
- Redis degraded count
- skipped rule count

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

- `transaction-events.dlt` 이벤트가 증가합니다.

탐지 지표:

- DLT count
- DLQ event count
- failure_reason 분포

영향:

- 일부 이벤트가 자동 처리되지 못하고 운영자 확인이 필요합니다.

확인 명령:

```bash
docker exec fraud-kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic transaction-events.dlt
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
docker exec fraud-kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic transaction-events.dlt
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
- reprocessing_history result

영향:

- 중복 FraudResult가 생성되면 안 됩니다.

확인 명령:

```bash
docker exec fraud-postgres psql -U fraud -d fraud -c "select event_id, count(*) from fraud_results group by event_id having count(*) > 1;"
```

대응 방법:

- 재처리 결과를 duplicate 또는 already processed로 기록
- DLQ status와 reprocessing_history 확인

복구 확인:

- 중복 FraudResult row가 없습니다.
- 재처리 이력이 남아 있습니다.

재발 방지:

- `fraud_results.event_id` unique constraint 유지
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
