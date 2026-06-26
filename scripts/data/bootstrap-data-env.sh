#!/usr/bin/env bash

set -euo pipefail

DATA_VENV_DIR="${DATA_VENV_DIR:-.venv-data}"
PYTHON="${PYTHON:-python3}"
REQUIREMENTS_FILE="scripts/data/requirements.txt"
VENV_PYTHON="${DATA_VENV_DIR}/bin/python"
PIP_CACHE_DIR="${PIP_CACHE_DIR:-${DATA_VENV_DIR}/.pip-cache}"

export PIP_CACHE_DIR
export PIP_DISABLE_PIP_VERSION_CHECK=1

if ! command -v "$PYTHON" >/dev/null 2>&1; then
  echo "ERROR: Python executable not found: ${PYTHON}" >&2
  echo "Set PYTHON=/path/to/python3 and retry: make data-env" >&2
  exit 1
fi

if [ ! -d "$DATA_VENV_DIR" ]; then
  echo "Creating data Python virtual environment: ${DATA_VENV_DIR}"
  if ! "$PYTHON" -m venv "$DATA_VENV_DIR"; then
    echo "ERROR: failed to create Python venv: ${DATA_VENV_DIR}" >&2
    echo "On Debian/Ubuntu, install python3-venv or use actions/setup-python in CI." >&2
    echo "You can also set PYTHON=/path/to/python3 and retry: make data-env" >&2
    exit 1
  fi
else
  echo "Reusing data Python virtual environment: ${DATA_VENV_DIR}"
fi

echo "Installing data requirements from ${REQUIREMENTS_FILE}"
"$VENV_PYTHON" -m pip install --disable-pip-version-check -r "$REQUIREMENTS_FILE"

"$VENV_PYTHON" -c "import kagglehub" >/dev/null

echo "PASS: data Python environment is ready"
