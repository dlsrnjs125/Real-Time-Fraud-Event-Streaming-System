# Blog Series Plan

이 폴더는 실시간 이상거래 탐지 시스템 개발 과정을 기술 기록 형태로 정리합니다.

글은 기능 나열보다 문제, 설계 변경, 검증 증거, 남은 한계를 중심으로 작성합니다.

> Status: Publication candidate text complete
> `series/`는 최종 발행 후보 본문이고, `drafts/`는 기존 원본 draft archive입니다.

## Directory Layout

- `series/`: 11개 발행 후보 파일. 본문은 작성 완료 상태이며 이미지는 별도 작업으로 추가합니다.
- `drafts/`: 기존 33개 draft 원본. 본문은 보존하고 source material로 사용합니다.

## Series Order

| No. | Topic | Draft Sources | Main Question |
|---:|---|---|---|
| 1 | [Kafka 기반 이상거래 탐지 시스템을 만든 이유](series/01-kafka-fraud-system-problem.md) | `drafts/01`, `drafts/02`, `drafts/03` | API와 Consumer를 왜 분리했는가 |
| 2 | [이벤트 스키마와 감사 저장 모델](series/02-event-schema-audit-model.md) | `drafts/04-*` | `eventId`, `traceId`, `userId` partition key를 어떻게 잡았는가 |
| 3 | [Consumer manual ack와 재처리 가능성](series/03-consumer-manual-ack-reprocessing.md) | `drafts/05-*` | offset commit 시점을 어디에 둘 것인가 |
| 4 | [Redis sliding window와 degraded mode](series/04-redis-sliding-window-degraded-mode.md) | `drafts/06`, `drafts/07`, `drafts/10` | Redis 장애를 탐지 실패로 볼 것인가, degraded 결과로 남길 것인가 |
| 5 | [DLT 재처리 API와 운영자 조작 보호](series/05-dlt-reprocessing-admin-safety.md) | `drafts/07`, `drafts/09`, `drafts/17` | 재처리/폐기를 어떻게 감사 가능하게 만들 것인가 |
| 6 | [Consumer Lag과 Detection Latency 관측](series/06-consumer-lag-detection-latency.md) | `drafts/08-*` | API latency와 detection latency를 왜 분리했는가 |
| 7 | [k6 부하/장애 테스트로 한계 측정](series/07-load-failure-test-evidence.md) | `drafts/09`, `drafts/11`, `drafts/15`, `drafts/16` | peak, duplicate, Redis down에서 어떤 지표를 볼 것인가 |
| 8 | [PaySim 데이터를 replay 가능한 이벤트로 바꾸기](series/08-paysim-replayable-events.md) | `drafts/18` through `drafts/23` | raw data를 커밋하지 않으면서 재현성을 어떻게 남길 것인가 |
| 9 | [PaySim replay evaluation을 evidence로 만들기](series/09-paysim-replay-evaluation-evidence.md) | `drafts/24` through `drafts/28` | precision/recall을 과장하지 않으려면 무엇을 기록해야 하는가 |
| 10 | [ruleVersion 추적성 설계](series/10-rule-version-traceability.md) | `drafts/29` through `drafts/31` | Java/Python drift와 active/stored version 혼동을 어떻게 막을 것인가 |
| 11 | [ruleVersion 변경 runbook과 evidence closure](series/11-rule-version-change-runbook-evidence-closure.md) | `drafts/32`, `drafts/33` | hold/rollback readiness와 automatic rollback을 어떻게 구분할 것인가 |

## Draft Archive

The numbered files under `drafts/` remain as source material for the next blog cleanup step. They are not the final publication order.

Core drafts: `drafts/01` through `drafts/17`

V2 PaySim and ruleVersion drafts: `drafts/18` through `drafts/33`

Historical duplicate-topic drafts kept for now:

- `04-consumer-manual-ack-processing-log.md`
- `05-fraud-result-rule-engine.md`
- `07-redis-integration-test-and-metrics.md`
- `08-failure-drill-consumer-recovery.md`
- `09-retry-dlt-reprocessing.md`

## 글 구조

각 글은 아래 흐름을 기본으로 합니다.

- 문제
- 초기 설계
- 구현
- 측정 또는 재현
- 발견한 문제
- 변경한 설계
- 남은 한계

## Image Planning

이미지와 다이어그램 후보는 [Blog Image Plan](image-plan.md)에 정리합니다.

현재 단계에서는 새 이미지 파일을 생성하지 않았습니다. Mermaid로 충분한 흐름은 본문에 직접 포함했고, 실제 screenshot이 필요한 항목은 image plan에 capture candidate로만 남깁니다.
