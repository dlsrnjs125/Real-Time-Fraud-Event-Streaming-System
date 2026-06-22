# Troubleshooting Log

개발 중 설계 변경 또는 문제 해결이 발생하면 아래 형식으로 기록합니다.

## 기록 형식

### 문제 제목

#### 초기 설계

#### 발생한 문제

#### 재현 방법

#### 원인 분석

#### 변경한 설계

#### 개선 결과

#### 남은 한계

#### 다시 설계한다면

---

## 후보 1. Partition Key 변경

### 초기 설계

`eventId`를 partition key로 사용합니다.

### 발생 가능한 문제

같은 `userId`의 거래 이벤트가 여러 partition에 분산되어 사용자별 거래 순서가 깨질 수 있습니다.

### 변경 방향

`userId`를 partition key로 사용합니다.

### 확인할 지표

- 사용자별 이벤트 순서
- partition별 lag
- hot partition 발생 여부

---

## 후보 2. Auto Commit에서 Manual Ack로 변경

### 초기 설계

Kafka consumer auto commit을 사용합니다.

### 발생 가능한 문제

DB 저장 전 offset이 commit되면 Consumer 장애 시 처리되지 않은 이벤트가 유실된 것처럼 보일 수 있습니다.

### 변경 방향

처리 성공 후 manual ack를 수행합니다.

### 확인할 지표

- Consumer 재시작 후 재처리 여부
- 중복 fraud_result 생성 여부
- missing event count

---

## 후보 3. Redis INCR + TTL에서 ZSET Sliding Window로 변경

### 초기 설계

`userId`별 INCR + TTL로 최근 거래 횟수를 계산합니다.

### 발생 가능한 문제

고정 윈도우 경계에서 탐지 정확도가 흔들릴 수 있습니다.

### 변경 방향

ZSET에 `eventTime`을 score로 저장하고 sliding window 방식으로 최근 거래 수를 계산합니다.

### 확인할 지표

- velocity rule 탐지 정확도
- Redis command latency
- 오래된 이벤트 제거 여부

---

## 후보 4. DLQ payload 원문 저장에서 masked payload + payload_hash로 변경

### 초기 설계

DLQ 이벤트에 실패 payload 원문을 저장합니다.

### 발생 가능한 문제

DLQ는 운영자 조회와 장애 분석 대상이므로 accountId, deviceId, ipAddress 등 민감정보가 장기간 노출될 수 있습니다.

### 변경 방향

DLQ에는 masked payload와 `payload_hash`를 저장하고, 원문 payload 접근은 별도 권한과 감사 로그가 필요하도록 설계합니다.

### 확인할 지표

- DLQ 조회 응답의 민감정보 노출 여부
- payload_hash 저장 여부
- reprocessing_history 기록 여부

---

## 후보 5. API latency만 보다가 Consumer Lag을 핵심 SLI로 추가

### 초기 설계

API p95 latency만 주요 응답성 지표로 봅니다.

### 발생 가능한 문제

API가 빠르게 응답해도 Consumer Lag이 증가하면 이상거래 탐지는 지연됩니다.

### 변경 방향

API latency, Consumer Lag, detection latency, DLQ count를 함께 핵심 지표로 봅니다.

### 확인할 지표

- API p95/p99 latency
- Consumer Lag
- detection latency
- DLQ count

---

## 후보 6. userId key로 인한 hot partition 발생과 대응

### 초기 설계

사용자별 순서 보장을 위해 `userId`를 partition key로 사용합니다.

### 발생 가능한 문제

특정 userId에 이벤트가 몰리면 일부 partition lag이 증가할 수 있습니다.

### 변경 방향

초기에는 userId key를 유지하고 hot partition을 측정합니다. key 전략 변경은 사용자별 순서 보장 영향까지 함께 검토합니다.

### 확인할 지표

- partition별 lag
- partition별 message count
- hot userId 부하 테스트 결과

---

## 후보 7. unsupported schemaVersion을 임의 변환하지 않고 DLT로 이동

### 초기 설계

Consumer가 이벤트 payload를 가능한 형태로 변환해 처리합니다.

### 발생 가능한 문제

지원하지 않는 schemaVersion을 임의로 처리하면 잘못된 탐지 결과가 생성될 수 있습니다.

### 변경 방향

지원하지 않는 schemaVersion은 DLT로 보내고 운영자가 재처리 가능 여부를 판단합니다.

### 확인할 지표

- unsupported schemaVersion DLT count
- schema compatibility test 결과
- DLQ failure_reason 분포

---

## 후보 8. Redis 장애 시 전체 실패가 아니라 degraded mode로 전환

### 초기 설계

Redis 장애 시 Consumer 처리를 실패로 봅니다.

### 발생 가능한 문제

Redis 장애가 전체 탐지 중단으로 이어질 수 있습니다.

### 변경 방향

단건 기반 rule은 계속 수행하고 Redis 기반 rule만 SKIPPED 처리합니다. FraudResult에는 `degraded=true`를 기록합니다.

### 확인할 지표

- degraded result count
- skipped rule count
- Redis error count

---

## 후보 9. Outbox Pattern 제외 후 한계와 향후 도입 조건 정리

### 초기 설계

API Server는 Kafka publish 성공 이후 `ACCEPTED`를 반환하고 Outbox Pattern은 구현하지 않습니다.

### 발생 가능한 문제

API 접수 기록과 Kafka 발행 원자성이 필요한 요구가 생기면 현재 구조만으로는 감사 기준이 부족할 수 있습니다.

### 변경 방향

초기 범위에서는 제외하되, 감사 대상 접수 기록이 필요해지면 `transaction_event_intake`와 `outbox_events` 테이블, Outbox Publisher를 추가합니다.

### 확인할 지표

