# Development Roadmap

## Progress Overview

| Phase | Status | 현재 상태 | 주요 산출물 | 다음 작업 |
|---:|---|---|---|---|
| Phase 0 | Done | 초기 기획/설계와 스캐폴딩 작성 완료 | README, docs, Gradle multi-module, app skeleton, Docker Compose skeleton | Phase 1 검증 |
| Phase 1 | Done | 로컬 실행 기반과 scaffold 검증 완료 | Gradle Wrapper, Docker Compose 검증, topic script 검증, app health 검증 | API 계약과 DTO 확정 |
| Phase 2 | Done | API 계약, DTO, validation, OpenAPI skeleton 구현 완료 | app-common event schema, app-api DTO/controller skeleton, Makefile | Phase 3 Kafka Producer 구현 |
| Phase 3 | Done | 거래 이벤트 접수 API, receipt 저장, Kafka Producer 구현 완료 | transaction_event_receipts, Kafka producer, intake service tests | Phase 4 Consumer manual ack와 processing log 구현 |
| Phase 4 | Not Started | Consumer 애플리케이션 골격만 작성 | app-consumer skeleton | Kafka listener, manual ack, processing log 구현 |
| Phase 5 | Not Started | FraudResult 저장 미구현 | Data model/API contract | 기본 LOW FraudResult 저장과 조회 API 구현 |
| Phase 6 | Not Started | Redis 없는 rule engine 미구현 | Fraud strategy docs | AmountRule, RiskScore, rule result detail 구현 |
| Phase 7 | Not Started | Redis sliding window 미구현 | Redis 설계 문서 | VelocityRule, Redis degraded mode 구현 |
| Phase 8 | Not Started | context rule 미구현 | Fraud strategy docs | NewDevice/Location rule 후보 구현 또는 범위 조정 |
| Phase 9 | Not Started | Retry/DLT 설계만 작성 | retry/dlt topic, reprocessing docs | DLT 저장, 조회, 재처리, 폐기 흐름 구현 |
| Phase 10 | Not Started | Actuator/Prometheus 설정 초안 | prometheus.yml, actuator config | custom metrics와 Grafana dashboard 구성 |
| Phase 11 | Not Started | k6 시나리오 초안 | load-test/k6 scripts | 정상/피크/장애 부하 측정 |
| Phase 12 | Not Started | 결과 문서 템플릿 준비 | troubleshooting/failure docs | 측정 결과와 설계 변경 기록 |
| Phase 13+ | Not Started | 운영/보안 확장 후보 정리 | security, SLO, DevOps, runbook docs | CI/CD, 인증/인가, alert hardening |

Status 기준:

- `Done`: 완료 기준을 충족했고 검증 결과가 문서에 남아 있음
- `In Progress`: 산출물이 일부 작성되었지만 완료 기준을 모두 충족하지 않음
- `Not Started`: 설계 또는 초안 외 실제 구현/검증이 시작되지 않음

## Phase 0. Initial Planning

### 목표

도메인 문제, 아키텍처 결정, Kafka topic 설계, 로컬 인프라 골격을 정의합니다.

### 완료 기준

- docs 12개 이상 생성
- Gradle multi-module 구성
- app-api/app-consumer bootRun 가능
- docker compose config 통과
- create-topics.sh 실행 가능

### 결과

Gradle Wrapper를 추가하고 `./gradlew clean build`, module test, Docker Compose config, topic script 실행, `app-api`/`app-consumer` Actuator health 검증까지 완료했습니다.

## Phase 1. Local Infrastructure Validation

### 목표

Kafka, PostgreSQL, Redis, Prometheus, Grafana를 로컬에서 실행하고 health를 확인합니다.

### Status

Done

### Completed

- Gradle Wrapper 추가 및 multi-module build 검증
- `app-common`, `app-api`, `app-consumer` module test task 검증
- `app-api` 독립 실행 및 `/actuator/health` 확인
- `app-consumer` 독립 실행 및 `/actuator/health` 확인
- Docker Compose config와 서비스 기동 검증
- Kafka topic 생성 스크립트 실행 및 topic 목록 확인
- shell script syntax check와 smoke script 실행 검증
- Prometheus scrape target `app-api`, `app-consumer` UP 확인

