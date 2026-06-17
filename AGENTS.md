# AGENTS.md

## 1. Project Identity

This repository is the **Real-Time Fraud Event Streaming System**.

It handles high-volume financial transaction events through a Spring Boot + Kafka event-driven architecture.

The core goal is not fast feature delivery or simple CRUD implementation, but proving:

* real-time financial event ingestion
* Kafka-based asynchronous processing
* user-based event ordering
* fraud detection latency measurement
* Consumer Lag observation
* retry and Dead Letter Topic handling
* safe reprocessing
* Redis-based sliding window detection
* degraded behavior under Redis or Consumer failure
* reproducible verification under load and failure scenarios

This project does **not** implement a real core banking ledger or account settlement system.

The main domain problem is:

> Financial transaction events can arrive at high volume. Fraud detection must process those events quickly and reliably, while making processing delay, Consumer Lag, failure handling, and reprocessing behavior observable and reproducible.

Kafka is the event streaming backbone.

PostgreSQL stores durable processing results, fraud detection results, audit logs, DLQ metadata, and reprocessing history.

Redis is a short-lived state store for real-time detection rules such as user velocity windows. Redis must not be treated as the final consistency authority.

Every implementation should support the goals of measurable event processing, safe retry/reprocessing, clear failure behavior, and reproducible load/failure verification.

---

## 2. Current Repository Context

Expected repository structure:

```text
app-api/
  Spring Boot API server.
  Handles transaction event intake, admin APIs, DLQ reprocessing APIs, OpenAPI, Actuator.

app-consumer/
  Spring Boot Kafka Consumer worker.
  Handles transaction event consumption, fraud rule execution, Redis sliding window lookup, PostgreSQL result persistence, Retry/DLT handling, Consumer metrics.

app-common/
  Shared event schemas, common DTOs, enums, exceptions, logging keys, trace/event context utilities.

docs/
  Design documents, architecture decisions, Kafka topic design, data model, API contract, Redis design, consistency/reprocessing policy, observability design, load-test plan, failure scenarios, troubleshooting logs, AI review notes, development roadmap, security/privacy rules, SLO/readiness, fraud detection strategy, DevOps architecture, and runbooks.

infra/
  Local infrastructure configuration.
  Kafka, Kafka UI, PostgreSQL, Redis, Prometheus, Grafana, and optional Nginx.

load-test/
  k6 load-test scenarios.

scripts/
  Local development and operation helper scripts.

README.md
  Project overview, execution guide, architecture summary, documentation index, current implementation status.

build.gradle / settings.gradle
  Gradle multi-module configuration.

.github/workflows/
  CI pipelines if present.
```

---

## 3. Document Reading Priority

Do not read every document by default.

Read only what is needed for the current task.

Always start with:

```text
README.md
docs/13-development-roadmap.md
docs/02-architecture-decision.md
```

Then read only the documents directly related to the current task.

### Phase-specific reading guide

#### Planning / Architecture

```text
docs/01-domain-problem.md
docs/02-architecture-decision.md
docs/03-kafka-topic-design.md
docs/13-development-roadmap.md
```

#### Kafka Topic / Producer / Consumer

```text
docs/03-kafka-topic-design.md
docs/07-consistency-and-reprocessing.md
docs/08-observability.md
```

#### Event Schema

```text
docs/03-kafka-topic-design.md
docs/04-data-model.md
docs/05-api-design.md
app-common/
```

#### API Server

```text
docs/05-api-design.md
docs/03-kafka-topic-design.md
app-api/
app-common/
```

#### Consumer Worker

```text
docs/03-kafka-topic-design.md
docs/07-consistency-and-reprocessing.md
docs/08-observability.md
docs/16-fraud-detection-strategy.md
app-consumer/
app-common/
```

#### Fraud Detection Strategy

```text
docs/16-fraud-detection-strategy.md
docs/06-redis-sliding-window.md
docs/08-observability.md
app-consumer/
app-common/
```

