#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 2 ]]; then
  echo "usage: $0 <identity-seed> <fixture.json> [extra foundry args...]" >&2
  exit 2
fi
seed="$1"; fixture="$2"; shift 2
java -jar target/uao-foundry-0.1.0.jar manufacture "$seed" --fixture "$fixture" "$@"
