# Review

## 1. 검토 목적

설계와 구현 과정에서 다음 항목을 반복 검토합니다.

- 핵심 문제가 코드와 문서에 일관되게 반영되었는가
- API와 Consumer의 책임 경계가 흐려지지 않았는가
- Kafka key, offset commit, DLQ, 재처리 정책이 테스트 가능한 형태인가
- Redis 장애 시 degraded mode가 명확히 드러나는가
- 관측 지표가 실제 장애 상황을 설명할 수 있는가

## 2. 초기 검토 항목

- 완전한 MSA로 과도하게 확장하지 않습니다.
- API Gateway, Service Discovery, OAuth2는 초기 범위에 넣지 않습니다.
- 거래 원장이나 승인 시스템은 구현 범위에서 제외합니다.
- README에는 구현 상태와 설계 의도를 분리해 기록합니다.
- 설계 변경은 `docs/11-troubleshooting-log.md`에 누적합니다.
- 구조화 로그에 계좌, 기기, IP 등 민감 필드 원문이 남지 않도록 확인합니다.

## Phase 12 Review

### 잘한 점

- k6 scenario를 normal, peak, duplicate replay, Redis down load로 분리해 API 응답성과 consistency/degraded mode 검증을 같은 도구로 반복할 수 있게 했습니다.
- Redis down load는 shell script에서 Redis stop/start를 담당하고 `trap`으로 복구를 시도해 테스트 실패 후 환경이 망가질 위험을 줄였습니다.
- Redis down load cleanup은 `redis-cli ping` readiness 확인과 degraded metric before/after 출력까지 포함합니다.
- Duplicate Replay는 409 응답을 단순 실패로 해석하지 않고 PostgreSQL unique constraint와 fraud result count를 최종 기준으로 두었습니다.
- Duplicate Replay 후 `scripts/load_tests/check_duplicate_result_count.sh`로 fraud result count를 확인할 수 있게 했습니다.
- `make k6-smoke`가 전용 `load-test/k6/scenarios/smoke.js`를 실행하고, smoke 결과 문서도 같은 script 기준으로 기록되는지 재확인했습니다.
- load test raw result는 git에 커밋하지 않고, 요약과 해석만 `docs/22-load-test-results.md`에 기록하도록 분리했습니다.

### 의도적으로 제외한 것

- 무거운 k6 부하 테스트를 기본 CI gate에 넣지 않았습니다.
- 측정하지 않은 p50/p95/p99 수치를 문서에 임의로 작성하지 않았습니다.
- Consumer Lag dashboard와 Grafana screenshot은 후속 Observability Hardening 범위로 남겼습니다.
- 운영 환경 URL 대상 부하 테스트는 명시적으로 제외했습니다.

### 결과 해석

Phase 12의 핵심은 "빠르다"는 결론이 아니라 어떤 부하에서 API latency, error rate, degraded metric, duplicate 방어 결과를 함께 봐야 하는지 기록하는 것입니다. 실제 수치는 로컬 Docker Compose 환경에서 실행한 뒤 `docs/22-load-test-results.md`에 남깁니다.

### 병목 후보

- API: validation, Kafka publish latency, DB receipt 저장
- Kafka: broker resource, partition hot spot, publish timeout
- Consumer: rule execution, manual ack 전 DB 저장 지연
- Redis: sliding window command latency, unavailable fallback
- PostgreSQL: unique constraint conflict, insert latency, connection pool

### 다음 단계 보완

- Consumer Lag metric 연결
- Grafana dashboard와 alert rule 후보 정리
- DB insert latency와 connection pool metric 보강
- 반복 가능한 full load/failure evidence 캡처

## Phase 13 Review

### 잘한 점

- Phase 13 결과 기록을 `docs/23-load-test-results.md`로 분리해 Phase 12 템플릿과 최신 측정 evidence를 혼동하지 않게 했습니다.
- `make k6-smoke`가 3회 요청 전용 `smoke.js`를 실행하고, Normal/Peak/Duplicate/Redis down scenario와 역할이 분리되어 있는지 재확인했습니다.
- Redis down load는 shell script에서 Redis stop/start를 담당하고, cleanup에서 `redis-cli ping` readiness와 Redis degraded, detection degraded, skipped rule metric 증가를 검증하도록 유지했습니다.
- Duplicate Replay는 API 409 응답을 단순 실패로 해석하지 않고 PostgreSQL fraud result count를 최종 consistency 기준으로 두며, `make k6-duplicate-check`로 replay와 count 검증을 함께 실행할 수 있게 했습니다.
- k6 payload는 synthetic identifier만 사용하고, raw result 파일은 git에 커밋하지 않는 기준을 보안 문서와 결과 문서에 연결했습니다.

### 의도적으로 제외한 것

- 무거운 Normal/Peak/Redis-down load test를 기본 CI gate에 넣지 않았습니다.
- 측정하지 않은 p50/p95/p99, DLT count, Redis degraded count를 임의로 작성하지 않았습니다.
- Consumer Lag metric과 Grafana screenshot evidence는 후속 Observability hardening 범위로 남겼습니다.
- 운영 환경 URL 대상 부하 테스트는 명시적으로 제외했습니다.

### 결과 해석

Phase 13의 핵심은 "기능이 동작한다"가 아니라 어느 부하에서 API latency, request failure, Redis degraded metric, duplicate 방어 결과가 어떻게 달라지는지 설명 가능한 evidence를 남기는 것입니다. 실제 수치는 로컬 Docker Compose 환경에서 실행한 뒤 `docs/23-load-test-results.md`에 남깁니다.

### 병목 후보

- API: validation, Kafka publish latency, receipt 저장 지연
- Kafka: broker resource, partition hot spot, publish timeout
- Consumer: rule execution, manual ack 전 DB 저장 지연
- Redis: sliding window command latency, unavailable fallback
- PostgreSQL: unique constraint conflict, insert latency, connection pool

### 다음 단계 보완

- Consumer Lag metric 연결
- Grafana dashboard와 screenshot evidence 정리
- DLT pending/reprocess/discard metric 보강
- 반복 가능한 scheduled load test 또는 수동 evidence capture 절차 정리

## 3. 리뷰 질문

1. `userId` partition key로 사용자별 순서가 충분히 보장되는가?
2. hot partition이 발생했을 때 어떤 지표로 설명할 수 있는가?
3. 처리 성공 전 offset commit을 막고 있는가?
4. 중복 이벤트가 들어와도 FraudResult가 중복 생성되지 않는가?
5. Redis 장애가 원본 이벤트 유실로 이어지지 않는가?
6. DLQ 이벤트를 운영자가 안전하게 재처리할 수 있는가?
7. API latency와 detection latency를 분리해서 측정할 수 있는가?
8. 지원하지 않는 `schemaVersion` 이벤트가 DLT로 이동하는가?
9. `receivedAt`, `detectedAt` 기준으로 지연 시간을 계산할 수 있는가?
10. 로그에 민감 식별자가 원문 그대로 남지 않는가?