#### Redis Sliding Window

```text
docs/06-redis-sliding-window.md
docs/07-consistency-and-reprocessing.md
app-consumer/
```

#### Retry / DLT / Reprocessing

```text
docs/07-consistency-and-reprocessing.md
docs/10-failure-scenarios.md
docs/05-api-design.md
```

#### Data Model / Migration

```text
docs/04-data-model.md
docs/07-consistency-and-reprocessing.md
app-consumer/
app-api/
```

#### Observability

```text
docs/08-observability.md
docs/15-slo-and-operational-readiness.md
infra/prometheus/
infra/grafana/
app-api/
app-consumer/
```

#### Security / Privacy

```text
docs/14-security-and-privacy.md
docs/07-consistency-and-reprocessing.md
docs/10-failure-scenarios.md
```

#### DevOps / Runbook

```text
docs/15-slo-and-operational-readiness.md
docs/17-devops-architecture.md
docs/18-runbook.md
infra/
scripts/
```

#### Load Test

```text
docs/09-load-test-plan.md
docs/08-observability.md
load-test/k6/
```

#### Failure Scenarios

```text
docs/10-failure-scenarios.md
docs/07-consistency-and-reprocessing.md
docs/08-observability.md
```

#### Troubleshooting

```text
docs/11-troubleshooting-log.md
docs directly related to the issue
```

#### Review

```text
docs/12-review.md
Target files generated or modified with AI/Codex assistance
```

### Token-saving rules

* Do not read the entire `docs/` directory unless the task explicitly asks for broad documentation review.
* Do not read the entire repository before editing.
* Identify the relevant phase first.
* Read only the phase-specific documents and directly related files.
* Do not read all load-test files unless the task is about load testing.
* Do not read all infrastructure files unless the task is about local infra, observability, or deployment.
* If information is already captured in this file, do not repeat long explanations.
* Before editing code, inspect only the directly related module and shared dependencies.
* Avoid broad arbitrary refactoring.

---

## 4. Phase Boundary Rules

Most important rule:

> Do not implement features outside the requested Phase.

### Phase 0 - Initial Planning and Scaffold

Scope:

* README
* docs
* Gradle multi-module skeleton
* app-api skeleton
* app-consumer skeleton
* app-common skeleton
* Docker Compose skeleton
* Prometheus/Grafana skeleton
* scripts skeleton

Do not implement real Kafka business flow yet.

### Phase 1 - Local Infrastructure Validation

Scope:

* Kafka
* Kafka UI
* PostgreSQL
* Redis
* Prometheus
* Grafana
* local health checks
* topic creation scripts
* basic smoke scripts

Do not implement fraud detection rules yet.

### Phase 2 - Event Schema and API Producer

Scope:

* TransactionEventMessage
* schemaVersion
* eventTime / receivedAt / traceId
* transaction event intake API
* request validation
* Kafka producer
* userId as Kafka key
* producer metric foundation

Do not implement Consumer processing logic beyond what is required for smoke testing.

### Phase 3 - Consumer Processing Foundation

Scope:

* Kafka listener
* manual ack
* enable-auto-commit=false
* EventProcessingLog
* PostgreSQL persistence
* duplicate event handling foundation
* basic retry/DLT wiring if needed

Do not implement full fraud rule engine yet.

### Phase 4 - Fraud Rule Engine

Scope:

* AmountRule
* VelocityRule
* NewDeviceRule
* RiskScoreService
* FraudResult
* rule result codes
* skipped rule tracking
* degraded flag foundation

Do not implement advanced notification service.

### Phase 5 - Redis Sliding Window

Scope:

* Redis ZSET-based user velocity window
* eventTime-based sliding window
* stale event cleanup
* Redis command latency metric
* Redis unavailable behavior
* degraded mode when Redis is down

Redis must remain a short-lived detection state store.

Do not store final fraud result only in Redis.

