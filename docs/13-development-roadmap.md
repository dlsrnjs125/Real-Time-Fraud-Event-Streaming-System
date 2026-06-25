# Development Roadmap

## Progress Overview

| Phase | Status | 현재 상태 | 주요 산출물 | 다음 작업 |
|---:|---|---|---|---|
| Phase 0 | Done | 초기 기획/설계와 스캐폴딩 작성 완료 | README, docs, Gradle multi-module, app skeleton, Docker Compose skeleton | Phase 1 검증 |
| Phase 1 | Done | 로컬 실행 기반과 scaffold 검증 완료 | Gradle Wrapper, Docker Compose 검증, topic script 검증, app health 검증 | API 계약과 DTO 확정 |
| Phase 2 | Done | API 계약, DTO, validation, OpenAPI skeleton 구현 완료 | app-common event schema, app-api DTO/controller skeleton, Makefile | Phase 3 Kafka Producer 구현 |
| Phase 3 | Done | 거래 이벤트 접수 API, receipt 저장, Kafka Producer 구현 완료 | transaction_event_receipts, Kafka producer, intake service tests | Phase 4 Consumer manual ack와 processing log 구현 |
| Phase 4 | Done | Kafka Consumer manual ack와 processing log 저장/조회 구현 완료 | Kafka listener, event_processing_logs, processing log query API | 기본 LOW FraudResult 저장과 조회 API 구현 |
| Phase 5 | Done | Rule Engine v1과 FraudResult 저장/조회 구현 완료 | fraud_detection_results, Rule Engine v1, fraud result query API | Redis Sliding Window rule 구현 |
| Phase 6 | Done | Redis Sliding Window 기반 최근 거래 패턴 탐지 구현 완료 | Redis window store, stateful rules, degraded mode, fraud result degraded fields | Redis command metric과 integration test 보강 |
| Phase 7 | Done | Redis 통합 검증과 metric foundation 구현 완료 | Redis integration test, Redis latency/degraded/skipped metrics | Grafana dashboard와 alert 후보 연결 |
| Phase 8 | Done | Redis/Kafka failure drill과 Consumer recovery 검증 절차 작성 완료 | failure drill scripts, Kafka unavailable runbook, recovery evidence docs | Retry/DLT 설계 구현 |
| Phase 9 | Done | DLT 저장, 조회, 재처리, 폐기 흐름 구현 완료 | transaction-events-dlt, dead_letter_events, admin DLT API, 상태 전이 테스트 | 운영 관측 고도화와 부하/장애 검증 |
| Phase 10 | Done | 최종 운영 검증 기준과 Phase 10 readiness 문서화 완료 | docs/19, troubleshooting 보강, blog 초안, README 링크 | Final readiness review |
| Phase 11 | Done | Final Readiness Review and Portfolio Documentation | readiness checklist, evidence index, troubleshooting index, final review blog | Observability hardening |
| Phase 12 | Done | Load Test Evidence and Performance Review | k6 normal/peak/duplicate/Redis down scenarios, result template | measured local results |
| Phase 13 | Done | Load and Failure Test Evidence | Phase 13 result template, runbook, review, security note | follow-up metric/dashboard evidence |
| Phase 14 | Done | Operational Security and Automation | admin token protection, audit log, max reprocess attempts | JWT/RBAC, audit query API, gateway rate limit |
| V2 Planning | In Progress | PaySim preprocessing-first fraud workflow design | data provenance, raw protection, preprocessing, validation, sampling, replay, Rule V2, action/case 설계 문서 | V2 Phase 1 구현 |
| Phase 14+ | Not Started | Production hardening follow-up | dashboard/alert hardening, CI/E2E drill, deployment safety | production hardening |

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
- `PUBLISH_FAILED` 상태의 동일 `eventId` 재요청은 `409 CONFLICT` 반환
- `eventTime`이 `receivedAt + 5분`을 초과하면 validation failure 처리
- application clock은 UTC 기준으로 사용

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
- Kafka publish 성공 후 `PUBLISHED` 상태 저장 또는 DB commit 실패 가능성이 있습니다.
- `PUBLISH_FAILED` receipt 자동 재발행은 후속 hardening 후보입니다.
- Entity lifecycle timestamp는 JPA callback 기준이므로 application `Clock`과 완전히 통일되어 있지는 않습니다.
- Kafka Consumer, manual ack, `event_processing_logs` 저장은 Phase 4 범위입니다.

### Next

- Phase 4에서 Kafka Consumer, manual ack, `event_processing_logs` 저장을 구현합니다.

## Phase 4. Consumer Manual Ack and Processing Log

### Phase 4-A. Minimum CI Gate

Consumer 구현 전에 GitHub Actions 기반 최소 CI Gate를 먼저 추가했습니다.

#### 배경

Phase 4부터 Kafka Consumer, manual ack, processing log, 이후 Rule Engine과 Retry/DLT가 추가될 예정이므로 로컬 테스트만으로는 회귀 버그를 안정적으로 막기 어렵다고 판단했습니다.

#### 구현

- `.github/workflows/ci.yml` 추가
- `workflow_dispatch` 수동 실행 지원
- workflow 권한은 `contents: read`로 제한
- push/pull request 시 `make ci-check` 실행
- `make ci-check`는 `./gradlew test`와 `./gradlew assemble`을 실행해 test 중복 실행을 피함
- Docker Compose 기반 Kafka/PostgreSQL/Redis 통합 검증은 후속 Phase로 분리

#### 선택 이유