## Phase 1 Review

### 제안 또는 변경한 내용

- Gradle Wrapper를 추가해 로컬 Gradle CLI 유무와 무관하게 build/test gate를 실행하도록 했습니다.
- `settings.gradle`의 repository 중앙 관리 정책과 충돌하는 root `build.gradle` repository 선언을 제거했습니다.
- Kafka Docker image를 로컬에서 pull 가능한 `apache/kafka:3.7.0`으로 변경했습니다.
- Kafka listener를 host app용 `localhost:9092`와 Docker network 내부용 `kafka:29092`로 분리했습니다.
- Kafka healthcheck와 topic script에서 `/opt/kafka/bin/kafka-topics.sh`를 명시적으로 사용하도록 수정했습니다.
- `app-consumer`의 Actuator HTTP health 검증을 위해 `spring-boot-starter-web`을 추가했습니다.

### 검토한 기준

- `app-common`이 `app-api` 또는 `app-consumer`에 역의존하지 않는가
- `app-api`와 `app-consumer` port가 충돌하지 않는가
- Kafka advertised listener가 host 실행 방식과 Docker 내부 실행 방식을 모두 설명하는가
- Prometheus scrape target이 실제 로컬 실행 방식과 일치하는가
- topic 생성 script가 Kafka container 준비 이후 실행 가능한가
- README의 로컬 실행 명령이 실제 검증 명령과 맞는가

### 수정 또는 거절한 이유

- Gradle repository 선언은 settings-level 정책으로 통일하는 편이 multi-module 프로젝트에서 중복과 충돌을 줄입니다.
- Kafka UI는 Docker 내부 서비스이므로 `localhost:9092` 대신 internal listener를 사용해야 합니다.
- `app-consumer`는 worker 애플리케이션이지만, 이번 Phase 완료 기준에 HTTP health endpoint 검증이 포함되어 있어 embedded web server가 필요합니다.
- 실제 transaction API, Kafka producer, Kafka listener, rule engine, DLQ API는 Phase 1 범위가 아니므로 구현하지 않았습니다.

### 최종 반영 내용

- `./gradlew clean build` 통과
- module test task 통과
- Docker Compose config와 service 기동 확인
- Kafka topic 5개 생성 확인
- `app-api` `/actuator/health` 확인
- `app-consumer` `/actuator/health` 확인
- Prometheus `app-api`, `app-consumer` target `up` 확인

## API/Development Planning Review

### 제안 또는 변경한 내용

- `docs/05-api-design.md`를 API 계약 중심으로 확장했습니다.
- 거래 이벤트 접수 API 응답에 `receivedAt`을 포함하도록 문서화했습니다.
- 거래 이벤트 접수 상태 조회, FraudResult 목록/상세 조회, FraudRule 조회, DLQ 목록/재처리/폐기, ProcessingLog 조회, 운영 요약 API 계약을 추가했습니다.
- Admin API는 초기에는 local/development-only이며, raw DLQ payload와 민감 식별자를 기본 응답에 노출하지 않는 원칙을 명시했습니다.
- `docs/13-development-roadmap.md`를 API Contract, Transaction Intake, Consumer Log, Basic FraudResult, Rule Engine, Redis, DLT, Observability, Load/Failure Test 순서로 세분화했습니다.
- `transaction_event_receipts`를 Phase 3부터 저장하되 Outbox는 도입하지 않는 결정을 `docs/04-data-model.md`에 보강했습니다.

### 검토한 기준

- API 계약이 프로젝트 핵심 목표인 detection latency, Consumer Lag, degraded mode, DLQ 재처리를 설명할 수 있는가
- API Server와 Consumer Worker 책임 경계가 유지되는가
- FraudResult 상세 조회가 matched/skipped rule과 latency를 설명할 수 있는가
- DLQ API가 raw payload를 직접 노출하지 않는가
- 로드맵이 구현 순서와 테스트 기준을 함께 제시하는가
- 완료되지 않은 기능을 구현 완료로 표현하지 않는가

### 수정 또는 거절한 이유

- Rule 설정 변경 API는 초기 구현 범위에서 제외하고 Phase 13+ 후보로 두었습니다.
- 운영 요약 API는 Prometheus/Grafana의 대체가 아니라 local 검증 보조 수단으로 제한했습니다.
- Outbox는 초기 목표보다 범위를 키우므로 도입하지 않았습니다. 대신 receipt 저장과 Kafka publish 실패 한계를 명시했습니다.
- Redis 기반 VelocityRule은 Rule Engine 초기 Phase에서 분리해, Redis 없는 AmountRule과 RiskScore를 먼저 검증하도록 순서를 조정했습니다.

### 최종 반영 내용

- API 계약과 OpenAPI 기준 보강
- 개발 Phase 세분화
- Data Model의 receipt 저장/Outbox 한계 보강
- Load Test Plan의 target API와 측정 항목 보강
- SLO 문서의 API별 확인 기준 보강

## Phase 2 Review

### 제안 또는 변경한 내용

- `app-common`에 `TransactionEventMessage`, `FraudRiskEventMessage`, `FraudAlertEventMessage`와 enum 기반 계약을 정리했습니다.
- `app-api`에 transaction/admin request-response DTO와 공통 `ErrorResponse`를 추가했습니다.
- `POST /api/v1/transactions/events` validation과 Phase 2 contract skeleton controller를 추가했습니다.
- springdoc OpenAPI 설정을 추가해 `/swagger-ui/index.html`과 `/v3/api-docs`에서 API 계약을 확인할 수 있게 했습니다.
- Java/Spring 반복 검증을 위한 루트 `Makefile`을 추가했습니다.

### 검토한 기준

- `app-common`이 `app-api` 또는 `app-consumer` 구현에 의존하지 않는가
- `app-common`에 Controller, Repository, Kafka listener, Redis logic이 들어가지 않았는가
- `TransactionEventRequest` validation 기준이 `docs/05-api-design.md`와 일치하는가
- Controller skeleton이 실제 Kafka publish나 DB 저장을 수행하지 않는가
- OpenAPI 설명에 Phase 2 contract-only 성격이 드러나는가
- ErrorResponse가 raw payload나 민감 식별자를 포함하지 않는가
- Makefile target이 실제 repository script와 일치하는가

### 수정 또는 거절한 이유

