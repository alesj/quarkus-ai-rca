#!/bin/bash

PORTS=${1:-}
shift 2>/dev/null || true

if [ -n "$PORTS" ]; then
  ./mvnw quarkus:dev --projects :telemetry-ai-core --also-make -DskipCompanion -Dapp.ports="$PORTS" "$@"
else
  ./mvnw quarkus:dev --projects :telemetry-ai-core --also-make -DskipCompanion "$@"
fi
