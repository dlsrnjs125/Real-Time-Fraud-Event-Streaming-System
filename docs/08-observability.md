# Observability

## 1. 관측 목표

API가 빠르게 응답하더라도 Consumer Lag이 계속 증가하면 위험 거래 탐지가 늦어집니다. 따라서 API latency뿐 아니라 Consumer Lag, detection latency, DLQ count를 함께 봅니다.

## 2. 핵심 지표

접수 안정성:

- API error rate
- Kafka publish success rate
- API p50/p95/p99 latency

탐지 실시간성:

- Consumer Lag
- `consumer.processing.duration`
- `fraud.detection.duration`

처리 신뢰성:

- DLQ count
- duplicate result count
- missing event count
- Redis degraded count

## 3. Actuator와 Prometheus

`app-api`와 `app-consumer`는 Spring Boot Actuator와 Micrometer Prometheus registry를 통해 `/actuator/prometheus` endpoint를 노출합니다.

Prometheus는 두 애플리케이션과 Kafka 관련 metric을 scrape하고, Grafana dashboard에서 API/Consumer/Kafka/Redis 상태를 확인합니다.

## 4. 로그 기준

Structured logging 필드:

- `traceId`
- `eventId`
- `userId`
- `topic`
- `partition`
- `offset`
- `riskLevel`
- `degraded`