- Kafka publish failure count
- accepted event count와 Kafka append count 비교
- outbox pending count, if implemented

---

## Phase 1. Gradle repository 정책 충돌

### 초기 설계

`settings.gradle`에서 dependency repository를 중앙 관리하고, root `build.gradle`의 `subprojects`에서도 `mavenCentral()`을 선언했습니다.

### 발생한 문제

`./gradlew clean build` 실행 시 `RepositoriesMode.FAIL_ON_PROJECT_REPOS` 정책과 subproject repository 선언이 충돌했습니다.

### 재현 방법

```bash
./gradlew clean build
```

### 원인 분석

settings-level repository 정책이 project-level repository 선언을 금지하는 상태에서 root build script가 repository를 중복 선언했습니다.

### 변경한 설계

repository 선언은 `settings.gradle`의 `dependencyResolutionManagement`로 고정하고, root `build.gradle`의 subproject repository 선언을 제거했습니다.

### 개선 결과

`./gradlew clean build`와 module test task가 통과했습니다.

### 남은 한계

현재 test source는 아직 없어서 test task는 `NO-SOURCE`로 통과합니다.

---

## Phase 1. Kafka Docker image tag와 CLI 경로 불일치

### 초기 설계

Kafka container image는 `bitnami/kafka:3.7`을 사용하고, topic script와 healthcheck는 `kafka-topics.sh`가 PATH에 있다고 가정했습니다.

### 발생한 문제

Docker image pull 단계에서 `bitnami/kafka:3.7` tag를 찾지 못했고, Apache Kafka image로 변경한 뒤에는 `kafka-topics.sh`가 PATH에 없어 healthcheck와 topic script가 실패했습니다.

### 재현 방법

```bash
docker compose -f infra/docker-compose.yml up -d
./scripts/create-topics.sh
```

### 원인 분석

로컬에서 사용할 image tag가 실제 registry tag와 맞지 않았고, `apache/kafka:3.7.0` image의 Kafka CLI는 `/opt/kafka/bin` 아래에 위치합니다.

### 변경한 설계

- Kafka image를 `apache/kafka:3.7.0`으로 변경했습니다.
- Docker Compose healthcheck와 topic 관련 scripts에서 `/opt/kafka/bin/kafka-topics.sh`를 명시적으로 사용하도록 수정했습니다.

### 개선 결과

Docker Compose 기동 후 Kafka container가 healthy 상태가 되었고, 다음 topic들이 생성되는 것을 확인했습니다.

```text
transaction-events
fraud-risk-events
fraud-alert-events
transaction-events.retry
transaction-events-dlt
```

### 남은 한계

로컬 환경은 single broker이므로 replication factor는 1입니다. 운영 환경 수준의 multi-broker replication은 문서 설계로만 유지합니다.

---

## Phase 1. Kafka advertised listener와 Kafka UI 연결

### 초기 설계

Kafka advertised listener를 `localhost:9092` 중심으로만 두었습니다.

### 발생한 문제

Docker network 내부의 Kafka UI가 `localhost:9092`를 사용하면 Kafka UI container 자기 자신을 바라보게 되어 broker 연결이 불안정해질 수 있습니다.

