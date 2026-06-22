.PHONY: help build test test-common test-api test-consumer redis-integration-test ci-check clean api consumer infra-up infra-down infra-ps infra-logs infra-config scripts-check topics smoke final-check

help:
	@echo "Available targets:"
	@echo "  make build          - Build all Gradle modules"
	@echo "  make test           - Run all tests"
	@echo "  make test-common    - Run app-common tests"
	@echo "  make test-api       - Run app-api tests"
	@echo "  make test-consumer  - Run app-consumer tests"
	@echo "  make redis-integration-test - Run Redis integration tests"
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

topics:
	./scripts/create-topics.sh

smoke:
	./scripts/run-smoke-test.sh

final-check: build infra-config scripts-check
