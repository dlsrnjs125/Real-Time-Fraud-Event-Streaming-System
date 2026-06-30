# Phase 11. 실시간 이상거래 탐지 시스템 최종 운영 준비도 점검

## 1. 이번 Phase에서 풀려는 문제

Phase 11의 목표는 새로운 기능을 추가하는 것이 아니라, Phase 1~10에서 만든 기능과 검증 evidence를 운영 준비도 관점으로 다시 정리하는 것이다.

Kafka publish, Consumer manual ack, Redis Sliding Window, DLT 재처리 API가 각각 존재하더라도 리뷰어와 운영자가 어디서 무엇을 확인해야 하는지 알 수 없다면 시스템은 설명 가능한 상태가 아니다.

## 2. 기능 구현보다 최종 준비도 점검이 필요한 이유

이 프로젝트의 핵심은 빠른 CRUD 구현이 아니라 장애 상황에서도 처리 결과를 추적하고 복구할 수 있는 구조를 검증하는 것이다.

그래서 Phase 11에서는 README를 길게 늘리기보다 checklist, evidence index, troubleshooting index를 분리했다. README는 입구이고, docs는 판단 근거이며, blog는 설계와 시행착오를 기록하는 공간으로 역할을 나눴다.

## 3. Phase 1~10에서 검증한 것

Phase 1~3에서는 로컬 인프라, API 계약, Kafka producer를 검증했다. Phase 4~5에서는 Consumer manual ack, processing log, Rule Engine, FraudResult 저장을 구현했다.

Phase 6~8에서는 Redis Sliding Window, degraded mode, Redis integration test, Redis down/Consumer restart/Kafka unavailable drill을 정리했다. Phase 9~10에서는 DLT 저장, 조회, 재처리, 폐기 흐름과 재처리 이후 운영 완료 기준을 문서화했다.

## 4. 정합성 관점에서 확인한 것

최종 정합성 기준은 PostgreSQL이다. 같은 `eventId`가 여러 번 들어와도 `fraud_detection_results.event_id` unique constraint가 중복 FraudResult 생성을 막는다.

Kafka offset 기준 중복은 `event_processing_logs(topic, partition_no, offset_no)` unique constraint로 방어하고, DLT 중복 저장은 `dead_letter_events(source_topic, source_partition, source_offset)` unique constraint로 막는다.

## 5. 장애 대응 관점에서 확인한 것

Redis 장애는 전체 이벤트 실패로 처리하지 않는다. Redis 의존 rule만 skipped 처리하고, FraudResult에는 `degraded=true`를 저장한다.

Consumer 장애는 Kafka에 쌓인 이벤트를 재시작 후 처리하는 방식으로 확인한다. DLT는 반복 실패 이벤트를 격리하고, 운영자가 조회, 재처리, 폐기할 수 있는 복구 지점을 제공한다.

## 6. 관측성 관점에서 확인한 것

현재 구현된 metric은 Redis window latency, Redis degraded count, skipped rule count, degraded detection count다. Metric tag에는 `eventId`, `traceId`, `userId`, `accountId` 같은 고카디널리티 식별자를 넣지 않는다.

DLT pending count, reprocess failed count, Kafka Consumer Lag dashboard, API p95/p99 dashboard는 후속 Observability Hardening 후보로 분리했다.

## 7. 보안/개인정보 관점에서 남은 한계

Admin API는 local/development-only로 다룬다. 운영 환경에서는 인증/인가, operator audit log, DLT payload 접근 권한 분리가 필요하다.

DLT payload는 복구에 필요한 정보를 담지만 민감정보 저장소가 될 수 있다. 현재 프로젝트는 synthetic identifier 기준으로 검증하고, 운영 확장 시 masking/redaction과 retention 정책을 추가해야 한다.

## 8. README를 최소화하고 docs/blog로 분리한 이유

README가 모든 장애 대응과 phase evidence를 담기 시작하면 오히려 중요한 정보가 묻힌다. README는 프로젝트 목적, 아키텍처, 실행 방법, 핵심 문서 링크만 제공한다.

상세 판단은 docs에, 설계 배경과 회고는 blog에 남긴다. 이 구조가 유지되면 README는 짧게 유지하면서도 검증 근거는 사라지지 않는다.

## 9. 최종 체크리스트

Phase 11에서는 `docs/19-final-readiness-checklist.md`를 추가해 기능, 정합성, 장애 대응, 관측성, 보안, 문서 준비도를 한 번에 확인할 수 있게 했다.

체크된 항목은 현재 구현 또는 문서 evidence가 있는 항목이고, 미완료 항목은 `Follow-up`으로 분리했다. 아직 구현하지 않은 운영 보안, batch reprocess, dashboard capture를 완료된 기능처럼 표현하지 않는 것이 중요하다.

## 10. 다음 Phase에서 보완할 점

다음 단계는 크게 세 갈래다.

- Observability Hardening: DLT metric, Kafka Consumer Lag, Grafana dashboard, alert rule
- Load and Failure Test: k6 normal/peak/hot partition/Redis down/Consumer restart measurement
- Operational Security and Automation: admin auth, audit log, DLQ rate limit, CI/E2E drill

Phase 11은 끝을 선언하는 단계가 아니라, 지금까지 만든 시스템의 증거를 찾기 쉽게 정리하고 다음 운영 고도화의 경계를 분명히 하는 단계다.
