# Phase 10. DLT 재처리 이후, 운영 가능한 이상거래 탐지 시스템으로 마무리하기

## 1. Phase 10을 왜 했는가

Phase 9까지는 실패 이벤트를 DLT로 격리하고, 운영자가 조회하고, 재처리하거나 폐기할 수 있는 흐름을 구현했다.

하지만 운영 관점에서는 API가 성공했다고 끝나는 것도 아니고, DLT 재처리 API가 200을 반환했다고 끝나는 것도 아니다. 재처리 이후 Consumer가 실제로 메시지를 처리했는지, 같은 `eventId`로 FraudResult가 중복 생성되지 않았는지, Consumer Lag이 회복됐는지, DLQ row 상태가 종료 상태로 전이됐는지까지 확인해야 한다.

Phase 10은 큰 기능을 추가하기보다 이 기준을 문서로 정리하는 단계로 잡았다.

## 2. DLT reprocessing만으로는 부족한 이유

DLT 재처리는 실패 이벤트를 다시 원본 topic으로 넣는 기능이다. 이 기능만 보면 복구가 끝난 것처럼 보일 수 있다.

그러나 실제 확인 기준은 더 넓다.

- Kafka publish가 성공했는가
- Consumer가 재처리 메시지를 소비했는가
- `fraud_detection_results.event_id` unique constraint가 중복 결과를 막았는가
- `dead_letter_events` 상태가 `REPROCESSED` 또는 `DISCARDED`로 정리됐는가
- processing log가 남았는가
- Consumer Lag이 계속 증가하지 않는가
- Redis 장애 중이었다면 degraded result와 skipped rule이 설명 가능한가

이 기준이 없으면 운영자는 "재처리 버튼을 눌렀다"까지만 말할 수 있고, "복구가 완료됐다"는 판단을 설명하기 어렵다.

## 3. 운영자가 확인해야 하는 최종 기준

API 정상 여부:

- `POST /api/v1/transactions/events`가 validation failure와 Kafka publish failure를 구분하는지 확인한다.
- API latency만 보고 전체 시스템이 정상이라고 판단하지 않는다.

Consumer 정상 여부:

- manual ack가 유지되는지 확인한다.
- FraudResult와 processing log 저장 이후 offset이 ack되는지 확인한다.
- Consumer 재시작 후 미처리 메시지가 처리되는지 확인한다.

Kafka topic / DLT 상태:

- `transaction-events`와 `transaction-events-dlt` topic이 존재하는지 확인한다.
- DLT count가 증가했다면 failure reason과 상태 전이를 함께 본다.

Consumer lag:

- Consumer 중지 또는 DB 장애 중에는 lag이 증가할 수 있다.
- 복구 이후 lag이 줄어드는지 확인해야 한다.

Redis degraded 여부:

- Redis 장애 시 전체 처리를 실패시키지 않는다.
- Redis 의존 rule은 skipped 처리하고 FraudResult에는 `degraded=true`를 남긴다.

PostgreSQL detection result / audit log:

- `fraud_detection_results.event_id` unique constraint로 중복 결과를 방어한다.
- `event_processing_logs`로 topic, partition, offset, status를 확인한다.
- `dead_letter_events`로 DLT 상태와 재처리 횟수를 확인한다.

재처리 후 중복 처리 여부:

- 원본 `eventId`가 유지됐는지 확인한다.
- 같은 `eventId`로 FraudResult row가 2개 이상 생기지 않아야 한다.

## 4. 개발 중 트러블슈팅

문제:

DLT 재처리 흐름은 구현됐지만, 운영자가 어떤 증거를 보고 복구 완료를 판단해야 하는지 문서화되어 있지 않았다.

판단:

Phase 10에서는 새로운 기능보다 final readiness checklist를 우선했다. API, Consumer, Kafka, Redis, PostgreSQL, DLT 재처리 관점으로 확인 기준을 나누고, 남은 한계는 후속 보완 후보로 분리했다.

행동:

- `docs/19-phase-10-final-readiness.md` 추가
- `docs/11-troubleshooting-log.md` Phase 10 섹션 추가
- README 현재 구현 범위와 문서 링크 최소 수정
- Phase 10 blog draft 추가

검증 중에는 sandbox 환경에서 Gradle wrapper가 `~/.gradle` lock file을 생성하지 못해 최초 `./gradlew clean build`가 실패했다. 애플리케이션 문제가 아니라 실행 권한 문제였고, 승인된 로컬 권한으로 재실행하자 build/test는 통과했다.

## 5. 최종 검증 명령과 결과

2026-06-22 로컬 기준으로 다음 명령을 실행했다.

```bash
./gradlew clean build
./gradlew test
make test
make final-check
```

결과:

- `./gradlew clean build`: PASS
- `./gradlew test`: PASS
- `make test`: PASS
- `make final-check`: PASS

`make final-check`는 build, Docker Compose config, shell script syntax check를 함께 실행한다.

## 6. 이 프로젝트에서 포트폴리오로 설명할 수 있는 점

이 프로젝트의 핵심은 단순 CRUD가 아니라 비동기 이벤트 처리의 운영 기준을 끝까지 설명하는 것이다.

- Kafka event-driven architecture로 API 접수와 Consumer 탐지를 분리했다.
- `userId` partition key로 사용자별 이벤트 순서를 우선했다.
- manual ack로 저장 성공 전 offset commit을 피했다.
- PostgreSQL unique constraint로 duplicate FraudResult를 방어했다.
- Redis 장애는 degraded mode로 처리하고 skipped rule을 남겼다.
- DLT/DLQ reprocessing으로 실패 이벤트를 조회, 재처리, 폐기할 수 있게 했다.
- Consumer Lag, detection latency, DLQ count, Redis degraded count를 핵심 운영 신호로 정의했다.
- runbook, troubleshooting log, final readiness checklist로 운영 완료 기준을 문서화했다.

## 7. 배운 점

DLT 재처리는 메시지를 다시 발행하는 기능만으로는 충분하지 않다. 운영 기능이 되려면 재처리 후 어떤 지표와 데이터로 정상화를 확인할지까지 정리되어야 한다.

이번 Phase에서 정리한 기준은 다음 단계의 dashboard, alert, k6 부하 측정으로 이어진다. 즉 Phase 10은 끝이라기보다, 지금까지 만든 시스템을 운영자가 검증 가능한 언어로 바꾸는 마무리 단계다.
