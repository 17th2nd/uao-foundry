#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 ]]; then echo "usage: $0 <package-dir>" >&2; exit 2; fi
java -jar target/uao-foundry-0.1.0.jar verify "$1"