- Phase 2에서 실제 Kafka publish, PostgreSQL receipt persistence, FraudResult query, DLQ reprocessing은 구현하지 않았습니다.
- `app-common` test에서 AssertJ dependency를 추가하지 않고 JUnit 기본 assertion으로 변경했습니다. 공유 모듈의 test dependency를 가볍게 유지하기 위한 결정입니다.
- Fraud rule 설정 변경 API는 계속 Phase 13+ 후보로 유지했습니다.

### 최종 반영 내용

- app-common event schema와 enum 정리
- app-api DTO, validation, ErrorResponse, ControllerAdvice 추가
- Phase 2 contract-only controller skeleton 추가
- OpenAPI 설정과 smoke test 추가
- validation MVC test 추가
- Makefile 추가
- docs/05, docs/13, docs/17, docs/11 업데이트

### PR 피드백 반영

- FraudResult 목록 API의 `riskLevel`, `degraded`, `ruleCode`, `from`, `to` query parameter를 controller skeleton에 추가해 OpenAPI 계약과 문서를 맞췄습니다.
- DLQ 목록 API의 `status`, `from`, `to` query parameter를 controller skeleton에 추가했습니다.
- FraudResult 상세 stub response를 문서 예시와 같은 설명 가능한 HIGH risk fixture로 변경했습니다.
- raw `List` 사용 여부를 확인했고, 현재 DTO와 event schema는 `List<FraudRuleCode>` 또는 `List<FraudRuleResultResponse>`로 명시되어 있음을 확인했습니다.
- `eventTime` future validation은 Phase 3에서 `receivedAt` 생성 정책과 함께 구현하기로 문서화했습니다.
- Phase 2에서는 validation error mapping만 구현하고, 실제 service/domain exception mapping은 Phase 3 이후 TODO로 남겼습니다.

## Phase 3 Review

### 제안 또는 변경한 내용

- `POST /api/v1/transactions/events`를 contract skeleton에서 실제 intake service 호출로 전환했습니다.
- `transaction_event_receipts` Flyway migration, JPA entity, repository를 추가했습니다.
- `TransactionEventIntakeService`가 validation, receipt 저장, Kafka publish, status 변경을 orchestration하도록 했습니다.
- `TransactionEventProducer`와 Kafka adapter를 분리해 Controller가 KafkaTemplate에 직접 의존하지 않게 했습니다.
- `TransactionEventMessageMapper`로 request/receipt/message 변환 책임을 분리했습니다.
- duplicate `eventId`는 `409 CONFLICT`로 처리하고 idempotent replay는 구현하지 않았습니다.

### 검토한 기준

- Controller가 Repository 또는 KafkaTemplate을 직접 호출하지 않는가
- Kafka key가 `userId`인지 테스트로 확인했는가
- `TransactionEventMessage`에 `schemaVersion`, `eventId`, `userId`, `receivedAt`, `traceId`가 포함되는가
- `OffsetDateTime` fields가 Kafka JSON payload에서 ISO 문자열로 직렬화되는가
- Kafka publish 실패를 무시하고 202를 반환하지 않는가
- Kafka publish 실패 시 receipt 상태가 `PUBLISH_FAILED`로 남는가
- `eventId` unique constraint와 duplicate handling이 문서화되어 있는가
- app-common이 JPA/Kafka/Spring Web에 의존하지 않는가

### 수정 또는 거절한 이유

- Phase 3에서는 Outbox Pattern을 구현하지 않았습니다. Producer intake 구현이 목표이며, 자동 재발행 publisher는 후속 hardening 후보입니다.
- DB transaction과 Kafka publish를 원자적으로 묶지 않으므로 DB 저장 성공 후 Kafka publish 실패, Kafka publish 성공 후 DB commit 실패가 모두 남은 한계입니다.
- 중복 `eventId` 요청을 replay처럼 반환하지 않았습니다. 동일 body 비교와 idempotency response는 별도 정책이 필요하기 때문입니다.
- `PUBLISH_FAILED` 상태의 동일 `eventId` 재요청도 Phase 3에서는 `409 CONFLICT`로 막고, failed receipt 재발행 flow는 후속 hardening 대상으로 둡니다.
- application clock은 UTC 기준으로 변경했습니다. 다만 Entity lifecycle timestamp는 현재 JPA callback에서 생성하므로, JPA auditing 또는 service clock 주입 방식으로 통일하는 개선 여지가 있습니다.
- `publish_error_message`는 raw payload를 저장하지 않는 요약 메시지로 제한하고, entity에서 500자 truncate를 적용했습니다.
- Kafka Consumer, manual ack, retry/DLT, FraudResult 저장은 구현하지 않았습니다.

### 최종 반영 내용

- transaction event receipt persistence 구현
- Kafka producer adapter 구현
- `userId` Kafka key 검증 test 추가
- duplicate `eventId` 409 test 추가
- Kafka publish failure 503 및 `PUBLISH_FAILED` status test 추가
- future `eventTime` validation test 추가
- `PUBLISH_FAILED` receipt 재요청 409 policy test 추가
- `TransactionEventMessage` time field JSON serialization test 추가
- local Kafka consume으로 `transaction-events` key=`userId`, ISO time payload 확인
- docs/04, docs/05, docs/07, docs/11, docs/13 업데이트

## Phase 4-A Review - Minimum CI Gate

### 잘한 점

Consumer 구현 전에 최소 CI Gate를 먼저 추가하여 이후 Phase의 회귀 검증 기반을 마련했습니다. 특히 Kafka Consumer 작업은 offset commit과 DB 저장 순서가 중요하므로, `make ci-check` 자동화가 먼저 들어간 점은 운영 안정성 관점에서 의미가 있습니다.

### 의도적으로 제외한 것

Docker Compose 기반 Kafka/PostgreSQL/Redis 통합 테스트는 이번 CI에 포함하지 않았습니다. 초기부터 무거운 통합 테스트를 CI에 넣으면 workflow가 불안정해지고 개발 속도가 떨어질 수 있기 때문입니다.

`./gradlew build`는 test를 다시 실행할 수 있으므로 CI에서는 `./gradlew test`와 `./gradlew assemble`을 묶은 `make ci-check`를 사용했습니다.

### 다음 보완

Phase 5 이후 Rule Engine과 Fraud Result 저장이 안정화되면, Kafka end-to-end 검증을 포함한 `ci-integration.yml`을 별도로 추가합니다.

## Phase 4 Review

### 제안 또는 변경한 내용

