#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { printf 'UAO Foundry preflight: %s\n' "$*" >&2; exit 2; }
pass() { printf 'PASS  %s\n' "$*"; }

command -v java >/dev/null 2>&1 || fail "java not found"
JAVA_VERSION="$(java -version 2>&1 | head -n1)"
[[ "$JAVA_VERSION" =~ \"21(\.|\") ]] || fail "Java 21 required; found: $JAVA_VERSION"
pass "$JAVA_VERSION"

command -v mvn >/dev/null 2>&1 || fail "mvn not found"
pass "$(mvn -version | head -n1)"

command -v python3 >/dev/null 2>&1 || fail "python3 not found"
python3 - <<'PY' || exit 2
import sys
if sys.version_info < (3, 11):
    raise SystemExit(f"UAO Foundry preflight: Python 3.11+ required; found {sys.version.split()[0]}")
print("PASS  Python " + sys.version.split()[0])
PY

CLAUDE_BIN="${UAO_FOUNDRY_CLAUDE_BIN:-$(command -v claude || true)}"
[[ -n "$CLAUDE_BIN" ]] || fail "Claude Code not found; install it or set UAO_FOUNDRY_CLAUDE_BIN"
[[ -f "$CLAUDE_BIN" ]] || fail "Claude Code path is not a file: $CLAUDE_BIN"
[[ -x "$CLAUDE_BIN" ]] || fail "Claude Code path is not executable: $CLAUDE_BIN"
CLAUDE_VERSION="$($CLAUDE_BIN --version 2>&1 | head -n1)"
python3 - "$CLAUDE_VERSION" <<'PYV' || fail "Claude Code v2.1.205+ required; found: $CLAUDE_VERSION"
import re, sys
m = re.search(r"(?<!\d)(\d+)\.(\d+)\.(\d+)(?!\d)", sys.argv[1])
if not m or tuple(map(int, m.groups())) < (2, 1, 205):
    raise SystemExit(1)
PYV
pass "Claude Code: $CLAUDE_VERSION"

ADAPTER="$ROOT/adapters/claude-code/claude_provider.py"
[[ -f "$ADAPTER" ]] || fail "Claude adapter missing: $ADAPTER"
python3 - "$ADAPTER" <<'PY' || fail "Claude adapter does not parse"
import ast, pathlib, sys
path = pathlib.Path(sys.argv[1])
ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
PY
pass "Claude adapter parses"

LAUNCHER_DIR="$(mktemp -d)"
trap 'rm -rf "$LAUNCHER_DIR"' EXIT
LAUNCHER="$LAUNCHER_DIR/uao-foundry-claude-provider"
printf '#!/usr/bin/env bash\nexec python3 %q\n' "$ADAPTER" > "$LAUNCHER"
chmod 700 "$LAUNCHER"
[[ -x "$LAUNCHER" ]] || fail "unable to manufacture executable provider launcher"
pass "temporary exact-executable provider launcher"

[[ -f schemas/fixture-bundle.schema.json ]] || fail "provider bundle schema missing"
[[ -f schemas/reuse-report.schema.json ]] || fail "reuse-report schema missing"
pass "Foundry schemas present"

mvn -B -ntp -q package -DskipTests
JAR="$ROOT/target/uao-foundry-0.1.0.jar"
[[ -f "$JAR" ]] || fail "packaged JAR missing after Maven build"
pass "Foundry JAR packaged"

REGISTRY="${UAO_FOUNDRY_REGISTRY:-$ROOT/.uao-registry}"
if [[ -f "$REGISTRY/index.json" ]]; then
    java -cp "$JAR" org.seventeenthsecond.uaofoundry.registry.RegistryApplication verify --registry "$REGISTRY" >/dev/null
    pass "registry verified: $REGISTRY"
elif [[ -d "$REGISTRY/packages" ]] && find "$REGISTRY/packages" -mindepth 1 -maxdepth 1 -type d -print -quit | grep -q .; then
    fail "registry has package directories but no index.json; run RegistryApplication rebuild explicitly"
else
    pass "registry is empty/uninitialised and can be initialised on first live manufacture: $REGISTRY"
fi

printf '\nUAO Foundry live preflight complete.\n'
