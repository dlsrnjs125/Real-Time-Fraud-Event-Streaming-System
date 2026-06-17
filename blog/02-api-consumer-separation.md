# Spring Boot API와 Kafka Consumer를 분리한 이유

> Status: Draft
> 이 글은 구현과 측정 결과가 추가되면서 갱신됩니다.

## 문제

거래 이벤트 접수와 이상거래 탐지는 처리 특성이 다릅니다. 두 흐름을 동기 요청으로 묶으면 탐지 지연이 API 응답 지연으로 전파됩니다.

## 초기 설계

`app-api`는 거래 이벤트를 검증하고 Kafka에 발행합니다. `app-consumer`는 Kafka 이벤트를 소비해 탐지와 저장을 수행합니다.

## 구현

초기 스캐폴딩에서는 두 Spring Boot 애플리케이션을 별도 모듈로 둡니다.

Phase 1 실행 검증에서는 `app-api`와 `app-consumer`가 각각 별도 Spring Boot 실행 단위로 기동되고, 서로 다른 포트의 Actuator health endpoint로 상태를 확인할 수 있는지 먼저 검증합니다.

Phase 2에서는 실제 Kafka publish를 구현하기 전에 API DTO, validation, ErrorResponse, OpenAPI 계약을 먼저 고정합니다. 이렇게 해야 Phase 3에서 Producer를 붙일 때 API 계약 변경과 Kafka 처리 구현 변경을 분리해서 검토할 수 있습니다.

Phase 3에서는 `app-api`가 실제 transaction event receipt를 저장하고 Kafka에 원본 이벤트를 발행합니다. Consumer 처리는 여전히 분리되어 있으며, manual ack와 processing log 저장은 다음 Phase에서 구현합니다.

## 측정 또는 재현

API p95 latency와 Consumer processing latency를 별도로 수집합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

DB는 초기에는 하나의 PostgreSQL을 공유하므로 완전한 서비스 독립성은 없습니다.
