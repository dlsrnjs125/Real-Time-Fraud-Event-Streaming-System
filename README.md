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

## 로컬 실행 방법 초안

```bash
docker compose -f infra/docker-compose.yml up -d
./gradlew build
./gradlew :app-api:bootRun
./gradlew :app-consumer:bootRun
```

Kafka topic 초안 생성:

```bash
./scripts/create-topics.sh
```

## 문서

- [01. Domain Problem](docs/01-domain-problem.md)
- [02. Architecture Decision](docs/02-architecture-decision.md)
- [03. Kafka Topic Design](docs/03-kafka-topic-design.md)
- [04. Data Model](docs/04-data-model.md)
- [05. API Design](docs/05-api-design.md)
- [06. Redis Sliding Window](docs/06-redis-sliding-window.md)
- [07. Consistency and Reprocessing](docs/07-consistency-and-reprocessing.md)
- [08. Observability](docs/08-observability.md)
- [09. Load Test Plan](docs/09-load-test-plan.md)
- [10. Failure Scenarios](docs/10-failure-scenarios.md)
- [11. Troubleshooting Log](docs/11-troubleshooting-log.md)
- [12. AI Review](docs/12-ai-review.md)
- [13. Development Roadmap](docs/13-development-roadmap.md)
- [14. Security and Privacy](docs/14-security-and-privacy.md)
- [15. SLO and Operational Readiness](docs/15-slo-and-operational-readiness.md)
- [16. Fraud Detection Strategy](docs/16-fraud-detection-strategy.md)
- [17. DevOps Architecture](docs/17-devops-architecture.md)
- [18. Runbook](docs/18-runbook.md)

## 현재 구현 범위

현재는 초기 기획과 설계, Gradle multi-module 골격, Spring Boot 애플리케이션 골격, Docker Compose 인프라 초안을 구성하는 단계입니다.
