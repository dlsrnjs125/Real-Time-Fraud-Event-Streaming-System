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
- 중복 `eventId` 요청을 replay처럼 반환하지 않았습니다. 동일 body 비교와 idempotency response는 별도 정책이 필요하기 때문입니다.
- Kafka Consumer, manual ack, retry/DLT, FraudResult 저장은 구현하지 않았습니다.

### 최종 반영 내용

- transaction event receipt persistence 구현
- Kafka producer adapter 구현
- `userId` Kafka key 검증 test 추가
- duplicate `eventId` 409 test 추가
- Kafka publish failure 503 및 `PUBLISH_FAILED` status test 추가
- `TransactionEventMessage` time field JSON serialization test 추가
- local Kafka consume으로 `transaction-events` key=`userId`, ISO time payload 확인
- docs/04, docs/05, docs/07, docs/11, docs/13 업데이트
