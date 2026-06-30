# Blog Image Plan

이미지는 모든 글에 넣지 않습니다. 문제 재현, 성능 측정, 운영 evidence를 설명하는 데 필요한 경우만 사용합니다.

## Current Status

No bitmap image files have been added in this pass. Mermaid diagrams are embedded directly in the relevant posts where they are enough to explain the flow.

## Image Candidates

| Priority | Series | Image | Type | Planned File Name | Purpose | Status |
|---:|---:|---|---|---|---|---|
| 1 | 1 | 전체 아키텍처 흐름 | Mermaid in post | N/A | API, Kafka, Consumer, Redis, PostgreSQL, DLT 흐름 설명 | Done as Mermaid |
| 2 | 3 | Consumer processing sequence | Mermaid in post | N/A | manual ack와 persistence 이후 ack 순서 설명 | Done as Mermaid |
| 3 | 4 | Redis degraded mode flow | Mermaid in post | N/A | Redis 장애 시 skipped rule과 degraded result 설명 | Done as Mermaid |
| 4 | 5 | DLT reprocess/discard flow | Mermaid in post | N/A | 운영자 조작과 audit log 설명 | Done as Mermaid |
| 5 | 6 | Consumer Lag / detection latency dashboard | Screenshot | `blog/images/06-consumer-lag-detection-latency-dashboard.png` | API latency와 detection latency 분리 evidence | Capture candidate |
| 6 | 7 | k6 result summary | Screenshot | `blog/images/07-k6-load-failure-summary.png` | normal/peak/duplicate/redis-down 결과 비교 | Capture candidate |
| 7 | 8 | PaySim preprocessing pipeline | Mermaid in post | N/A | raw -> processed -> sample -> replay 흐름 설명 | Done as Mermaid |
| 8 | 10 | ruleVersion traceability flow | Mermaid in post | N/A | active -> stored -> admin/evaluator 연결 설명 | Done as Mermaid |
| 9 | 11 | runbook decision flow | Mermaid in post | N/A | pre-check -> deploy/hold -> post-check -> rollback readiness 설명 | Done as Mermaid |

## Screenshot Capture Candidates

| File | Capture Source | Must Hide |
|---|---|---|
| `blog/images/06-consumer-lag-detection-latency-dashboard.png` | Grafana dashboard or Prometheus graph after local run | host secrets, raw identifiers, tokens |
| `blog/images/07-k6-load-failure-summary.png` | k6 terminal summary or Grafana load panel | local paths containing sensitive names, raw request payloads |
| `blog/images/05-dlt-admin-audit-response.png` | sanitized admin response or DB summary | admin token, accountId, deviceId, raw payload |

## Boundaries

- Do not add images that only decorate the post.
- Do not capture raw/full PaySim data.
- Do not show secrets, admin tokens, local credentials, account identifiers, or device identifiers.
- Prefer diagrams for architecture/process flow and screenshots only for measured or operational evidence.
- Do not add broken image links to posts before the files exist.
- Store future image files under `blog/images/` unless a later decision chooses another directory.
