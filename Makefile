.PHONY: help build test test-common test-api test-consumer redis-integration-test failure-drill-redis failure-drill-consumer failure-drill-dlt dlt-drill failure-drill ci-check clean api consumer infra-up infra-down infra-ps infra-logs infra-config observability-check observability-rules-check scripts-check data-env data-python-check data-policy-check download-paysim prepare-paysim prepare-paysim-smoke profile-paysim-v3 validate-paysim validate-paysim-strict generate-paysim-sample generate-paysim-sample-strict replay-paysim-sample replay-paysim-sample-dry-run replay-paysim-processed-smoke evaluate-paysim-sample evaluate-paysim-sample-no-replay-report evaluate-paysim-replay evaluate-paysim-native-replay evaluate-paysim-threshold-policy-report evaluate-paysim-threshold-regression verify-paysim-evaluation-report-contract verify-paysim-native-replay-contract verify-paysim-rule-threshold-regression verify-paysim-rule-version-contract verify-paysim-result-rule-version-contract verify-v2-phase7 verify-v2-phase8 verify-v2-phase9 verify-v2-phase11 verify-v2-phase12 verify-v2-phase13 verify-v3-workload-manifests verify-v3-phase0 verify-v3-phase3-partition-assignment v2-phase7-evidence v2-phase8-evidence v2-phase9-evidence test-data-scripts test-data-scripts-ci topics smoke k6-smoke k6-normal k6-peak k6-duplicate k6-duplicate-check k6-redis-down k6-v3-baseline k6-v3-phase1-capacity k6-v3-phase1-knee k6-v3-phase1-recovery k6-v3-phase2-state-baseline k6-v3-phase2-state-pressure k6-v3-phase3-partition-balanced k6-v3-phase3-partition-skew k6-v3-phase4-stateful-redelivery k6-v3-phase5-late-out-of-order k6-v3-phase6-organic-burst k6-v3-phase6-catch-up-burst final-check