초기 CI는 빠르고 안정적인 test/assemble Gate로 구성하고, 무거운 E2E 검증은 기능 안정화 이후 별도 workflow로 확장하기로 했습니다.

#### 한계

현재 CI는 Kafka end-to-end consume, Redis 장애, DLT 재처리, k6 부하 테스트까지 검증하지 않습니다.

### 목표

Consumer가 `transaction-events`를 소비하고 처리 로그를 PostgreSQL에 저장합니다.

### Status

Done

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

### Completed

- app-consumer Kafka listener 구현
- `enable-auto-commit=false`, `AckMode.MANUAL_IMMEDIATE` 설정
- `auto-offset-reset=earliest`로 Consumer 중지 중 발행된 미처리 이벤트 재소비 확인 가능
- Consumer group ID를 `fraud-event-consumer`로 명시
- `app-api`를 Flyway schema owner로 두고 app-consumer runtime은 `ddl-auto=validate`만 수행하도록 정리
- app-consumer module test 전용 Flyway migration은 `src/test/resources`에 분리
- `event_processing_logs(topic, partition_no, offset_no)` unique constraint 추가
- `EventProcessingLogEntity`, repository, service 구현
- DB processing log 저장 성공 후 acknowledge 수행
- 처리 실패 시 ack하지 않고 exception을 전파하는 listener test 추가
- 이미 처리된 offset 재소비 시 duplicate log를 만들지 않고 ack 가능한 정책 구현
- 이미 처리된 offset 재소비 시에도 acknowledge하는 listener test 추가
- 같은 eventId라도 offset이 다르면 별도 log를 저장하는 test 추가
- eventId 조회 결과의 `processedAt desc` 정렬 test 추가
- `GET /api/v1/admin/events/{eventId}/processing-log` 조회 API 구현
- Consumer structured log에 `traceId`, `eventId`, `userId`, `topic`, `partition`, `offset` 포함

### Commands

```bash
make ci-check
make test
make build
make final-check
./scripts/reset-local-env.sh
make infra-up
make topics
make api
make consumer
```

### Results

| Check | Result | Notes |
|---|---|---|
| Makefile ci-check | PASS | `./gradlew test`, `./gradlew assemble` 성공 |
| Makefile test | PASS | `make test` 성공 |
| Makefile build | PASS | `make build` 성공 |
| Makefile final-check | PASS | build, Docker Compose config, script syntax check 성공 |
| Manual API publish | PASS | Consumer 중지 상태에서 `evt-phase4-manual-001` 발행, `202 Accepted` |
| Manual pre-consumer lookup | PASS | Consumer 시작 전 processing log 조회 결과 `logs: []` |
| Manual consumer restart | PASS | Consumer 시작 후 `evt-phase4-manual-001` 소비, log에 `partition=4`, `offset=0`, `duplicateSkipped=false` 확인 |
| Manual processing log lookup | PASS | `GET /api/v1/admin/events/evt-phase4-manual-001/processing-log` returned status `PROCESSED` |

Evidence:

- Local review record: `docs/12-review.md#phase-4-review`
- Troubleshooting and decision notes: `docs/11-troubleshooting-log.md`
- Runbook procedure: `docs/18-runbook.md#14-consumer-재시작-후-미처리-이벤트-재소비-확인`

### Known Limitations

- Phase 4에서는 FraudResult를 저장하지 않습니다.
- Retry/DLT는 Phase 9 범위입니다.
- Consumer Lag dashboard와 추가 custom metric은 후속 Observability Hardening 범위입니다.
- Phase 4에서는 eventId 기준 전체 processing log를 조회합니다. Retry/DLT와 재처리 API가 추가되면 `limit`, `page`, `processedAt` range 조건을 추가합니다.
- Phase 4에서는 app-consumer write model과 app-api read model을 분리하기 위해 entity를 모듈별로 유지했습니다. status enum과 column 정의 drift를 막기 위해 후속 Phase에서 shared enum 또는 projection 기반 조회를 검토합니다.
- 같은 offset이 이미 processing log에 있으면 이전 처리 성공으로 보고 ack 가능하게 처리합니다. 이 정책은 processing log 기준이며, eventId 기준 business idempotency는 Phase 5 이후에서 구현합니다.
- `FAILED` processing status는 Phase 4에서는 예약 상태입니다. DB 자체가 저장 불가능한 실패는 ack하지 않고 재소비 가능성을 열어두며, 저장 가능한 business failure/DLT 기록은 Phase 9에서 구체화합니다.

### Next

- Phase 5에서 기본 `LOW` FraudResult 저장과 조회 API를 구현합니다.

## Phase 5. Fraud Detection Result and Rule Engine v1

### 목표

Consumer가 수신한 거래 이벤트를 Rule Engine v1으로 평가하고, 탐지 결과를 PostgreSQL에 저장합니다.

### Status

Done

### 범위

- `fraud_detection_results` 테이블
- `fraud_detection_results.event_id` unique constraint
- Rule Engine v1
- `AMOUNT_THRESHOLD`, `NIGHT_TIME_TRANSACTION`, `SUSPICIOUS_LOCATION`
- riskScore, riskLevel, decision 계산
- Consumer 처리 흐름에 fraud result 저장 연결
- duplicate `eventId` skip 처리
- `GET /api/v1/admin/events/{eventId}/fraud-result`

### 완료 기준

