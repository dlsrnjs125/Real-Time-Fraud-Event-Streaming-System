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

Docker Compose 기동 후 Kafka container가 healthy 상태가 되었고, 다음 topic 5개가 생성되는 것을 확인했습니다.

```text
transaction-events
fraud-risk-events
fraud-alert-events
transaction-events.retry
transaction-events.dlt
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
