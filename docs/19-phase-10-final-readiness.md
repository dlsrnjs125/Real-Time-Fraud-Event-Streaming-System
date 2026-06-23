# Phase 10 Final Readiness

## 1. 목적

Phase 10은 새로운 기능을 크게 추가하는 단계가 아니라, Phase 9까지 구현한 Kafka 기반 이상거래 탐지 흐름을 운영 관점에서 설명 가능한 상태로 마무리하는 단계입니다.

핵심 목표는 다음과 같습니다.

- DLT 재처리 이후 운영자가 복구 완료를 판단할 기준 정리
- API, Consumer, Kafka, Redis, PostgreSQL, DLT 상태를 함께 확인하는 final checklist 작성
- 로컬에서 반복 가능한 검증 명령과 결과 기록
- 남은 한계와 실제 운영 확장 시 후속 보완 후보 분리

## 2. Phase 9까지 완료된 기능 요약

- `app-api`는 거래 이벤트를 검증하고 `transaction-events` topic으로 publish합니다.
- Kafka partition key는 `userId`를 사용해 사용자별 이벤트 순서를 유지합니다.
- `app-consumer`는 manual ack 기반으로 메시지를 처리하고, 저장 성공 이후 offset을 ack합니다.
- `fraud_detection_results.event_id` unique constraint로 같은 이벤트의 중복 FraudResult 생성을 방어합니다.
- Redis ZSET sliding window로 사용자별 최근 거래 패턴을 계산합니다.
- Redis 장애 시 Redis 의존 rule은 skipped 처리하고, FraudResult에는 `degraded=true`를 저장합니다.
- Rule Engine 예외처럼 반복 실패 가능성이 큰 이벤트는 `transaction-events-dlt`와 `dead_letter_events`로 격리합니다.
- 운영자는 DLQ 목록/단건 조회, 재처리, 폐기 API로 실패 이벤트 상태를 전이할 수 있습니다.
- DLT 재처리는 원본 `eventId`를 유지해 idempotent 처리 기준을 보존합니다.

## 3. 최종 검증 체크리스트

| 영역 | 확인 기준 | Evidence |
|---|---|---|
| Build/Test | 전체 Gradle build와 test가 통과해야 함 | `./gradlew clean build`, `./gradlew test` |
| Makefile | 반복 검증 target이 실제 명령과 일치해야 함 | `make test`, `make final-check` |
| Docker Compose | 로컬 Kafka/PostgreSQL/Redis/Prometheus/Grafana 설정이 유효해야 함 | `docker compose -f infra/docker-compose.yml config` |
| Scripts | topic/smoke/failure drill script syntax가 유효해야 함 | `bash -n scripts/*.sh`, `bash -n scripts/failure_drills/*.sh` |
| Documentation | README는 요약만, 상세 운영 판단은 docs/blog에 위치해야 함 | README, docs/19, blog/13 |

## 4. 운영 관점별 검증 기준

### API

- `POST /api/v1/transactions/events`는 Kafka publish 성공 전 `202 Accepted`를 반환하지 않습니다.
- validation failure와 publish failure는 서로 구분되는 응답과 traceId를 남겨야 합니다.
- API latency만으로 시스템 정상 여부를 판단하지 않습니다.

### Consumer

- `enable-auto-commit=false`와 manual ack 정책을 유지합니다.
- FraudResult와 processing log 저장 이후 ack합니다.
- 같은 Kafka record 또는 같은 `eventId`가 재소비되어도 중복 FraudResult가 생성되지 않아야 합니다.
- Consumer 중지 중에는 Kafka Lag이 증가할 수 있으며, 재시작 후 처리 결과와 processing log로 복구를 확인합니다.

### Kafka

- `transaction-events` key는 기본적으로 `userId`입니다.
- `transaction-events-dlt`는 처리 불가능 이벤트 격리용입니다.
- DLT 증가 여부는 원본 topic 처리 성공과 별도로 확인합니다.
- hot partition 가능성은 userId key의 trade-off로 유지하고, 부하 테스트에서 별도 측정합니다.

### Redis

- Redis는 최종 저장소가 아니라 sliding window용 단기 상태 저장소입니다.
- Redis 장애 시 전체 이벤트를 실패시키지 않고 degraded mode로 처리합니다.
- Redis 의존 rule이 skipped 되었는지, `degraded=true` 결과가 저장됐는지 확인합니다.
- Redis 복구 후 신규 이벤트가 정상 rule execution으로 돌아오는지 확인합니다.

