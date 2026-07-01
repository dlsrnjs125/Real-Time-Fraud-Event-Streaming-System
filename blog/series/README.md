# Blog Series Writing Guardrail

This series records problems found during development and the design changes made in response. It is not a feature catalog or a general technology tutorial.

Status: publication candidate text complete. Images are tracked separately and should be referenced only when the screenshot or diagram actually exists.

## Writing Flow

Each post should keep this internal flow, but the published section names should match the topic of each post instead of exposing the same template repeatedly:

1. 문제
2. 초기 설계
3. 실제로 막힌 지점
4. 확인한 증거
5. 변경한 설계
6. 검증
7. 남은 한계

각 글은 최소한 이 세 가지를 남긴다.

1. 이 글의 핵심 질문
2. 실제로 부딪힌 판단/트러블슈팅
3. 이 글에서 말할 수 있는 것과 말하면 안 되는 것

`12-project-retrospective.md`는 회고 글이므로 writing flow를 엄격히 따르지 않는다. 대신 프로젝트 전체에서 기준이 바뀐 지점, 가장 크게 배운 점, 아쉬웠던 점, 다음 보완 방향을 중심으로 작성한다.

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
