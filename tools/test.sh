#!/usr/bin/env bash
# Dudunka Family: local validation without spending Codex turns.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-all}"
case "$MODE" in
  build|gametest|all) ;;
  -h|--help|help)
    printf 'Usage: ./tools/test.sh [build|gametest|all]\n'
    printf 'Default: all. DUDUNKA_TEST_ONLINE=1 disables Gradle --offline.\n'
    exit 0 ;;
  *) printf 'Unknown mode: %s\n' "$MODE" >&2; exit 2 ;;
esac

if [[ ! -f ./gradlew ]]; then
  echo 'Run inside a Dudunka Family repository containing gradlew.' >&2
  exit 2
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="$(command -v java || true)"
fi
if [[ -z "$JAVA_CMD" || ! -x "$JAVA_CMD" ]]; then
  echo 'Java not found. Set JAVA_HOME to JDK 17.' >&2
  exit 2
fi
JAVA_VERSION="$($JAVA_CMD -version 2>&1 | head -n 1)"
if ! grep -Eq 'version "17([.+"]|$)' <<< "$JAVA_VERSION"; then
  printf 'Expected JDK 17; detected: %s\nSet JAVA_HOME to JDK 17.\n' "$JAVA_VERSION" >&2
  exit 2
fi

mkdir -p build/reports/dudunka-tests run
REPORT_ROOT="$ROOT/build/reports/dudunka-tests"
# Do not run two copies of this wrapper concurrently against shared Gradle output.
if command -v flock >/dev/null 2>&1; then
  exec 9>"$REPORT_ROOT/.runner.lock"
  if ! flock -n 9; then
    echo 'Another tools/test.sh run is active. Try again after it exits.' >&2
    exit 2
  fi
fi

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
REPORT_DIR="$REPORT_ROOT/$RUN_ID"
mkdir "$REPORT_DIR"
WORLD_REL="run/dudunka-test-$RUN_ID"
WORLD_DIR="$ROOT/$WORLD_REL"
if [[ "$MODE" != build ]]; then
  # Every full GameTest starts with a genuinely unused working directory.
  mkdir "$WORLD_DIR"
fi

GRADLE_ARGS=(--offline -Dnet.minecraftforge.gradle.check.certs=false)
if [[ "${DUDUNKA_TEST_ONLINE:-0}" == 1 ]]; then
  GRADLE_ARGS=(-Dnet.minecraftforge.gradle.check.certs=false)
fi
HEAD="$(git rev-parse --short HEAD 2>/dev/null || printf unknown)"
DIRTY="$( (git status --porcelain 2>/dev/null || true) | wc -l | tr -d ' ')"

INIT="$REPORT_DIR/fresh-world.gradle"
cat >"$INIT" <<'GROOVY'
gradle.taskGraph.whenReady { graph ->
    def task = graph.allTasks.find { it.name == 'runGameTestServer' }
    if (task == null) throw new GradleException('runGameTestServer was not found')
    task.doFirst {
        def requested = task.project.findProperty('dudunkaTestWorld')
        if (!(requested instanceof String) || !(requested ==~ /run\/dudunka-test-[A-Za-z0-9_-]+/)) {
            throw new GradleException('Invalid or missing dudunkaTestWorld')
        }
        def dir = task.project.file(requested)
        if (!dir.isDirectory() || new File(dir, 'world/level.dat').exists()) {
            throw new GradleException('A new, unused GameTest directory is required: ' + dir)
        }
        task.runConfig.get().workingDirectory(dir)
    }
}
GROOVY

BUILD_STATUS='SKIPPED'
GAME_STATUS='SKIPPED'
FAILED=0

run_phase() {
  local label="$1" log="$2"
  shift 2
  printf '%s ... (log: %s)\n' "$label" "${log#"$ROOT/"}"
  if "$@" >"$log" 2>&1; then
    printf '%s: PASS\n' "$label"
    return 0
  else
    local status=$?
    printf '%s: FAIL (exit %s). Last 40 log lines:\n' "$label" "$status" >&2
    tail -n 40 "$log" >&2 || true
    return "$status"
  fi
}

printf 'Dudunka local validation | %s | JDK: %s | HEAD: %s | dirty entries: %s\n' \
  "$MODE" "$JAVA_VERSION" "$HEAD" "$DIRTY"

if [[ "$MODE" == build || "$MODE" == all ]]; then
  if run_phase 'BUILD' "$REPORT_DIR/build.log" \
      ./gradlew "${GRADLE_ARGS[@]}" build verifyModels verifyPufik; then
    BUILD_STATUS=PASS
  else
    BUILD_STATUS=FAIL
    FAILED=1
  fi
fi

if [[ "$MODE" == gametest || "$MODE" == all ]]; then
  if [[ "$FAILED" -ne 0 ]]; then
    printf 'GAME TESTS: SKIPPED because build failed.\n'
  elif run_phase 'GAME TESTS' "$REPORT_DIR/gametest.log" \
      ./gradlew "${GRADLE_ARGS[@]}" -I "$INIT" \
      "-PdudunkaTestWorld=$WORLD_REL" runGameTestServer -x downloadAssets; then
    GAME_STATUS=PASS
  else
    GAME_STATUS=FAIL
    FAILED=1
  fi
fi

{
  printf 'run_id=%s\nmode=%s\ngit_head=%s\ngit_dirty_entries=%s\njava=%s\n' \
    "$RUN_ID" "$MODE" "$HEAD" "$DIRTY" "$JAVA_VERSION"
  printf 'build=%s\ngametest=%s\n' "$BUILD_STATUS" "$GAME_STATUS"
  printf 'report=%s\n' "$REPORT_DIR"
  if [[ "$MODE" != build ]]; then printf 'fresh_world=%s\n' "$WORLD_DIR"; fi
  if [[ -f "$REPORT_DIR/gametest.log" ]]; then
    printf '\nGameTest result lines (from server log; check the full log for details):\n'
    grep -Ei '([0-9]+/[0-9]+.*(tests?|gametests?)|(gametests?|tests?).*[0-9]+/[0-9]+|[0-9]+ tests? (passed|failed)|GameTest.*(failed|passed)|BUILD (SUCCESSFUL|FAILED))' \
      "$REPORT_DIR/gametest.log" | tail -n 12 || true
  fi
} >"$REPORT_DIR/summary.txt"

printf '\n===== RESULT =====\n'
cat "$REPORT_DIR/summary.txt"
printf '\nSaved logs: %s\n' "$REPORT_DIR"
if [[ "$FAILED" -eq 0 ]]; then
  echo 'PASS: requested phases completed successfully.'
else
  echo 'FAIL: inspect the indicated log(s). No production save was changed.' >&2
fi
exit "$FAILED"