### Phase 6 - Retry, DLT, and Reprocessing

Scope:

* retry topic handling
* DLT handling
* DLQ metadata persistence
* admin DLQ query API
* reprocess API
* discard API
* reprocessing history
* idempotent reprocessing

Do not allow DLQ reprocessing to create duplicate FraudResult rows.

### Phase 7 - Observability

Scope:

* Spring Boot Actuator
* Micrometer metrics
* Prometheus scrape
* Grafana dashboard
* API latency
* Kafka publish result
* Consumer processing latency
* fraud detection latency
* Consumer Lag
* DLQ count
* Redis degraded count
* structured logging
* traceId/eventId propagation

Do not add unrelated infrastructure complexity.

### Phase 8 - Load and Failure Tests

Scope:

* k6 normal load
* k6 peak load
* duplicate event test if relevant
* consumer stop/start test
* Redis down test
* hot partition test
* Consumer Lag recovery measurement
* p50/p95/p99 measurement
* error-rate measurement
* DLQ count measurement

### Phase 9 - Result Documentation and Hardening

Scope:

* benchmark result docs
* failure scenario evidence
* troubleshooting log updates
* README update
* roadmap status update
* AI review update
* known limitations

### Phase 10+

Scope:

* CI/CD gates
* deployment safety
* security/privacy hardening
* SLO dashboard and alert hardening
* DevOps architecture validation
* runbook updates
* Nginx reverse proxy
* optional blue-green simulation
* optional blog drafts
* optional portfolio documentation

Do not add Kubernetes unless explicitly requested.

---

## 5. Non-Negotiable Domain Rules

The same `eventId` must not create duplicate `FraudResult` rows.

The same Kafka message may be consumed more than once. Consumers must be idempotent.

Kafka delivery is not the same as business-level exactly-once processing.

PostgreSQL unique constraints are the final duplicate-defense layer for persisted processing results.

Redis must never be the only correctness mechanism.

Redis is used for short-lived detection state such as user velocity windows.

If Redis is unavailable, the system should enter degraded mode instead of silently pretending all rules were executed.

A degraded fraud result must record which rules were skipped.

A Consumer crash must not cause silent event loss.

Offset commit should happen only after required processing and persistence succeed.

DLT events must be visible and reprocessable through controlled operational flow.

DLT reprocessing must preserve the original `eventId`.

DLT reprocessing must not create duplicate fraud results.

Kafka partition key should be `userId` by default because fraud detection depends on user-based recent event order.

Changing the partition key requires documentation and test/measurement updates.

Do not treat API latency alone as system health.

For this system, Consumer Lag, detection latency, DLQ count, duplicate result count, and missing event count are core health signals.

---

## 6. Architecture Rules

### Selected architecture

Use:

```text
Spring Boot Modular Monolith
+
Kafka Event-Driven Worker
```

Execution units:

```text
app-api
app-consumer
```

Shared module:

```text
app-common
```

### app-api responsibilities

Allowed:

* HTTP request/response handling
* transaction event intake
* request validation
* Kafka event publishing
* admin read APIs if defined in the current Phase
* DLQ reprocess command API if defined in the current Phase
* Actuator endpoint
* OpenAPI/Swagger
* HTTP status mapping

Not allowed:

* Fraud rule execution
* Redis velocity calculation
* Consumer offset management
* Direct Consumer processing logic
* Business logic inside Controller

### app-consumer responsibilities

Allowed:

* Kafka message consumption
* manual offset acknowledgement
* fraud rule execution
* Redis sliding window access
* FraudResult persistence
* EventProcessingLog persistence
* Retry/DLT handling
* DLQ metadata persistence
* Consumer metrics
* degraded mode handling

Not allowed:

* HTTP request handling unless explicitly required
* API request validation logic
* unrelated admin UI logic
* treating Redis as durable truth

### app-common responsibilities

Allowed:

