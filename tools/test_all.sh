#!/usr/bin/env bash
# Runs every SDK test suite against the shared mock server. Languages whose toolchain is missing are skipped.
# Optional overrides: GO_BIN, RUBY_BIN, JAVA_HOME, JUNIT_JAR, JACKSON_DIR (a folder with the three jackson-*.jar files).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${AXIOAPI_MOCK_PORT:-8765}"
export AXIOAPI_MOCK_URL="http://127.0.0.1:${PORT}"
FAILED=()

python "$ROOT/tools/sync_spec.py" >/dev/null
python "$ROOT/tools/mock_server.py" "$PORT" &
MOCK_PID=$!
trap 'kill $MOCK_PID 2>/dev/null' EXIT
sleep 1

run() {
    local name="$1"; shift
    echo "=== $name"
    if "$@"; then echo "--- $name: ok"; else echo "--- $name: FAILED"; FAILED+=("$name"); fi
}
have() { command -v "$1" >/dev/null 2>&1; }

have python && run python bash -c "cd '$ROOT/python' && PYTHONPATH=src python -m unittest discover -s tests"
have node && run node bash -c "cd '$ROOT/node' && node --test test/"
if have php && [ -f "$ROOT/../vendor/bin/phpunit" ]; then run php bash -c "cd '$ROOT/php' && php ../../vendor/bin/phpunit"; elif have phpunit; then run php bash -c "cd '$ROOT/php' && phpunit"; fi
have dotnet && run csharp bash -c "cd '$ROOT/csharp/tests/AxioAPI.Tests' && dotnet test"
GO_BIN="${GO_BIN:-$(command -v go || true)}"
[ -n "$GO_BIN" ] && run go bash -c "cd '$ROOT/go' && '$GO_BIN' vet ./... && '$GO_BIN' test ./... -count=1"
RUBY_BIN="${RUBY_BIN:-$(command -v ruby || true)}"
[ -n "$RUBY_BIN" ] && run ruby bash -c "cd '$ROOT/ruby' && '$RUBY_BIN' -w test/client_test.rb"
if [ -n "${JUNIT_JAR:-}" ] && [ -n "${JACKSON_DIR:-}" ]; then
    JBIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
    OUT="$(mktemp -d)"
    command -v cygpath >/dev/null 2>&1 && OUT="$(cygpath -m "$OUT")"
    CP="$JACKSON_DIR/jackson-core.jar;$JACKSON_DIR/jackson-databind.jar;$JACKSON_DIR/jackson-annotations.jar;$JUNIT_JAR"
    run java bash -c "cd '$ROOT/java' && '${JBIN}javac' -encoding UTF-8 --release 11 -cp '$CP' -d '$OUT' \$(find src -name '*.java') && cp src/main/resources/operations.json '$OUT/' && '${JBIN}java' -jar '$JUNIT_JAR' execute -cp '$OUT;$CP' --scan-classpath '$OUT' --fail-if-no-tests"
else
    echo "=== java: skipped (set JUNIT_JAR and JACKSON_DIR, or run 'mvn test' in sdk/java)"
fi

echo
if [ ${#FAILED[@]} -eq 0 ]; then echo "All SDK suites passed."; else echo "Failed: ${FAILED[*]}"; exit 1; fi
