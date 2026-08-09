#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ $# -lt 1 ]]; then
  cat >&2 <<'EOF'
Usage:
  bash scripts/manufacture-claude.sh <identity-seed> [RegistryManufactureApplication options]

Examples:
  bash scripts/manufacture-claude.sh "Certificate III Electrotechnology"
  bash scripts/manufacture-claude.sh "Electric Vehicle Maintenance" --registry .uao-registry --register

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
[[ -f "$ADAPTER" ]] || { echo "Claude adapter missing: $ADAPTER" >&2; exit 2; }
python3 - "$ADAPTER" <<'PY' || { echo "Claude adapter failed Python syntax validation." >&2; exit 2; }
import ast, pathlib, sys
path = pathlib.Path(sys.argv[1])
ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
PY

JAR="$ROOT/target/uao-foundry-0.1.0.jar"
if [[ ! -f "$JAR" ]]; then
  mvn -B -ntp -q package -DskipTests
fi

LAUNCHER_DIR="$(mktemp -d)"
trap 'rm -rf "$LAUNCHER_DIR"' EXIT
LAUNCHER="$LAUNCHER_DIR/uao-foundry-claude-provider"
printf '#!/usr/bin/env bash\nexec python3 %q\n' "$ADAPTER" > "$LAUNCHER"
chmod 700 "$LAUNCHER"

java -cp "$JAR" \
  org.seventeenthsecond.uaofoundry.reuse.RegistryManufactureApplication \
  "$IDENTITY" \
  --provider-command "$LAUNCHER" \
  "$@"