* shared event schemas
* shared enums
* shared exception base types if needed
* shared logging field names
* traceId/eventId utilities
* common validation constants

Not allowed:

* module-specific business services
* persistence repositories
* Spring Web controllers
* Kafka listener implementation
* Redis-specific logic

---

## 7. Layering Rules

### API layer

Responsibilities:

* receive request
* validate request DTO
* call application service
* return response
* map exceptions to HTTP response

Do not put business logic in controllers.

### Application service layer

Responsibilities:

* use case orchestration
* Kafka publish command
* fraud detection orchestration
* reprocessing orchestration
* transaction boundary if persistence is used

### Domain layer

Responsibilities:

* rule interfaces
* risk score calculation
* risk level decision
* pure logic
* domain exceptions
* enums

### Infrastructure layer

Responsibilities:

* Kafka producer
* Kafka consumer
* Redis access
* JPA repository
* external system adapters
* metrics adapters

### Persistence layer

Responsibilities:

* JPA entities
* Spring Data repositories
* DB constraints
* indexes
* migrations

Repositories must not contain business decisions.

---

## 8. Kafka Rules

### Topic rules

Initial topics:

```text
transaction-events
fraud-risk-events
fraud-alert-events
transaction-events.retry
transaction-events.dlt
```

Any new topic must document:

* purpose
* producer
* consumer
* key
* value schema
* retention
* retry/DLT behavior
* owner module

### Partition key

Default key:

```text
userId
```

Reason:

* fraud detection depends on user-level recent event order
* Kafka preserves order only within a partition
* using userId keeps events from the same user in the same partition

Trade-off:

* userId key may create hot partitions
* hot partition behavior must be measured under targeted load tests

Do not change the partition key without updating:

```text
docs/03-kafka-topic-design.md
docs/09-load-test-plan.md
docs/11-troubleshooting-log.md
related tests
```

### Offset commit

Default policy:

```text
enable-auto-commit=false
manual ack after successful processing
```

Offset should be acknowledged only after:

* event validation succeeds
* fraud result persistence succeeds if applicable
* processing log persistence succeeds if applicable
* required downstream publish succeeds if applicable

If processing fails:

* retry if failure is transient
* send to DLT if failure is permanent or retry exhausted
* do not commit offsets in a way that hides unprocessed events

### Retry/DLT

Retry is for transient failures:

* temporary DB connection failure
* temporary Redis timeout
* temporary Kafka publish failure

DLT is for:

* invalid payload
* unsupported schemaVersion
* exhausted retry
* unrecoverable processing error

DLT events must be queryable.

DLT events must have an explicit reprocessing or discard flow.

---

## 9. Redis Rules

Redis is used for short-lived fraud detection state.

Primary use case:

```text
user velocity sliding window
```

Preferred structure:

```text
ZSET fraud:velocity:{userId}
score = eventTime epoch millis
value = eventId
```

Sliding window behavior:

* add current event
* remove events older than the configured window
* count remaining events
* compare with threshold

Do not use simple `INCR + TTL` for velocity rules unless the limitation is explicitly documented.

Reason:

* fixed-window counters can produce boundary inaccuracies
* sliding window is easier to reason about for recent event detection

Redis failure behavior:

* do not fail the whole event silently
* enter degraded mode
* execute rules that do not depend on Redis
* mark Redis-dependent rules as skipped
* persist degraded result information
* increment degraded metrics

Redis must not be the final store for fraud results.

---

## 10. PostgreSQL Rules

PostgreSQL stores durable operational and audit data.

Expected stored data:

* transaction event intake record if implemented
* fraud detection result
* fraud rule configuration if implemented
* event processing log
* DLQ event metadata
* reprocessing history

Required constraints when corresponding tables exist:

```text
fraud_results.event_id unique
event_processing_logs(topic, partition_no, offset_no) unique
dlq_events.event_id unique where appropriate
```

Money values must not use floating point.

Use:

```text
BigDecimal
```

