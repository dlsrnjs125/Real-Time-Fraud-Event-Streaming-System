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
| Phase 8 | Not Started | Load/failure 검증 미실행 | k6 시나리오 초안 | Redis down, consumer lag, hot partition 검증 |
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
- Consumer Lag과 custom metric은 Phase 10 범위입니다.
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
