# 처리량 실험이 데이터를 망치지 않게 만든 안전장치들

V3의 headline은 throughput, state, freshness다. 그러나 부하를 높이고 실패를 주입하는 동안 결과가 중복되거나 운영 기록이 사라지면 성능 수치는 의미가 없다.

이 글은 전면에 드러나지 않지만 모든 실험 아래에 깔린 안전장치를 한 번에 정리한다.

## 1. offset: 늦게 ack하고 재전달을 받아들인다

Consumer는 auto commit을 끄고 `MANUAL_IMMEDIATE`를 사용한다. processing log와 fraud result 같은 required processing이 끝난 뒤 ack한다.

이 선택은 “한 번만 처리”를 보장하지 않는다. DB 저장 뒤 ack 전에 죽으면 redelivery가 발생한다. 대신 처리하지 않은 이벤트의 offset이 먼저 전진하는 silent loss를 피한다.

## 2. 중복 방어: 빠른 확인과 최종 제약을 겹친다

listener는 result precheck로 이미 처리된 `eventId`를 건너뛴다. 동시에 PostgreSQL의 `fraud_detection_results.event_id` unique constraint가 race condition의 최종 방어선이다.

processing log에는 `(topic, partition_no, offset_no)` unique constraint가 있다. 같은 business event와 같은 Kafka record 위치는 서로 다른 중복 축이므로 제약도 나눴다.

## 3. Redis: correctness authority가 아니라 online state다

Redis가 unavailable이면 전체 이벤트를 정상 처리한 척하지 않는다.

```text
Redis failure
  -> state-dependent rules skipped
  -> remaining rules evaluated
  -> degraded=true + skippedRuleCodes + reason persisted
  -> degraded counter incremented
```

fraud result의 최종 기록은 PostgreSQL에 남는다. Redis는 recent window를 위한 단기 상태이며 최종 truth가 아니다.

too-late도 degraded로 남지만 Redis infrastructure failure와 별도 counter/reason을 쓴다. 같은 결과 flag 아래에서도 원인은 보존한다.

## 4. DLT: 실패 보관함이 아니라 상태 머신이다

unrecoverable rule error는 DLT metadata를 저장하고 DLT topic에 envelope를 발행한 뒤 원본 record를 ack한다. DB 장애는 같은 PostgreSQL에 DLT metadata도 못 쓸 가능성이 있으므로 ack하지 않아 재소비를 유도한다.

운영자 동작은 상태 전이로 제한한다.

```text
PENDING / REPROCESS_FAILED
  -> REPROCESSING
      -> REPROCESSED
      -> REPROCESS_FAILED

PENDING / REPROCESS_FAILED
  -> DISCARDED
```

- row lock으로 동시 reprocess/discard를 직렬화한다.
- max reprocess attempts 기본값은 3이다.
- attempts 초과가 자동 discard로 이어지지는 않는다.
- 성공과 실패 action을 `admin_audit_logs`에 남긴다.
- local/dev admin API는 `X-Admin-Token`으로 최소 보호한다.

Kafka publish와 DB 상태 변경은 atomic하지 않다. outbox나 reconciliation은 아직 없다. 이 한계까지 상태와 audit에 기록하는 것이 현재 보장 범위다.

## 5. replay: 실패해도 원래 stream으로 돌아간다

DLT reprocess publisher는 source topic allowlist를 검사한다.

- live source → `transaction-events`
- replay source → `transaction-events-replay`

원본 `eventId`와 `userId` key를 유지한다. replay DLT를 실수로 live topic에 넣어 freshness와 Redis state를 오염시키는 경로를 닫았다.

## 6. ruleVersion: 현재 코드와 과거 결과를 섞지 않는다

V2에서는 하나의 `ruleVersion`을 세 의미로 분해했다.

| Version 관점 | 의미 |
|---|---|
| active runtime | 현재 Consumer가 실행 중인 rule baseline |
| stored result | 각 fraud result가 어떤 rule version으로 생성됐는가 |
| evaluator expected | Python evaluation이 기대하는 version |

Java와 Python의 version drift를 CI-safe verifier로 검사하고, stored result에도 version을 저장한다. 배포 직후 old/new result가 함께 있는 것은 가능하지만 의미를 잃은 채 섞이지 않게 한다.

PaySim evaluation도 precision/recall만 남기지 않는다. denominator, missing result, excluded/unsupported type, mapping policy, threshold/evaluation version을 같이 저장한다. synthetic data 결과를 production fraud accuracy로 확대하지 않는다.

## 7. evidence gate: 좋은 숫자보다 유효한 실행

V3 accepted run은 event limit, HTTP failure, dropped iteration, final Lag, DB 세 count, clean state를 확인한다. 조건을 어긴 실행은 버리지 않고 discarded reason을 남긴다.

대표적인 폐기 사례는 다음과 같다.

- workload stage 계약 오류
- sandbox network로 모든 HTTP 요청 실패
- VU 부족으로 설정 event count 미달
- 같은 Redis instance의 logical DB를 사용한 memory 비교
- replay rerun 전 남은 group Lag

`make final-check`는 Gradle build, Docker Compose config, script syntax, V2 fixture verifier 같은 repository guardrail을 실행한다. local V3 runtime capacity나 실제 탐지 품질까지 보증하지는 않는다.

## 안전장치의 공통 원칙

모든 장치가 같은 철학을 가진다.

1. 실패를 숨기지 않는다.
2. 중복 전달을 현실로 받아들인다.
3. 최종 correctness는 durable constraint에 둔다.
4. degraded와 skipped 범위를 결과에 남긴다.
5. 운영자 action도 상태와 audit 대상이다.
6. 측정하지 않은 보장을 이름으로 만들지 않는다.

처리량 그래프는 이 바닥이 유지될 때만 해석할 수 있다.
