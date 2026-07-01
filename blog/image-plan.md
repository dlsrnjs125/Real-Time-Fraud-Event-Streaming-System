# Blog Image Plan

이미지는 모든 글에 넣지 않습니다. 문제 재현, 성능 측정, 운영 evidence를 설명하는 데 필요한 경우만 사용합니다.

## Current Status

Initial evidence screenshots have been added for Prometheus scrape target health, the local Grafana observability dashboard, k6 duplicate replay interpretation, Redis degraded drill evidence, DLT admin operation evidence, and PaySim evaluation summary evidence. Mermaid diagrams remain embedded directly in the relevant posts where they are enough to explain the flow.

## Image Candidates

| Priority | Series | Image | Type | Planned File Name | Purpose | Status |
|---:|---:|---|---|---|---|---|
| 1 | 1 | 전체 아키텍처 흐름 | Mermaid in post | N/A | API, Kafka, Consumer, Redis, PostgreSQL, DLT 흐름 설명 | Done as Mermaid |
| 2 | 3 | Consumer processing sequence | Mermaid in post | N/A | manual ack와 persistence 이후 ack 순서 설명 | Done as Mermaid |
| 3 | 4 | Redis degraded mode flow | Mermaid in post | N/A | Redis 장애 시 skipped rule과 degraded result 설명 | Done as Mermaid |
| 4 | 5 | DLT reprocess/discard flow | Mermaid in post | N/A | 운영자 조작과 audit log 설명 | Done as Mermaid |
| 5 | 6 | Prometheus scrape targets | Screenshot | `blog/images/06-prometheus-targets-api-consumer-up.png` | Prometheus scrape target health for app-api and app-consumer | Added |
| 6 | 6 | Grafana observability dashboard | Screenshot | `blog/images/06-grafana-observability-dashboard.png` | Local Grafana dashboard for API status, p95, Redis degraded, processing latency, and DLT operation counter | Added |
| 6 | 4 | k6 Redis down summary | Screenshot | `blog/images/04-k6-redis-down-summary.png` | `make k6-redis-down` terminal summary with degraded/skipped metric before-after values | Added |
| 6 | 4 | Prometheus Redis degraded metric | Screenshot | `blog/images/04-prometheus-redis-window-degraded-total.png` | Prometheus graph for `fraud_redis_window_degraded_total` after Redis down drill | Added |
| 6 | 6 | Grafana Kafka Consumer Lag panel | Screenshot | `blog/images/06-grafana-kafka-consumer-lag.png` | Kafka consumer group lag panel after consumer stop/start or backlog drill | Capture candidate |
| 6 | 4 | Grafana Redis degraded dashboard | Screenshot | `blog/images/04-grafana-redis-degraded-dashboard.png` | Redis 장애 시 degraded/skipped signal 확인 | Capture candidate |
| 7 | 7 | Grafana API status count | Screenshot | `blog/images/07-grafana-api-status-count.png` | duplicate replay 이후 status bucket 확인 | Capture candidate |
| 8 | 7 | k6 duplicate replay summary | Screenshot | `blog/images/07-k6-duplicate-replay-summary.png` | k6 duplicate replay summary showing high `http_req_failed` with 100% `accepted or duplicate` checks | Added |
| 9 | 5 | DLT admin drill result | Screenshot | `blog/images/05-dlt-admin-drill-result.png` | `make failure-drill-dlt` terminal summary showing Admin discard API, audit log, and `fraud_dlt_discarded_total` increase | Added |
| 9 | 5 | Grafana DLT operation counters | Screenshot | `blog/images/05-grafana-dlt-operation-counters.png` | Grafana DLT operation counter after DLT admin drill; operation counter, not backlog gauge | Added |
| 10 | 9 | PaySim evaluation summary | Screenshot | `blog/images/09-paysim-evaluation-summary.png` | PaySim evaluation report JSON 화면으로 precision/recall보다 denominator, missing, excluded count를 먼저 보여줌 | Added |
| 11 | 8 | PaySim preprocessing pipeline | Mermaid in post | N/A | raw -> processed -> sample -> replay 흐름 설명 | Done as Mermaid |
| 12 | 10 | ruleVersion traceability flow | Mermaid in post | N/A | active -> stored -> admin/evaluator 연결 설명 | Done as Mermaid |
| 13 | 11 | runbook decision flow | Mermaid in post | N/A | pre-check -> deploy/hold -> post-check -> rollback readiness 설명 | Done as Mermaid |
| 14 | 12 | retrospective learning map | Mermaid in post | N/A | API latency, Consumer Lag, Redis degraded, DLT, PaySim evaluation, ruleVersion을 회고 관점으로 연결 | Done as Mermaid |

