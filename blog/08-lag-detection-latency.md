# Consumer Lag과 Detection Latency 관측

## 문제

API가 빠르게 응답해도 Consumer Lag이 누적되면 이상거래 탐지는 늦어집니다.

## 초기 설계

API latency, Consumer Lag, detection latency, DLQ count, Redis degraded count를 함께 봅니다.

## 구현

Actuator, Micrometer, Prometheus, Grafana를 사용합니다.

## 측정 또는 재현

`/actuator/prometheus`에서 custom metric을 확인하고 Grafana dashboard로 시각화합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

Kafka broker metric과 애플리케이션 metric을 어떻게 연결해서 볼지 추가 설계가 필요합니다.