DATA_VENV_DIR ?= .venv-data
DATA_PYTHON := $(DATA_VENV_DIR)/bin/python

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
	@echo "  make failure-drill-dlt - Run local DLT admin operation drill with synthetic DB seed"
	@echo "  make dlt-drill      - Alias for failure-drill-dlt"
	@echo "  make failure-drill  - Run automated Redis failure drill only"
	@echo "  make ci-check       - Run lightweight CI checks"
	@echo "  make clean          - Clean Gradle build outputs"
	@echo "  make api            - Run app-api"
	@echo "  make consumer       - Run app-consumer"
	@echo "  make infra-config   - Validate docker compose config"
	@echo "  make observability-check - Validate local Prometheus/Grafana provisioning files"
	@echo "  make observability-rules-check - Validate Prometheus alert rule syntax with promtool"
	@echo "  make infra-up       - Start local infrastructure"
	@echo "  make infra-down     - Stop local infrastructure"
	@echo "  make infra-ps       - Show local infrastructure status"
	@echo "  make infra-logs     - Show local infrastructure logs"
	@echo "  make scripts-check  - Validate shell scripts"
	@echo "  make data-env       - Create local Python env for PaySim data helpers"
	@echo "  make data-policy-check - Validate V2 PaySim data commit policy"
	@echo "  make download-paysim - Download PaySim raw CSV locally"
	@echo "  make prepare-paysim - Normalize PaySim CSV into processed JSONL"
	@echo "  make prepare-paysim-smoke - Normalize a limited PaySim subset"
	@echo "  make profile-paysim-v3 - Generate the local ignored V3 PaySim corpus profile"
	@echo "  make validate-paysim - Validate processed PaySim outputs"
	@echo "  make validate-paysim-strict - Validate PaySim outputs with non-default salt policy"
	@echo "  make generate-paysim-sample - Generate safe PaySim JSONL samples"
	@echo "  make replay-paysim-sample-dry-run - Validate replay payloads without HTTP"
	@echo "  make replay-paysim-sample - Replay committed PaySim sample into local app-api"
	@echo "  make evaluate-paysim-sample - Evaluate local PaySim detection result export"
	@echo "  make evaluate-paysim-replay - Evaluate existing PaySim labels and local detection result export"
	@echo "  make evaluate-paysim-threshold-policy-report - Generate default Phase 9 threshold policy report"
	@echo "  make verify-paysim-evaluation-report-contract - Verify Phase 7 report schema with fixtures"
	@echo "  make verify-paysim-native-replay-contract - Verify Phase 8 native type contract with fixtures"
	@echo "  make verify-paysim-rule-threshold-regression - Verify Phase 9 rule/threshold regression contract"
	@echo "  make verify-paysim-rule-version-contract - Verify Phase 11 Java/Python ruleVersion contract"
	@echo "  make verify-paysim-result-rule-version-contract - Verify Phase 12 per-result ruleVersion contract"
	@echo "  make verify-v2-phase7 - Run CI-safe Phase 7 checks without full PaySim/local DB export"
	@echo "  make verify-v2-phase8 - Run CI-safe Phase 8 checks without full PaySim/local DB export"
	@echo "  make verify-v2-phase9 - Run CI-safe Phase 9 checks without full PaySim/local DB export"
	@echo "  make verify-v2-phase11 - Run CI-safe Phase 11 checks without full PaySim/local DB export"
	@echo "  make verify-v2-phase12 - Run CI-safe Phase 12 checks without full PaySim/local DB export"
	@echo "  make verify-v2-phase13 - Run CI-safe V2 data/evaluation guardrails; Phase 13 Java tests run through Gradle build/final-check"
	@echo "  make verify-v3-workload-manifests - Validate committed V3 workload manifests"
	@echo "  make verify-v3-phase3-partition-assignment - Verify Phase 3 partition workloads do not create hot-user pressure"
	@echo "  make verify-v3-phase0 - Run CI-safe V3 Phase 0 data/workload checks"
	@echo "  make v2-phase7-evidence - Generate local/manual Phase 7 evidence from existing detection result export"
	@echo "  make v2-phase8-evidence - Generate local/manual Phase 8 evidence from existing detection result export"
	@echo "  make v2-phase9-evidence - Generate local/manual Phase 9 evidence from existing detection result export"
	@echo "  make test-data-scripts - Run Python data script tests"
	@echo "  make test-data-scripts-ci - Run Python data script tests without bootstrapping KaggleHub"
	@echo "  make topics         - Create Kafka topics"
	@echo "  make smoke          - Run local smoke test"
	@echo "  make k6-smoke       - Run short k6 smoke scenario"
	@echo "  make k6-normal      - Run normal load k6 scenario"
	@echo "  make k6-peak        - Run peak load k6 scenario"
	@echo "  make k6-duplicate   - Run duplicate replay k6 scenario"
	@echo "  make k6-duplicate-check - Run duplicate replay and DB count check"
	@echo "  make k6-redis-down  - Run Redis down load k6 scenario"
	@echo "  make k6-v3-baseline - Run the committed V3 Phase 0 normal baseline"
	@echo "  make k6-v3-phase1-capacity - Run V3 Phase 1 capacity discovery workload"
	@echo "  make k6-v3-phase1-knee - Run V3 Phase 1 knee confirmation workload"
	@echo "  make k6-v3-phase1-recovery - Run V3 Phase 1 backlog recovery workload"
	@echo "  make k6-v3-phase2-state-baseline - Run V3 Phase 2 low Redis state-size workload"
	@echo "  make k6-v3-phase2-state-pressure - Run V3 Phase 2 high Redis state-size workload"
	@echo "  make k6-v3-phase3-partition-balanced - Run V3 Phase 3 balanced partition-affinity workload"
	@echo "  make k6-v3-phase3-partition-skew - Run V3 Phase 3 hot partition-affinity workload"
	@echo "  make k6-v3-phase4-stateful-redelivery - Run V3 Phase 4 deterministic redelivery drill workload"
	@echo "  make k6-v3-phase5-late-out-of-order - Run V3 Phase 5 controlled lateness workload"
	@echo "  make k6-v3-phase6-organic-burst - Run V3 Phase 6 organic burst source-emulator workload"
	@echo "  make k6-v3-phase6-catch-up-burst - Run V3 Phase 6 catch-up burst source-emulator workload"
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

