# Real-Time Fraud Event Streaming System

대량의 금융 거래 이벤트를 Kafka로 수집하고, Spring Boot Consumer가 사용자별 거래 패턴을 기반으로 이상거래를 탐지하며, 처리 지연, Consumer Lag, DLQ, 재처리 가능성을 측정하는 이벤트 기반 시스템입니다.

## 해결하려는 문제

금융 거래 이벤트는 짧은 시간에 대량으로 발생할 수 있습니다. 이상거래 탐지가 지연되면 위험 거래를 사후에 발견하게 되므로, 거래 이벤트를 비동기로 수집하고 사용자별 최근 거래 패턴을 기준으로 위험도를 계산하는 구조가 필요합니다.

이 프로젝트는 거래 저장 자체보다 다음 문제를 중심으로 다룹니다.

- 대량 거래 이벤트 유입
- 사용자별 거래 순서 보장
- 실시간 이상거래 탐지 지연
- Consumer 장애 시 이벤트 재처리
- Redis 장애 시 degraded mode
- DLQ 보관과 수동 재처리
- 처리 결과와 감사 로그 저장

## 선택한 아키텍처

Spring Boot Modular Monolith + Kafka Event-Driven Worker 구조를 선택합니다.

- `app-api`: 거래 이벤트 접수 API, 운영자 조회 API, DLQ 재처리 API, Actuator
- `app-consumer`: Kafka Consumer, Rule Engine, Redis 기반 최근 거래 패턴 계산, PostgreSQL 저장, Retry/DLT
- `app-common`: 공통 이벤트 스키마, 공통 응답/예외, traceId/eventId 전파 유틸
- `infra`: Kafka, PostgreSQL, Redis, Prometheus, Grafana
- `load-test`: k6 성능 테스트

API 서버와 Consumer Worker를 분리해 API latency와 Consumer processing latency를 따로 측정하고, Consumer 장애가 거래 이벤트 접수 기능에 직접 전파되지 않도록 합니다.

## 설계 원칙

- API 응답성과 이상거래 탐지 지연을 분리해서 측정합니다.
- Kafka는 이벤트 전달과 재처리 기반으로 사용합니다.
- PostgreSQL은 탐지 결과와 감사 로그의 기준 저장소로 사용합니다.
- Redis는 실시간 탐지를 위한 단기 상태 저장소로만 사용합니다.
- Consumer 장애, Redis 장애, DLQ 재처리를 처음부터 검증 대상으로 둡니다.

Kafka를 선택한 이유는 거래 이벤트가 지속적으로 대량 유입되고, 탐지·저장·알림·통계 처리를 서로 분리해야 하며, Consumer 장애 이후에도 이벤트 로그를 기준으로 재처리할 수 있어야 하기 때문입니다. 또한 Consumer Lag을 통해 비동기 탐지 지연을 관측하고, `userId` 기반 partition key로 사용자별 이벤트 순서를 유지하는 것을 핵심 설계 기준으로 둡니다.

## 기술 스택

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Kafka
- Spring Data JPA
- Spring Validation
- Spring Boot Actuator
- PostgreSQL
- Redis
- Apache Kafka
- Prometheus
- Grafana
- Docker Compose
- JUnit 5
- k6

## 로컬 실행 방법

```bash
docker compose -f infra/docker-compose.yml up -d
./scripts/wait-for-kafka.sh
./scripts/create-topics.sh
./gradlew clean build
./gradlew :app-api:bootRun
./gradlew :app-consumer:bootRun
```

Health check:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

Representative verification:

```bash
make final-check
```

`make final-check` validates repository readiness guardrails, not production fraud detection quality.

Detailed local, infrastructure, and PaySim commands are documented in the `Makefile` and [PaySim Data Scripts](scripts/data/README.md).

OpenAPI contract: `http://localhost:8080/swagger-ui/index.html`

로컬 포트:

- Kafka: `localhost:9092`
- Kafka UI: `http://localhost:8088`
- app-api: `http://localhost:8080`
- app-consumer: `http://localhost:8081`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## 문서

Start here:

- [Domain Problem](docs/01-domain-problem.md)
- [Architecture Decision](docs/02-architecture-decision.md)
- [Development Roadmap](docs/13-development-roadmap.md)
- [Evidence Index](docs/20-evidence-index.md)
- [Troubleshooting Index](docs/21-troubleshooting-index.md)
- [V2 Final Readiness](docs/34-v2-final-readiness.md)
- [PaySim Data Scripts](scripts/data/README.md)
- [Blog Drafts](blog/README.md)

## 현재 구현 범위

현재는 Phase 14까지 완료된 상태입니다. Phase 14에서는 Admin API 최소 보호, DLT 재처리/폐기 audit log, max reprocess attempts 정책을 추가해 운영 조작의 보안성과 감사 가능성을 강화했습니다.

운영 관점에서 이 프로젝트는 Consumer manual ack, PostgreSQL unique constraint 기반 idempotency, Redis degraded mode, DLT 격리/재처리, metric tag cardinality 제한, failure drill 기반 검증을 핵심 판단 근거로 둡니다.

V2 문서는 PaySim 기반 replay/evaluation evidence와 후속 확장 계획을 함께 다룹니다. 완료된 evidence contract, local/manual 검증, future work의 구분은 [V2 Final Readiness](docs/34-v2-final-readiness.md)에 정리합니다.

## V2 PaySim Evaluation

V2 extends the fraud detection pipeline with PaySim-based replay/evaluation workflows. The goal is not to claim production fraud model performance, but to make rule baseline evaluation reproducible with documented data, mapping, and threshold contracts.

Raw and full processed PaySim data are intentionally excluded from the repository.

Details:

- [PaySim Data Scripts](scripts/data/README.md)
- [V2 Final Readiness](docs/34-v2-final-readiness.md)
- [Evidence Index](docs/20-evidence-index.md)
- [Troubleshooting Index](docs/21-troubleshooting-index.md)
