# k6 Load Tests

Phase 12 load tests are local Docker Compose evidence scenarios. They are intentionally not part of the default CI gate because load results depend on local CPU, memory, Docker resources, and whether the API and Consumer are running.

## Preconditions

```bash
make infra-up
make topics
make api
make consumer
```

Run `make api` and `make consumer` in separate terminals.

## Environment Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `API_BASE_URL` | `http://localhost:8080` | app-api base URL |
| `EVENT_PREFIX` | `phase12` | synthetic eventId prefix |
| `USER_PREFIX` | `user-phase12` | synthetic userId prefix |

Do not point `API_BASE_URL` at a production environment. Payloads use synthetic account, user, device, and merchant identifiers only.

## Commands

| Scenario | Command | Purpose |
| --- | --- | --- |
| Smoke | `make k6-smoke` | Short syntax/connectivity check |
| Normal Load | `make k6-normal` | Stable event intake load |
| Peak Load | `make k6-peak` | Ramping arrival-rate pressure |
| Duplicate Replay | `make k6-duplicate` | Duplicate `eventId` consistency check |
| Redis Down Load | `make k6-redis-down` | Degraded mode load with Redis stopped by script |

Duplicate replay consistency can be checked after the scenario with:

```bash
scripts/load_tests/check_duplicate_result_count.sh phase12-duplicate-fixed-event-id
```

`make k6-redis-down` prints Redis/degraded metric before and after values when `app-consumer` metrics are reachable. It also starts Redis again and waits for `redis-cli ping` during cleanup.

Raw k6 result files should be written under `load-test/k6/results/` and must not be committed. Keep only `results/.gitkeep`.
