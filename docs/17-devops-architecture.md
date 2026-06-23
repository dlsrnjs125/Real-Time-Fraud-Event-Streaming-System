# DevOps Architecture

## 1. 목표

이 문서는 로컬 Docker Compose 기반 인프라와 운영 환경으로 확장할 때 달라지는 지점을 정리합니다.

핵심은 단순 실행이 아니라, healthcheck, readiness, metric, 장애 재현이 가능한 구조를 만드는 것입니다.

## 2. 로컬 구성

```text
app-api      -> Kafka transaction-events publish
app-consumer -> Kafka consume, Redis lookup, PostgreSQL write

Kafka
Kafka UI
PostgreSQL
Redis
Prometheus
Grafana
```

현재 `app-api`와 `app-consumer`는 host에서 `bootRun`으로 실행하고, 인프라는 Docker Compose로 실행합니다.

## 3. 서비스 역할

| Service | 역할 | Local Port |
|---|---|---:|
| Kafka | 이벤트 브로커 | 9092 |
| Kafka UI | topic, consumer group 확인 | 8088 |
| PostgreSQL | 탐지 결과, 처리 로그, DLQ metadata 저장 | 5432 |
| Redis | 사용자별 short-lived detection state | 6379 |
| Prometheus | metric scrape | 9090 |
| Grafana | dashboard | 3000 |
| app-api | 거래 이벤트 접수 API | 8080 |
| app-consumer | Kafka Consumer Worker | 8081 |

## 4. Healthcheck와 Readiness

초기 readiness 기준:

- app-api `/actuator/health`
- app-consumer `/actuator/health`
- Kafka topic 존재 여부
- PostgreSQL connection 가능 여부
- Redis connection 가능 여부
- Prometheus target UP
- Grafana dashboard provisioning 확인

Docker Compose healthcheck:

- Kafka: `kafka-topics.sh --list`
- PostgreSQL: `pg_isready`
- Redis: `redis-cli ping`

## 5. 서비스 시작 순서

1. Kafka, PostgreSQL, Redis 시작
2. Kafka topic 생성
3. Prometheus, Grafana 시작
4. app-api 시작
5. app-consumer 시작
6. smoke test 실행

`scripts/reset-local-env.sh`는 로컬 인프라 재생성을 위한 도구이며, 운영 환경에서는 사용하지 않습니다.

## 6. 네트워크와 Listener

현재 Kafka listener는 host 실행과 Docker 내부 접근을 모두 검증할 수 있도록 분리합니다.

- host에서 실행하는 `app-api`, `app-consumer`: `localhost:9092`
- Docker 내부에서 실행하는 Kafka UI: `kafka:29092`

추후 `app-api`와 `app-consumer`를 Docker Compose 서비스로 포함하면 Spring Boot 애플리케이션은 내부 listener(`kafka:29092`)를 사용하도록 profile을 분리합니다.

## 7. 볼륨 정책

초기 로컬 환경에서는 빠른 재현을 위해 `docker compose down -v`로 데이터를 지울 수 있습니다.

운영 환경 가정에서는 다음 데이터를 보존 대상으로 봅니다.

- PostgreSQL fraud_detection_results
- PostgreSQL event_processing_logs
- DLQ metadata
- reprocessing audit history if added later
- Grafana dashboard provisioning

Kafka topic retention과 PostgreSQL retention은 별도로 관리합니다.

## 8. 로컬과 운영 가정 차이

| 항목 | Local | 운영 가정 |
|---|---|---|
| Kafka listener | PLAINTEXT localhost | internal/external listener 분리, ACL 검토 |
| PostgreSQL 계정 | fraud/fraud | secret 관리 |
| Redis | no-auth | auth/TLS/network 제한 |
| Grafana | admin/admin | 기본 계정 변경, 권한 분리 |
| Actuator | health/info/prometheus 노출 | internal network 제한 |
| DLQ payload | local/admin 조회 | masking, audit, 접근권한 필수 |

## 9. Dashboard 전략

Dashboard는 다음 4개로 분리합니다.

- API Dashboard
- Consumer Dashboard
- Redis/PostgreSQL Dashboard
- Fraud Detection Dashboard

중요한 것은 탐지 건수 자체보다 어떤 rule이 얼마나 매칭되었고, 장애 상황에서 어떤 rule이 skipped 되었는지를 관측하는 것입니다.

## 10. CI Gate 초안

초기 CI는 배포 자동화보다 변경사항이 로컬 실행 가능성을 깨뜨리지 않는지 검증하는 데 집중합니다.

최소 gate:

- Gradle build/test
- markdown link check
- docker compose config
- shell script syntax check
- secret scan
- dependency vulnerability check

Kafka, Redis, PostgreSQL을 포함한 통합 테스트는 Phase가 진행되면서 Testcontainers 또는 Docker Compose 기반 검증으로 확장합니다.

## 11. Local Makefile

Phase 2부터 루트 `Makefile`로 반복 검증 명령을 제공합니다.

주요 target:

- `make build`: 전체 Gradle clean build
- `make test`: 전체 test
- `make test-common`: `app-common` test
- `make test-api`: `app-api` test
- `make test-consumer`: `app-consumer` test
- `make infra-config`: Docker Compose config 검증
- `make scripts-check`: shell script syntax check
- `make final-check`: build, Docker Compose config, script syntax check

Makefile은 macOS/Linux 로컬 개발 기준입니다. Windows shell 대응은 초기 범위에서 제외합니다.