- Consumer가 event 소비 후 Rule Engine v1을 실행
- processing log 저장, Rule Engine 평가, fraud result 저장 성공 후 acknowledge
- fraud result 저장 실패 또는 rule engine 실패 시 acknowledge하지 않음
- 같은 `eventId` 재소비 시 중복 FraudResult가 생성되지 않음
- eventId 기준 fraud result 조회 API에서 결과 조회 가능
- 없는 fraud result 조회 시 `404 FRAUD_RESULT_NOT_FOUND`

### Completed

- app-api runtime Flyway migration `V3__create_fraud_detection_results.sql` 추가
- app-consumer test resources에 V3 migration 추가
- app-consumer runtime Flyway 비활성 정책 유지
- `FraudRuleEngine`, `FraudRule`, rule별 class 구현
- `riskScore` 0~100 cap 적용
- `LOW/APPROVE`, `MEDIUM/REVIEW`, `HIGH/BLOCK` mapping 구현
- `FraudDetectionResultEntity`, repository, service 구현
- `eventId` unique constraint 기반 duplicate save 방어 구현
- Listener 흐름을 processing log -> rule engine -> fraud result save -> acknowledge로 변경
- duplicate fraud result이면 idempotent 성공으로 보고 acknowledge 가능하게 처리
- app-api fraud result read model과 query service 추가
- `GET /api/v1/admin/events/{eventId}/fraud-result` 추가

### Commands

```bash
make ci-check
make infra-up
make topics
make api
make consumer
```

### Results

| Check | Result | Notes |
|---|---|---|
| Rule Engine unit test | PASS | score 합산, cap, riskLevel, decision 검증 |
| Fraud result idempotency test | PASS | duplicate eventId 저장 시 추가 row 생성 없음 |
| Consumer ack test | PASS | success/duplicate ack, failure/rule error miss-ack 검증 |
| Admin API test | PASS | eventId 조회, matchedRules 변환, 404 검증 |
| Makefile ci-check | PASS | `./gradlew test`, `./gradlew assemble` 성공 |
| Manual API publish | PASS | `evt-phase5-low-001`, `evt-phase5-high-001` 모두 `202 Accepted` |
| Manual fraud result lookup | PASS | high risk result returned `riskScore=100`, `riskLevel=HIGH`, `decision=BLOCK` |
| Manual processing log lookup | PASS | high risk event processing log returned status `PROCESSED` |
| Manual duplicate request | PASS | duplicate `eventId` returned `409`, fraud result row count remained `1` |

Evidence:

- Local review record: `docs/12-review.md#phase-5-review`
- Troubleshooting and decision notes: `docs/11-troubleshooting-log.md#phase-5-fraud-result-저장과-consumer-ack-순서`
- Runbook procedure: `docs/18-runbook.md#15-fraud-result와-rule-engine-v1-수동-검증`

### Known Limitations

- Redis Sliding Window 기반 최근 거래 패턴은 후속 Phase 범위입니다.
- Retry/DLT 기반 실패 복구는 후속 Phase 범위입니다.
- Phase 5에서는 processing log와 fraud result를 하나의 DB transaction으로 묶지 않습니다. processing log 저장 후 fraud result 저장 전에 장애가 발생하면 일시적으로 processing log만 존재할 수 있으며, ack 미호출로 Kafka 재소비를 유도합니다.
- 최종 중복 방어는 Consumer 코드가 아니라 PostgreSQL `event_id` unique constraint가 담당합니다. `existsByEventId()`는 불필요한 insert를 줄이는 fast path입니다.
- Rule threshold는 Phase 5에서 코드 상수로 관리합니다. 이는 테스트 가능성과 구현 단순성을 우선한 선택이며, 운영 중 threshold 변경이 필요한 경우 application config, DB rule table, feature flag 방식으로 분리합니다.
- Phase 5 result는 eventId 기준 단일 결과만 저장합니다. rule version 변경 후 재평가 이력을 남기려면 result versioning이 필요합니다.
- `matched_rules`는 comma-separated text로 저장합니다. rule rename 또는 unknown code 대응을 위해 후속 Phase에서 JSONB, rule version, raw string 응답 정책을 검토합니다.
- Fraud result 조회 API는 운영자용 admin API이며, 실제 운영 확장 시 ADMIN 권한 기반 인증/인가와 감사 로그가 필요합니다.
- `detectedAt`은 application `Clock` 기준으로 생성하지만 `createdAt`, `updatedAt`은 Entity lifecycle callback의 `OffsetDateTime.now()` 기준입니다. timestamp 기준 통일은 후속 개선 대상입니다.
- Kafka/PostgreSQL/Redis E2E CI는 후속 integration workflow에서 보완합니다.

### Next

- Redis 기반 VelocityRule과 degraded mode를 구현합니다.

## Phase 6. Redis Sliding Window Risk Detection

### 목표

단일 이벤트 기반 Rule Engine을 Redis 기반 사용자별 최근 거래 패턴 탐지로 확장합니다.

### Status

Done

### 범위

- `fraud:tx:user:{userId}:events` ZSET
- `fraud:tx:event:{eventId}` Hash
- score = `eventTime` epoch millis
- member = `eventId`
- 최근 5분 거래 횟수와 누적 금액 계산
- `RAPID_TRANSACTION_COUNT`, `WINDOW_AMOUNT_SUM` rule
- Redis unavailable degraded mode
- `fraud_detection_results.skipped_rules`, `degraded`

### Completed

