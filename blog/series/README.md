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
