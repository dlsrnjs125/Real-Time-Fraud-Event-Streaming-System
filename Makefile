.PHONY: help build test test-common test-api test-consumer redis-integration-test failure-drill-redis failure-drill-consumer failure-drill ci-check clean api consumer infra-up infra-down infra-ps infra-logs infra-config scripts-check topics smoke k6-smoke k6-normal k6-peak k6-duplicate k6-redis-down final-check

help:
	@echo "Available targets:"
	@echo "  make build          - Build all Gradle modules"
	@echo "  make test           - Run all tests"
	@echo "  make test-common    - Run app-common tests"
	@echo "  make test-api       - Run app-api tests"
	@echo "  make test-consumer  - Run app-consumer tests"
	@echo "  make redis-integration-test - Run Redis integration tests"
	@echo "  make failure-drill-redis - Run Redis down failure drill"
	@echo "  make failure-drill-consumer - Run Consumer restart drill"
	@echo "  make failure-drill  - Run automated Redis failure drill only"
	@echo "  make ci-check       - Run lightweight CI checks"
	@echo "  make clean          - Clean Gradle build outputs"
	@echo "  make api            - Run app-api"
	@echo "  make consumer       - Run app-consumer"
	@echo "  make infra-config   - Validate docker compose config"
	@echo "  make infra-up       - Start local infrastructure"
	@echo "  make infra-down     - Stop local infrastructure"
	@echo "  make infra-ps       - Show local infrastructure status"
	@echo "  make infra-logs     - Show local infrastructure logs"
	@echo "  make scripts-check  - Validate shell scripts"
	@echo "  make topics         - Create Kafka topics"
	@echo "  make smoke          - Run local smoke test"
	@echo "  make k6-smoke       - Run short k6 smoke scenario"
	@echo "  make k6-normal      - Run normal load k6 scenario"
	@echo "  make k6-peak        - Run peak load k6 scenario"
	@echo "  make k6-duplicate   - Run duplicate replay k6 scenario"
	@echo "  make k6-redis-down  - Run Redis down load k6 scenario"
	@echo "  make final-check    - Run Phase validation checks"

build:
	./gradlew clean build

test:
	./gradlew test

test-common:
	./gradlew :app-common:test

test-api:
	./gradlew :app-api:test

test-consumer:
	./gradlew :app-consumer:test

redis-integration-test:
	docker compose -f infra/docker-compose.yml up -d redis
	@for i in 1 2 3 4 5; do \
		docker exec fraud-redis redis-cli -n 15 ping && exit 0; \
		sleep 1; \
	done; \
	echo "Redis did not become ready in time"; \
	exit 1
	./gradlew :app-consumer:redisIntegrationTest

failure-drill-redis:
	bash scripts/failure_drills/redis_down_drill.sh

failure-drill-consumer:
	bash scripts/failure_drills/consumer_restart_drill.sh

failure-drill:
	$(MAKE) failure-drill-redis
	@echo "Consumer restart drill requires manual app-consumer restart. Run: make failure-drill-consumer"

ci-check:
	./gradlew test
	./gradlew assemble

clean:
	./gradlew clean

api:
	./gradlew :app-api:bootRun

consumer:
	./gradlew :app-consumer:bootRun

infra-config:
	docker compose -f infra/docker-compose.yml config

infra-up:
	docker compose -f infra/docker-compose.yml up -d

infra-down:
	docker compose -f infra/docker-compose.yml down

infra-ps:
	docker compose -f infra/docker-compose.yml ps

infra-logs:
	docker compose -f infra/docker-compose.yml logs --tail=100

scripts-check:
	bash -n scripts/create-topics.sh
	bash -n scripts/reset-local-env.sh
	bash -n scripts/run-smoke-test.sh
	bash -n scripts/wait-for-kafka.sh
	bash -n scripts/failure_drills/*.sh
	bash -n scripts/load_tests/*.sh

topics:
	./scripts/create-topics.sh

smoke:
	./scripts/run-smoke-test.sh

k6-smoke:
	k6 run load-test/k6/scenarios/smoke.js

k6-normal:
	k6 run load-test/k6/scenarios/normal-load.js

k6-peak:
	k6 run load-test/k6/scenarios/peak-load.js

k6-duplicate:
	k6 run load-test/k6/scenarios/duplicate-replay.js

k6-redis-down:
	bash scripts/load_tests/run_redis_down_load.sh

final-check: build infra-config scripts-check