### 재현 방법

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml logs kafka-ui --tail=100
```

### 원인 분석

host에서 실행하는 Spring Boot 앱과 Docker network 내부 서비스가 Kafka broker를 바라보는 주소가 다릅니다.

### 변경한 설계

- host app용 external listener: `localhost:9092`
- Docker network 내부용 internal listener: `kafka:29092`
- Kafka UI bootstrap server: `kafka:29092`

### 개선 결과

Kafka, Kafka UI, topic 생성 script가 동일 Compose 환경에서 정상 동작했습니다.

### 남은 한계

향후 app-api/app-consumer를 Docker Compose 내부에서 실행하는 profile을 추가하면 Spring Boot Kafka bootstrap server도 internal listener를 사용하도록 profile 분리가 필요합니다.

---

## Phase 1. app-consumer health endpoint 검증 불가

### 초기 설계

`app-consumer`는 Kafka Consumer worker로 준비되어 있고 Actuator dependency와 `server.port: 8081` 설정을 가지고 있었습니다.

### 발생한 문제

`app-consumer`가 웹 애플리케이션으로 뜨지 않아 시작 직후 종료되었고, `/actuator/health` HTTP 검증을 수행할 수 없었습니다.

### 재현 방법

```bash
./gradlew :app-consumer:bootRun
curl http://localhost:8081/actuator/health
```

### 원인 분석

`app-consumer`에 `spring-boot-starter-web`이 없어 embedded web server가 생성되지 않았습니다. Actuator dependency만으로는 HTTP endpoint가 열리지 않습니다.

### 변경한 설계

Phase 1의 health endpoint 검증을 위해 `app-consumer`에 `spring-boot-starter-web`을 추가했습니다. 실제 Kafka listener 비즈니스 로직은 구현하지 않았습니다.

### 개선 결과

`app-consumer`가 8081 포트로 기동하고 `/actuator/health`가 `UP`을 반환했습니다. Prometheus도 `app-consumer` target을 `up`으로 scrape했습니다.

### 남은 한계

실제 Consumer processing, manual ack, EventProcessingLog 저장은 다음 Phase 이후에 구현해야 합니다.

---

## Phase 2. app-common test에서 AssertJ 의존성 누락

### 초기 설계

`app-common`은 공유 event schema와 enum만 포함하고, 테스트에서는 AssertJ assertion을 사용했습니다.

### 발생한 문제

`./gradlew test` 실행 시 `app-common` test compile 단계에서 AssertJ package를 찾지 못했습니다.

### 재현 방법

```bash
./gradlew test
```

### 원인 분석

`app-common`은 Spring Boot starter test를 사용하지 않고 `org.junit.jupiter:junit-jupiter`만 test dependency로 둡니다. 따라서 AssertJ는 test classpath에 포함되지 않았습니다.

### 변경한 설계

불필요한 test dependency를 추가하지 않고, `TransactionEventMessageTest`를 JUnit 기본 assertion으로 수정했습니다.

### 개선 결과

`./gradlew test`가 통과했습니다.

### 남은 한계

현재 `app-common` test는 event schema의 기본 필드 계약만 확인합니다. schema compatibility test는 Kafka producer/consumer 구현 이후 확장합니다.

---

## Phase 3. Kafka publish 실패 시 receipt 상태 보존

### 초기 상태

거래 이벤트 접수 흐름은 request validation, receipt 저장, Kafka publish, accepted response 순서로 설계했습니다.

### 발생한 문제

DB receipt 저장 후 Kafka publish가 실패하면 API는 실패를 반환해야 하지만, 운영 추적을 위해 실패한 receipt 상태도 남아야 합니다.

### 재현 방법

```bash
make test
```

`TransactionEventControllerTest`에서 Producer mock이 `KafkaPublishFailedException`을 던지도록 설정합니다.

### 원인 분석

receipt 저장과 Kafka publish를 하나의 원자적 transaction으로 묶는 Outbox Pattern은 Phase 3 범위가 아닙니다. 일반 runtime exception으로 rollback되면 `PUBLISH_FAILED` 상태도 사라질 수 있습니다.

### 수정 내용

`TransactionEventIntakeService`에서 Kafka publish 실패 시 receipt를 `PUBLISH_FAILED`로 변경하고 API는 `KAFKA_PUBLISH_FAILED` 503을 반환하도록 했습니다. 해당 exception은 transaction rollback 대상에서 제외했습니다.

### 검증 명령

```bash
make test
```

### 남은 한계

`PUBLISH_FAILED` receipt를 자동으로 재발행하는 outbox publisher는 아직 없습니다. 후속 hardening 후보로 둡니다.

---

## Phase 3. 중복 eventId와 idempotency 정책 분리

### 초기 상태

`eventId`는 transaction event의 중복 방어 기준입니다.

### 발생한 문제

중복 `eventId` 요청을 기존 receipt replay처럼 반환할지, 명확한 conflict로 처리할지 결정이 필요했습니다.

### 재현 방법

```bash
make test
```

동일 `eventId`로 `POST /api/v1/transactions/events`를 두 번 호출합니다.

### 원인 분석

동일 body 비교와 replay response는 별도 idempotency 정책이 필요합니다. Phase 3은 idempotent replay 구현 단계가 아니라 Producer intake 구현 단계입니다.

### 수정 내용

Phase 3에서는 중복 `eventId`를 `409 CONFLICT`와 `DUPLICATE_TRANSACTION_EVENT`로 처리합니다.

### 검증 명령

```bash
make test
```

### 남은 한계

idempotent replay 정책은 필요성이 확인되면 별도 Phase에서 설계합니다.

---

## Phase 3. Outbox 미적용에 따른 양방향 불일치 가능성

### 초기 상태

Phase 3에서는 `transaction_event_receipts` 저장과 Kafka publish를 하나의 원자적 작업으로 묶는 Outbox Pattern을 구현하지 않습니다.

### 발생한 문제

다음 두 방향의 불일치 가능성이 남습니다.

- receipt 저장은 성공했지만 Kafka publish가 실패하는 경우
- Kafka publish는 성공했지만 `PUBLISHED` 상태 저장 또는 DB commit이 실패하는 경우

### 원인 분석

DB transaction과 Kafka publish는 서로 다른 시스템의 작업입니다. Outbox Pattern, Kafka transaction과 DB transaction 연계, 또는 발행 감사 테이블 기반 보정 작업이 없으면 완전한 원자성을 보장할 수 없습니다.

### 수정 내용

- Kafka publish 실패는 receipt status를 `PUBLISH_FAILED`로 남기고 API는 503을 반환합니다.
- Kafka publish 성공 후 DB commit 실패 가능성은 문서화하고 후속 hardening 대상으로 남깁니다.
- `PUBLISH_FAILED` receipt 재요청은 Phase 3에서 `409 CONFLICT`로 처리하며 자동 재발행은 지원하지 않습니다.

### 검증 명령

```bash
make test
```

### 남은 한계

Outbox publisher 또는 발행 감사 테이블 기반 보정 작업은 아직 없습니다.

---

## Phase 3. 시간 기준과 실패 메시지 저장 정책

### 초기 상태

Application clock은 server default timezone을 사용했고, Entity lifecycle timestamp는 `OffsetDateTime.now()`를 직접 호출했습니다. Kafka publish 실패 메시지는 receipt에 저장됩니다.

### 발생한 문제

- 서버 timezone에 따라 `receivedAt` 기준이 흔들릴 수 있습니다.
- Entity lifecycle timestamp와 service clock 기준이 완전히 같지 않습니다.
- 실패 메시지에 긴 외부 예외 메시지나 민감한 세부 정보가 섞일 수 있습니다.

### 수정 내용

- Application clock은 `Clock.systemUTC()`로 변경했습니다.
- `publish_error_message`는 raw payload를 저장하지 않는 요약 메시지로 제한하고 500자 이내로 truncate합니다.

### 남은 한계

Entity lifecycle timestamp는 아직 JPA callback 기준입니다. JPA auditing 또는 service clock 주입 방식으로 timestamp 기준을 통일하는 작업은 후속 개선 대상으로 둡니다.

---

## Phase 4-A. Consumer 구현 전 Minimum CI Gate 선반영

### 문제 상황

Phase 3까지는 로컬에서 `make test`, `make build`, `make final-check`를 직접 실행하며 검증했습니다. 하지만 Phase 4부터 Kafka Consumer, manual ack, processing log, 이후 Redis Sliding Window와 Retry/DLT가 추가될 예정이라 로컬 검증만으로는 회귀 버그를 안정적으로 막기 어렵다고 판단했습니다.

### 원인

Consumer 작업은 offset commit 시점, DB 저장 성공 여부, 중복 offset 처리처럼 작은 변경에도 동작이 달라질 수 있는 영역입니다. 자동화된 CI Gate가 없으면 main 브랜치에 깨진 코드가 들어갈 위험이 커집니다.

### 판단

Consumer 구현 전에 GitHub Actions 기반 최소 CI Gate를 먼저 추가했습니다. push와 pull request 시점에 `make ci-check`를 자동 실행하여 기본 회귀 검증을 자동화했습니다.

### 선택한 방식

초기 CI는 Java 17, Gradle cache, `./gradlew test`, `./gradlew assemble` 중심의 `make ci-check`로 구성했습니다. `workflow_dispatch`로 수동 재검증을 허용하고, workflow 권한은 `contents: read`로 제한했습니다. Docker Compose 기반 Kafka/PostgreSQL/Redis 통합 검증은 개발 속도와 안정성을 고려해 후속 Phase로 분리했습니다.

### 트레이드오프

초기 CI는 빠르고 안정적이지만 Kafka end-to-end 처리, Consumer Lag, Redis 장애, DLT 재처리까지 검증하지는 못합니다. 대신 후속 Phase에서 `ci-integration.yml` 또는 nightly workflow로 확장할 수 있도록 범위를 분리했습니다.

### 검증 기준

- push 시 GitHub Actions CI가 실행됩니다.
- unit test와 artifact assembly가 통과해야 합니다.
- CI 실패 시 main merge를 보류합니다.
- 인프라 의존 검증은 로컬 `make infra-up`, `make api`, `make consumer`로 별도 수행합니다.

## Phase 4. Consumer Manual Ack and Processing Log

### 문제 상황

Kafka Consumer에서 메시지를 처리했지만 offset commit 시점과 DB 저장 시점의 순서가 명확하지 않으면 processing log 누락 또는 중복 처리가 발생할 수 있습니다.

### 원인

auto commit을 사용하면 DB 저장 실패와 무관하게 offset이 commit될 수 있습니다. 반대로 DB 저장 성공 후 ack 직전에 Consumer가 죽으면 같은 offset이 재소비될 수 있습니다.

### 판단

Phase 4에서는 `enable-auto-commit=false`와 `AckMode.MANUAL_IMMEDIATE`를 사용하고, `event_processing_logs` 저장 성공 후 acknowledge를 수행합니다. 즉시 commit 의도를 코드에서 명확히 표현하기 위해 `MANUAL` 대신 `MANUAL_IMMEDIATE`를 선택했습니다.

Consumer가 처음 생성된 group ID로 시작될 때 이미 topic에 쌓인 미처리 이벤트를 읽을 수 있도록 local 설정은 `auto-offset-reset=earliest`로 둡니다.

### 대안

- auto commit 사용
- listener 진입 즉시 ack
- DB 저장 성공 후 ack

### 선택

DB 저장 성공 후 ack

### 트레이드오프

DB 저장은 성공했지만 ack 직전에 Consumer가 죽으면 같은 offset이 재소비될 수 있습니다. 이를 `(topic, partition_no, offset_no)` unique constraint와 service의 duplicate skip 정책으로 방어합니다.

Flyway migration은 `app-api`를 schema owner로 두고, app-consumer runtime은 Flyway를 실행하지 않고 JPA validate만 수행합니다. 같은 DB를 바라보는 두 애플리케이션이 각각 Flyway를 실행하면 checksum drift로 한쪽 서비스가 부팅 실패할 수 있기 때문입니다. app-consumer module test에서는 H2 검증을 위해 test resources에만 migration을 둡니다.

### 검증

- processing log 저장 성공 시 ack 호출 테스트
- 이미 처리된 offset 재소비 시 ack 호출 테스트
- 저장 실패 시 ack 미호출 테스트
- 동일 offset 재처리 시 duplicate log가 생성되지 않는지 검증
- 같은 eventId라도 offset이 다르면 별도 log가 생성되는지 검증
- eventId 조회 시 `processedAt desc` 정렬 검증

### 남은 한계

- Retry/DLT는 Phase 9 범위입니다.
- FraudResult 저장과 eventId 기준 idempotency는 Phase 5 이후 범위입니다.
- `FAILED` processing status는 Phase 4에서는 예약 상태입니다. DB 자체가 실패 로그를 저장할 수 없는 경우에는 ack하지 않고 재소비 가능성을 열어두며, 저장 가능한 business failure와 DLT 기록은 Phase 9에서 구체화합니다.

## Phase 5. Fraud Result 저장과 Consumer Ack 순서

### 문제 상황

Consumer가 메시지를 처리한 뒤 processing log와 fraud result를 모두 저장해야 했습니다. 하지만 어느 시점에 ack를 호출해야 하는지에 따라 탐지 결과 누락 또는 중복 저장 가능성이 달라졌습니다.

### 원인

ack를 먼저 호출하면 fraud result 저장 실패 시 Kafka 재소비가 불가능해질 수 있습니다. 반대로 fraud result 저장 후 ack 직전에 Consumer가 죽으면 같은 이벤트가 재소비될 수 있습니다.

### 판단

Phase 5에서는 processing log 저장, Rule Engine 평가, fraud result 저장이 모두 성공한 뒤 ack를 호출합니다. ack 직전 장애로 인한 재소비는 `event_id` unique constraint로 중복 저장을 방어합니다.

Phase 5에서는 processing log와 fraud result를 하나의 DB transaction으로 묶지 않고, Consumer 재소비와 idempotent 저장으로 복구 가능하게 설계했습니다. 따라서 processing log 저장 후 fraud result 저장 전에 장애가 발생하면 일시적으로 processing log만 존재할 수 있습니다. 이 경우 ack가 호출되지 않으므로 Kafka 재소비가 발생하고, processing log는 `(topic, partition_no, offset_no)` unique constraint로 duplicate skip되며, fraud result 저장을 다시 시도합니다.

### 선택

PostgreSQL `event_id` unique constraint를 최종 정합성 기준으로 두고, Consumer 로직은 duplicate event를 정상 처리 가능한 idempotent 흐름으로 구성했습니다.

`existsByEventId()`는 불필요한 insert 시도를 줄이기 위한 fast path이며, 최종 중복 방어는 PostgreSQL `event_id` unique constraint가 담당합니다. 동시 Consumer 또는 재소비 상황에서 race condition이 발생해도 unique constraint 충돌을 duplicate result로 처리합니다.

### 트레이드오프

eventId 기준으로 하나의 탐지 결과만 저장하므로, rule version이 바뀐 뒤 동일 event를 재평가하는 시나리오는 별도 result versioning이 필요합니다. 이는 후속 Phase에서 보완합니다.

`matched_rules`는 Phase 5에서 comma-separated text로 저장합니다. rule code rename 또는 과거 데이터에 남은 unknown rule code가 생기면 enum 변환 기반 조회가 깨질 수 있으므로, 후속 Phase에서 JSONB, rule code version, unknown rule code 응답 정책을 검토합니다.

## Phase 5. Rule Engine v1 분리와 Redis 제외

### 문제 상황

Listener에 rule 판단과 저장 로직을 직접 넣으면 ack 시점, rule 변경, 저장 idempotency가 한 메서드에 섞일 수 있었습니다.

### 판단

Listener는 orchestration만 담당하고, Rule Engine은 순수 rule 평가, FraudDetectionResultService는 저장과 idempotency만 담당하도록 분리했습니다.

Redis Sliding Window는 사용자 최근 거래 패턴을 보려면 필요하지만, Phase 5에서는 단건 이벤트 기반 rule만 먼저 구현했습니다. 이렇게 하면 Consumer ack와 result persistence의 정합성을 먼저 검증한 뒤 Redis 장애/degraded mode를 다음 단계에서 좁게 다룰 수 있습니다.

### 검증

- Rule Engine unit test
- fraud result duplicate eventId test
- fraud result 저장 실패 시 ack 미호출 test
- rule engine 예외 시 ack 미호출 test
- processing log 저장 실패 시 fraud result 저장 미시도 test

## Phase 6. Redis Sliding Window 장애 시 Consumer Ack 정책

### 문제 상황

Sliding Window 탐지를 위해 Redis를 호출하면 Redis 장애가 Consumer 처리 실패로 이어질 수 있었습니다. 이 경우 Redis가 잠깐 불안정해도 Kafka Lag이 증가하고 정상 이벤트 처리까지 지연될 수 있습니다.

### 원인

Redis는 최근 거래 횟수와 누적 금액을 빠르게 계산하기 위한 보조 상태 저장소입니다. 최종 fraud result 저장과 eventId 중복 방어 기준은 PostgreSQL `fraud_detection_results.event_id` unique constraint입니다.

### 판단

Phase 6에서는 Redis 장애를 전체 Consumer 실패가 아니라 탐지 민감도 저하로 처리합니다. Redis store가 예외를 만나면 `RecentTransactionWindowResult.degraded`를 반환하고, Rule Engine은 Redis 의존 rule을 `skipped_rules`에 기록한 뒤 stateless rule만 평가합니다.

이미 `fraud_detection_results`에 같은 `eventId` 결과가 존재하면 Redis window를 갱신하지 않고 duplicate fraud result로 ack합니다. 이는 동일 eventId의 conflict replay가 Redis Hash metadata를 덮어써 보조 상태를 오염시키는 일을 막기 위한 fast path입니다. 최종 중복 방어는 여전히 PostgreSQL unique constraint가 담당합니다.

### 선택

Redis 장애 시 fraud result를 `degraded=true`로 저장하고 ack를 허용합니다. PostgreSQL fraud result 저장 실패, processing log 저장 실패, Rule Engine 자체 예외는 기존처럼 ack하지 않습니다.

### 트레이드오프

Redis 장애 중에는 `RAPID_TRANSACTION_COUNT`, `WINDOW_AMOUNT_SUM` 탐지가 누락될 수 있습니다. 대신 Consumer가 중단되지 않고, 어떤 rule이 생략됐는지 운영 조회 API와 reason으로 확인할 수 있습니다.

### 검증

- Redis degraded window result가 들어와도 fraud result 저장 후 ack 호출
- duplicate fraud result fast path에서는 Redis store, Rule Engine, fraud result save 미호출
- fraud result 저장 실패 시 ack 미호출
- processing log 저장 실패 시 Redis store와 fraud result 저장 미호출
- Rule Engine에서 Redis degraded 시 stateful rule score 미반영과 skipped rule 기록

## Phase 6. eventTime 기준 window와 중복 eventId 처리

### 문제 상황

Sliding Window를 시스템 현재 시각 기준으로 계산하면 오래된 이벤트 재처리나 지연 소비 시 탐지 결과가 왜곡될 수 있습니다. 또한 같은 `eventId`가 재소비될 때 Redis count가 중복 증가할 수 있습니다.

### 판단

window 기준은 `eventTime`으로 두었습니다. Redis ZSET score는 `eventTime` epoch millis이고 member는 `eventId`입니다. 같은 eventId를 다시 기록하면 ZSET member가 update되므로 count 중복 증가를 완화할 수 있습니다.

### 선택

- user window key: `fraud:tx:user:{userId}:events`
- event metadata key: `fraud:tx:event:{eventId}`
- sorted set member: `eventId`
- hash metadata: `amount`, `currency`, `eventTime`, `userId`
- TTL: window보다 긴 10분

### 트레이드오프

Hash key 수가 늘어나지만 amount 합산을 위해 ZSET member parsing을 피할 수 있습니다. TTL을 짧게 유지해 오래된 key가 무기한 남지 않도록 했습니다.

### 검증

- 같은 eventId를 두 번 기록해도 Redis window count가 1로 유지되는 테스트
- window 밖 이벤트가 count/sum에서 제외되는 테스트
- Redis store 예외 시 degraded result 반환 테스트

## Phase 6. Redis multi-command 부분 실패와 window count 기준

### 문제 상황

Redis Sliding Window 저장은 Hash metadata 저장, ZSET 추가, cleanup, TTL 갱신, window 조회처럼 여러 명령으로 구성됩니다. 중간 실패가 발생하면 ZSET에는 eventId가 있지만 Hash amount가 없는 불완전 상태가 남을 수 있습니다.

### 판단

Phase 6에서 Redis Lua나 transaction까지 도입하면 구현 범위가 커집니다. 대신 Hash metadata를 먼저 저장한 뒤 ZSET에 추가하고, window 계산 시 amount metadata가 있는 eventId만 count와 amount sum에 포함합니다.

### 선택

- `HSET fraud:tx:event:{eventId}`를 먼저 수행
- 그 다음 `ZADD fraud:tx:user:{userId}:events`
- `ZRANGEBYSCORE` 결과 중 amount Hash가 없는 eventId는 count/sum에서 제외
- Redis 명령 예외는 degraded result로 반환

### 트레이드오프

완전한 원자성은 아닙니다. 다만 metadata 없는 ZSET member가 탐지 count를 부풀리는 문제를 막고, 실제 Redis transaction/Lua는 후속 hardening 범위로 남깁니다.

### 검증

- Hash metadata 저장 후 ZSET 추가 순서 검증
- metadata 없는 ZSET member가 count와 amount sum에서 제외되는지 검증
- TTL 갱신 호출 검증

## Phase 6. V4 migration의 H2 호환 문법

### 문제 상황

`fraud_detection_results`에 `skipped_rules`, `degraded` 컬럼을 추가하는 V4 migration이 PostgreSQL 문법으로는 자연스러웠지만 H2 테스트에서 syntax error가 발생했습니다.

### 원인

H2 PostgreSQL mode가 하나의 `alter table` 안에서 여러 `add column` 절을 comma로 연결하는 문법을 처리하지 못했습니다.

### 선택

`alter table ... add column` 문을 컬럼별로 분리했습니다. 이 방식은 PostgreSQL과 H2 테스트 환경 모두에서 동작합니다.

### 검증

- `./gradlew :app-consumer:test` 재실행 후 PASS
- `./gradlew :app-api:test` 실행 후 PASS

## Phase 7. Redis Integration Test를 기본 CI에서 분리한 이유

### 문제 상황

Redis Sliding Window는 실제 Redis 자료구조로 검증할 필요가 있었지만, Docker 기반 integration test를 기본 CI에 바로 포함하면 CI 시간이 증가하고 환경 의존성이 커질 수 있었습니다.

### 판단

Phase 7에서는 Redis integration test를 추가하되 기본 `make ci-check`와 분리했습니다. 기본 CI는 빠른 회귀 검증을 담당하고, Redis integration test는 `make redis-integration-test`로 필요할 때 실행합니다. 해당 target은 Docker Compose Redis readiness를 확인한 뒤 Gradle integration test를 실행합니다.

### 선택

Testcontainers Redis를 먼저 시도했지만 로컬 Docker Desktop provider API 호환 문제로 Testcontainers가 유효한 Docker environment를 찾지 못했습니다. Phase 7에서는 Docker Compose Redis를 띄운 뒤 실제 Redis에 연결하는 integration test로 전환했습니다. 테스트는 Redis database index `15`를 사용하고, 테스트 시작 전 해당 DB만 초기화합니다.

### 트레이드오프

기본 CI만으로는 실제 Redis ZSET/Hash 동작을 검증하지 못합니다. 대신 Docker가 가능한 로컬 또는 별도 workflow에서 `make redis-integration-test`를 실행해 실제 Redis 기준 검증을 수행할 수 있습니다.

### 검증

- `make redis-integration-test` PASS
- 실제 Redis DB 15 기준 ZSET/Hash 저장, duplicate eventId, TTL, cleanup, metadata exclusion 검증

## Phase 7. Metric tag에 고유 식별자를 넣지 않은 이유

### 문제 상황

Redis degraded, skipped rule, latency metric을 추가하면서 eventId, traceId, userId를 tag로 넣으면 개별 이벤트 추적은 쉬워질 수 있었습니다.

### 판단

Prometheus metric은 집계와 추세 관측을 위한 데이터입니다. eventId, traceId, userId는 cardinality가 높고 운영 환경에서는 식별자 노출 위험도 있습니다.

### 선택

Metric tag에는 낮은 cardinality의 `rule`만 사용합니다. 개별 이벤트 추적은 structured log의 `traceId`와 `eventId`로 수행합니다.

### 검증

- `fraud.rule.skipped.total{rule=RAPID_TRANSACTION_COUNT}` 형태로 skipped rule metric 확인
- eventId/userId/traceId tag 미사용 확인

## Phase 7. Redis latency 측정 위치

### 문제 상황

Redis command latency를 개별 HSET/ZADD/ZRANGE마다 측정할지, window store 호출 전체를 측정할지 결정해야 했습니다.

### 판단

현재 Phase의 목표는 Consumer가 Redis Sliding Window 처리에 얼마나 묶이는지 관측하는 것입니다. 따라서 Hash 저장, ZSET 갱신, cleanup, TTL, window 조회를 포함하는 `recordAndGetWindow` 전체 시간을 Timer로 측정했습니다.

### 선택

`fraud.redis.window.record.latency` Timer를 `RedisRecentTransactionWindowStore.recordAndGetWindow` 경계에 적용했습니다.

### 트레이드오프

개별 Redis command 병목은 아직 분리해 볼 수 없습니다. 필요하면 후속 Phase에서 command별 metric 또는 Redis client metric을 추가합니다.

## Phase 7. Degraded metric이 alert 후보가 되는 이유

### 문제 상황

Redis 장애는 Consumer 전체 실패가 아니라 degraded mode로 처리됩니다. 따라서 API/Consumer가 계속 정상처럼 보이더라도 Redis 기반 rule이 계속 skip될 수 있습니다.

### 판단

degraded count와 skipped rule count는 탐지 민감도 저하를 알려주는 핵심 신호입니다. 로그만으로는 추세와 alert를 만들기 어렵기 때문에 metric으로 집계합니다.

### 선택

- `fraud.redis.window.degraded.total`
- `fraud.detection.degraded.total`
- `fraud.rule.skipped.total`

위 metric을 추가하고 Prometheus/Grafana alert 후보로 문서화했습니다.

## Phase 8. Redis Down Drill에서 ack를 유지한 이유

### 문제 상황

Redis가 중단되면 Sliding Window 기반 stateful rule을 평가할 수 없습니다. 이때 ack를 막으면 Kafka Lag이 증가하고, Redis와 무관한 정상 이벤트까지 Consumer가 처리하지 못할 수 있습니다.

### 판단

Redis는 Source of Truth가 아니므로 Redis 장애는 degraded mode로 처리합니다. Redis 의존 rule은 skipped 처리하고, stateless rule만으로 fraud result를 저장한 뒤 ack를 유지합니다.

### 트레이드오프

Redis 장애 중에는 최근 거래 패턴 기반 탐지가 누락될 수 있습니다. 대신 이벤트 처리와 결과 저장은 유지되어 서비스 가용성을 확보합니다.

### 검증

`redis_down_drill.sh`는 Redis를 중지한 뒤 이벤트를 발행하고, fraud result의 `degraded=true`, `RAPID_TRANSACTION_COUNT`/`WINDOW_AMOUNT_SUM` skipped rule, Prometheus degraded/skipped/latency metric 증가를 확인합니다.

## Phase 8. Consumer 중지 중 발행된 메시지 확인 방법

### 문제 상황

app-consumer가 중지된 동안 app-api는 Kafka publish를 계속 성공할 수 있습니다. 이 경우 이벤트는 Kafka topic에 남고 fraud result는 Consumer 재시작 전까지 생성되지 않습니다.

### 판단

Consumer restart drill은 app-consumer를 먼저 중지한 상태에서 이벤트를 발행하고, app-consumer를 다시 시작한 뒤 fraud result와 processing log가 생성되는지 확인합니다.

### 검증 기준

- Consumer 중지 중 API publish는 `202 Accepted`
- Consumer 재시작 후 fraud result 조회 가능
- processing log에 `PROCESSED` 기록 존재
- 같은 `eventId`에 대한 `fraud_detection_results` row count가 1건

## Phase 8. Kafka Unavailable Drill을 Runbook으로 분리한 이유

### 문제 상황

Kafka broker stop/start는 topic metadata, producer timeout, Consumer reconnect, 로컬 smoke workflow에 모두 영향을 줍니다. 자동 script로 기본 target에 포함하면 로컬 개발 환경을 쉽게 깨뜨릴 수 있습니다.

### 판단

Phase 8에서는 Kafka unavailable을 markdown runbook으로 분리하고, 수동으로 API publish failure와 Consumer reconnect log를 확인합니다.

### 한계

Retry/DLT, DLQ 저장, 자동 reprocess는 이번 Phase 범위가 아닙니다. Kafka 장애 자동 복구 정책은 후속 Retry/DLT Phase에서 구현합니다.

## Phase 8. Drill PASS/FAIL 기준

### Redis Down PASS

- app-api와 app-consumer health endpoint가 drill 시작 전에 응답
- Redis stop 후 이벤트 발행 성공
- fraud result `degraded=true`
- Redis 의존 rule이 `skippedRules`에 포함
- `fraud_redis_window_degraded_total`, `fraud_detection_degraded_total`, `fraud_rule_skipped_total`, `fraud_redis_window_record_latency_seconds_count` 증가 확인
- Redis restart 후 신규 이벤트는 `degraded=false`

### Consumer Restart PASS

- Consumer 중지 중 이벤트가 Kafka에 publish됨
- Consumer 재시작 후 fraud result와 processing log 조회 가능
- `fraud_detection_results` row count가 1건

### Kafka Unavailable PASS

- Kafka 중지 중 API가 publish 성공으로 응답하지 않음
- Kafka 복구 후 topic 조회와 Consumer reconnect가 가능
- 복구 후 신규 이벤트 처리 가능

## Phase 8. Metric만 보지 않고 API/DB 조회를 함께 보는 이유

Metric은 장애 추세를 보여주지만 단일 이벤트가 실제로 저장되었는지, 어떤 rule이 skipped 되었는지, processing log가 남았는지는 보장하지 않습니다. Phase 8 drill은 Prometheus metric 증가와 함께 fraud result API, processing log API, DB row count를 확인해 운영 증거를 남깁니다.

## Phase 9. 어떤 실패를 DLT로 보낼 것인가

### 문제 상황

Consumer 처리 중 모든 예외를 DLT로 보낼지, 일부는 ack하지 않고 Kafka 재소비에 맡길지 결정해야 했습니다.

### 판단

Rule Engine 예외처럼 payload 또는 처리 로직 문제로 같은 실패가 반복될 가능성이 큰 경우는 `transaction-events-dlt`로 격리합니다. Redis 장애는 Phase 6 정책대로 degraded mode로 처리하며 DLT 대상이 아닙니다.

### 트레이드오프

DLT로 보낸 이벤트는 원본 topic에서 ack되므로 무한 재소비를 막을 수 있습니다. 대신 운영자가 DLT 상태와 payload를 확인해 재처리 또는 폐기를 명시적으로 결정해야 합니다.

## Phase 9. DB 장애를 DLT로 보내지 않은 이유

### 문제 상황

Consumer 처리 중 processing log 또는 fraud result 저장이 실패했을 때 이 이벤트를 DLT로 보낼지, ack하지 않고 재소비되게 둘지 결정해야 했습니다.

### 판단

DB 장애는 DLT 저장 자체도 실패할 가능성이 높습니다. 따라서 DB 장애는 DLT로 보내기보다 ack하지 않고 Kafka 재소비를 유도하는 편이 더 안전하다고 판단했습니다.

### 트레이드오프

일시적인 DB 장애 동안 Kafka Lag이 증가할 수 있습니다. 대신 DLT 저장 실패로 이벤트를 잃거나 상태를 설명할 수 없는 상황을 피할 수 있습니다.

## Phase 9. DLT 저장과 Kafka publish 사이 atomicity 한계

### 문제 상황

DLT DB row 저장과 `transaction-events-dlt` publish, 재처리 상태 변경과 원본 topic publish는 서로 다른 시스템을 건드립니다.

### 판단

Phase 9에서는 outbox 또는 Kafka transaction을 도입하지 않고, 각 중간 상태를 운영 문서에 기록했습니다. DLT 저장 후 publish 실패는 ack하지 않아 원본 record 재소비를 유도합니다. 재처리 publish 실패는 `REPROCESS_FAILED`로 남기고 API는 `503 KAFKA_PUBLISH_FAILED`를 반환합니다.

### 남은 한계

Kafka publish 성공 후 DB 상태 변경 실패 같은 보정은 후속 Phase의 outbox/reconciliation 후보입니다.

## Phase 9. 재처리 API의 상태 전이 충돌

`PENDING`과 `REPROCESS_FAILED`만 재처리/폐기 가능합니다. `REPROCESSED`와 `DISCARDED`는 종료 상태이므로 다시 재처리하면 `409 DLT_STATE_CONFLICT`로 응답합니다.

이 정책은 운영자가 같은 DLT row를 반복 클릭하거나, 이미 폐기한 이벤트를 실수로 재발행하는 상황을 막기 위한 방어입니다.

Phase 9 보완에서는 재처리/폐기 조회에 `PESSIMISTIC_WRITE` row lock을 적용했습니다. 같은 DLT id에 대한 동시 상태 변경을 직렬화해 중복 publish 가능성을 줄입니다.

## Phase 9. duplicate DLT 저장 방어 기준

같은 Kafka record가 여러 번 DLT path로 들어올 수 있으므로 `(source_topic, source_partition, source_offset)` unique constraint를 둡니다. 같은 `eventId`라도 서로 다른 offset에서 여러 실패 이력이 생길 수 있으므로 `event_id` unique는 걸지 않습니다.

## Phase 9. DLT payload sanitizer를 둔 이유

### 문제 상황

DLT는 실패 이벤트를 보관하는 저장소이므로 원본 payload와 예외 메시지가 오래 남기 쉽습니다. 운영 데이터가 들어오면 account, device, 연락처 같은 직접 식별자가 함께 저장될 위험이 있습니다.

### 판단

Phase 9에서는 완전한 masking rule engine을 만들지는 않았지만, DLT 저장 경로를 `sanitizePayload`와 `sanitizeErrorMessage`로 분리했습니다. 현재 local payload는 synthetic identifier만 사용하므로 필드 마스킹은 적용하지 않습니다.

### 방어 기준

- `errorMessage`는 500자로 제한합니다.
- null 또는 blank error message는 예외 class 이름으로 대체합니다.
- stacktrace 전체는 저장하지 않습니다.
- 운영 확장 시 payload sanitizer에서 카드번호, 계좌번호, 이메일, 전화번호 등 직접 식별자를 제거합니다.

## Phase 9. 재처리 횟수 제한과 rate limit을 후속으로 남긴 이유

Phase 9에서는 `reprocess_attempts`를 기록하지만 최대 재처리 횟수 제한은 적용하지 않았습니다. 운영 환경에서는 동일 이벤트의 반복 재처리로 인한 Kafka 부하와 운영 실수를 막기 위해 maxAttempts, cooldown, 관리자 승인 정책을 추가해야 합니다.

재처리 API는 단건 수동 재처리를 기준으로 구현했습니다. 대량 재처리나 반복 호출에 따른 Kafka 부하를 막기 위한 rate limit, batch size 제한, cooldown 정책은 후속 운영 안정화 Phase에서 보완합니다.

## Phase 9. 관리자 인증/인가와 DLT metric을 후속으로 남긴 이유

Phase 9의 DLT Admin API는 운영자용 계약과 상태 전이 검증에 집중했습니다. 실제 운영 환경에서는 ADMIN 권한 기반 인증/인가, 재처리/폐기 audit log, 요청자 식별자, 변경 전후 상태 기록이 필수입니다.

DLT 상태는 DB와 Admin API로 조회 가능하게 만들었습니다. DLT pending count, reprocess failed count, discard count 기반 metric과 alert rule은 후속 Observability Phase에서 추가합니다.
