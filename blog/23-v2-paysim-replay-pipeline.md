# V2 PaySim Replay Pipeline

## 문제

V2 Phase 2~4에서 PaySim CSV를 runtime event JSONL로 정규화하고 sample까지 만들었습니다. 하지만 JSONL이 안전하다는 것과 실제 app-api, Kafka, Consumer 경로에 넣어도 안전하다는 것은 다른 문제입니다.

Replay boundary에서는 다음 문제가 생길 수 있습니다.

- label field가 HTTP payload로 섞이는 문제
- client가 `receivedAt`을 보내 server generated time policy가 깨지는 문제
- deterministic `eventId` replay로 duplicate/idempotency 결과가 발생하는 문제
- DTO mapping mismatch로 PaySim 전용 field가 API에 그대로 들어가는 문제
- PaySim native event type이 current app-api enum과 충돌하는 문제
- 실패 분석 report에 payload나 token이 남는 문제
- rate limit 없이 local API/Kafka/DB를 압박하는 문제

## 초기 설계

Replay input은 events JSONL만 사용합니다. Label sidecar는 evaluation join 용도이고 HTTP payload로 보내지 않습니다.

Replay mode는 두 가지로 나눴습니다.

- `preserve`: PaySim eventId를 그대로 사용해 duplicate/idempotency behavior를 확인합니다.
- `prefix`: eventId 앞에 prefix를 붙여 같은 API/DB에 여러 dataset이나 sample을 섞어 넣을 때 collision을 줄입니다.

Dry-run과 actual replay도 분리했습니다. Dry-run은 app-api 없이 payload validation, DTO mapping, dropped field 집계, report 생성을 확인합니다. Actual replay는 local app-api와 infrastructure가 실행 중일 때만 수행합니다.

Event type은 current app-api 호환성을 기본값으로 잡았습니다. Phase 5에서는 Java DTO를 바꾸지 않기 때문에 `CASH_OUT`, `CASH_IN`, `DEBIT` 같은 PaySim native type은 HTTP로 보내기 전에 rejected로 집계합니다.

## 구현

추가한 script:

```text
scripts/data/replay_paysim_events.py
```

주요 Makefile target:

```bash
make replay-paysim-sample-dry-run
make replay-paysim-sample
make replay-paysim-processed-smoke
```

Current app-api DTO는 PaySim runtime event보다 좁습니다. 그래서 replay script는 `balanceFeatures`, `source`, `schemaVersion`, `destinationAccountId`를 HTTP body에서 제외하고 report의 `droppedFields`에 기록합니다.

`traceId`는 body가 아니라 `X-Trace-Id` header로 전달합니다.

Replay report는 `data/processed/paysim-replay-report.json`에 생성합니다. 이 파일은 commit 대상이 아닙니다.

## 트러블슈팅

app-api가 꺼져 있으면 actual replay는 `connectionError`로 집계됩니다. 이 실패는 정상적인 expected failure evidence로 남길 수 있습니다.

같은 sample을 preserve mode로 반복 replay하면 `409`가 발생할 수 있습니다. 이 결과는 `httpDuplicateOrConflict`로 집계합니다.

DTO mapping mismatch는 `droppedFields`로 확인합니다. 이 값이 늘어나면 API DTO 변경 또는 PaySim event contract 변경 여부를 검토해야 합니다.

PaySim native event type이 current API enum에 없으면 `unsupportedEventTypes`에 집계됩니다. 이는 replay script 오류가 아니라 Phase 5 scope에서 API DTO를 변경하지 않기로 한 결과입니다.

Report에는 request body, response body, token을 저장하지 않습니다. 실패 분석은 row number, eventId, reason 수준으로 제한합니다.

Rate limit은 `--rate-per-second`로 제어합니다. 기본값은 local smoke에 맞춘 보수적인 값입니다.

## 검증

기본 검증:

```bash
make test-data-scripts
make data-policy-check
make ci-check
make replay-paysim-sample-dry-run
```

Actual replay는 local app-api와 infrastructure가 실행 중일 때만 수행합니다.

```bash
docker compose -f infra/docker-compose.yml up -d
make api
make replay-paysim-sample
```

## 남은 한계

Rule Engine V2는 Phase 6 범위입니다.

Replay result와 label sidecar join/evaluation은 후속 단계에서 구현합니다.

대규모 replay 성능 측정은 별도 load/evidence 단계에서 다룹니다.

PaySim native `CASH_OUT`, `CASH_IN`, `DEBIT` actual replay는 Phase 6 이후 API DTO 확장 또는 Rule Engine V2 contract와 함께 다시 다룹니다.