or integer minor units if the policy is defined.

For KRW-only scenarios, integer won units are acceptable if documented.

Do not store only in Kafka when operational query or audit is required.

Do not store only in PostgreSQL when event replay or downstream event processing is required.

---

## 11. Event Schema Rules

Every Kafka event schema should include:

```text
schemaVersion
eventId
userId
eventTime
receivedAt when produced by API
traceId
```

For transaction events, expected fields include:

```text
eventId
userId
accountId
eventType
amount
currency
merchantId
deviceId
location
eventTime
receivedAt
traceId
schemaVersion
```

For fraud risk events, expected fields include:

```text
eventId
userId
riskLevel
riskScore
matchedRuleCodes
skippedRuleCodes
degraded
detectedAt
traceId
schemaVersion
```

Time field meaning:

```text
eventTime: when the financial event occurred
receivedAt: when app-api accepted the event
detectedAt: when app-consumer completed fraud detection
```

Latency calculations:

```text
ingestDelay = receivedAt - eventTime
detectionLatency = detectedAt - receivedAt
endToEndLatency = detectedAt - eventTime
```

Unsupported schema versions should be rejected or sent to DLT according to the current Phase design.

---

## 12. Observability Rules

This system must observe both synchronous API behavior and asynchronous processing behavior.

API metrics:

* request count
* API latency p50/p95/p99
* API error rate
* Kafka publish success/failure count

Consumer metrics:

* consumed event count
* consumer processing latency
* fraud detection latency
* Consumer Lag
* retry count
* DLT count
* duplicate event skip count
* Redis degraded count
* rule execution count
* rule skipped count

Kafka-related metrics:

* topic message rate
* partition lag
* consumer group lag
* rebalance count if available

Redis metrics:

* command latency
* failure count
* degraded mode count
* sliding window size

PostgreSQL metrics:

* insert latency
* query latency
* connection pool usage
* constraint violation count

Logging rules:

* use structured logs
* include traceId
* include eventId
* include topic/partition/offset in Consumer logs
* do not log full accountId, deviceId, or sensitive identifiers
* do not log raw request payloads if they contain sensitive fields
* mask or hash identifiers where appropriate

---

## 13. Security and Privacy Rules

Never commit secrets.

Never hardcode production credentials.

Use local-only default credentials only in Docker Compose examples.

Mask or avoid logging:

* accountId
* deviceId
* user identifiers if they represent real users
* IP address
* raw payloads
* authentication tokens

Authentication and authorization are not part of early phases unless explicitly requested.

If admin APIs are added before full auth, document that they are local/development-only.

Do not claim production-grade security before implementing it.

---

## 14. Testing Rules

After code changes, run as much of the following as practical.

### Basic build

```bash
./gradlew build
```

### Module-specific tests

```bash
./gradlew :app-common:test
./gradlew :app-api:test
./gradlew :app-consumer:test
```

### Local infrastructure validation

```bash
docker compose -f infra/docker-compose.yml config
```

### Script validation

```bash
bash -n scripts/create-topics.sh
bash -n scripts/reset-local-env.sh
bash -n scripts/run-smoke-test.sh
bash -n scripts/wait-for-kafka.sh
```

### Suggested local smoke checks

```bash
docker compose -f infra/docker-compose.yml up -d
./scripts/create-topics.sh
./gradlew :app-api:bootRun
./gradlew :app-consumer:bootRun
```

Testing expectations:

* Unit tests should cover pure rule logic.
* Integration tests should cover Kafka producer/consumer behavior when practical.
* Repository tests should verify unique constraints.
* Consumer tests should verify manual ack behavior when practical.
* Reprocessing tests should prove duplicate FraudResult is not created.
* Redis tests should verify sliding window behavior.
* Failure tests should be reproducible.

Use Testcontainers when verifying Kafka/PostgreSQL/Redis behavior if the current Phase requires it.

---

## 15. Load Test Rules

k6 scenarios should be stored under:

