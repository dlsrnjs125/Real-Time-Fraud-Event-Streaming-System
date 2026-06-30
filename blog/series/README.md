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
| 1 | [Kafka 기반 이상거래 탐지 시스템을 만든 이유](01-kafka-fraud-system-problem.md) | API와 Consumer를 왜 분리했는가 |
| 2 | [이벤트 스키마와 감사 저장 모델](02-event-schema-audit-model.md) | `eventId`, `traceId`, `userId`를 어떻게 잡았는가 |
| 3 | [Consumer manual ack와 재처리 가능성](03-consumer-manual-ack-reprocessing.md) | offset commit 시점을 어디에 둘 것인가 |
| 4 | [Redis sliding window와 degraded mode](04-redis-sliding-window-degraded-mode.md) | Redis 장애를 어떻게 기록할 것인가 |
| 5 | [DLT 재처리 API와 운영자 조작 보호](05-dlt-reprocessing-admin-safety.md) | 재처리/폐기를 어떻게 감사 가능하게 만들 것인가 |
| 6 | [Consumer Lag과 Detection Latency 관측](06-consumer-lag-detection-latency.md) | API latency와 detection latency를 왜 분리했는가 |
| 7 | [k6 부하/장애 테스트로 한계 측정](07-load-failure-test-evidence.md) | 부하/장애 시 어떤 지표를 봐야 하는가 |
| 8 | [PaySim 데이터를 replay 가능한 이벤트로 바꾸기](08-paysim-replayable-events.md) | raw data 없이 재현성을 어떻게 남길 것인가 |
| 9 | [PaySim replay evaluation을 evidence로 만들기](09-paysim-replay-evaluation-evidence.md) | precision/recall을 과장하지 않으려면 무엇을 기록해야 하는가 |
| 10 | [ruleVersion 추적성 설계](10-rule-version-traceability.md) | Java/Python drift와 active/stored 혼동을 어떻게 막을 것인가 |
| 11 | [ruleVersion 변경 runbook과 evidence closure](11-rule-version-change-runbook-evidence-closure.md) | rollback readiness와 automatic rollback을 어떻게 구분할 것인가 |

Archived source drafts remain under `../drafts`. They are source material, not the final publication order.