### Commands

```bash
./gradlew clean build
./gradlew :app-common:test :app-api:test :app-consumer:test
docker compose -f infra/docker-compose.yml config --quiet
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
bash -n scripts/create-topics.sh scripts/reset-local-env.sh scripts/run-smoke-test.sh scripts/wait-for-kafka.sh
./scripts/create-topics.sh
./scripts/run-smoke-test.sh
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

### Results

| Check | Result | Notes |
|---|---|---|
| Gradle multi-module build | PASS | `./gradlew clean build` 성공 |
| Module tests | PASS | 현재 test source는 `NO-SOURCE`이나 각 module task 성공 |
| Docker Compose config | PASS | `infra/docker-compose.yml` config 검증 성공 |
| Docker Compose services | PASS | Kafka, Kafka UI, PostgreSQL, Redis, Prometheus, Grafana 기동 확인 |
| Kafka topic script | PASS | 설계 topic 5개 생성 확인 |
| Script syntax | PASS | `scripts/*.sh` 주요 script syntax check 성공 |
| app-api health | PASS | `{"status":"UP"}` |
| app-consumer health | PASS | `{"status":"UP"}` |
| Prometheus targets | PASS | `app-api`, `app-consumer` target `up` |

### Evidence

- Local command result and review record: `docs/12-review.md#phase-1-review`
- Runbook/troubleshooting record: `docs/11-troubleshooting-log.md`

### Notes

- 기존 Docker Compose의 Kafka image tag와 Kafka CLI 경로가 실제 로컬 실행 환경과 맞지 않아 수정했습니다.
- Kafka UI는 Docker network 내부에서 `kafka:29092`를 사용하고, host에서 실행하는 Spring Boot 앱은 `localhost:9092`를 사용합니다.
- `app-consumer`는 worker 성격이지만 Phase 1 health endpoint 검증을 위해 embedded web server를 띄우도록 `spring-boot-starter-web`을 추가했습니다.
- 실제 transaction event API, Kafka producer, Kafka listener, rule engine, Redis sliding window, DLQ API는 구현하지 않았습니다.

## Phase 2. API Contract and Event Schema

### 목표

기능 구현 전에 API request/response, validation, error response, OpenAPI 기준, 공통 event message 계약을 확정합니다.

### Status

Done

### 범위

- `TransactionEventRequest`
- `TransactionEventAcceptedResponse`
- `TransactionEventReceiptResponse`
- `ErrorResponse`
- `FraudResultSummaryResponse`
- `FraudResultDetailResponse`
- `DlqEventSummaryResponse`
- `ProcessingLogResponse`
- `TransactionEventMessage`
- OpenAPI/Swagger 설정
- `traceId`, `eventId`, `schemaVersion`, `receivedAt` 정책
- Java/Spring 반복 검증용 Makefile

### 완료 기준

- Swagger UI 또는 OpenAPI JSON에서 API 계약 확인 가능
- request validation 테스트 작성
- error response 테스트 작성
- `docs/05-api-design.md`와 DTO 필드 일치
- `app-common`에는 공유 event schema와 enum만 포함

### Completed

- `TransactionEventMessage`, `FraudRiskEventMessage`, `FraudAlertEventMessage` schema를 `app-common`에 정리
- `TransactionEventType`, `RiskLevel`, `FraudRuleCode` enum 추가
- `TransactionEventRequest`, `TransactionEventAcceptedResponse`, `TransactionEventReceiptResponse` DTO 추가
- FraudResult, FraudRule, DLQ, ProcessingLog, OperationSummary 조회용 response DTO 추가
- `ErrorResponse`와 validation exception handler 추가
- `POST /api/v1/transactions/events` contract skeleton controller 추가
- Admin 조회/재처리 contract skeleton controller 추가
- springdoc OpenAPI 설정 추가
- validation error MVC test와 OpenAPI smoke test 추가
- Java/Spring project Makefile 추가

### Makefile Targets

- `make build`
- `make test`
- `make test-common`
- `make test-api`
- `make test-consumer`
- `make clean`
- `make api`
- `make consumer`
- `make infra-config`
- `make infra-up`
- `make infra-down`
- `make infra-ps`
- `make infra-logs`
- `make scripts-check`
- `make topics`
- `make smoke`
- `make final-check`

### Commands

```bash
./gradlew test
make build
make test
make final-check
curl http://localhost:8080/actuator/health
curl http://localhost:8080/v3/api-docs
```

### Results

| Check | Result | Notes |
|---|---|---|
| Gradle test | PASS | validation, OpenAPI smoke, app-common schema test 통과 |
| Makefile build | PASS | `make build` 성공 |
| Makefile test | PASS | `make test` 성공 |
| Makefile final-check | PASS | build, Docker Compose config, script syntax check 성공 |
| app-api health | PASS | `curl http://localhost:8080/actuator/health` 200, `UP` |
| OpenAPI docs | PASS | `curl http://localhost:8080/v3/api-docs` 200 |

### Next

- Phase 3에서 실제 Kafka Producer 구현
- `transaction_event_receipts` persistence 구현
- Kafka record key가 `userId`인지 검증
- Kafka publish success/failure metric foundation 구현
- `eventTime` future validation을 `receivedAt` 생성 정책과 함께 구현
- 실제 service/domain exception에 대한 `ErrorResponse` mapping 구현

### 범위 제외

- 실제 Kafka publish
- Consumer processing
- Fraud rule execution
- DLQ reprocessing 동작

## Phase 3. Transaction Event Intake and Kafka Producer

### 목표

거래 이벤트 요청을 받아 접수 기록을 남기고 `transaction-events` topic으로 발행합니다.

### Status

Done

### 범위

- `POST /api/v1/transactions/events`
- `GET /api/v1/transactions/events/{eventId}`
- `receivedAt` 생성
- `schemaVersion` 포함
- `eventId` 생성 정책
- `transaction_event_receipts` 저장
- Kafka key = `userId`
- Kafka publish success/failure metric foundation
- Kafka publish 실패 시 503 응답

### 완료 기준

- API 호출 시 Kafka 메시지 발행 확인
- `transaction_event_receipts` 저장 확인
- Kafka record key가 `userId`인지 확인
- message에 `eventId`, `traceId`, `schemaVersion`, `eventTime`, `receivedAt` 포함
- validation 실패 시 Kafka publish가 발생하지 않음
- Kafka publish 실패 정책과 Outbox 미적용 한계가 문서에 남아 있음

### Completed

- `POST /api/v1/transactions/events`를 contract skeleton에서 실제 intake service 호출로 전환
- `GET /api/v1/transactions/events/{eventId}` receipt 조회 구현
- `transaction_event_receipts` Flyway migration 추가
- `TransactionEventReceiptEntity`, repository, status 구현
- `TransactionEventIntakeService`에서 validation, receipt 저장, Kafka publish orchestration 구현
- Kafka topic 상수와 `TransactionEventProducer` adapter 구현
- Kafka key가 `userId`임을 unit test로 검증
- 중복 `eventId`는 `409 CONFLICT`로 처리
- Kafka publish 실패는 receipt를 `PUBLISH_FAILED`로 남기고 `503 SERVICE_UNAVAILABLE` 반환
- `eventTime`이 `receivedAt + 5분`을 초과하면 validation failure 처리

### Commands

```bash
make test
make build
make final-check
make infra-up
make topics
make api
```

### Results

| Check | Result | Notes |
|---|---|---|
| Makefile test | PASS | `make test` 성공 |
| Makefile build | PASS | `make build` 성공 |
| Makefile final-check | PASS | build, Docker Compose config, script syntax check 성공 |
| Manual API publish | PASS | `POST /api/v1/transactions/events` returned `202 Accepted` for `evt-manual-phase3-002` |
| Manual receipt lookup | PASS | `GET /api/v1/transactions/events/evt-manual-phase3-002` returned receipt status `PUBLISHED` |
| Manual Kafka consume | PASS | `transaction-events` record key was `user-1001`; payload included `schemaVersion`, `eventId`, `traceId`, `eventTime`, `receivedAt` |

Evidence:

- Local review record: `docs/12-review.md#phase-3-review`
- Troubleshooting and decision notes: `docs/11-troubleshooting-log.md`

### Known Limitations

- Phase 3에서는 Outbox Pattern을 구현하지 않습니다.
- DB receipt 저장 성공 후 Kafka publish 실패 가능성이 있습니다.
- `PUBLISH_FAILED` receipt 자동 재발행은 후속 hardening 후보입니다.
- Kafka Consumer, manual ack, `event_processing_logs` 저장은 Phase 4 범위입니다.

### Next

- Phase 4에서 Kafka Consumer, manual ack, `event_processing_logs` 저장을 구현합니다.

## Phase 4. Consumer Manual Ack and Processing Log

### 목표

Consumer가 `transaction-events`를 소비하고 처리 로그를 PostgreSQL에 저장합니다.

### 범위

- Kafka listener
- `enable-auto-commit=false`
- manual ack
- `event_processing_logs` 저장
- `event_processing_logs(topic, partition_no, offset_no)` unique constraint
- 처리 성공 후 ack
- 처리 실패 시 ack하지 않는 기본 정책
- `GET /api/v1/admin/events/{eventId}/processing-log`

### 완료 기준

- 처리 성공 후 manual ack 확인
- Consumer 재시작 후 미처리 이벤트 재소비 확인
- processing log 조회 API로 topic/partition/offset/status 확인
- 중복 offset 처리 시 duplicate log가 생성되지 않음

## Phase 5. Basic FraudResult Persistence and Query

### 목표

Rule Engine 전에 Consumer 처리 결과 저장 골격과 조회 API를 먼저 닫습니다.

### 범위

- 기본 `LOW` FraudResult 저장
- `fraud_results.event_id` unique constraint
- duplicate `eventId` skip 처리
- `GET /api/v1/admin/fraud-results`
- `GET /api/v1/admin/fraud-results/{eventId}`
- `detectedAt`, `detectionLatencyMs`, `endToEndLatencyMs` 계산 기초

### 완료 기준

- Consumer가 event 소비 후 FraudResult를 저장
- 같은 `eventId` 재소비 시 중복 FraudResult가 생성되지 않음
- FraudResult 목록/상세 API에서 결과 조회 가능
- duplicate skip count metric foundation 추가

## Phase 6. Rule Engine without Redis

### 목표

Redis에 의존하지 않는 rule부터 구현해 탐지 결과 설명 가능성을 확보합니다.

### 범위

- `FraudRule` interface
- `AmountRule`
- `RiskScoreService`
- `RiskLevel` 결정
- `matchedRuleCodes`
- `ruleResults`
- `HIGH_AMOUNT` rule result reason

### 완료 기준

- 고액 거래가 rule에 매칭됨
- risk score와 risk level이 deterministic하게 계산됨
- FraudResult 상세 API에서 `matchedRuleCodes`와 `ruleResults` 확인 가능
- 순수 rule logic unit test 작성

## Phase 7. Redis Sliding Window Rule

### 목표

Redis ZSET 기반 사용자별 최근 거래 window를 구현하고 Redis 장애 시 degraded mode를 기록합니다.

### 범위

- `fraud:velocity:{userId}` ZSET
- score = `eventTime` epoch millis
- value = `eventId`
- window 밖 이벤트 cleanup
- VelocityRule
- Redis command latency metric
- Redis unavailable handling
- `skippedRuleCodes`
- `degraded=true` FraudResult

### 완료 기준

- window 내 거래 횟수 기준으로 VelocityRule 매칭
- Redis 장애 시 Redis 의존 rule이 skipped 처리됨
- Redis 장애 중에도 Redis 비의존 rule은 실행됨
- FraudResult 상세 API에서 `degraded`, `skippedRuleCodes` 확인 가능
- Redis sliding window unit/integration test 작성

## Phase 8. Context Rules

### 목표

기기/위치 기반 rule을 추가하거나, 초기 범위에서 제외할 경우 그 이유와 후속 작업을 명확히 기록합니다.

### 범위 후보

- `NEW_DEVICE`
- `LOCATION_CHANGE`
- 최근 device/location context 저장 정책
- context 데이터 부재 시 fallback 정책
- 오탐 가능성 문서화

### 완료 기준

- 구현 시 rule result와 risk score 반영
- 제외 시 `docs/16-fraud-detection-strategy.md`에 제외 이유와 다음 Phase 조건 기록

## Phase 9. Retry, DLT, and Reprocessing

### 목표

Consumer 처리 실패를 retry와 DLT로 분리하고, DLQ 이벤트를 안전하게 조회/재처리/폐기합니다.

### 범위

- retry topic handling
- DLT handling
- `dlq_events` 저장
- `GET /api/v1/admin/dlq-events`
- `POST /api/v1/admin/dlq-events/{dlqId}/reprocess`
- `PATCH /api/v1/admin/dlq-events/{dlqId}/discard`
- `reprocessing_history`
- 원본 `eventId` 보존
- 중복 FraudResult 방어

### 완료 기준

- invalid payload 또는 unsupported schemaVersion이 DLT로 이동
- DLQ metadata 조회 가능
- raw payload가 기본 API 응답에 노출되지 않음
- 재처리 이력이 저장됨
- DLT 재처리 후에도 중복 FraudResult가 생성되지 않음

## Phase 10. Observability

### 목표

API latency, Kafka publish result, Consumer processing latency, detection latency, Consumer Lag, DLQ count, Redis degraded count를 수집합니다.

### 범위

- `/actuator/prometheus`
- API request count/error/latency
- Kafka publish success/failure
- consumed event count
- consumer processing latency
- fraud detection latency
- Consumer Lag
- retry count
- DLT count
- duplicate skip count
- Redis degraded count
- rule matched/skipped count
- Grafana dashboard 초안
- `GET /api/v1/admin/operations/summary`

### 완료 기준

- Prometheus에서 custom metric 확인
- Grafana에서 API/Consumer/Redis/DLQ 관점 dashboard 확인
- 운영 요약 API로 DB 기반 count 확인
- 로그에 `traceId`, `eventId`, topic/partition/offset 포함
- 민감 식별자 원문 logging 없음

## Phase 11. Load and Failure Test

### 목표

정상 부하, 피크 부하, Consumer 장애, Redis 장애, hot partition, invalid schemaVersion을 재현합니다.

### 범위

- normal load
- peak load
- consumer down/restart
- redis down
- hot partition
- invalid schemaVersion
- future eventTime validation

### 완료 기준

- p50/p95/p99 API latency 기록
- Consumer Lag 최대값 기록
- Lag 회복 시간 기록
- fraud detection latency 기록
- DLQ count 기록
- Redis degraded count 기록
- duplicate result count 기록
- 테스트 조건, VU, duration, event count, local environment notes 기록

## Phase 12. Result Documentation and Hardening

### 목표

초기 설계와 실제 구현 차이, 장애 재현 결과, 알려진 한계를 문서화합니다.

### 범위

- `docs/10-failure-scenarios.md`
- `docs/11-troubleshooting-log.md`
- `docs/12-review.md`
- `docs/13-development-roadmap.md`
- README 구현 상태
- benchmark 결과 표
- known limitations

### 완료 기준

- 실행한 검증 명령과 결과가 남아 있음
- 실패한 검증의 원인과 후속 작업이 남아 있음
- README/docs가 실제 구현 상태와 일치함
- 완료되지 않은 기능을 완료로 표현하지 않음

## Phase 13+. Operational and Security Hardening

### 목표

초기 기능 구현 이후 운영 안정성, 보안, 배포 안전성을 보강합니다.

### 후보 작업

- CI/CD gate
- 인증/인가
- secret 관리
- Nginx reverse proxy
- 운영 환경용 Kafka listener 분리
- 보안/개인정보 점검
- SLO 기반 dashboard와 alert 정리
- optional blue-green simulation

## Minimum Verification Gate

다음 PR부터 최소 검증 기준으로 사용합니다.

```bash
./gradlew test
docker compose -f infra/docker-compose.yml config
bash -n scripts/*.sh
```

문서만 변경한 경우에는 markdown 구조와 링크, Phase 상태 정합성을 확인합니다.