- app-consumer Redis Sliding Window store 구현
- eventTime 기준 window 계산과 window 밖 이벤트 cleanup 구현
- Redis Hash metadata로 amount 합산 구현
- duplicate fraud result fast path로 이미 저장된 eventId는 Redis window 갱신 없이 ack
- Redis 부분 실패로 Hash metadata가 없는 ZSET member는 count/sum 계산에서 제외
- Rule Engine에 `RecentTransactionWindowResult`를 전달해 infra 접근과 rule 평가를 분리
- stateless rule score와 stateful rule score 합산, total score 100 cap 유지
- Redis 장애 시 stateful rule을 skipped 처리하고 `degraded=true` 결과 저장
- fraud result 조회 API에 `skippedRules`, `degraded` 응답 추가
- app-api runtime migration owner 정책을 유지하고 app-consumer runtime Flyway 비활성 정책 유지

### Commands

```bash
./gradlew :app-consumer:test
./gradlew :app-api:test
```

### Results

| Check | Result | Notes |
|---|---|---|
| Redis window store test | PASS | count/sum, duplicate eventId, window cleanup, partial metadata exclusion, degraded result 검증 |
| Rule Engine stateful test | PASS | rapid count, window amount sum, score cap, Redis degraded skip 검증 |
| Consumer ack test | PASS | duplicate fraud result Redis skip, Redis degraded ack, DB/rule 실패 시 ack 미호출 검증 |
| Admin API test | PASS | fraud result 응답의 `skippedRules`, `degraded` 필드 검증 |
| app-consumer test | PASS | `./gradlew :app-consumer:test` 성공 |
| app-api test | PASS | `./gradlew :app-api:test` 성공 |

### Known Limitations

- Redis integration test와 Testcontainers 검증은 이번 Phase에서 제외했습니다.
- Redis command latency metric, Redis degraded count metric, rule skipped metric은 Observability Phase에서 구현합니다.
- Retry/DLT와 DLQ 재처리는 이번 Phase 범위가 아닙니다.
- `matched_rules`와 `skipped_rules`는 comma-separated text로 저장합니다. rule detail 구조화는 후속 개선 대상입니다.
- Redis 상태는 최종 정합성 기준이 아니며, 최종 duplicate 방어는 PostgreSQL `fraud_detection_results.event_id` unique constraint가 담당합니다.

### Next

- Redis degraded count, Consumer Lag, detection latency custom metric을 추가합니다.
- Redis down failure scenario와 부하 테스트에서 degraded 결과 비율과 처리 지연을 측정합니다.

## Phase 7. Redis Integration Test and Metrics Hardening

### 목표

Phase 6에서 구현한 Redis Sliding Window를 실제 Redis 기준으로 검증하고, Redis degraded mode를 관측할 수 있는 metric foundation을 추가합니다.

### Status

Done

### 범위

- 실제 Redis 기반 sliding window integration test
- Redis ZSET/Hash 저장 검증
- duplicate eventId count 방어 검증
- window cleanup, TTL, metadata 없는 ZSET member 제외 검증
- `fraud.redis.window.record.latency` Timer
- `fraud.redis.window.degraded.total` Counter
- `fraud.rule.skipped.total{rule=...}` Counter
- `fraud.detection.degraded.total` Counter
- Redis degraded/window 정보 structured log 보강

### Completed

- `redisIntegrationTest` Gradle task와 `make redis-integration-test` target 추가
- Docker Compose Redis 기반 integration test를 기본 unit test와 분리
- Redis readiness 확인 후 integration test 실행
- 테스트 전용 Redis database index `15`만 초기화
- 실제 Redis에서 ZSET member, Hash metadata, TTL, cleanup, duplicate eventId, eventTime window 계산 검증
- `FraudConsumerMetrics` adapter 추가
- Redis window record/get latency Timer 추가
- Redis degraded, skipped rule, degraded detection Counter 추가
- Consumer degraded 처리 시 metric 증가와 structured log 필드 보강

### Commands

```bash
./gradlew :app-consumer:test
make redis-integration-test
```

### Results

| Check | Result | Notes |
|---|---|---|
| app-consumer test | PASS | metric unit test, listener metric test, Redis store unit test 통과 |
| Redis integration test | PASS | Docker Compose Redis DB 15 기준 ZSET/Hash/TTL/cleanup/duplicate 검증 |
| Testcontainers attempt | FAIL/DOCUMENTED | local Docker provider API 호환 문제로 Docker Compose Redis fallback 선택 |

### Known Limitations

- Kafka + Redis + PostgreSQL 전체 E2E 검증은 후속 Phase 범위입니다.
- Grafana dashboard 구성과 alert rule은 Observability Phase에서 연결합니다.
- k6 기반 Redis down/Consumer Lag 부하 검증은 Load Test Phase에서 수행합니다.
- metric foundation은 추가했지만 운영 threshold와 alert 기준은 아직 정의하지 않았습니다.

### Next

- Prometheus/Grafana dashboard에 Redis degraded, skipped rule, latency metric을 연결합니다.
- Redis down failure scenario와 k6 부하 테스트로 degraded 비율과 Consumer Lag 영향을 측정합니다.

## Phase 8. Redis/Kafka Failure Drill and Consumer Recovery

### 목표

Redis down, Consumer restart, Kafka unavailable 상황을 재현하고, degraded mode와 idempotency가 유지되는지 검증합니다.

### Status

Done

### 범위

- Redis down drill script
- Consumer restart drill script/runbook
- Kafka unavailable drill runbook
- failure drill Makefile target
- degraded metric/log/API 조회 절차
- event consistency 보조 script
- failure scenario, troubleshooting, runbook, blog 기록

### Completed

