#!/usr/bin/env bash
#
# USI Foundry — local installer (Linux).
#
# Builds the application and installs a `usi-foundry` launcher. Deliberately does not install
# system packages, touch a package manager or require root: everything lands under the user's own
# home so an install can be undone by deleting two paths.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREFIX="${USI_FOUNDRY_PREFIX:-$HOME/.local}"
BIN_DIR="$PREFIX/bin"
LIB_DIR="$PREFIX/lib/usi-foundry"
HOME_DIR="${USI_FOUNDRY_HOME:-$HOME/.usi-foundry}"

say()  { printf '  %s\n' "$*"; }
fail() { printf '  ERROR: %s\n' "$*" >&2; exit 1; }

printf '\n  USI Foundry — install\n\n'

# ---- toolchain ------------------------------------------------------------
command -v java >/dev/null 2>&1 || fail "java not found. A JDK 21 runtime is required."
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
[ "${JAVA_MAJOR:-0}" -ge 21 ] || fail "Java 21 or newer is required (found ${JAVA_MAJOR:-unknown})."
say "java ${JAVA_MAJOR}"

JAR="$REPO_ROOT/target/uao-foundry-0.1.0.jar"
if [ ! -f "$JAR" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not found and $JAR is not built. Install Maven 3.9+ or build the jar first."
  command -v javac >/dev/null 2>&1 || fail "javac not found. A full JDK is required to build, not just a JRE."
  say "building (mvn -B -ntp package)"
  (cd "$REPO_ROOT" && mvn -B -ntp -q package -DskipTests)
fi
[ -f "$JAR" ] || fail "build did not produce $JAR"
say "jar $(basename "$JAR")"

# ---- install --------------------------------------------------------------
mkdir -p "$BIN_DIR" "$LIB_DIR"
cp "$JAR" "$LIB_DIR/usi-foundry.jar"
# The audited core validates JSON contracts from disk, so the schemas travel with the install.
rm -rf "$LIB_DIR/schemas" && cp -r "$REPO_ROOT/schemas" "$LIB_DIR/schemas"
rm -rf "$LIB_DIR/examples" && cp -r "$REPO_ROOT/examples" "$LIB_DIR/examples"

cat > "$BIN_DIR/usi-foundry" <<LAUNCHER
#!/usr/bin/env bash
set -euo pipefail
LIB_DIR="$LIB_DIR"
cd "\$LIB_DIR"
exec java -cp "\$LIB_DIR/usi-foundry.jar" org.seventeenthsecond.usifoundry.UsiFoundryApp "\$@"
LAUNCHER
chmod +x "$BIN_DIR/usi-foundry"

mkdir -p "$HOME_DIR"
say "installed $BIN_DIR/usi-foundry"
say "library   $LIB_DIR"
say "home      $HOME_DIR"

printf '\n'
case ":$PATH:" in
  *":$BIN_DIR:"*) say "run:  usi-foundry" ;;
  *) say "run:  $BIN_DIR/usi-foundry"
     say "(add $BIN_DIR to PATH to shorten this)" ;;
esac
printf '\n  To uninstall: rm -rf %s %s\n  Your registry in %s is left untouched.\n\n' \
  "$BIN_DIR/usi-foundry" "$LIB_DIR" "$HOME_DIR"