- app-consumer에 `transaction-events` Kafka listener를 추가했습니다.
- Kafka consumer 설정을 `enable-auto-commit=false`, `AckMode.MANUAL_IMMEDIATE`로 명시했습니다.
- `event_processing_logs` migration은 `app-api` schema owner 기준으로 두고, app-consumer runtime Flyway는 비활성화했습니다.
- app-consumer에는 processing log JPA entity, repository, service를 추가했습니다.
- `(topic, partition_no, offset_no)` unique constraint로 같은 Kafka record의 duplicate processing log 생성을 방어했습니다.
- `GET /api/v1/admin/events/{eventId}/processing-log`를 실제 DB 조회 API로 전환했습니다.

### 검토한 기준

- ack가 processing log 저장 성공 이후 호출되는가
- duplicate offset으로 이미 processing log가 있는 경우에도 ack 가능한가
- service 처리 실패 시 ack하지 않는가
- listener가 긴 비즈니스 로직을 직접 수행하지 않고 service로 위임하는가
- eventId unique를 `event_processing_logs`에 걸지 않았는가
- app-common에 JPA/Kafka listener 구현이 들어가지 않았는가
- 응답과 로그에 raw payload, accountId, deviceId 원문이 포함되지 않는가

### 수정 또는 거절한 이유

- Phase 4에서는 FraudResult 저장, Rule Engine, Redis, Retry/DLT를 구현하지 않았습니다.
- 같은 offset이 이미 processing log에 있으면 이전 처리 성공으로 보고 duplicate log를 만들지 않은 뒤 ack 가능하게 처리했습니다.
- eventId 기준 business idempotency는 FraudResult 저장이 들어오는 Phase 5 이후로 남겼습니다.
- `FAILED` processing status는 Phase 9 Retry/DLT에서 사용할 예약 상태로 남겼습니다.

### 최종 반영 내용

- Kafka Consumer manual ack 구현
- processing log 저장 구현
- processing log 조회 API 구현
- ack success/failure/duplicate unit test 추가
- duplicate offset 방어 test 추가
- same eventId different offset test 추가
- `processedAt desc` 정렬 test 추가
- docs/04, docs/05, docs/07, docs/08, docs/11, docs/13, docs/18 업데이트

## Phase 5 Review

### 잘한 점

- Phase 4의 processing log와 별도로 `fraud_detection_results`를 추가해 "처리 여부"와 "위험 판단 결과"를 분리했습니다.
- Consumer ack 시점을 fraud result 저장 이후로 이동해 탐지 결과 누락 가능성을 줄였습니다.
- `fraud_detection_results.event_id` unique constraint를 최종 중복 방어선으로 두어 Kafka 재소비를 idempotent하게 처리했습니다.
- Rule Engine을 Listener에서 분리해 rule 평가를 unit test로 검증할 수 있게 했습니다.

### 의도적으로 제외한 것

- Redis Sliding Window와 VelocityRule은 Phase 5에 포함하지 않았습니다.
- Retry/DLT 기반 실패 복구와 failed result 보정 flow는 후속 Phase로 남겼습니다.
- Rule threshold 동적 관리와 rule versioning은 구현하지 않았습니다.

### 남은 한계

- Phase 5에서는 processing log와 fraud result를 하나의 DB transaction으로 묶지 않았습니다. processing log 저장 후 fraud result 저장 전에 장애가 발생하면 일시적으로 processing log만 존재할 수 있고, ack 미호출에 따른 Kafka 재소비로 fraud result 저장을 다시 시도합니다.
- 같은 eventId는 하나의 fraud result만 가집니다. rule version 변경 후 재평가 이력을 남기려면 result versioning이 필요합니다.
- `matched_rules`는 Phase 5에서 text로 저장합니다. rule별 상세 결과는 후속 Phase에서 JSONB 또는 별도 detail table로 확장할 수 있습니다.
- `existsByEventId()`는 fast path이며, 최종 중복 방어는 PostgreSQL `event_id` unique constraint가 담당합니다.
- Phase 5의 fraud result 조회 API는 운영자용 admin API입니다. 실제 운영 확장 시 ADMIN 권한 기반 접근 제어와 감사 로그를 추가해야 합니다.
- Hidden/bidirectional Unicode check: `rg --pcre2` 검사 결과 no result.
- Kafka/PostgreSQL/Redis E2E 검증은 아직 GitHub Actions integration workflow에 포함하지 않았습니다.

### 다음 보완

- Redis 기반 Sliding Window rule과 degraded mode를 추가합니다.
- rule result detail과 skipped rule 기록을 확장합니다.
- DLT/reprocessing 단계에서 duplicate result와 reprocess history 정합성을 검증합니다.

## Phase 6 Review

### 잘한 점

- Redis 접근을 Rule class 내부에 넣지 않고 Listener orchestration에서 `RecentTransactionWindowStore`를 호출한 뒤 Rule Engine에 window result를 전달했습니다.
- Redis 장애를 Consumer 실패로 전파하지 않고 degraded result로 변환해 stateless rule과 fraud result 저장을 계속 수행하도록 했습니다.
- Redis 기반 rule이 생략된 경우 `skipped_rules`, `degraded`, reason으로 운영 조회 가능하게 남겼습니다.
- 같은 `eventId`의 duplicate fraud result는 Redis window 갱신 전에 fast path로 ack해 conflict replay가 Redis metadata를 덮어쓰지 않도록 했습니다.
- Redis 부분 실패로 Hash metadata가 없는 ZSET member가 남아도 count/sum 계산에서 제외되도록 했습니다.
- 최종 중복 방어 기준은 Redis가 아니라 PostgreSQL `fraud_detection_results.event_id` unique constraint로 유지했습니다.

### 의도적으로 제외한 것

- Redis Testcontainers integration test는 이번 Phase에서 제외했습니다. Phase 6는 store logic, degraded policy, Rule Engine 연동을 먼저 검증하고 실제 Redis integration은 hardening 단계에서 추가합니다.
- Retry/DLT, DLQ 재처리, Consumer Lag metric, k6 부하 테스트는 이번 Phase 범위에 넣지 않았습니다.
- Redis command latency metric과 degraded count metric은 Observability/Hardening 단계로 남겼습니다.
- rule threshold를 DB나 feature flag로 분리하지 않았습니다. 현재는 구현 단순성과 테스트 명확성을 우선해 configuration properties와 코드 상수 조합으로 유지합니다.

### 남은 한계

