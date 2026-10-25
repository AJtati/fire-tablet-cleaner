#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ ! -x "$ROOT/.venv/bin/python" ]; then
  echo "Create the environment first: python3 -m venv .venv && .venv/bin/pip install -e ." >&2
  exit 1
fi
exec "$ROOT/.venv/bin/python" -m firemanager.gui
