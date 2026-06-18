# Phase 5. Rule Engine v1과 Fraud Detection Result 저장

## 1. 이번 Phase에서 풀려는 문제

Phase 4는 Consumer가 Kafka record를 처리했는지 남기는 단계였습니다. Phase 5는 그 이벤트가 어떤 위험 판단을 받았는지 저장하는 단계입니다.

## 2. Processing Log만으로 부족한 이유

`event_processing_logs`는 topic, partition, offset 기준 처리 흔적입니다. 운영자가 실제로 필요한 것은 해당 거래가 LOW/MEDIUM/HIGH 중 어떤 판단을 받았고, 어떤 rule 때문에 그런 결과가 나왔는지입니다.

## 3. Fraud Detection Result 테이블을 분리한 이유

`fraud_detection_results`는 탐지 결과의 기준 저장소입니다. processing log와 분리해 Consumer 처리 추적과 이상거래 판단 결과를 각각 독립적으로 조회할 수 있게 했습니다.

## 4. Rule Engine v1을 Consumer에 연결한 방식

Listener는 orchestration만 담당합니다. 흐름은 다음과 같습니다.

```text
Kafka consume
-> processing log 저장
-> Rule Engine v1 평가
-> fraud_detection_results 저장
-> acknowledge
```

Rule Engine은 단건 이벤트 기반 rule만 평가합니다.

- `AMOUNT_THRESHOLD`
- `NIGHT_TIME_TRANSACTION`
- `SUSPICIOUS_LOCATION`

## 5. eventId unique constraint로 중복 탐지를 막은 이유

Kafka message는 재소비될 수 있습니다. 따라서 Consumer는 같은 eventId를 다시 받아도 중복 fraud result를 만들면 안 됩니다. Phase 5에서는 PostgreSQL `event_id` unique constraint를 최종 중복 방어 기준으로 둡니다.

## 6. Ack는 왜 Fraud Result 저장 이후에 호출했는가

fraud result 저장 전에 acknowledge하면 저장 실패 시 Kafka 재소비 기회를 잃을 수 있습니다. 그래서 Phase 5에서는 processing log 저장, rule 평가, fraud result 저장이 모두 성공한 뒤 acknowledge합니다.

duplicate fraud result는 이미 탐지 결과가 존재한다는 뜻이므로 idempotent 성공으로 보고 acknowledge할 수 있습니다.

## 7. Redis 없이 v1 Rule Engine을 먼저 만든 이유

Redis Sliding Window는 사용자 최근 거래 패턴을 다루는 중요한 기능입니다. 하지만 Redis 장애, degraded mode, skipped rule까지 한 번에 넣으면 ack와 result 저장 정합성 검증이 흐려집니다.

Phase 5에서는 단건 이벤트 기반 rule만 먼저 구현해 result persistence와 idempotency를 닫았습니다.

## 8. 테스트와 검증 결과

자동 테스트에서는 다음을 확인했습니다.

- Rule Engine score 합산과 100점 cap
- LOW/MEDIUM/HIGH와 APPROVE/REVIEW/BLOCK mapping
- fraud result 저장과 duplicate eventId 방어
- fraud result 저장 실패 시 ack 미호출
- rule engine 실패 시 ack 미호출
- processing log 저장 실패 시 fraud result 저장 미시도
- admin fraud result 조회와 404 응답

`make ci-check`가 통과했습니다.

## 9. 이번 Phase의 한계

- Redis Sliding Window 기반 최근 거래 패턴은 아직 없습니다.
- Retry/DLT 기반 실패 복구는 아직 없습니다.
- Rule threshold는 코드 상수입니다.
- 같은 eventId는 하나의 fraud result만 가집니다.
- rule별 상세 결과는 아직 별도 저장하지 않습니다.

## 10. 다음 Phase에서 보완할 점

다음 단계에서는 Redis 기반 VelocityRule을 추가하고, Redis 장애 시 degraded mode와 skipped rule 기록을 구현합니다. 이후 Retry/DLT와 reprocessing 단계에서 duplicate result와 재처리 이력 정합성을 검증합니다.