- `scripts/failure_drills/redis_down_drill.sh` 추가
- `scripts/failure_drills/consumer_restart_drill.sh` 추가
- `scripts/failure_drills/check_event_consistency.sh` 추가
- `scripts/failure_drills/kafka_unavailable_drill.md` 추가
- `scripts/failure_drills/common.sh`로 HTTP retry, event 발행, metric 증가 확인, DB row count helper 정리
- `make failure-drill-redis`, `make failure-drill-consumer`, `make failure-drill` target 추가
- `scripts-check`에 failure drill shell syntax check 추가
- Redis down 시 `degraded=true`, skipped Redis rule, degraded metric 증가 확인 절차 정리
- Consumer restart 시 Kafka 보존 메시지 처리와 `fraud_detection_results` row count 1건 확인 절차 정리
- Kafka unavailable은 자동 script 대신 runbook으로 분리

### Commands

```bash
make ci-check
make redis-integration-test
make failure-drill-redis
bash -n scripts/failure_drills/*.sh
```

### Results

| Check | Result | Notes |
|---|---|---|
| CI check | PASS | `make ci-check` |
| Redis integration test | PASS | `make redis-integration-test` |
| script syntax check | PASS | `bash -n scripts/failure_drills/*.sh` |
| Redis failure drill | PASS | Redis stop 중 degraded fraud result, skipped rule, degraded metric 증가 확인 |
| Consumer restart drill | MANUAL/RUNBOOK | app-consumer가 Docker Compose service가 아니라 local Gradle process라 수동 restart 전제, DB row count 검증 포함 |
| Kafka unavailable drill | RUNBOOK | broker stop/start 자동화 위험 때문에 markdown runbook으로 분리 |

### Known Limitations

- Retry/DLT 자동 복구는 후속 Phase 범위입니다.
- Kafka + Redis + PostgreSQL full E2E 부하 검증은 후속 Phase 범위입니다.
- Grafana dashboard와 alert rule은 후속 Observability Phase에서 구성합니다.
- k6 기반 장애 부하 테스트는 후속 Load/Failure Test Phase에서 수행합니다.

## Phase 9. Retry / DLT / Reprocessing Flow

### 목표

Consumer 처리 실패 이벤트를 DLT로 격리하고, 운영자가 조회/재처리/폐기할 수 있는 흐름을 구현합니다.

### 구현

- `transaction-events-dlt` topic 추가
- `dead_letter_events` 테이블 추가
- Consumer DLT 저장 service와 DLT topic publisher 구현
- Rule Engine 예외 발생 시 DLT 저장/publish 후 원본 offset ack
- DB 저장 실패는 DLT가 아니라 no-ack 재소비 정책 유지
- DLT 목록 조회 API 추가
- DLT 단건 조회 API 추가
- DLT 재처리 API 추가
- DLT 폐기 API 추가
- 상태 전이 검증 추가
- `source_topic`, `source_partition`, `source_offset` unique constraint로 DLT 중복 저장 방어
- 재처리 시 원본 `eventId`를 유지해 Consumer duplicate fast path와 `fraud_detection_results.event_id` unique constraint로 중복 FraudResult 방어

### 검증

- DLT 저장 idempotency test
- Consumer failure to DLT test
- Admin DLT API test
- 상태 전이 conflict test
- `./gradlew :app-api:test :app-consumer:test` PASS

### 한계

- Kafka publish와 DB update의 atomic transaction은 이번 Phase에서 제외했습니다.
- 대량 DLT batch reprocess와 rate limit은 후속 Phase 후보입니다.
- 관리자 인증/인가와 audit log는 후속 보안 Phase에서 보강합니다.
- Grafana dashboard와 alert rule은 후속 Observability Phase에서 구성합니다.

## Phase 10. Final Readiness Documentation

### 목표

Phase 9 DLT 재처리 흐름 이후 운영자가 복구 완료를 판단할 수 있도록 최종 검증 기준과 문서 evidence를 정리합니다.

### 범위

- `docs/19-phase-10-final-readiness.md`
- `docs/11-troubleshooting-log.md` Phase 10 섹션
- README 현재 구현 범위와 문서 링크 최소 갱신
- Phase 10 blog draft
- 최종 검증 명령 실행 결과 기록

### 완료 기준

- Phase 9까지 완료된 기능과 운영 검증 기준이 문서에 정리됨
- API/Consumer/Kafka/Redis/PostgreSQL/DLT 관점 확인 기준이 분리됨
- 남은 한계와 실제 운영 확장 후보가 과장 없이 기록됨
- `./gradlew clean build`, `./gradlew test`, `make test`, `make final-check` 결과가 기록됨

### 결과

| Check | Result | Notes |
|---|---|---|
| Gradle clean build | PASS | `./gradlew clean build` |
| Gradle test | PASS | `./gradlew test` |
| Make test | PASS | `make test` |
| Final check | PASS | `make final-check`로 build, Docker Compose config, script syntax check 통과 |

### 남은 한계

- 실제 end-to-end DLT recovery evidence는 후속 local drill에서 캡처합니다.
- DLT batch reprocess, rate limit, 관리자 인증/인가, audit log는 후속 운영 안정화 후보입니다.
- Prometheus/Grafana dashboard와 alert rule hardening은 별도 Phase에서 보강합니다.
- k6 부하 수치와 hot partition 측정은 후속 Load/Failure Test에서 최신 기준으로 재측정합니다.

## Phase 11. Final Readiness Review and Portfolio Documentation

### 목표

Phase 1~10까지 구현한 기능, 운영 검증, 장애 대응, metric, DLT 재처리 흐름을 최종 포트폴리오 관점으로 정리합니다.

