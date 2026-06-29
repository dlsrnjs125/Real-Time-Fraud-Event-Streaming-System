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

## 로컬 실행 방법 초안

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

Local commands:

```bash
make build
make test
make infra-up
make api
make consumer
make data-env
make data-policy-check
make test-data-scripts
make final-check
```

OpenAPI contract: `http://localhost:8080/swagger-ui/index.html`

로컬 포트:

- Kafka: `localhost:9092`
- Kafka UI: `http://localhost:8088`
- app-api: `http://localhost:8080`
- app-consumer: `http://localhost:8081`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

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
- [12. Review](docs/12-review.md)
- [13. Development Roadmap](docs/13-development-roadmap.md)
- [14. Security and Privacy](docs/14-security-and-privacy.md)
- [15. SLO and Operational Readiness](docs/15-slo-and-operational-readiness.md)
- [16. Fraud Detection Strategy](docs/16-fraud-detection-strategy.md)
- [17. DevOps Architecture](docs/17-devops-architecture.md)
- [18. Runbook](docs/18-runbook.md)
- [19. Phase 10 Final Readiness](docs/19-phase-10-final-readiness.md)
- [20. Final Readiness Checklist](docs/19-final-readiness-checklist.md)
- [21. Evidence Index](docs/20-evidence-index.md)
- [22. Troubleshooting Index](docs/21-troubleshooting-index.md)
- [23. Load Test Results](docs/22-load-test-results.md)
- [24. Phase 13 Load Test Results](docs/23-load-test-results.md)
- [25. Kaggle PaySim Data Provenance](docs/24-kaggle-paysim-data-provenance.md)
- [26. PaySim Normalization Mapping](docs/25-paysim-normalization-mapping.md)
- [27. Fraud Rule V2 Strategy](docs/26-fraud-rule-v2-strategy.md)
- [28. Fraud Action Decision](docs/27-fraud-action-decision.md)
- [29. Fraud Case Management](docs/28-fraud-case-management.md)
- [30. V2 Result Evidence Plan](docs/29-v2-result-evidence.md)
- [31. V2 Visualization Plan](docs/30-v2-visualization.md)
- [V2 Replay Evaluation Evidence](docs/31-v2-replay-evaluation-evidence.md)
- [PaySim Data Scripts](scripts/data/README.md)
- [Blog Drafts](blog/README.md)

## 현재 구현 범위

현재는 Phase 14까지 완료된 상태입니다. Phase 14에서는 Admin API 최소 보호, DLT 재처리/폐기 audit log, max reprocess attempts 정책을 추가해 운영 조작의 보안성과 감사 가능성을 강화했습니다.

운영 관점에서 이 프로젝트는 Consumer manual ack, PostgreSQL unique constraint 기반 idempotency, Redis degraded mode, DLT 격리/재처리, metric tag cardinality 제한, failure drill 기반 검증을 핵심 판단 근거로 둡니다.

후속 V2 기획은 Kaggle PaySim synthetic 거래 데이터를 재현 가능한 방식으로 연동하고, Rule 기반 탐지 결과를 위험도별 action decision과 fraud case 관리 흐름으로 확장하는 방향으로 문서화되어 있습니다. V2 문서는 설계 기준이며, 실제 구현 완료 상태를 의미하지 않습니다.

V2 Phase 1에서는 PaySim 원본 CSV와 processed 전체 결과가 repository에 커밋되지 않도록 `data/` 디렉터리, `.gitignore`, `make data-policy-check` guardrail을 추가했습니다. V2 Phase 2에서는 optional KaggleHub download helper와 PaySim preprocessing normalization script를 추가했습니다. 상세 기준은 [Kaggle PaySim Data Provenance](docs/24-kaggle-paysim-data-provenance.md), [PaySim Normalization Mapping](docs/25-paysim-normalization-mapping.md), [PaySim Data Scripts](scripts/data/README.md)를 확인합니다.

V2 PaySim data workflow:

```bash
make data-env
make download-paysim
make prepare-paysim-smoke
make validate-paysim
make generate-paysim-sample
make test-data-scripts
```

V2 PaySim strict hash/salt checks:

```bash
make validate-paysim-strict
make generate-paysim-sample-strict
make test-data-scripts
```

V2 PaySim replay:

```bash
make replay-paysim-sample-dry-run
make replay-paysim-sample
```

Actual replay requires local app-api and infrastructure to be running. Detailed replay contract is documented in [PaySim Data Scripts](scripts/data/README.md) and [V2 Result Evidence Plan](docs/29-v2-result-evidence.md).

V2 PaySim replay evaluation:

```bash
make evaluate-paysim-replay
make verify-v2-phase7
make test-data-scripts
```

V2 Phase 7 turns the PaySim replay evaluation baseline into reproducible evidence. The result is a rule baseline/report contract check, not a production fraud performance guarantee.

Details: [PaySim Data Scripts](scripts/data/README.md), [V2 Result Evidence Plan](docs/29-v2-result-evidence.md), [V2 Replay Evaluation Evidence](docs/31-v2-replay-evaluation-evidence.md), [V2 Phase 7 Blog Draft](blog/25-v2-paysim-replay-evaluation-evidence.md).

Raw and full processed PaySim data are intentionally excluded from the repository.

Python dependencies for PaySim helpers are installed into `.venv-data`; the Java application runtime does not depend on this Python environment.

Detailed validation and sampling contracts are documented in [PaySim Normalization Mapping](docs/25-paysim-normalization-mapping.md) and [PaySim Data Scripts](scripts/data/README.md).
Detailed hash/salt policy is documented in [Kaggle PaySim Data Provenance](docs/24-kaggle-paysim-data-provenance.md), [PaySim Normalization Mapping](docs/25-paysim-normalization-mapping.md), and [PaySim Data Scripts](scripts/data/README.md).
