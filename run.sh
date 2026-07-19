#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f .env ]; then
  echo "No .env file found. Copy .env.example to .env and set TWELVE_DATA_API_KEY." >&2
  exit 1
fi

set -a
source .env
set +a

if [ -z "${TWELVE_DATA_API_KEY:-}" ]; then
  echo "TWELVE_DATA_API_KEY is not set in .env." >&2
  exit 1
fi

cd src
javac App.java
java App