- `matched_rules`와 `skipped_rules`는 comma-separated text입니다. unknown rule code 대응과 rule별 상세 결과를 위해 JSONB 또는 별도 detail table을 검토해야 합니다.
- Redis Hash metadata key 수가 이벤트 수만큼 증가합니다. TTL을 짧게 설정했지만 부하 테스트에서 memory 사용량을 확인해야 합니다.
- Redis 장애 중 stateful rule은 탐지되지 않습니다. 이는 의도된 degraded behavior이며, 운영 metric과 alert로 관측해야 합니다.
- Redis 상태가 손상되거나 TTL로 사라져도 PostgreSQL fraud result 정합성에는 영향을 주지 않지만, 최근 거래 패턴 탐지 민감도는 낮아질 수 있습니다.
- Redis multi-command 갱신은 아직 완전 원자적이지 않습니다. Phase 6에서는 Hash-first 저장과 유효 metadata 기준 계산으로 완화했고, Lua/transaction은 후속 hardening 후보입니다.

### 다음 보완

- 실제 Redis 기반 integration test 추가
- Redis down failure scenario 문서와 수동 검증 결과 추가
- Redis command latency, degraded count, rule skipped count metric 추가
- k6 redis-down/hot-partition 시나리오에서 Consumer Lag과 fraud detection latency 측정

## Phase 7 Review

### 잘한 점

- Redis Sliding Window를 mock이 아니라 실제 Redis 자료구조 기준으로 검증하는 integration test를 추가했습니다.
- 기본 `make ci-check`와 Redis integration test를 분리해 빠른 회귀 검증과 Docker 의존 검증의 역할을 나눴습니다.
- Redis integration test는 테스트 전용 database index `15`를 사용하고 해당 DB만 초기화해 로컬 Redis 전체 DB 삭제 위험을 줄였습니다.
- `make redis-integration-test`에 Redis readiness 확인을 추가해 Redis 미준비 상태에서 integration test가 skip되는 착시를 줄였습니다.
- Redis degraded, skipped rule, degraded detection, Redis window latency metric foundation을 추가했습니다.
- Metric tag에 eventId/userId/traceId를 넣지 않아 high-cardinality와 식별자 노출 위험을 피했습니다.
- Redis degraded 처리 시 structured log에 window result, matched/skipped rule, risk score 정보를 함께 남기도록 보강했습니다.

### 의도적으로 제외한 것

- Kafka + Redis + PostgreSQL 전체 E2E test는 이번 Phase에 포함하지 않았습니다.
- Grafana dashboard와 alert rule 구성은 Observability Phase로 남겼습니다.
- k6 Redis down 부하 테스트는 Load/Failure Test Phase로 분리했습니다.
- Testcontainers Redis는 로컬 Docker provider 호환 문제로 최종 선택하지 않고 Docker Compose Redis 기반 integration test로 대체했습니다.

### 남은 한계

- Redis integration test는 Docker Compose Redis가 필요한 분리 검증입니다.
- Metric foundation은 추가됐지만 운영 threshold와 alert 기준은 아직 없습니다.
- `fraud.redis.window.record.latency`는 store 호출 전체 시간이며 command별 latency를 분리하지 않습니다.

### 다음 보완

- Prometheus/Grafana dashboard에 Phase 7 metric을 연결합니다.
- Redis down failure scenario에서 degraded metric 증가와 Consumer Lag 변화를 함께 기록합니다.
- k6 부하 테스트로 Redis latency와 skipped rule count가 어떻게 변하는지 측정합니다.

## Phase 8 Review

### 잘한 점

- Redis down을 전체 Consumer 실패로 보지 않고 degraded mode evidence를 자동 drill로 확인하도록 만들었습니다.
- Redis drill은 Redis stop, 이벤트 발행, fraud result 조회, skipped rule 확인, degraded/skipped/latency metric 증가 확인, Redis restart, recovery event 확인까지 포함합니다.
- Consumer restart drill은 로컬 Gradle process 구조를 고려해 precondition과 수동 재시작 절차를 명확히 하고, DB row count 1건 검증을 포함했습니다.
- Kafka unavailable은 자동화로 로컬 환경을 깨뜨릴 수 있어 markdown runbook으로 분리했습니다.
- Metric만 보지 않고 fraud result API와 processing log API를 함께 확인하도록 했습니다.
- Failure drill script는 synthetic identifier만 사용하고 credential을 하드코딩하지 않았습니다.

### 의도적으로 제외한 것

- Retry/DLT 자동 복구 구현은 이번 Phase에 포함하지 않았습니다.
- Kafka stop/start 자동 script를 기본 target에 포함하지 않았습니다.
- k6 기반 장애 부하 테스트와 Grafana dashboard 구성은 제외했습니다.
- app-consumer Docker Compose service 추가는 현재 로컬 실행 구조를 바꾸므로 제외했습니다.

### 남은 한계

- Consumer restart drill은 완전 자동화가 아니라 app-consumer 수동 재시작을 전제합니다.
- Kafka unavailable drill은 runbook이며 자동 PASS/FAIL 결과를 만들지 않습니다.
- Consumer Lag과 detection latency dashboard evidence는 아직 연결되지 않았습니다.
- PostgreSQL 장애 drill은 문서 시나리오로 남아 있고 자동화하지 않았습니다.

### 다음 보완

- Retry/DLT Phase에서 transient failure와 unrecoverable failure를 분리합니다.
- Observability Phase에서 Consumer Lag, detection latency, DLQ count dashboard를 연결합니다.
- Load/Failure Test Phase에서 k6 Redis down, Consumer restart, hot partition 시나리오를 측정합니다.

## Phase 9 Review

### 잘한 점

- DLT 저장 기준을 `source_topic`, `source_partition`, `source_offset` unique constraint로 잡아 같은 Kafka record의 중복 DLT row를 방어했습니다.
- DB 장애는 DLT로 억지 격리하지 않고 no-ack 재소비 정책을 유지했습니다.
- 재처리 API가 원본 `eventId`를 유지해 Consumer duplicate fast path와 `fraud_detection_results.event_id` unique constraint를 그대로 활용합니다.
- DLT 상태 전이를 entity/service에서 제한하고 종료 상태 재처리를 `409 Conflict`로 막았습니다.
- `transaction-events-dlt` topic key를 `eventId`로 두어 운영 재처리 단위와 topic key를 맞췄습니다.
- 같은 DLT row의 재처리/폐기는 `PESSIMISTIC_WRITE` row lock으로 직렬화했습니다.
- 재처리 Kafka publish 실패는 `REPROCESS_FAILED`로 저장한 뒤 HTTP 503으로 호출자에게 실패를 명확히 알립니다.
- DLT 저장 경로에 sanitizer 메서드를 분리하고, errorMessage 길이 제한/null 처리/stacktrace 미저장 기준을 코드와 문서에 반영했습니다.

### 의도적으로 제외한 것

