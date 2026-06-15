#!/usr/bin/env bash
set -euo pipefail

docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d
./scripts/wait-for-kafka.sh
./scripts/create-topics.sh