### 구현

- README 최소 요약 정리
- 운영 준비도 checklist 추가
- phase별 evidence index 추가
- stale limitation 정리
- troubleshooting index 추가
- blog final review 초안 추가

### 검증

- `make ci-check`
- `make redis-integration-test`
- `make failure-drill-redis`
- `make scripts-check`
- `make topics`
- docs 링크 검토

### 한계

- 실제 운영 인증/인가와 audit log는 후속 보안 고도화 범위입니다.
- k6 기반 본격 부하 테스트와 Grafana dashboard 캡처는 후속 evidence phase 후보입니다.
- DLT pending/reprocess failed/discard metric과 alert rule은 후속 Observability Hardening에서 보강합니다.

## Phase 12. Load Test Evidence and Performance Review

### 목표

k6 기반 정상/피크/중복/Redis down 부하 시나리오를 통해 API 응답성과 장애 시 degraded mode를 측정할 수 있는 evidence 체계를 정리합니다.

### 구현

- normal-load k6 scenario 추가
- peak-load k6 scenario 추가
- duplicate-replay k6 scenario 추가
- redis-down-load k6 scenario 추가
- Makefile k6 target 추가
- Redis down load 안전 실행 script 추가
- load test result template 추가
- docs/blog에 Phase 12 결과 해석 기준 추가

### 검증

- `make ci-check` PASS
- `make scripts-check` PASS
- `make k6-smoke` PASS
- hidden unicode check PASS
- app-api/app-consumer health after smoke PASS
- consumer Prometheus fraud metric sample 확인

### 한계

- 실제 p50/p95/p99와 병목 후보는 로컬 Docker Compose 환경에서 실행한 뒤 `docs/22-load-test-results.md`에 기록합니다.
- Consumer Lag metric과 Grafana dashboard는 후속 Observability Hardening Phase에서 보강합니다.
- CI 자동 부하 테스트는 후속 운영 자동화 Phase 후보입니다.
- 로컬 환경 기준 결과이므로 절대 성능 수치로 일반화하지 않습니다.

## Phase 13. Load and Failure Test Evidence

### 목표

k6 기반 정상/피크/중복/Redis down 부하 시나리오를 통해 API 응답성과 장애 시 degraded mode를 측정하고, 결과 해석 기준을 evidence로 남깁니다.

### 구현

- smoke k6 scenario 정렬
- normal-load k6 scenario 정렬
- peak-load k6 scenario 정렬
- duplicate-replay k6 scenario 정렬
- redis-down-load k6 scenario 정렬
- Makefile k6 target 확인
- Redis down load 안전 실행 script 확인
- duplicate result count 확인 script 확인
- Phase 13 load test result template 추가
- docs/blog에 Phase 13 결과 해석 기준 추가
- README 최소 요약 갱신

### 검증

- `make ci-check` PASS
- `make scripts-check` PASS
- `k6 inspect` for smoke/normal/peak/duplicate/redis-down PASS
- `make k6-smoke` PASS
- app-api/app-consumer health after smoke PASS
- consumer Prometheus fraud metric sample 확인
- hidden unicode check PASS

### 한계

- Consumer Lag metric은 후속 Observability hardening에서 보강합니다.
- Grafana dashboard screenshot evidence는 후속 Phase에서 남깁니다.
- CI 자동 부하 테스트는 후속 운영 자동화 Phase 후보입니다.
- 로컬 환경 기준 결과이므로 절대 성능 수치로 일반화하지 않습니다.

## Phase 14. Operational Security and Automation

### 목표

Admin API 보호, DLT 재처리/폐기 audit log, max reprocess attempts 정책을 추가해 운영 조작의 보안성과 감사 가능성을 높입니다.

### 구현

- `/api/v1/admin/**`에 `X-Admin-Token` 기반 local/dev 최소 보호를 추가했습니다.
- `admin_audit_logs` 테이블을 추가하고 DLT reprocess/discard 성공과 실패를 기록합니다.
- Audit metadata에는 `eventId`, 상태, attempts, 결과 사유 같은 최소 정보만 저장하고 admin token 또는 원본 payload 전체를 저장하지 않습니다.
- DLT reprocess는 `DLT_REPROCESS_MAX_ATTEMPTS` 설정을 사용하며 기본값은 3입니다.
- `reprocess_attempts >= maxAttempts`이면 `409 MAX_REPROCESS_ATTEMPTS_EXCEEDED`를 반환하고 Kafka publish를 호출하지 않습니다.
- discard/reprocess request의 `operatorId`, `reason` 길이 validation을 추가했습니다.
- OpenAPI에 admin token header security scheme을 반영했습니다.
- Security docs, API docs, data model, consistency/reprocessing docs, troubleshooting log, runbook, review, evidence index, blog draft, README를 갱신했습니다.

### 검증

- `./gradlew :app-api:test` PASS
- `make ci-check` PASS
- `./gradlew :app-consumer:test` PASS
- hidden unicode check PASS
- Admin API unauthorized/invalid token test PASS
- Valid admin token DLT list test PASS
- Transaction ingest API without admin token test PASS
- DLT reprocess/discard audit log test PASS
- max attempts conflict and no-publish test PASS

### 한계

- `X-Admin-Token`은 local/dev 최소 보호이며 production-grade 인증/인가가 아닙니다.
- JWT/OAuth2/RBAC, audit log 조회 API, IP allowlist, gateway/Nginx/API Gateway rate limit은 후속 운영 보안 Phase에서 보완합니다.
- Batch reprocess, 관리자 승인 workflow, cooldown 정책은 후속 운영 자동화 범위입니다.

