#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ $# -lt 1 ]]; then
  cat >&2 <<'EOF'
Usage:
  scripts/manufacture-claude.sh <identity-seed> [RegistryManufactureApplication options]

Examples:
  scripts/manufacture-claude.sh "Certificate III Electrotechnology"
  scripts/manufacture-claude.sh "Electric Vehicle Maintenance" --registry .uao-registry --register

Common options passed through:
  --registry <dir>
  --register
  --language <tag>
  --profile <name>
  --provider-timeout-seconds <1..3600>
  --catalog-limit <1..100000>
  --work-dir <dir>
  --dist-dir <dir>
  --repository-commit <sha>
EOF
  exit 2
fi

IDENTITY="$1"
shift

ADAPTER="$ROOT/adapters/claude-code/claude_provider.py"
[[ -x "$ADAPTER" ]] || { echo "Claude adapter missing or not executable: $ADAPTER" >&2; exit 2; }

JAR="$ROOT/target/uao-foundry-0.1.0.jar"
if [[ ! -f "$JAR" ]]; then
  mvn -B -ntp -q package -DskipTests
fi

exec java -cp "$JAR" \
  org.seventeenthsecond.uaofoundry.reuse.RegistryManufactureApplication \
  "$IDENTITY" \
  --provider-command "$ADAPTER" \
  "$@"