```text
load-test/k6/
```

Recommended scenarios:

```text
normal-load.js
peak-load.js
consumer-lag-test.js
redis-down-test.js
hot-partition-test.js
```

Load tests should measure:

* p50/p95/p99 API latency
* Kafka publish success rate
* event ingestion throughput
* Consumer Lag max
* Consumer Lag recovery time
* fraud detection latency
* error rate
* DLT count
* Redis degraded count
* duplicate result count

Do not report performance results without:

* test scenario
* VU count
* duration
* event count
* hardware/local environment notes
* measured p50/p95/p99
* observed bottleneck
* known limitations

---

## 16. Failure Scenario Rules

Failure scenarios must be reproducible.

Minimum target scenarios:

```text
Kafka unavailable
Consumer stopped
Consumer restarted after lag accumulation
Redis unavailable
PostgreSQL connection failure
invalid payload sent to DLT
DLT reprocessing duplicate prevention
hot partition caused by concentrated userId
```

For each failure scenario, document:

* failure situation
* expected cause
* user/system impact
* detection method
* response method
* recovery result
* metric evidence
* remaining limitation

Failure scenario docs should be updated in:

```text
docs/10-failure-scenarios.md
docs/11-troubleshooting-log.md
```

---

## 17. Development Roadmap Rules

Keep roadmap status aligned with actual implementation.

The main roadmap document is:

```text
docs/13-development-roadmap.md
```

When a Phase is completed, update:

* implemented behavior
* commands run
* test result
* remaining TODOs
* known limitations

Do not mark a Phase as complete if:

* required tests were not run
* docs do not match implementation
* major TODOs are still required for that Phase
* feature behavior is only documented but not implemented

---

## 18. Documentation Rules

If behavior changes, update relevant docs in the same task.

Do not claim a feature is implemented before it exists.

Keep these aligned:

```text
README.md
docs/13-development-roadmap.md
docs/03-kafka-topic-design.md
docs/04-data-model.md
docs/05-api-design.md
docs/06-redis-sliding-window.md
docs/07-consistency-and-reprocessing.md
docs/08-observability.md
docs/09-load-test-plan.md
docs/10-failure-scenarios.md
docs/11-troubleshooting-log.md
docs/12-review.md
docs/14-security-and-privacy.md
docs/15-slo-and-operational-readiness.md
docs/16-fraud-detection-strategy.md
docs/17-devops-architecture.md
docs/18-runbook.md
```

Documentation tone:

* focus on problem definition
* focus on technical decisions
* focus on trade-offs
* focus on measured results
* focus on reproducible verification

Avoid phrases such as:

```text
portfolio
interviewer
appeal
resume
for hiring
```

The documentation should read like engineering notes, design decisions, and verification records.

---

## 19. Review Rules

AI may be used for:

* initial code skeleton
* Spring Kafka configuration draft
* DTO draft
* test skeleton
* k6 scenario draft
* documentation draft
* metrics candidate list

Human review is required for:

* partition key choice
* offset commit timing
* retry/DLT policy
* DLQ reprocessing idempotency
* Redis sliding window correctness
* PostgreSQL unique constraints
* sensitive logging
* failure scenario reproducibility
* performance result interpretation

Common AI-generated issues to check:

* auto commit enabled by default
* eventId used as partition key without considering user order
* Redis `INCR + TTL` used without documenting fixed-window limitation
* DLT exists but no reprocessing idempotency
* duplicate FraudResult possible after retry
* full accountId/deviceId logged
* API latency treated as the only performance metric
* no Consumer Lag measurement
* business logic placed in Controller
* broad folder restructuring without need
* unnecessary Kubernetes/MSA/Auth complexity added too early

Update:

```text
docs/12-review.md
```

when AI-generated code or design was accepted, rejected, or modified.

---

## 20. Anti-Pattern Rules

Avoid these anti-patterns:

### Architecture anti-patterns

