#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PLATFORM="${1:-lgtm}"
AI="${2:-openai}"
SCORER="${3:-grok}"

TESTS=(chaos db weather)

PASS=0
FAIL=0
RESULTS=""

echo "=========================================="
echo " Integration Test Suite"
echo "  Platform: $PLATFORM"
echo "  AI: $AI"
echo "  Scorer: $SCORER"
echo "=========================================="
echo ""

for TEST in "${TESTS[@]}"; do
  echo ""
  echo "=========================================="
  echo " Running: $TEST | AI=$AI | SCORER=$SCORER | Platform=$PLATFORM"
  echo "=========================================="

  docker rm -f chaos-toxiproxy chaos-mysql chaos-weather 2>/dev/null || true

  if OBSERVABILITY_PLATFORM="$PLATFORM" "$SCRIPT_DIR/run-integration-test.sh" "$TEST" "$AI" "$SCORER"; then
    PASS=$((PASS + 1))
    RESULTS="${RESULTS}\n  PASS: $TEST"
  else
    FAIL=$((FAIL + 1))
    RESULTS="${RESULTS}\n  FAIL: $TEST"
  fi
done

TOTAL=$((PASS + FAIL))

echo ""
echo "=========================================="
echo " Summary: $PASS/$TOTAL passed"
echo "  Platform: $PLATFORM | AI: $AI | Scorer: $SCORER"
echo "=========================================="
echo -e "$RESULTS"
echo ""

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
