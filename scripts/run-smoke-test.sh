#!/usr/bin/env bash
set -euo pipefail

curl -fsS http://localhost:8080/actuator/health
echo
curl -fsS http://localhost:8081/actuator/health
echo