* splitting into many microservices without a clear boundary
* adding Kubernetes before local event processing is stable
* adding API Gateway, Service Discovery, OAuth2 too early
* mixing API request handling and Consumer processing logic
* making app-common depend on app-api or app-consumer
* placing persistence logic in DTOs or controllers

### Kafka anti-patterns

* enabling auto commit without considering failure behavior
* committing offset before persistence succeeds
* using random partition key when user-level order matters
* treating Kafka as a database for admin queries
* no DLT for unrecoverable failures
* no reprocessing strategy for DLT
* retrying invalid payloads forever

### Redis anti-patterns

* treating Redis as final truth
* using Redis lock/cache as the only correctness mechanism
* ignoring Redis failure
* not recording degraded rule execution
* using fixed-window counters without documenting limitations

### Database anti-patterns

* no unique constraint on eventId-based results
* duplicate FraudResult allowed
* no processing log for topic/partition/offset
* using float/double for money
* no migration for schema changes

### Observability anti-patterns

* measuring only API latency
* no Consumer Lag metric
* no detection latency metric
* no DLQ count
* no traceId/eventId in logs
* logging sensitive identifiers in full

### Documentation anti-patterns

* documenting unimplemented features as complete
* writing broad promotional text instead of technical decisions
* not updating docs after design changes
* not recording failed assumptions or changed decisions

---

## 21. Coding Rules

Use Java 17.

Follow the existing code style.

Prefer clear package boundaries.

Use enums for domain strings when practical.

Prefer `OffsetDateTime` for event times.

Use `BigDecimal` for money unless a documented integer-money policy is chosen.

Do not make large arbitrary folder-structure changes.

Do not introduce unnecessary frameworks.

Do not add dependencies without a clear reason.

When changing an existing public interface, update tests and docs together.

Do not log sensitive identifiers in full.

Do not place business logic in Controllers.

Do not make repositories decide business rules.

Do not make Consumer code directly depend on API request DTOs.

Consumer should consume common event messages from `app-common`.

---

## 22. Pull Request Expectations

PR summaries should include:

* changed files or areas
* implemented behavior
* design decisions made or changed
* tests added/updated
* commands run and results
* documentation updated
* current Phase status
* TODOs for next Phase

Before finalizing a PR, verify:

* implementation does not exceed the requested Phase
* docs match actual implementation
* roadmap status is accurate
* tests pass or skipped tests are justified
* no sensitive values are logged
* no unnecessary infrastructure complexity was added
* event processing rules remain intact
* Consumer failure behavior is considered
* retry/DLT behavior is documented when touched
* Redis failure behavior is documented when touched

Suggested PR checklist:

```text
[ ] Scope stays within the requested Phase
[ ] README/docs updated if behavior changed
[ ] Roadmap updated
[ ] ./gradlew build passed or reason documented
[ ] docker compose config passed if infra changed
[ ] scripts validated if scripts changed
[ ] Tests added/updated
[ ] Sensitive logs checked
[ ] Kafka topic/key/offset behavior checked if Kafka code changed
[ ] Redis degraded behavior checked if Redis code changed
[ ] DLT/reprocessing idempotency checked if retry/DLT code changed
```

---

## 23. Current Project Defaults

Unless explicitly changed by a later design decision, use the following defaults:

```text
Architecture:
Spring Boot API Server + Spring Boot Kafka Consumer Worker

Modules:
app-api
app-consumer
app-common

Kafka partition key:
userId

Offset policy:
manual ack after successful processing

Redis detection structure:
ZSET sliding window

Durable result store:
PostgreSQL

Core API metric:
API p95/p99 latency

Core async metrics:
Consumer Lag
detection latency
DLT count
duplicate result count
Redis degraded count

Initial infrastructure:
Docker Compose

Initial deployment target:
local reproducible environment

Do not add by default:
Kubernetes
Service Discovery
API Gateway
OAuth2
full MSA service split
```
