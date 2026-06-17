# Phase 4. Kafka Consumer Manual Ack와 Processing Log 구현

## 0. Consumer를 만들기 전에 CI Gate를 먼저 추가한 이유

Phase 4부터는 단순 API 구현이 아니라 Kafka Consumer의 offset commit 시점, DB 저장 성공 여부, 중복 offset 처리처럼 회귀 버그가 발생하기 쉬운 영역이 추가됩니다.

Phase 3까지는 로컬에서 `make test`, `make build`, `make final-check`를 직접 실행하며 검증했습니다. 하지만 이후 Phase에서 Consumer, Rule Engine, Retry/DLT, Redis 장애 대응이 차례로 추가될 예정이기 때문에 로컬 검증만으로는 부족하다고 판단했습니다.

그래서 Consumer 구현 전에 GitHub Actions 기반 최소 CI Gate를 먼저 추가했습니다. 이번 CI는 Docker Compose 전체 인프라를 띄우는 방식이 아니라 `make ci-check` 중심의 가벼운 Gate로 시작했습니다.

Kafka end-to-end 검증은 아직 로컬에서 수행하고, 이후 Retry/DLT와 부하 테스트가 안정화되면 별도 integration workflow로 확장할 계획입니다.

## 1. 이번 Phase에서 풀려는 문제

Phase 4의 목표는 이상거래 탐지가 아니라 Consumer가 Kafka record를 언제 처리한 것으로 볼지 명확히 하는 것입니다.

API는 Phase 3에서 `transaction-events` topic으로 거래 이벤트를 발행합니다. Phase 4에서는 app-consumer가 이 이벤트를 소비하고, 처리 사실을 PostgreSQL `event_processing_logs`에 남긴 뒤 offset을 commit합니다.

## 2. auto commit을 쓰지 않은 이유

auto commit을 사용하면 DB 저장 성공 여부와 무관하게 offset이 commit될 수 있습니다.

그러면 Consumer가 메시지를 읽었지만 processing log 저장에 실패한 경우에도 Kafka는 해당 offset이 처리된 것으로 볼 수 있습니다. 이 프로젝트는 장애 재현과 처리 추적이 핵심이므로 auto commit을 사용하지 않습니다.

## 3. DB 저장 성공 후 ack를 선택한 이유

Phase 4의 처리 완료 기준은 `event_processing_logs` 저장 성공입니다.

Phase 4에서는 `enable-auto-commit=false`와 `AckMode.MANUAL_IMMEDIATE`를 사용해 애플리케이션이 acknowledge 호출 시점을 통제하도록 했습니다. Processing log 저장 성공 후 acknowledge를 호출하며, DB 저장 실패 시 acknowledge하지 않아 재소비 가능성을 열어둡니다.

따라서 흐름은 다음과 같습니다.

```text
Kafka record consume
-> EventProcessingLog 저장
-> 저장 성공
-> manual ack
```

listener는 metadata를 수집하고 service에 위임합니다. service가 성공한 뒤 listener가 acknowledgment를 호출합니다.

## 4. DB 저장 성공 후 ack 전 장애가 나면 생기는 문제

DB 저장은 성공했지만 ack 직전에 Consumer가 죽으면 같은 offset이 다시 소비될 수 있습니다.

이 경우 같은 processing log가 계속 쌓이면 운영자가 실제 처리 횟수를 잘못 해석할 수 있습니다. 그래서 Phase 4에서는 Kafka record의 원천 식별자인 topic, partition, offset 조합을 중복 방어 기준으로 잡았습니다.

## 5. topic/partition/offset unique constraint로 중복 처리 방어

`event_processing_logs`에는 다음 unique constraint를 둡니다.

```text
(topic, partition_no, offset_no)
```

같은 offset이 재소비되었고 이미 log가 있으면 duplicate log를 새로 만들지 않습니다. 이 경우 이전 processing log 저장을 처리 성공 근거로 보고 ack 가능합니다.

## 5-1. Phase 4에서 migration owner를 app-api로 둔 이유

`event_processing_logs`는 app-consumer가 쓰고 app-api가 조회하지만, 같은 DB schema에 대해 두 애플리케이션이 각각 Flyway를 실행하면 migration checksum drift가 생길 수 있습니다.

그래서 Phase 4에서는 app-api를 schema owner로 두고, app-consumer runtime은 Flyway를 실행하지 않고 JPA validate만 수행합니다. app-consumer module test에서는 H2 기반 검증을 위해 test resources에만 migration을 둡니다.

## 6. eventId 기준 조회 API를 만든 이유

운영자가 장애를 볼 때 Kafka offset만 알고 있는 경우보다 eventId를 알고 있는 경우가 많습니다.

그래서 app-api에 다음 조회 API를 추가했습니다.

```text
GET /api/v1/admin/events/{eventId}/processing-log
```

응답에는 `traceId`, `userId`, `topic`, `partition`, `offset`, `consumerGroupId`, `status`, `processedAt`을 포함합니다. `accountId`, `deviceId`, raw payload는 포함하지 않습니다.

## 7. 테스트와 수동 검증 결과

자동 테스트에서는 다음을 확인합니다.

- processing log 저장 성공 후 ack 호출
- service 실패 시 ack 미호출
- transaction event message 처리 시 processing log 저장
- `(topic, partition_no, offset_no)` unique constraint 방어
- eventId 기준 processing log 조회 API

수동 검증 결과는 `docs/13-development-roadmap.md`에 기록합니다.

## 8. 이번 Phase의 한계

Phase 4는 Consumer 처리 사실을 남기는 단계입니다.

다음은 구현하지 않았습니다.

- FraudResult 저장
- Rule Engine
- Redis Sliding Window
- Retry/DLT
- Consumer Lag custom metric
- Kafka/PostgreSQL/Redis E2E 검증을 포함한 GitHub Actions integration workflow

`FAILED` processing status는 Phase 4에서는 예약 상태입니다. DB 자체가 실패 로그를 저장할 수 없는 경우에는 acknowledge하지 않고 재소비 가능성을 열어두며, 저장 가능한 business failure와 DLT 기록은 Retry/DLT 단계에서 구체화합니다.

## 9. 다음 Phase에서 보완할 점

Phase 5에서는 기본 `LOW` FraudResult 저장과 eventId 기준 business idempotency를 구현합니다. Retry/DLT는 Phase 9, custom metric과 Grafana dashboard는 Phase 10에서 다룹니다.
