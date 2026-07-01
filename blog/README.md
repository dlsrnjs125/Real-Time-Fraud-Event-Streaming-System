# Blog Series Plan

이 폴더는 실시간 이상거래 탐지 시스템 개발 과정을 기술 기록 형태로 정리합니다.

글은 기능 나열보다 문제, 설계 변경, 검증 증거, 남은 한계를 중심으로 작성합니다. 다만 발행 후보 본문에서는 같은 템플릿 제목을 반복해서 노출하지 않고, 각 글의 핵심 질문에 맞는 섹션명을 사용합니다.

> Status: Publication candidate text complete
> `series/`는 최종 발행 후보 본문이고, `drafts/`는 기존 원본 draft archive입니다.

## Directory Layout

- `series/`: 12개 발행 후보 파일. 본문은 작성 완료 상태이며 이미지는 별도 작업으로 추가합니다.
- `drafts/`: 기존 draft archive. 최종 발행 순서는 아닙니다.

## Series Order

| No. | Topic | Draft Sources | Main Question |
|---:|---|---|---|
| 1 | [API는 빨랐는데 탐지는 늦을 수 있다](series/01-kafka-fraud-system-problem.md) | `drafts/01`, `drafts/02`, `drafts/03` | API와 Consumer를 왜 분리했는가 |
| 2 | [eventId, traceId, userId를 같은 식별자로 쓰지 않은 이유](series/02-event-schema-audit-model.md) | `drafts/04-*` | 추적, 중복 방어, ordering 기준을 어떻게 분리했는가 |
| 3 | [DB 저장 전에 ack하면 무엇이 사라지는가](series/03-consumer-manual-ack-reprocessing.md) | `drafts/05-*` | offset commit 시점을 어디에 둘 것인가 |
| 4 | [Redis가 죽으면 탐지를 멈출 것인가](series/04-redis-sliding-window-degraded-mode.md) | `drafts/06`, `drafts/07`, `drafts/10` | Redis 장애를 탐지 실패로 볼 것인가, degraded 결과로 남길 것인가 |
| 5 | [재처리는 복구 기능이면서 운영자 조작 위험이다](series/05-dlt-reprocessing-admin-safety.md) | `drafts/07`, `drafts/09`, `drafts/17` | 재처리/폐기를 어떻게 감사 가능하게 만들 것인가 |
| 6 | [API p95가 정상인데 탐지가 밀리는 상황](series/06-consumer-lag-detection-latency.md) | `drafts/08-*` | API latency와 detection latency를 왜 분리했는가 |
| 7 | [k6 결과를 좋게 보이게 쓰지 않기](series/07-load-failure-test-evidence.md) | `drafts/09`, `drafts/11`, `drafts/15`, `drafts/16` | peak, duplicate, Redis down에서 어떤 지표를 볼 것인가 |
| 8 | [raw PaySim을 커밋하지 않고 재현성을 남기기](series/08-paysim-replayable-events.md) | `drafts/18` through `drafts/23` | raw data를 커밋하지 않으면서 재현성을 어떻게 남길 것인가 |
| 9 | [precision/recall을 믿기 전에 분모부터 고정했다](series/09-paysim-replay-evaluation-evidence.md) | `drafts/24` through `drafts/28` | precision/recall을 과장하지 않으려면 무엇을 기록해야 하는가 |
| 10 | [active, stored, evaluator ruleVersion을 섞지 않기](series/10-rule-version-traceability.md) | `drafts/29` through `drafts/31` | Java/Python drift와 active/stored version 혼동을 어떻게 막을 것인가 |
| 11 | [ruleVersion 변경 runbook과 rollback readiness](series/11-rule-version-change-runbook-evidence-closure.md) | `drafts/32`, `drafts/33` | hold/rollback readiness와 automatic rollback을 어떻게 구분할 것인가 |
| 12 | [회고: Kafka, Redis, PaySim보다 중요했던 것은 설명 가능한 기준이었다](series/12-project-retrospective.md) | `drafts/32`, `drafts/33`, series summary | 이 프로젝트에서 어떤 엔지니어링 기준을 얻었는가 |

## Image Planning

이미지와 다이어그램 후보는 [Blog Image Plan](image-plan.md)에 정리합니다.

Mermaid로 충분한 흐름은 본문에 직접 포함하고, 실제 screenshot이 필요한 항목은 [Blog Image Plan](image-plan.md)에서 상태를 추적합니다. 이미지 링크는 파일이 실제로 추가된 경우에만 본문에 둡니다.
