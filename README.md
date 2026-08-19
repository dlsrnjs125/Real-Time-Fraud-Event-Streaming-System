# High-Throughput Fraud Stream Processing System

Kafka 기반 금융 거래 스트림에서 대량·Burst·Hot-Key·Late Event 상황을 재현하고, Redis에 사용자별 최근 상태를 유지하면서 처리량, 지연, Partition 확장성, Event Freshness를 측정하는 Stateful Stream Processing 시스템입니다.

## 해결하려는 문제

금융 거래 이벤트는 일정한 속도로 도착하지 않습니다. 신규 거래가 순간적으로 증가할 수 있고, 특정 사용자에게 트래픽이 집중될 수 있으며, 외부 시스템에 누적된 오래된 이벤트가 한꺼번에 도착할 수도 있습니다.

이 프로젝트는 다음 세 축을 중심으로 다룹니다.

- Throughput: 지속 처리 가능한 EPS, Consumer Lag 증가와 회복, Partition별 처리량
- Stateful Processing: Redis Sliding Window의 사용자별 상태 비용과 정확성
- Event Freshness: event/source/ingress/Kafka/Consumer 경계별 지연과 Late Event

PostgreSQL idempotency, manual ack, Retry/DLT, degraded mode는 안전장치로 유지하지만 V3의 중심 주제는 아닙니다. 실제 금융 원장, 정산, 계좌 승인, DR 시스템은 구현 범위가 아닙니다.

## 선택한 아키텍처

Spring Boot Modular Monolith + Kafka Event-Driven Worker 구조를 선택합니다.

- `app-api`: 거래 이벤트 접수, Kafka publish, 운영 조회 API, Actuator
- `app-consumer`: Kafka Consumer, Redis 사용자 상태, Rule Engine, PostgreSQL result sink
- `app-common`: 공통 이벤트 스키마, 공통 응답/예외, traceId/eventId 전파 유틸
- `infra`: Kafka, PostgreSQL, Redis, Prometheus, Grafana
- `load-test`: HTTP intake workload와 장애 시나리오
- `scripts/data`: PaySim preprocessing, V3 corpus profiling, replay/evaluation 도구

API intake와 Kafka Consumer를 분리하고, Kafka를 burst buffer와 partition-based parallelism 경계로 사용합니다. 기본 partition key는 `userId`이며, 이 선택으로 얻는 사용자별 순서와 hot partition 위험을 함께 측정합니다.

## 설계 원칙

- Dataset volume과 runtime event velocity를 분리합니다.
- 평균 처리량뿐 아니라 total/partition Lag과 backlog recovery를 측정합니다.
- Event Time과 arrival/processing time을 분리해 지연 경계를 추적합니다.
- PostgreSQL은 탐지 결과와 감사 로그의 기준 저장소로 사용합니다.
- Redis는 사용자별 최근 거래 상태를 위한 online state store로 사용합니다.
- 동일 workload와 환경 fingerprint로 개선 전후를 비교합니다.

측정하지 않은 처리량이나 장애 원인을 단정하지 않습니다. Local Docker 환경의 workload, duration, achieved EPS, Lag, p95/p99, resource 조건을 함께 기록한 경우에만 해당 범위의 결과로 사용합니다.

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
make k6-v3-baseline # requires running local infrastructure and applications
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

- [Documentation Index](docs/00-index.md)
- [Domain Problem](docs/01-domain-problem.md)
- [Architecture Decision](docs/02-architecture-decision.md)
- [Development Roadmap](docs/13-development-roadmap.md)
- [Evidence Index](docs/20-evidence-index.md)
- [Troubleshooting Index](docs/21-troubleshooting-index.md)
- [V2 Final Readiness](docs/34-v2-final-readiness.md)
- [V3 High-Throughput Stream Direction](docs/41-v3-high-throughput-stream-processing-direction.md)
- [V3 Dataset, Workload, and Time Contract](docs/42-v3-dataset-workload-time-contract.md)
- [V3 Phase 0 Foundation Plan](docs/43-v3-phase0-foundation-plan.md)
- [V3 Phase 0 Foundation Evidence](docs/44-v3-phase0-foundation-evidence.md)
- [PaySim Data Scripts](scripts/data/README.md)
- [Blog Series Plan](blog/README.md)

## 현재 구현 범위

이 프로젝트는 대량 거래 이벤트를 API에서 접수한 뒤 Kafka를 통해 Consumer로 전달하고, Consumer가 Redis 기반 최근 거래 패턴과 Rule Engine을 이용해 이상거래 결과를 계산한 뒤 PostgreSQL에 저장하는 구조까지 구현했습니다.

현재 구현에는 다음 안전장치와 실험 기반이 있습니다.

- Kafka Consumer manual ack와 processing log 기반 처리 추적
- PostgreSQL unique constraint 기반 idempotency 보장
- Redis sliding window rule과 Redis 장애 시 degraded/skipped rule 기록
- DLT 격리, 재처리, 폐기, audit log, max reprocess attempts 정책
- Consumer Lag, processing latency, degraded count, DLT count 중심의 기존 관측 기준
- k6 기반 normal/peak/duplicate/Redis down 부하·장애 테스트 기준
- `make final-check` 기반 repository readiness guardrail

V2에서는 운영 데이터가 아닌 PaySim 기반 synthetic transaction data를 사용해 replay/evaluation 흐름을 추가했습니다. 목적은 실제 금융 fraud model 성능을 주장하는 것이 아니라, rule baseline 변경 시 동일한 입력 데이터와 동일한 evaluation contract로 결과를 비교할 수 있게 만드는 것입니다.

V2 범위에는 다음 내용이 포함됩니다.

- raw/full PaySim data를 저장소에 커밋하지 않는 data provenance 정책
- HMAC 기반 identifier hashing과 salt policy
- fixture/sample 기반 CI-safe preprocessing, replay, evaluation 검증
- full PaySim replay/evaluation은 local/manual evidence로 분리
- denominator, missing result, unsupported type, rejected row를 분리한 evaluation report
- Java Rule Engine과 Python evaluator 사이의 ruleVersion drift 검증
- active runtime ruleVersion, stored result ruleVersion, evaluator expected ruleVersion 분리

구현된 기능, local/manual 검증, future work의 구분은 [V2 Final Readiness](docs/34-v2-final-readiness.md)에 정리했습니다.

## V3 High-Throughput Stream Processing

V3는 Throughput, Stateful Processing, Event Freshness를 핵심 축으로 둡니다. PaySim Dataset 자체의 크기와 runtime workload 속도를 분리하고, Normal/Organic Burst/Catch-up Burst/Skew/Late/Replay를 서로 다른 실험으로 관리합니다.

V3 Phase 0의 dataset profile, versioned workload manifest, stream-stage metrics, timestamp decision, Grafana dashboard, low-rate baseline evidence를 완료했습니다. 상세 구현과 측정 한계는 [V3 Phase 0 Foundation Evidence](docs/44-v3-phase0-foundation-evidence.md)를 기준으로 하며, 다음 단계는 동일한 계약을 사용해 sustainable EPS와 backlog recovery를 측정하는 V3 Phase 1입니다.
