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

### 의도적으로 제외한 것

- Kafka publish와 DB update를 atomic하게 묶는 outbox/reconciliation은 이번 Phase에서 제외했습니다.
- 대량 batch reprocess, rate limit, 관리자 인증/인가는 후속 Phase로 남겼습니다.
- Grafana dashboard, k6 부하 테스트, alert rule은 Observability/Load Phase 범위로 분리했습니다.

### 남은 한계

- DLT payload masking은 운영 확장 시 보강해야 합니다. Phase 9 local 데이터는 synthetic identifier를 전제로 합니다.
- `reprocess_attempts`는 증가하지만 별도 `reprocessing_history` 테이블은 아직 만들지 않았습니다.
- Rule Engine 예외 중심으로 DLT를 구현했고, invalid payload/schema version DLT 분류는 Spring Kafka deserialization/error handler 확장 시 보강합니다.

### 다음 단계 보완

- outbox 또는 reconciliation job으로 Kafka publish와 DB 상태 변경 사이의 중간 상태를 보정합니다.
- DLT batch reprocess, rate limit, audit log를 추가합니다.
- DLT count, reprocess failure count를 Prometheus/Grafana dashboard에 연결합니다.
