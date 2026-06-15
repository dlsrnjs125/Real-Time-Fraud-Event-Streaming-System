# Load Test Plan

## 1. 목표

정상 부하, 피크 부하, Consumer 장애, Redis 장애를 재현하고 API 응답성과 비동기 탐지 지연을 분리해서 측정합니다.

## 2. 시나리오

### normal-load

일반적인 거래 이벤트 유입을 재현합니다.

측정:

- API p50/p95/p99
- Kafka publish success rate
- Consumer processing duration

### peak-load

짧은 시간 동안 대량 이벤트가 몰리는 상황을 재현합니다.

측정:

- Consumer Lag 최대값
- Lag 회복 시간
- DLQ count

### consumer-lag-test

Consumer 중단 후 재시작 상황을 재현합니다.

측정:

- Consumer 중단 시간
- 재시작 후 미처리 이벤트 재소비 여부
- 중복 FraudResult 생성 건수

### redis-down-test

Redis 장애 상태에서 degraded mode가 동작하는지 확인합니다.

측정:

- Redis 장애 중 처리된 이벤트 수
- `degraded=true` 탐지 결과 수
- Redis 기반 rule skipped count

## 3. 산출물

테스트 결과는 이 문서에 표로 추가합니다. 초기 설계값과 실제 측정값이 다르면 변경 이유를 `docs/11-troubleshooting-log.md`에 기록합니다.
