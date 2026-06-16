# AI Review

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

## Phase 1 AI Review

### AI/Codex가 제안한 내용

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
