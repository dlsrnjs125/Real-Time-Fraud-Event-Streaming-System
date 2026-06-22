# Kafka Unavailable Drill

## Purpose

Verify how the API producer and Consumer behave when the local Kafka broker is temporarily unavailable.

Kafka unavailable handling is documented as a runbook in Phase 8 instead of a default automation script because stopping Kafka affects topic metadata, producer timeout behavior, Consumer reconnect logs, and every local workflow that depends on the broker.

## Preconditions

- Local infrastructure is running with `make infra-up`.
- Topics were created with `make topics`.
- `app-api` is running with `make api`.
- `app-consumer` is running with `make consumer`.
- Test data uses synthetic identifiers only.

## Procedure

1. Check the current health endpoints.

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

2. Stop Kafka.

```bash
docker compose -f infra/docker-compose.yml stop kafka
```

3. Try to publish a transaction event.

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-phase8-kafka-down-001","userId":"user-phase8-kafka","accountId":"acc-phase8-kafka","amount":10000,"currency":"KRW","merchantId":"merchant-phase8","deviceId":"device-phase8","location":"SEOUL","eventType":"PAYMENT","eventTime":"2026-06-22T00:00:00Z"}'
```

4. Confirm that API does not return a successful publish response for a broker outage. The core PASS criterion is a non-2xx response. Local verification usually expects `503 Service Unavailable`, but timeout and exception type can change the exact status.

5. Restart Kafka and wait for readiness.

```bash
docker compose -f infra/docker-compose.yml start kafka
./scripts/wait-for-kafka.sh
./scripts/create-topics.sh
```

6. Confirm that the Consumer reconnects and polling resumes from logs.

```bash
make infra-logs
```

7. Publish a new event after recovery and verify fraud result and processing log.

```bash
curl -X POST http://localhost:8080/api/v1/transactions/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-phase8-kafka-recovery-001","userId":"user-phase8-kafka","accountId":"acc-phase8-kafka","amount":10000,"currency":"KRW","merchantId":"merchant-phase8","deviceId":"device-phase8","location":"SEOUL","eventType":"PAYMENT","eventTime":"2026-06-22T00:05:00Z"}'
curl http://localhost:8080/api/v1/admin/events/evt-phase8-kafka-recovery-001/fraud-result
curl http://localhost:8080/api/v1/admin/events/evt-phase8-kafka-recovery-001/processing-log
```

## Expected Result

- API publish should not return `202 Accepted` while Kafka is unavailable.
- The receipt should be marked as publish failed by app-api when publish fails.
- app-consumer should reconnect after Kafka comes back.
- Retry/DLT automation is intentionally out of Phase 8 scope.

## Evidence To Capture

- API HTTP status and error response.
- app-api publish failure log.
- app-consumer reconnect log after Kafka recovery.
- Kafka topic list after recovery.
- Fraud result and processing log for the recovery event.

## Limitations

- Phase 8 does not implement automatic Kafka outage recovery beyond existing client reconnect behavior.
- Retry topics, DLT persistence, and DLQ reprocessing are handled in later phases.
- This drill is not a load test.