### PostgreSQL

- `fraud_detection_results.event_id` unique constraint가 중복 결과의 최종 방어선입니다.
- `event_processing_logs(topic, partition_no, offset_no)` unique constraint로 같은 Kafka 위치 중복을 방어합니다.
- `dead_letter_events(source_topic, source_partition, source_offset)` unique constraint로 같은 DLT 원천 record 중복 저장을 막습니다.
- 운영 조회와 감사 판단은 Kafka message만이 아니라 PostgreSQL row 상태까지 함께 확인합니다.

### DLT 재처리

- 재처리는 원본 `eventId`를 유지해야 합니다.
- `PENDING` 또는 `REPROCESS_FAILED` 상태만 재처리/폐기 대상입니다.
- `REPROCESSED`와 `DISCARDED`는 종료 상태로 보고 재처리를 막습니다.
- 재처리 후에는 DLQ row 상태, FraudResult 중복 여부, Consumer Lag 회복, processing log를 함께 확인합니다.

## 5. Phase 10 검증 결과

2026-06-22 기준 로컬 검증 결과입니다.

| Command | Result | Notes |
|---|---|---|
| `./gradlew clean build` | PASS | 전체 module build/test 통과 |
| `./gradlew test` | PASS | app-api, app-common, app-consumer test task 통과 |
| `make test` | PASS | `./gradlew test` wrapper target 통과 |
| `make final-check` | PASS | `build`, Docker Compose config, script syntax check 통과 |

검증 중 sandbox 환경에서는 Gradle wrapper가 `~/.gradle` lock file을 생성하지 못해 최초 실행이 실패했습니다. 승인된 로컬 권한으로 다시 실행한 결과 build/test는 통과했습니다. 이는 애플리케이션 코드 실패가 아니라 검증 실행 환경의 파일 권한 제약입니다.

## 5-1. 운영 시나리오 검증 범위와 한계

이번 Phase 10에서 자동 검증한 항목은 build/test, Docker Compose config, shell script syntax check입니다. `make final-check` 역시 build, infra config, script syntax validation을 묶는 반복 검증 target이며, Kafka topic 생성부터 DLT 재처리 이후 DB 상태 확인까지의 end-to-end 운영 drill을 자동 실행하지는 않습니다.

DLT 재처리 이후의 실제 운영 검증은 아래 절차로 확인해야 합니다.

1. infra 기동
2. topic 생성
3. API와 Consumer 실행
4. 실패 이벤트를 DLT로 격리
5. Admin API로 DLT row 조회
6. 재처리 실행
7. FraudResult 중복 row 없음 확인
8. `dead_letter_events` 상태가 `REPROCESSED` 또는 `DISCARDED`인지 확인
9. `event_processing_logs`에 topic/partition/offset 기록 확인
10. Consumer Lag 회복 여부 확인

이번 PR에서는 해당 절차와 판단 기준을 문서화했습니다. 실제 end-to-end DLT recovery evidence는 후속 Phase 또는 별도 local drill에서 캡처합니다.

## 6. 운영 관점에서 남은 한계

- Kafka publish와 DB 상태 변경은 하나의 atomic transaction으로 묶여 있지 않습니다.
- DLT 재처리는 단건 수동 흐름이며 batch reprocess, rate limit, cooldown은 없습니다.
- 관리자 인증/인가와 재처리 audit log는 아직 구현하지 않았습니다.
- Prometheus/Grafana dashboard는 초기 scaffold 수준이며, 운영 alert rule은 별도 보강이 필요합니다.
- k6 기반 정상/피크/장애 부하 수치는 아직 최신 Phase 기준으로 재측정하지 않았습니다.
- 실제 운영 환경의 SLO는 partition 수, Consumer 수, DB/Redis 리소스, 네트워크 조건에 따라 다시 산정해야 합니다.

## 7. 후속 보완 후보

- DLT pending/reprocess failed/discard count metric과 Grafana dashboard 연결
- DLQ 재처리 audit log, 요청자 기록, 관리자 권한 검증
- DLT batch reprocess, max attempts, cooldown, rate limit
- Kafka publish와 DB 상태 변경 사이 outbox 또는 reconciliation job
- Consumer Lag, detection latency, duplicate skip, Redis degraded alert rule
- k6 normal/peak/hot partition/Redis down/Consumer restart 시나리오 재측정
- 민감정보 masking rule 확장과 DLQ payload 접근 권한 분리