## First Screenshot Set

이미지를 한 번에 많이 만들기보다 evidence 역할이 큰 항목부터 추가합니다.

| Order | File | Why First |
|---:|---|---|
| 1 | `blog/images/06-grafana-observability-dashboard.png` | local dashboard가 provisioning으로 자동 로딩되는지 보여줌 |
| 2 | `blog/images/04-grafana-redis-degraded-dashboard.png` | Redis 장애가 전체 성공/실패가 아니라 degraded/skipped signal로 기록된다는 점을 보여줌 |
| 3 | `blog/images/07-grafana-api-status-count.png` | duplicate replay 이후 status bucket이 서버 metric으로 보이는지 보여줌 |
| 4 | `blog/images/07-k6-duplicate-replay-summary.png` | client 관점의 p95/p99와 duplicate 해석 기준을 보여줌 |
| 5 | `blog/images/05-dlt-admin-drill-result.png` | 재처리/폐기 조작이 audit evidence로 남는다는 점을 보여줌 |
| 6 | `blog/images/09-paysim-evaluation-summary.png` | PaySim evaluation report JSON 화면으로 precision/recall보다 denominator, missing, excluded count를 먼저 보여줌 |

## Screenshot Capture Candidates

| File | Target Post Section | Capture Source | Must Hide |
|---|---|---|---|
| `blog/images/06-grafana-observability-dashboard.png` | `06`의 `확인한 증거` 섹션 | Grafana dashboard after local run | host secrets, raw identifiers, tokens |
| `blog/images/06-grafana-kafka-consumer-lag.png` | `06`의 `확인한 증거` 섹션 | Grafana Kafka Consumer Lag panel after backlog drill | raw payload, accountId, deviceId, host secrets |
| `blog/images/04-grafana-redis-degraded-dashboard.png` | `04`의 `확인한 증거` 섹션 | Grafana Redis degraded/skipped panels after Redis down drill | accountId, deviceId, raw payload |
| `blog/images/07-grafana-api-status-count.png` | `07`의 `검증` 섹션 | Grafana API status panel after duplicate replay | local paths containing sensitive names, raw request payloads |
| `blog/images/07-k6-duplicate-replay-summary.png` | `07`의 `검증` 섹션 | k6 duplicate replay terminal summary | local paths containing sensitive names, raw request payloads |
| `blog/images/05-dlt-admin-drill-result.png` | `05`의 `확인한 증거` 섹션 | sanitized DLT admin operation drill result | admin token, accountId, deviceId, raw payload |
| `blog/images/05-grafana-dlt-operation-counters.png` | `05`의 `확인한 증거` 섹션 | Grafana DLT Operation Counters panel after admin drill | tokens, raw payload, backlog/count ambiguity |
| `blog/images/09-paysim-evaluation-summary.png` | `09`의 `확인한 증거` 섹션 | sanitized `paysim-evaluation-report.json` 화면 | raw PaySim rows, raw identifiers, local salt |

DLT Operation Counters in Grafana may show No data until a DLT publish/reprocess/discard operation is generated. For DLT evidence, run `make failure-drill-dlt` first. The first-choice image is a sanitized terminal drill result showing the Admin discard operation, audit log check, and operation metric increase; a Grafana DLT Operation Counters capture is secondary evidence. Do not mix Consumer DLT publish evidence with Admin DLT operation evidence. For screenshots, use only synthetic eventId/traceId values or mask them partially, in addition to hiding tokens, raw payload, accountId, and deviceId.

DLT evidence is not a screenshot made just to fill an empty dashboard panel. It should show that an event isolated in DLT can be discarded or reprocessed through an operator flow, with audit log and operation counter evidence.

## Boundaries

- Do not add images that only decorate the post.
- Do not capture raw/full PaySim data.
- Do not show secrets, admin tokens, local credentials, account identifiers, or device identifiers.
- Prefer diagrams for architecture/process flow and screenshots only for measured or operational evidence.
- Do not add broken image links to posts before the files exist.
- Store future image files under `blog/images/` unless a later decision chooses another directory.