- Kafka publish와 DB update를 atomic하게 묶는 outbox/reconciliation은 이번 Phase에서 제외했습니다.
- 대량 batch reprocess, rate limit, 관리자 인증/인가는 후속 Phase로 남겼습니다.
- Grafana dashboard, k6 부하 테스트, alert rule은 Observability/Load Phase 범위로 분리했습니다.

### 남은 한계

- DLT payload sanitizer 경로는 분리했지만 필드별 masking/redaction rule은 운영 확장 시 보강해야 합니다. Phase 9 local 데이터는 synthetic identifier를 전제로 합니다.
- `reprocess_attempts`는 증가하지만 별도 `reprocessing_history` 테이블은 아직 만들지 않았습니다.
- max attempts, cooldown, 재처리 rate limit은 별도 운영 정책이 필요하므로 이번 Phase에서는 적용하지 않았습니다.
- Rule Engine 예외 중심으로 DLT를 구현했고, invalid payload/schema version DLT 분류는 Spring Kafka deserialization/error handler 확장 시 보강합니다.

### 다음 단계 보완

- outbox 또는 reconciliation job으로 Kafka publish와 DB 상태 변경 사이의 중간 상태를 보정합니다.
- DLT batch reprocess, rate limit, max attempts, audit log를 추가합니다.
- DLT count, reprocess failure count를 Prometheus/Grafana dashboard에 연결합니다.

## Phase 11 Review

### 잘한 점

- README를 과도하게 늘리지 않고 readiness checklist, evidence index, troubleshooting index로 상세 문서를 분리했습니다.
- Phase 1~10 evidence와 후속 운영 고도화 후보를 분리해 완료된 것과 남은 일을 구분했습니다.
- 보안/개인정보 문서에서 Admin API local-only, DLT payload 한계, Redis key/value privacy, metric tag privacy를 최신 기준으로 정리했습니다.

### 검증 기록

```bash
rg --pcre2 "[\x{202A}-\x{202E}\x{2066}-\x{2069}]" \
  README.md .github app-api app-common app-consumer docs blog scripts
```

결과: no result. Hidden/bidirectional Unicode control character는 발견되지 않았습니다.

### 남은 한계

- GitHub UI의 hidden/bidirectional Unicode 경고가 한국어 Markdown 파일에서 계속 표시될 수 있으므로, PR 본문에도 검사 결과를 함께 남깁니다.
- DLT metric/alert, Grafana dashboard capture, k6 부하 수치는 후속 Phase에서 별도 evidence로 기록합니다.

## Phase 14 Review

### 잘한 점

- `/api/v1/admin/**`에 `X-Admin-Token` 기반 local/dev 최소 보호를 추가해 운영자 API가 완전히 공개된 상태로 보이지 않게 했습니다.
- 일반 transaction ingest API는 admin token 없이 동작하도록 filter 범위를 admin path로 제한했습니다.
- DLT reprocess/discard 성공과 실패를 `admin_audit_logs`에 저장해 운영자 조치 추적 근거를 만들었습니다.
- audit metadata에서 admin token, DLT payload 전체, accountId, deviceId를 제외했습니다.
- max reprocess attempts를 설정화하고, 초과 시 Kafka publish가 호출되지 않도록 테스트했습니다.
- 상태 전이 실패와 publish 실패 모두 FAILED audit log를 남기도록 보강했습니다.
- README는 최소 요약만 수정하고 상세 판단은 docs/blog로 분리했습니다.

### 의도적으로 제외한 것

- JWT/OAuth2/RBAC는 production-grade 인증/인가 범위이므로 후속 Phase로 남겼습니다.
- audit log 조회/필터링 API는 저장 구현보다 범위가 커지므로 이번 Phase에서는 제외했습니다.
- Gateway/Nginx/API Gateway rate limit, IP allowlist, 관리자 승인 workflow는 운영 자동화 follow-up으로 분리했습니다.
- 인증 실패를 DB audit log에 저장하지 않았습니다. 반복 공격 상황에서 DB write 폭증을 피하기 위해 structured log 기준으로 남겼습니다.

### 남은 한계

- `X-Admin-Token`은 local/dev guardrail이며 운영 인증/인가로 일반화하면 안 됩니다.
- audit log는 저장되지만 조회 API와 보존 기간 정책은 없습니다.
- Kafka publish와 DB 상태 변경의 완전한 atomic transaction은 여전히 outbox/reconciliation 후보입니다.
- app-api scale-out 환경에서 rate limit과 token rotation은 별도 계층에서 다뤄야 합니다.

### 다음 단계 보완

- JWT/OAuth2/RBAC 기반 Admin role과 권한 분리
- audit log 조회/필터링 API와 접근 권한
- Gateway/Nginx/API Gateway rate limit과 IP allowlist
- DLT batch reprocess, cooldown, 관리자 승인 workflow
- DLT pending/reprocess/discard metric과 alert rule

## V2 Planning Review

### 잘한 점

- V2 범위를 AI/ML 모델이 아니라 PaySim synthetic dataset 기반 Rule 탐지와 운영 action workflow로 제한했습니다.
- PaySim raw CSV를 repository에 커밋하지 않고 provenance, 재현 절차, sample 허용 범위를 문서화했습니다.
- PaySim `isFraud` label은 Rule 입력이 아니라 평가용 정답으로만 사용한다고 명시했습니다.
- runtime event와 evaluation label sidecar를 분리해 Consumer가 정답 label을 볼 수 있는 구조를 피하도록 설계했습니다.
- V2 시작 순서를 Rule Engine이 아니라 data provenance, raw protection, preprocessing, validation, sampling, hashing, replay pipeline으로 재정렬했습니다.
- ActionDecision은 CRITICAL 이벤트의 복수 action을 지원하기 위해 `unique(event_id, action_type)` 기준으로 정리했습니다.
- V2 runtime schema를 `TransactionBalanceFeatures` typed optional field로 확정하고 generic feature map을 제외했습니다.
- preprocessing fail-fast/row-level reject, streaming CSV 처리 기준을 문서화하고 max reject ratio는 Phase 3 후보로 분리했습니다.
- 자동 ActionDecision 생성은 admin audit log가 아니라 `fraud_action_decisions` table과 metrics/evidence로 추적하도록 정리했습니다.
- CRITICAL risk도 실제 계좌 정지로 자동 연결하지 않고 `BLOCK_TRANSACTION_CANDIDATE`, `ACCOUNT_RISK_FLAG`, Fraud Case, Admin Review로 분리했습니다.
- V2 구현 전 data mapping, Rule V2, Action Decision, Fraud Case, Evidence Plan을 독립 문서로 나눠 구현 순서를 명확히 했습니다.

### 의도적으로 제외한 것