failure-drill-dlt:
	bash scripts/failure_drills/dlt_admin_drill.sh

dlt-drill: failure-drill-dlt

failure-drill:
	$(MAKE) failure-drill-redis
	@echo "Consumer restart drill requires manual app-consumer restart. Run: make failure-drill-consumer"

ci-check:
	./gradlew test
	./gradlew assemble
	$(MAKE) test-data-scripts-ci
	$(MAKE) data-policy-check

clean:
	./gradlew clean

api:
	./gradlew :app-api:bootRun

consumer:
	./gradlew :app-consumer:bootRun

infra-config:
	docker compose -f infra/docker-compose.yml config

observability-check:
	docker compose -f infra/docker-compose.yml config >/dev/null
	test -f infra/grafana/provisioning/datasources/prometheus.yml
	test -f infra/grafana/provisioning/dashboards/dashboard-provider.yml
	test -f infra/grafana/dashboards/fraud-observability.json
	test -f infra/grafana/dashboards/v3-stream-foundation.json
	python3 -m json.tool infra/grafana/dashboards/fraud-observability.json >/dev/null
	python3 -m json.tool infra/grafana/dashboards/v3-stream-foundation.json >/dev/null
	test -f infra/prometheus/rules/fraud-alerts.yml

observability-rules-check:
	docker run --rm --entrypoint promtool -v "$$(pwd)/infra/prometheus:/etc/prometheus:ro" prom/prometheus:v2.54.1 check rules /etc/prometheus/rules/fraud-alerts.yml

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
	bash -n scripts/data/*.sh

data-env:
	bash scripts/data/bootstrap-data-env.sh

data-python-check: data-env
	$(DATA_PYTHON) -c "import sys; print(sys.executable)"
	$(DATA_PYTHON) -c "import kagglehub; print('kagglehub import ok')"

data-policy-check:
	bash scripts/data/check-data-policy.sh

download-paysim: data-env
	$(DATA_PYTHON) scripts/data/download_paysim_dataset.py

prepare-paysim: data-env
	$(DATA_PYTHON) scripts/data/prepare_paysim_dataset.py

prepare-paysim-smoke: data-env
	$(DATA_PYTHON) scripts/data/prepare_paysim_dataset.py --limit 1000 --force

profile-paysim-v3: data-env
	$(DATA_PYTHON) scripts/data/profile_paysim_v3.py --force

validate-paysim: data-env
	$(DATA_PYTHON) scripts/data/validate_paysim_outputs.py

validate-paysim-strict: data-env
	$(DATA_PYTHON) scripts/data/validate_paysim_outputs.py --require-non-default-salt

generate-paysim-sample: data-env
	$(DATA_PYTHON) scripts/data/generate_paysim_samples.py --sample-size 1000 --strategy balanced --force

generate-paysim-sample-strict: data-env
	$(DATA_PYTHON) scripts/data/generate_paysim_samples.py --sample-size 1000 --strategy balanced --require-non-default-salt --force

replay-paysim-sample: data-env
	$(DATA_PYTHON) scripts/data/replay_paysim_events.py --input data/samples/paysim-events-sample.jsonl --max-events 100 --rate-per-second 10 --force

replay-paysim-sample-dry-run: data-env
	$(DATA_PYTHON) scripts/data/replay_paysim_events.py --input data/samples/paysim-events-sample.jsonl --max-events 100 --dry-run --force

replay-paysim-processed-smoke: data-env
	$(DATA_PYTHON) scripts/data/replay_paysim_events.py --input data/processed/paysim-events.jsonl --max-events 1000 --rate-per-second 20 --force

evaluate-paysim-sample: data-env
	$(DATA_PYTHON) scripts/data/evaluate_paysim_replay_results.py --labels data/samples/paysim-labels-sample.jsonl --results data/processed/paysim-detection-results.jsonl --replay-report data/processed/paysim-replay-report.json --strict --force

evaluate-paysim-sample-no-replay-report: data-env
	$(DATA_PYTHON) scripts/data/evaluate_paysim_replay_results.py --labels data/samples/paysim-labels-sample.jsonl --results data/processed/paysim-detection-results.jsonl --strict --force

evaluate-paysim-replay: evaluate-paysim-sample

evaluate-paysim-native-replay: evaluate-paysim-replay

evaluate-paysim-threshold-policy-report: evaluate-paysim-replay

evaluate-paysim-threshold-regression: evaluate-paysim-threshold-policy-report

verify-paysim-evaluation-report-contract: data-env
	$(DATA_PYTHON) scripts/data/verify_paysim_evaluation_report_contract.py

verify-paysim-native-replay-contract: data-env
	$(DATA_PYTHON) scripts/data/verify_paysim_native_replay_contract.py

verify-paysim-rule-threshold-regression: data-env
	$(DATA_PYTHON) scripts/data/verify_paysim_rule_threshold_regression.py

verify-paysim-rule-version-contract: data-env
	$(DATA_PYTHON) scripts/data/verify_paysim_rule_version_contract.py

verify-paysim-result-rule-version-contract: data-env
	$(DATA_PYTHON) scripts/data/verify_paysim_result_rule_version_contract.py

verify-v2-phase7: test-data-scripts data-policy-check verify-paysim-evaluation-report-contract

verify-v2-phase8: test-data-scripts data-policy-check verify-paysim-evaluation-report-contract verify-paysim-native-replay-contract

verify-v2-phase9: test-data-scripts data-policy-check verify-paysim-evaluation-report-contract verify-paysim-native-replay-contract verify-paysim-rule-threshold-regression

verify-v2-phase11: test-data-scripts data-policy-check verify-paysim-evaluation-report-contract verify-paysim-native-replay-contract verify-paysim-rule-threshold-regression verify-paysim-rule-version-contract

verify-v2-phase12: test-data-scripts data-policy-check verify-paysim-evaluation-report-contract verify-paysim-native-replay-contract verify-paysim-rule-threshold-regression verify-paysim-rule-version-contract verify-paysim-result-rule-version-contract

verify-v2-phase13: verify-v2-phase12

verify-v3-workload-manifests: data-env
	$(DATA_PYTHON) scripts/data/validate_v3_workload_manifest.py \
		load-test/workloads/v3/normal-baseline-v1.json \
		load-test/workloads/v3/capacity-discovery-v1.json \
		load-test/workloads/v3/knee-confirmation-v1.json \
		load-test/workloads/v3/backlog-recovery-v1.json \
		load-test/workloads/v3/state-size-baseline-v1.json \
		load-test/workloads/v3/state-size-high-density-v1.json \
		load-test/workloads/v3/partition-balanced-v1.json \
		load-test/workloads/v3/partition-skew-hot-p2-v1.json \
		load-test/workloads/v3/stateful-redelivery-v1.json \
		load-test/workloads/v3/late-out-of-order-v1.json \
		load-test/workloads/v3/organic-burst-v1.json \
		load-test/workloads/v3/catch-up-burst-v1.json
	$(DATA_PYTHON) scripts/data/verify_v3_phase3_partition_assignment.py

verify-v3-phase3-partition-assignment: data-env
	$(DATA_PYTHON) scripts/data/verify_v3_phase3_partition_assignment.py

verify-v3-phase0: test-data-scripts data-policy-check verify-v3-workload-manifests

v2-phase7-evidence: evaluate-paysim-replay

v2-phase8-evidence: evaluate-paysim-native-replay

v2-phase9-evidence: evaluate-paysim-threshold-policy-report

test-data-scripts: data-env
	$(DATA_PYTHON) -m unittest discover -s scripts/data -p 'test_*.py'

test-data-scripts-ci: data-env
	$(DATA_PYTHON) -m unittest discover -s scripts/data -p 'test_*.py'

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

k6-duplicate-check:
	EVENT_PREFIX=phase13 k6 run load-test/k6/scenarios/duplicate-replay.js
	bash scripts/load_tests/check_duplicate_result_count.sh phase13-duplicate-fixed-event-id

k6-redis-down:
	bash scripts/load_tests/run_redis_down_load.sh

k6-v3-baseline:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=20260819-phase0-baseline-150 make k6-v3-baseline" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" run load-test/k6/scenarios/v3-normal-baseline.js

k6-v3-phase1-capacity:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase1-discovery-001 make k6-v3-phase1-capacity" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=capacity-discovery-v1.json run load-test/k6/scenarios/v3-phase1-throughput.js

k6-v3-phase1-knee:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase1-knee-before-001 make k6-v3-phase1-knee" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=knee-confirmation-v1.json run load-test/k6/scenarios/v3-phase1-throughput.js

k6-v3-phase1-recovery:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase1-recovery-before-001 make k6-v3-phase1-recovery" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=backlog-recovery-v1.json run load-test/k6/scenarios/v3-phase1-throughput.js

k6-v3-phase2-state-baseline:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase2-state-baseline-001 make k6-v3-phase2-state-baseline" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=state-size-baseline-v1.json run load-test/k6/scenarios/v3-phase2-stateful-window.js

k6-v3-phase2-state-pressure:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase2-state-pressure-001 make k6-v3-phase2-state-pressure" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=state-size-high-density-v1.json run load-test/k6/scenarios/v3-phase2-stateful-window.js

k6-v3-phase3-partition-balanced:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase3-balanced-c6-001 make k6-v3-phase3-partition-balanced" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=partition-balanced-v1.json run load-test/k6/scenarios/v3-phase3-partition-skew.js

k6-v3-phase3-partition-skew:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase3-hot-p2-c6-001 make k6-v3-phase3-partition-skew" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=partition-skew-hot-p2-v1.json run load-test/k6/scenarios/v3-phase3-partition-skew.js

k6-v3-phase4-stateful-redelivery:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase4-after-redis-001 make k6-v3-phase4-stateful-redelivery" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=stateful-redelivery-v1.json run load-test/k6/scenarios/v3-phase4-stateful-redelivery.js

k6-v3-phase5-late-out-of-order:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase5-late-out-of-order-001 make k6-v3-phase5-late-out-of-order" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=late-out-of-order-v1.json run load-test/k6/scenarios/v3-phase5-late-out-of-order.js

k6-v3-phase6-organic-burst:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase6-organic-001 make k6-v3-phase6-organic-burst" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=organic-burst-v1.json run load-test/k6/scenarios/v3-phase6-source-delay.js

k6-v3-phase6-catch-up-burst:
	@test -n "$(V3_RUN_ID)" || (echo "V3_RUN_ID is required, for example: V3_RUN_ID=phase6-catch-up-001 make k6-v3-phase6-catch-up-burst" && exit 1)
	k6 -e V3_RUN_ID="$(V3_RUN_ID)" -e V3_COMMIT_SHA="$$(git rev-parse --short HEAD)$$(git diff --quiet || echo -dirty)" -e V3_WORKLOAD_MANIFEST=catch-up-burst-v1.json run load-test/k6/scenarios/v3-phase6-source-delay.js

final-check: build infra-config observability-check scripts-check verify-v2-phase13 verify-v3-phase0
