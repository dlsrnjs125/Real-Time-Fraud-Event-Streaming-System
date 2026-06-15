# Spring Boot API와 Kafka Consumer를 분리한 이유

## 문제

거래 이벤트 접수와 이상거래 탐지는 처리 특성이 다릅니다. 두 흐름을 동기 요청으로 묶으면 탐지 지연이 API 응답 지연으로 전파됩니다.

## 초기 설계

`app-api`는 거래 이벤트를 검증하고 Kafka에 발행합니다. `app-consumer`는 Kafka 이벤트를 소비해 탐지와 저장을 수행합니다.

## 구현

초기 스캐폴딩에서는 두 Spring Boot 애플리케이션을 별도 모듈로 둡니다.

## 측정 또는 재현

API p95 latency와 Consumer processing latency를 별도로 수집합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

DB는 초기에는 하나의 PostgreSQL을 공유하므로 완전한 서비스 독립성은 없습니다.