- PaySim download/prepare/replay script 구현은 이번 문서화 작업에서 제외했습니다.
- DB migration, API, Rule V2 code, k6 scenario 변경은 아직 구현하지 않았습니다.
- JWT/OAuth2/RBAC, 실제 금융기관 API, production 제재 workflow는 V2 범위에서 제외했습니다.
- visualization image 생성은 실제 V2 replay/evaluation 결과가 나온 뒤 수행합니다.

### 남은 한계

- PaySim dataset column과 row count는 실제 다운로드 후 script 검증으로 확인해야 합니다.
- Kaggle dataset license와 사용 조건은 구현 전 다시 확인해야 합니다.
- V2 evidence 수치는 아직 `TBD`이며, 구현 후 replay 결과로 채워야 합니다.
- Identifier hashing salt는 local 예시만 문서화되어 있으며, 운영 환경에서는 secret 관리가 필요합니다.
- Offline evaluation과 online replay evaluation이 같은 rule version을 쓰는지 구현 단계에서 고정해야 합니다.
- `TransactionBalanceFeatures`를 app-common에 둘 때 PaySim label/source flag가 섞이지 않도록 schema review가 필요합니다.

## V2 Phase 1 Review

### 잘한 점

- PaySim raw CSV와 processed full output이 repository에 들어가지 않도록 `data/` 디렉터리와 `.gitignore` allowlist를 추가했습니다.
- `scripts/data/check-data-policy.sh`와 `make data-policy-check`를 추가해 tracked/staged data 파일을 검증할 수 있게 했습니다.
- `make final-check`에 data policy check를 연결해 최종 검증에서 data guardrail이 빠지지 않도록 했습니다.
- README는 링크와 명령만 최소 추가하고, 상세 기준은 `docs/24-kaggle-paysim-data-provenance.md`와 `scripts/data/README.md`로 분리했습니다.
- Phase 1 범위를 data guardrail로 제한하고 preprocessing/replay/rule 구현은 추가하지 않았습니다.

### 사람 검토 체크리스트

- [ ] `.gitignore`가 `data/raw/*`와 `data/processed/*`를 실제로 막는가
- [ ] `data/raw/.gitkeep`, `data/processed/.gitkeep`, `data/samples/.gitkeep`만 기본 커밋되는가
- [ ] `data/samples` 허용 범위가 과도하지 않은가
- [ ] sample 정책에 raw identifier 미포함과 row/size 제한이 문서화되어 있는가
- [ ] preprocessing, replay, Rule V2 구현이 Phase 1 범위를 넘어서 들어가지 않았는가
- [ ] README가 상세 문서처럼 길어지지 않았는가

### 의도적으로 제외한 것

- PaySim preprocessing script 구현
- sample generation script 구현
- replay script 구현
- Java Rule Engine V2, API DTO, Kafka schema, DB migration 변경
- Fraud Action Decision과 Fraud Case 구현

### 검증 기록

```bash
bash -n scripts/data/check-data-policy.sh
make scripts-check
make data-policy-check
make ci-check
./gradlew test
docker compose -f infra/docker-compose.yml config --quiet
make final-check
git check-ignore -v data/raw/PS_20174392719_1491204439457_log.csv
git check-ignore -v data/processed/paysim-events.jsonl
```

Negative guardrail 검증 결과:

- `git add -f data/raw/PS_20174392719_1491204439457_log.csv && make data-policy-check`
  - Result: FAIL
  - Message: `FAIL: raw data file must not be committed`
- `git add -f data/processed/paysim-events.jsonl && make data-policy-check`
  - Result: FAIL
  - Message: `FAIL: processed data file must not be committed`
- staged sample이 1MB를 초과한 뒤 working tree만 작게 변경된 상태에서 `make data-policy-check`
  - Result: FAIL
  - 기준: staged blob size
  - Message: `FAIL: sample file is larger than 1MB`

### 남은 한계

- shell 기반 data policy check는 sample 내부의 raw identifier를 완전히 검출하지 못합니다.
- 1MB sample size 기준은 초기 guardrail이며, Phase 3 sample 생성 정책에서 다시 조정할 수 있습니다.
- CSV sample 허용은 Phase 1에서 문서와 크기 기준으로만 제한합니다. Phase 3 sample generation 단계에서 raw `nameOrig`/`nameDest` column 제거와 hashed identifier 사용을 자동 검증해야 합니다.

## V2 Phase 2 Review

### 잘한 점

- KaggleHub download helper를 optional local tool로 분리하고 CI에서 실행하지 않도록 했습니다.
- PaySim preprocessing을 Python stdlib `csv.DictReader` streaming 방식으로 구현했습니다.
- runtime event와 label sidecar를 분리해 label leakage를 방지했습니다.
- amount와 balance 값을 `Decimal`로 읽고 JSON에는 문자열로 기록하도록 했습니다.
- rejected row에 raw row 전체나 `nameOrig`, `nameDest`를 남기지 않도록 했습니다.
- fixture 기반 unittest를 추가해 실제 Kaggle CSV 없이 normalization contract를 검증했습니다.

### 사람 검토 체크리스트

- [ ] Kaggle token 또는 access token이 커밋되지 않았는가
- [ ] raw CSV가 커밋되지 않았는가
- [ ] full processed output이 커밋되지 않았는가
- [ ] runtime event에 `isFraud`, `isFlaggedFraud`가 포함되지 않았는가
- [ ] runtime event에 `nameOrig`, `nameDest`가 포함되지 않았는가
- [ ] label sidecar가 `eventId` 기준으로만 연결되는가
- [ ] rejected row가 raw row 전체를 dump하지 않는가
- [ ] CSV를 streaming 방식으로 처리하는가
- [ ] Decimal/string 기준으로 금액을 처리하는가
- [ ] CI에서 Kaggle download/full preprocessing을 실행하지 않는가
- [ ] `make data-policy-check`가 계속 통과하는가

### 검증 기록

```bash
python3 -m unittest discover -s scripts/data -p 'test_*.py'
make test-data-scripts
make data-policy-check
make ci-check
make final-check
PYTHONPYCACHEPREFIX=/tmp/pycache-paysim python3 -m py_compile \
  scripts/data/download_paysim_dataset.py \
  scripts/data/prepare_paysim_dataset.py \
  scripts/data/test_prepare_paysim_dataset.py
```

### 의도적으로 제외한 것

- KaggleHub download helper의 CI 실행
- full PaySim CSV preprocessing의 CI 실행
- sample generation
- API replay pipeline
- Java Rule Engine V2, API DTO, Kafka schema, DB migration 변경

### 남은 한계

