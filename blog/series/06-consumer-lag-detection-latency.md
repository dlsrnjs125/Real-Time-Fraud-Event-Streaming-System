# Consumer Lag과 Detection Latency 관측

## 문제

API가 빠르게 응답해도 탐지가 늦으면 시스템은 위험해진다. 거래 접수 latency와 이상거래 detection latency를 같은 지표로 보면 Consumer backlog를 놓칠 수 있다.

## 초기 설계

API는 request count, API latency, error rate, Kafka publish success/failure를 본다. Consumer는 consumed event count, processing latency, detection latency, Consumer Lag, retry/DLT count, Redis degraded count를 본다. `traceId`와 `eventId`는 로그와 DB에서 흐름을 연결하는 기준으로 둔다.

## 실제로 막힌 지점

처음에는 API latency가 낮으면 전체 시스템이 정상이라고 해석하기 쉽다. 하지만 Kafka backlog가 쌓이면 API는 계속 빠르게 응답하면서도 탐지는 뒤에서 밀린다. 이때 필요한 질문은 “API가 빨랐는가”가 아니라 “Consumer가 얼마나 늦게 탐지했는가”다.

```mermaid
flowchart LR
    A[eventTime] --> B[receivedAt]
    B --> C[Kafka publish]
    C --> D[Consumer processed]
    D --> E[detectedAt]
    A -. "ingestDelay" .-> B
    B -. "detectionLatency" .-> E
    A -. "endToEndLatency" .-> E
```

## 확인한 증거

`docs/08-observability.md`와 `docs/15-slo-and-operational-readiness.md`에 API 지표와 Consumer 지표를 분리했다. load/failure 문서에서는 Consumer Lag max, recovery time, detection latency, DLT count, Redis degraded count를 함께 보도록 정리했다.

## 바꾼 설계

API latency는 접수 계층의 건강 신호로 제한한다. 비동기 탐지 상태는 Consumer Lag과 detection latency를 통해 본다. 지표 tag에는 `eventId`, `userId` 같은 high-cardinality 값을 넣지 않고, trace는 로그와 DB 조회로 연결한다.

## 검증

Prometheus/Grafana 후보 지표와 k6 시나리오를 문서화했다. 실제 dashboard screenshot은 아직 이미지로 첨부하지 않았으므로 `blog/image-plan.md`에서는 capture candidate로만 다룬다.

## 남은 한계

SLO threshold와 alert rule은 더 정교해질 수 있다. 특히 lag spike가 일시적인지, 지속적인 capacity 부족인지, Redis/DB 장애로 인한 처리 지연인지 구분하는 dashboard와 alert는 future work다.