## Phase 14+. Production Hardening Follow-up

### 목표

Phase 14의 local/dev 보안 guardrail을 운영 환경에 맞게 확장하고, 자동화와 배포 안전성을 보강합니다.

### 후보 작업

- JWT/OAuth2/RBAC 기반 관리자 인증/인가
- audit log 조회/필터링 API
- DLT batch reprocess, cooldown, gateway rate limit
- CI gate와 E2E drill 자동화
- secret 관리
- Nginx reverse proxy
- 운영 환경용 Kafka listener 분리
- 보안/개인정보 점검
- SLO 기반 dashboard와 alert 정리
- Consumer Lag, detection latency, DLT pending/reprocess/discard metric
- optional blue-green simulation

### 완료 기준

- admin API 보호 기준이 문서와 구현에 반영됨
- 재처리/폐기 이력이 감사 가능한 형태로 남음
- CI 또는 local automation에서 핵심 build/test/config/drill 검증을 반복 실행할 수 있음

## Minimum Verification Gate

다음 PR부터 최소 검증 기준으로 사용합니다.

```bash
./gradlew test
docker compose -f infra/docker-compose.yml config
bash -n scripts/*.sh
```

문서만 변경한 경우에는 markdown 구조와 링크, Phase 상태 정합성을 확인합니다.

## V2 Planning. PaySim-based Fraud Workflow

### 목표

Kaggle PaySim synthetic 금융 거래 데이터를 재현 가능한 방식으로 연동하고, Rule 기반 탐지 결과를 위험도별 action decision과 fraud case 운영 흐름으로 확장하는 후속 작업을 설계합니다.

### 설계 범위

- PaySim data provenance와 raw data 미커밋 정책
- raw/processed/sample data 보호 정책
- PaySim CSV to normalized JSONL mapping
- validation report, rejected row, sampling 정책
- identifier hashing과 raw identifier 비노출 정책
- replay pipeline 설계
- PaySim balance/type feature 기반 Rule Engine V2 전략
- PaySim label 기반 Rule coverage 평가
- Fraud Action Decision 정책
- Fraud Case 생성/조회/처리 정책
- V2 결과 evidence와 시각화 계획

### 산출물

- `docs/24-kaggle-paysim-data-provenance.md`
- `docs/25-paysim-normalization-mapping.md`
- `docs/26-fraud-rule-v2-strategy.md`
- `docs/27-fraud-action-decision.md`
- `docs/28-fraud-case-management.md`
- `docs/29-v2-result-evidence.md`
- `docs/30-v2-visualization.md`

### V2 구현 단계 제안

V2는 Rule Engine부터 시작하지 않습니다. Kaggle 데이터를 직접 사용할 예정이므로 첫 중심은 데이터 전처리 파이프라인입니다.

| V2 Phase | 목표 | 구현 범위 | 완료 기준 |
|---:|---|---|---|
| V2 Phase 1 | Data Provenance and Raw Data Protection | `data/raw`, `data/processed`, `data/samples`, `scripts/data/README.md`, `.gitignore` data policy, `make data-policy-check` | DONE. raw CSV와 processed 대용량 결과가 commit되지 않도록 repository guardrail과 문서 링크가 정리됨 |
| V2 Phase 2 | PaySim Preprocessing and Normalization | `prepare_paysim_dataset.py`, PaySim CSV to runtime event and label sidecar mapping | `paysim-events.jsonl`, `paysim-labels.jsonl`, `paysim-rejected.jsonl`, `paysim-validation-report.json` 출력 설계 구현 |
| V2 Phase 3 | Data Validation, Rejected Rows, Sampling | fail-fast/reject policy, streaming CSV processing, validation rules, `sample_paysim_dataset.py` | validation report와 100~1,000건 sample 생성, reject ratio 정책 적용 |
| V2 Phase 4 | Identifier Hashing and Data Privacy | `--hash-identifiers`, `--hash-salt`, user/account/destination hash policy | raw `nameOrig`, `nameDest`가 API/log/sample/result에 노출되지 않음 |
| V2 Phase 5 | PaySim Replay Pipeline | `replay_paysim_to_api.py`, `replay_paysim_sample.sh`, Makefile target | label 없는 runtime event를 rate-limited replay하고 API/Kafka 유입 확인 |
| V2 Phase 6 | Rule Engine V2 for PaySim Patterns | `BALANCE_DRAIN`, `ZERO_BALANCE_AFTER_TRANSFER`, `TRANSFER_CASHOUT_PATTERN` | Rule unit test와 score/risk mapping 통과 |
| V2 Phase 7 | PaySim Label-based Rule Evaluation | Java Rule Engine 기반 offline evaluation, confusion matrix, precision/recall/f1 coverage report | offline/online이 같은 rule version을 사용하고, missed/false positive 예시 문서화 |
| V2 Phase 8 | Fraud Action Decision Engine | `fraud_action_decisions`, action policy, admin query API | `unique(event_id, action_type)` 기준 action decision 생성 |
| V2 Phase 9 | Fraud Case Management | `fraud_cases`, list/detail/resolve API, audit action 확장 | HIGH/CRITICAL case 생성, resolved 상태 충돌 방어, audit log 저장 |
| V2 Phase 10 | Evidence and Visualization | result evidence doc, charts/tables, README/blog 정리 | risk/rule/action/case/confusion matrix evidence 작성 |

