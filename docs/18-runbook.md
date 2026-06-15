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