- 실제 Kaggle dataset 접근과 token 설정은 local 환경에서만 확인합니다.
- identifier hashing은 최소 구현이며 Phase 4에서 salt policy와 sample 검증을 강화합니다.
- rejected row taxonomy와 reject ratio policy는 Phase 3에서 고도화합니다.
- data script test는 missing column, invalid label/type, blank identifier, non-finite Decimal, existing output protection, raw identifier leakage까지 검증합니다.
- Phase 2 preprocessing은 output file을 직접 쓰므로 fail-fast/interrupt 시 partial output이 남을 수 있습니다. Phase 5 replay 연결 전 atomic write와 final rename 방식을 검토합니다.
- Phase 5 replay script에는 dataset/sample 충돌 방지를 위한 `--event-id-prefix` 옵션을 둡니다.
- Phase 3 sample generation에서는 `default-local` salt 사용을 금지하고 환경변수 또는 CLI salt 명시를 요구합니다.

## V2 Data Toolchain Review

### 잘한 점

- Java/Spring Boot runtime과 PaySim Python data helper 의존성을 분리했습니다.
- global `pip install kagglehub` 안내를 제거하고 `.venv-data` 기반 bootstrap으로 정리했습니다.
- `download-paysim`, `prepare-paysim`, `prepare-paysim-smoke`, `test-data-scripts`가 같은 venv Python을 사용하도록 Makefile을 통일했습니다.
- GitHub Actions에서 Python 3.11을 명시적으로 설정해 CI가 runner 기본 Python에 의존하지 않도록 했습니다.
- CI에서는 Kaggle download와 full preprocessing을 실행하지 않고 fixture test와 data policy check만 실행하도록 유지했습니다.
- `.venv-data/`를 Git ignore에 추가해 local dependency directory가 커밋되지 않도록 했습니다.
- macOS system Python SSL 호환 경고를 줄이기 위해 `urllib3<2` 제한을 문서화했습니다.

### 사람 검토 체크리스트

- [ ] global `pip install kagglehub` 안내가 제거되었는가
- [ ] `make data-env`가 `.venv-data`를 생성하는가
- [ ] `make download-paysim`이 `.venv-data/bin/python`을 사용하는가
- [ ] `make test-data-scripts`가 venv Python으로 실행되는가
- [ ] GitHub Actions가 Python version을 명시적으로 설정하는가
- [ ] `.venv-data/`가 Git에 커밋되지 않는가
- [ ] `data/raw/*.csv`가 커밋되지 않는가
- [ ] `data/processed/*`가 커밋되지 않는가
- [ ] CI에서 Kaggle download를 실행하지 않는가
- [ ] CI에서 fixture 기반 data script test는 실행되는가
- [ ] Kaggle credential이나 token이 로그/문서/Git에 남지 않는가

### 검증 기록

```bash
bash -n scripts/data/bootstrap-data-env.sh
make data-env
make test-data-scripts
make data-policy-check
make ci-check
make download-paysim
make prepare-paysim-smoke
```

`make download-paysim` copied the expected PaySim CSV to ignored `data/raw/PS_20174392719_1491204439457_log.csv`. `make prepare-paysim-smoke` accepted 1,000 rows and wrote ignored `data/processed/*` outputs. These files were not added to Git.

### 의도적으로 제외한 것

- V2 Phase 3 validation script 구현
- V2 Phase 3 sample generation script 구현
- Java Rule Engine V2, Kafka replay, API/DB/Kafka schema 변경
- CI에서 Kaggle dataset download 또는 full preprocessing 실행

### 남은 한계

- Kaggle 인증은 사용자 로컬 환경에서 별도로 준비해야 합니다.
- `.venv-data`는 재현 가능한 helper 실행 환경일 뿐, Java application runtime dependency가 아닙니다.
- KaggleHub API 변화가 생기면 `scripts/data/requirements.txt`의 version range를 조정해야 합니다.

## V2 Phase 3 Review

### 잘한 점

- processed events/labels/rejected/report 간 count와 eventId join consistency를 자동 검증했습니다.
- runtime event에 label field나 `receivedAt`이 들어오면 실패하도록 했습니다.
- raw identifier field와 PaySim identifier pattern leakage를 validator와 sample generator 양쪽에서 검사했습니다.
- rejected reason allowlist와 reject ratio threshold를 추가했습니다.
- JSONL sample과 label sidecar를 분리하고 sample manifest를 생성했습니다.
- `data/samples/*.csv`와 일반 JSON 허용을 제거하고 `*manifest*.json`만 제한 허용했습니다.
- fixture 기반 unittest를 추가해 실제 Kaggle CSV 없이 Phase 3 contract를 검증했습니다.

### 사람 검토 체크리스트

- [ ] full processed output이 커밋되지 않았는가
- [ ] data/samples에 1MB 초과 파일이 없는가
- [ ] data/samples에 CSV sample이 없는가
- [ ] data/samples에 일반 JSON 파일이 없고 manifest JSON만 허용되는가
- [ ] sample event에 label field가 없는가
- [ ] sample event에 `receivedAt`이 없는가
- [ ] sample event/label eventId set이 일치하는가
- [ ] sample manifest에 raw identifier나 salt 값이 없는가
- [ ] validation script가 reject ratio를 실패로 처리하는가
- [ ] validation script가 report count와 실제 line count를 비교하는가
- [ ] validation script가 eventId set mismatch를 잡는가
- [ ] CI에서 full validation이나 sample generation을 실행하지 않는가
- [ ] fixture 기반 test는 CI에서 실행되는가
- [ ] `make data-policy-check`가 sample policy를 실제로 강제하는가

### 검증 기록

```bash
bash -n scripts/data/*.sh
make data-env
make test-data-scripts
make data-policy-check
make prepare-paysim-smoke
make validate-paysim
make generate-paysim-sample-strict
```

결과:

- `make test-data-scripts`: PASS, 30 tests
- `make validate-paysim`: PASS, events=1000 labels=1000 rejected=0 fraud=9 flagged=0 rejectRatio=0.0000
- `make generate-paysim-sample-strict`: PASS, events=1000 labels=1000 fraud=9
- sample files: each under 1MB
- raw CSV, processed output, `.venv-data` are ignored and not staged

### 의도적으로 제외한 것

- Java Rule Engine V2 구현
- Kafka replay script
- API/DB/Kafka schema 변경
- CI에서 full Kaggle download, full preprocessing, full validation, sample generation 실행

### 남은 한계

- Full output validation은 accepted eventId set을 메모리에 보관합니다.
- Balanced sampling은 deterministic first-N-per-class 방식입니다.
- eventId prefix/replay 충돌과 stronger salt policy는 후속 Phase에서 다룹니다.
