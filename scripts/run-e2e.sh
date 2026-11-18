#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
frontend="$root/src/frontend"
compose=(docker compose --project-name f1predictions-e2e --file "$root/LocalRuns/compose.e2e.yaml")
logs="$(mktemp -d)"

cleanup() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    mkdir -p "$frontend/test-results"
    cp "$logs/playwright.log" "$frontend/test-results/runtime.log" 2>/dev/null || true
    "${compose[@]}" logs --no-color > "$frontend/test-results/compose.log" || true
  fi
  "${compose[@]}" down --volumes --remove-orphans || true
  rm -f "$logs/playwright.log"
  rmdir "$logs"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

export E2E_SESSION_HASH_SECRET="$(openssl rand -hex 32)"

"${compose[@]}" down --volumes --remove-orphans
"${compose[@]}" up --wait
npm --prefix "$frontend" run e2e -- "$@" 2>&1 | tee "$logs/playwright.log"
