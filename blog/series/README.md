# Blog Series Writing Guardrail

This series records problems found during development and the design changes made in response. It is not a feature catalog or a general technology tutorial.

Status: publication candidate text complete. Images are planned separately and should be added only when the screenshot or diagram actually exists.

## Common Structure

Each post should follow this flow:

1. 문제
2. 초기 설계
3. 실제로 막힌 지점
4. 확인한 증거
5. 변경한 설계
6. 검증
7. 남은 한계

## Include

- 처음에는 어떤 방식으로 생각했는지
- 실제 구현 중 어떤 문제가 드러났는지
- 로그, 테스트, 지표, DB 결과, Grafana, k6 등 무엇으로 확인했는지
- 왜 기존 방식을 버리고 다른 설계를 선택했는지
- 이번 범위에서 의도적으로 제외한 것

## Avoid

- technology overview without project evidence
- publication- or presentation-oriented phrasing
- claims about production automation, performance, detection accuracy, or operational maturity that were not measured
- p95, p99, RPS, or latency numbers that were not measured
- raw PaySim data, tokens, admin secrets, account identifiers, or device identifiers
- implemented/future work ambiguity

## Series Files

The 11 files in this directory are the final publication candidate sequence:

| No. | File | Main Question |
|---:|---|---|
| 1 | [API는 빨랐는데 탐지는 늦을 수 있다](01-kafka-fraud-system-problem.md) | API와 Consumer를 왜 분리했는가 |
| 2 | [eventId, traceId, userId를 같은 식별자로 쓰지 않은 이유](02-event-schema-audit-model.md) | 추적, 중복 방어, ordering 기준을 어떻게 분리했는가 |
| 3 | [DB 저장 전에 ack하면 무엇이 사라지는가](03-consumer-manual-ack-reprocessing.md) | offset commit 시점을 어디에 둘 것인가 |
| 4 | [Redis가 죽으면 탐지를 멈출 것인가](04-redis-sliding-window-degraded-mode.md) | Redis 장애를 어떻게 기록할 것인가 |
| 5 | [재처리는 복구 기능이면서 운영자 조작 위험이다](05-dlt-reprocessing-admin-safety.md) | 재처리/폐기를 어떻게 감사 가능하게 만들 것인가 |
| 6 | [API p95가 정상인데 탐지가 밀리는 상황](06-consumer-lag-detection-latency.md) | API latency와 detection latency를 왜 분리했는가 |
| 7 | [k6 결과를 좋게 보이게 쓰지 않기](07-load-failure-test-evidence.md) | 부하/장애 시 어떤 지표를 봐야 하는가 |
| 8 | [raw PaySim을 커밋하지 않고 재현성을 남기기](08-paysim-replayable-events.md) | raw data 없이 재현성을 어떻게 남길 것인가 |
| 9 | [precision/recall을 믿기 전에 분모부터 고정했다](09-paysim-replay-evaluation-evidence.md) | precision/recall을 과장하지 않으려면 무엇을 기록해야 하는가 |
| 10 | [active, stored, evaluator ruleVersion을 섞지 않기](10-rule-version-traceability.md) | Java/Python drift와 active/stored 혼동을 어떻게 막을 것인가 |
| 11 | [마지막 정리: 설명 가능한 변경만 남기기](11-rule-version-change-runbook-evidence-closure.md) | rollback readiness와 automatic rollback을 어떻게 구분할 것인가 |

Archived source drafts remain under `../drafts`. They are source material, not the final publication order.
