# Troubleshooting Log

개발 중 설계 변경 또는 문제 해결이 발생하면 아래 형식으로 기록합니다.

## 기록 형식

### 문제 제목

#### 초기 설계

#### 발생한 문제

#### 재현 방법

#### 원인 분석

#### 변경한 설계

#### 개선 결과

#### 남은 한계

#### 다시 설계한다면

---

## 후보 1. Partition Key 변경

### 초기 설계

`eventId`를 partition key로 사용합니다.

### 발생 가능한 문제

같은 `userId`의 거래 이벤트가 여러 partition에 분산되어 사용자별 거래 순서가 깨질 수 있습니다.

### 변경 방향

`userId`를 partition key로 사용합니다.

### 확인할 지표

- 사용자별 이벤트 순서
- partition별 lag
- hot partition 발생 여부

---

## 후보 2. Auto Commit에서 Manual Ack로 변경

### 초기 설계

Kafka consumer auto commit을 사용합니다.

### 발생 가능한 문제

DB 저장 전 offset이 commit되면 Consumer 장애 시 처리되지 않은 이벤트가 유실된 것처럼 보일 수 있습니다.

### 변경 방향

처리 성공 후 manual ack를 수행합니다.

### 확인할 지표

- Consumer 재시작 후 재처리 여부
- 중복 fraud_result 생성 여부
- missing event count

---

## 후보 3. Redis INCR + TTL에서 ZSET Sliding Window로 변경

### 초기 설계

`userId`별 INCR + TTL로 최근 거래 횟수를 계산합니다.

### 발생 가능한 문제

고정 윈도우 경계에서 탐지 정확도가 흔들릴 수 있습니다.

### 변경 방향

ZSET에 `eventTime`을 score로 저장하고 sliding window 방식으로 최근 거래 수를 계산합니다.

### 확인할 지표

- velocity rule 탐지 정확도
- Redis command latency
- 오래된 이벤트 제거 여부
