# Development Roadmap

## Progress Overview

| Phase | Status | 현재 상태 | 주요 산출물 | 다음 작업 |
|---:|---|---|---|---|
| Phase 0 | Done | 초기 기획/설계와 스캐폴딩 작성 완료 | README, docs, Gradle multi-module, app skeleton, Docker Compose skeleton | Phase 1 검증 결과 기준으로 기능 구현 준비 |
| Phase 1 | Done | 로컬 실행 기반과 scaffold 검증 완료 | Gradle Wrapper, Docker Compose 검증, topic script 검증, app health 검증 | 거래 이벤트 스키마와 Kafka Producer 구현 |
| Phase 2 | Not Started | 이벤트 스키마 초안만 작성 | app-common event records | 거래 이벤트 접수 API와 Kafka producer 구현 |
| Phase 3 | Not Started | Consumer 애플리케이션 골격만 작성 | app-consumer skeleton | Kafka listener, manual ack, processing log 구현 |
| Phase 4 | Not Started | Rule Engine 미구현 | 설계 문서 | AmountRule, VelocityRule, NewDeviceRule 구현 |
| Phase 5 | Not Started | Redis sliding window 미구현 | Redis 설계 문서 | ZSET 기반 velocity repository 구현 |
| Phase 6 | Not Started | Retry/DLT 설계만 작성 | retry/dlt topic, reprocessing docs | DLT 저장, 조회, 재처리 흐름 구현 |
| Phase 7 | Not Started | Actuator/Prometheus 설정 초안 | prometheus.yml, actuator config | custom metrics와 Grafana dashboard 구성 |
| Phase 8 | Not Started | k6 시나리오 초안 | load-test/k6 scripts | 정상/피크/장애 부하 측정 |
| Phase 9 | Not Started | 결과 문서 템플릿 준비 | troubleshooting/failure docs | 측정 결과와 설계 변경 기록 |
| Phase 10+ | Not Started | 운영/보안 확장 후보 정리 | security, SLO, DevOps, runbook docs | CI/CD, 인증/인가, alert hardening |

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

### Notes

- 기존 Docker Compose의 Kafka image tag와 Kafka CLI 경로가 실제 로컬 실행 환경과 맞지 않아 수정했습니다.
- Kafka UI는 Docker network 내부에서 `kafka:29092`를 사용하고, host에서 실행하는 Spring Boot 앱은 `localhost:9092`를 사용합니다.
- `app-consumer`는 worker 성격이지만 Phase 1 health endpoint 검증을 위해 embedded web server를 띄우도록 `spring-boot-starter-web`을 추가했습니다.
- 실제 transaction event API, Kafka producer, Kafka listener, rule engine, Redis sliding window, DLQ API는 구현하지 않았습니다.

### 완료 기준

- Kafka UI 접속 가능
- PostgreSQL 연결 가능
- Redis ping 성공
- Prometheus target UP
- Grafana 접속 가능

## Phase 2. Event Schema and Producer

### 목표

거래 이벤트 요청을 받아 `transaction-events` topic으로 발행합니다.

### 완료 기준

- `POST /api/v1/transactions/events` 구현
- `userId`가 Kafka key로 사용됨
- `eventId`, `traceId`, `schemaVersion`, `receivedAt`이 message에 포함됨
- k6 normal-load에서 Kafka publish 성공률 측정 가능

## Phase 3. Consumer Processing Log

### 목표

Consumer가 `transaction-events`를 소비하고 처리 로그를 PostgreSQL에 저장합니다.

### 완료 기준

- `enable-auto-commit=false`
- 처리 성공 후 manual ack
- EventProcessingLog 저장
- Consumer 재시작 후 미처리 이벤트 재소비 확인

## Phase 4. Fraud Rule Engine

### 목표

고액 거래, 반복 거래, 새 기기 거래 rule을 구현합니다.

### 완료 기준

- AmountRule
- VelocityRule
- NewDeviceRule
- FraudResult 저장
- `eventId` unique constraint로 중복 결과 방지

## Phase 5. Redis Sliding Window

### 목표

Redis ZSET 기반 사용자별 최근 거래 window를 구현합니다.

### 완료 기준

- userId별 ZSET 저장
- window 밖 이벤트 제거
- Redis 장애 시 degraded mode 기록

## Phase 6. Retry and DLT

### 목표

Consumer 처리 실패를 retry와 DLT로 분리합니다.

### 완료 기준

- 일시 실패는 retry
- 영구 실패는 DLT
- DLT 이벤트 조회
- DLT 재처리 시 중복 FraudResult 생성 없음

## Phase 7. Observability

### 목표

API latency, Consumer processing latency, detection latency, DLQ count, Redis degraded count를 수집합니다.

### 완료 기준

- `/actuator/prometheus` 노출
- custom metric 확인
- Grafana dashboard 초안 생성

## Phase 8. Load and Failure Test

### 목표

정상 부하, 피크 부하, Consumer 장애, Redis 장애, hot partition을 재현합니다.

### 완료 기준

- p50/p95/p99 기록
- Consumer Lag 최대값 기록
- Lag 회복 시간 기록
- DLQ count 기록
- Redis degraded count 기록

## Phase 9. Result Documentation

### 목표

초기 설계와 실제 구현 차이를 문서화합니다.

### 완료 기준

- troubleshooting-log 업데이트
- load-test 결과 표 작성
- failure-scenario 결과 작성
- README 정리

## Minimum Verification Gate

다음 PR부터 최소 검증 기준으로 사용합니다.

- `./gradlew test`
- `docker compose -f infra/docker-compose.yml config`
- `bash -n scripts/*.sh`
- markdown link check

## Phase 10+. Hardening

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