### V2 Phase 1 완료 기록

구현:

- `data/raw/.gitkeep`, `data/processed/.gitkeep`, `data/samples/.gitkeep` 추가
- `.gitignore`에 raw/processed commit 금지와 sample allowlist 반영
- `scripts/data/README.md` 추가
- `scripts/data/check-data-policy.sh` 추가
- `make data-policy-check` target 추가
- `make final-check`에 data policy check 연결
- README, docs, blog에 V2 Phase 1 guardrail 기록

검증:

- `bash -n scripts/data/check-data-policy.sh`: PASS
- `make scripts-check`: PASS
- `make data-policy-check`: PASS
- `./gradlew test`: PASS
- `docker compose -f infra/docker-compose.yml config --quiet`: PASS
- `make final-check`: PASS
- `git check-ignore -v data/raw/PS_20174392719_1491204439457_log.csv`: ignored by `.gitignore`
- `git check-ignore -v data/processed/paysim-events.jsonl`: ignored by `.gitignore`
- temporary `data/samples/paysim-events-sample.jsonl` check: allowed by sample allowlist, then removed

남은 한계:

- sample file의 raw identifier 포함 여부는 Phase 1 shell check가 완전히 판별하지 못합니다.
- Phase 2~4에서 preprocessing, validation, hashing, sample generation 검증을 구현해야 합니다.
- Kaggle CSV와 processed full output은 local에만 존재해야 하며 repository에 포함하지 않습니다.

### V2 다음 단계별 진행 순서

| Step | Phase | 작업 |
|---:|---|---|
| Step 1 | V2 Phase 1 | data directory, gitignore, provenance docs |
| Step 2 | V2 Phase 2 | PaySim normalization script |
| Step 3 | V2 Phase 3 | validation report, rejected rows, sample generation |
| Step 4 | V2 Phase 4 | identifier hashing |
| Step 5 | V2 Phase 5 | replay pipeline |
| Step 6 | V2 Phase 6 | Rule Engine V2 초기 rule 구현 |
| Step 7 | V2 Phase 7 | Rule evaluation confusion matrix |
| Step 8 | V2 Phase 8 | Action Decision Engine |
| Step 9 | V2 Phase 9 | Fraud Case Management |
| Step 10 | V2 Phase 10 | visualization/evidence, README, docs, blog 정리 |

### V2 첫 구현 PR 권장 범위

PR 제목 후보:

```text
docs: refine v2 paysim preprocessing plan
```

포함:

- data preprocessing phase 세분화
- raw/processed/sample data policy
- validation report schema
- rejected row schema
- fail-fast vs row-level reject policy
- streaming CSV processing policy
- identifier hashing policy
- typed `TransactionBalanceFeatures` runtime schema decision
- PaySim label usage boundary
- runtime event and label sidecar file separation
- offline evaluation and online replay evaluation boundary
- V2 phase roadmap 세분화

아직 포함하지 않음:

- Java Rule 구현
- action decision table
- fraud case API
- replay script 구현

### V2 구현 시작 전 체크리스트

Data preprocessing:

- raw CSV는 repository에 커밋하지 않음
- processed 전체 결과도 커밋하지 않음
- sample만 100~1,000건 이하로 커밋 가능
- runtime event와 label sidecar 분리
- identifier hashing 기본 적용
- inputSha256, scriptVersion, baseTime 기록
- fail-fast vs row-level reject 기준 명시
- streaming CSV 처리 기준 명시

Runtime schema:

- `TransactionBalanceFeatures` typed optional field 사용
- balance feature JSON field는 `sourceStep`으로 통일
- generic map feature 사용 안 함
- label/isFraud/sourceFlaggedFraud는 runtime payload에 포함하지 않음
- `receivedAt`은 replay payload에 포함하지 않고 app-api가 생성
- `currency=KRW`, `source=PAYSIM` 사용
- `TransactionEventType`에 `CASH_OUT`, `CASH_IN`, `DEBIT` 추가 검토
- amount/balance는 `BigDecimal`로 처리

Rule Engine:

- V2 초기 Rule은 3개로 제한
- `oldBalanceOrig <= 0`이면 `BALANCE_DRAIN` not matched 또는 skipped 처리
- `newBalanceOrig == 0` 판단은 `BigDecimal.compareTo(BigDecimal.ZERO) == 0` 사용
- score cap 100 적용
- HIGH/CRITICAL 기준이 evaluation positive prediction임을 명시

Evaluation:

- offline evaluation과 online replay evaluation 분리
- 두 평가가 같은 Java Rule Engine과 같은 ruleVersion을 사용
- precision/recall은 Rule coverage로만 해석
- missed fraud와 false positive examples 기록

Action and case:

- ActionDecision unique는 `eventId + actionType`
- 자동 ActionDecision은 admin audit log에 대량 저장하지 않음
- HIGH/CRITICAL만 FraudCase 생성
- FraudCase는 실제 제재가 아니라 review unit
- resolved 상태 충돌은 409
- operatorId는 self-claimed actor caveat 유지

### 명시적 제외 범위

- AI/ML 모델 학습과 serving
- 실제 개인정보 포함 데이터 사용
- 실제 계좌 정지 또는 금융 제재 실행
- 외부 금융기관 API 연동
- production-grade JWT/OAuth2/RBAC
- 복잡한 feature store 또는 real-time ML inference

### 현재 상태

기획/설계 문서화 단계입니다. V2 기능 구현, data script, DB migration, API 추가, Rule V2 코드는 아직 구현하지 않았습니다.
