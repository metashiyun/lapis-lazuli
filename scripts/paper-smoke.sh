#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_JAR="${PAPER_SERVER_JAR:-}"
SERVER_DIR="${PAPER_SMOKE_DIR:-$ROOT_DIR/.paper-smoke}"
JAVA_BIN="${PAPER_JAVA_BIN:-java}"
LOG_FILE="$SERVER_DIR/server.log"
COMMAND_FIFO="$SERVER_DIR/console.fifo"
RUNTIME_JAR="$ROOT_DIR/runtime-bukkit/build/libs/runtime-bukkit.jar"
BUNDLE_DIR="$ROOT_DIR/examples/hello-ts/dist/hello-ts"
SERVER_TIMEOUT="${PAPER_SMOKE_TIMEOUT_SECONDS:-120}"

if [[ -z "$SERVER_JAR" ]]; then
  echo "PAPER_SERVER_JAR must point to a Paper server jar."
  exit 1
fi

if [[ ! -f "$SERVER_JAR" ]]; then
  echo "Paper server jar not found: $SERVER_JAR"
  exit 1
fi

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    printf 'stop\n' >&3 || true
    wait "$SERVER_PID" || true
  fi

  exec 3>&- 2>/dev/null || true
}

trap cleanup EXIT

echo "Building runtime plugin jar..."
(cd "$ROOT_DIR" && ./gradlew :runtime-bukkit:shadowJar >/dev/null)

echo "Bundling example plugin..."
(cd "$ROOT_DIR" && bun packages/cli/src/index.ts bundle examples/hello-ts >/dev/null)

if [[ ! -f "$RUNTIME_JAR" ]]; then
  echo "Runtime jar not found at $RUNTIME_JAR"
  exit 1
fi

if [[ ! -f "$BUNDLE_DIR/lapis-plugin.json" || ! -f "$BUNDLE_DIR/main.js" ]]; then
  echo "Example bundle is missing expected files under $BUNDLE_DIR"
  exit 1
fi

rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR/plugins" "$SERVER_DIR/plugins/LapisLazuli/bundles"

cp "$SERVER_JAR" "$SERVER_DIR/paper.jar"
cp "$RUNTIME_JAR" "$SERVER_DIR/plugins/runtime-bukkit.jar"
cp -R "$BUNDLE_DIR" "$SERVER_DIR/plugins/LapisLazuli/bundles/"

cat >"$SERVER_DIR/eula.txt" <<'EOF'
eula=true
EOF

cat >"$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-port=25566
motd=Lapis Lazuli Smoke Test
enable-rcon=false
spawn-protection=0
white-list=false
EOF

echo "Starting Paper smoke server in $SERVER_DIR"

mkfifo "$COMMAND_FIFO"
exec 3<>"$COMMAND_FIFO"

( cd "$SERVER_DIR" && "$JAVA_BIN" -jar paper.jar nogui <"$COMMAND_FIFO" 2>&1 | tee "$LOG_FILE" >/dev/null ) &
SERVER_PID=$!

wait_for_log() {
  local pattern="$1"
  local timeout="$2"
  local elapsed=0

  while (( elapsed < timeout )); do
    if [[ -f "$LOG_FILE" ]] && grep -Fq "$pattern" "$LOG_FILE"; then
      return 0
    fi

    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "Server exited before pattern was observed: $pattern"
      if [[ -f "$LOG_FILE" ]]; then
        tail -n 200 "$LOG_FILE" || true
      fi
      return 1
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  echo "Timed out waiting for pattern: $pattern"
  if [[ -f "$LOG_FILE" ]]; then
    tail -n 200 "$LOG_FILE" || true
  fi
  return 1
}

wait_for_log "[hello-ts] Hello TS enabled." "$SERVER_TIMEOUT"
wait_for_log "[hello-ts] Server load event observed." "$SERVER_TIMEOUT"
wait_for_log "Loaded 1 bundle(s), failed 0." "$SERVER_TIMEOUT"

printf 'hello\n' >&3
wait_for_log "Hello from TypeScript." 30

perl -0pi -e 's/Hello from TypeScript\./Hello from hot reload./g' "$SERVER_DIR/plugins/LapisLazuli/bundles/hello-ts/main.js"
wait_for_log "Hot reloaded bundles after detecting changes. Loaded 1 bundle(s), failed 0." 30

printf 'hello\n' >&3
wait_for_log "Hello from hot reload." 30

printf 'stop\n' >&3
wait "$SERVER_PID"

echo "Paper smoke test passed."
