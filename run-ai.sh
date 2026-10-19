#!/bin/bash

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Usage: $0 <grafana-endpoint> <tempo-mcp-endpoint> [ports] [extra JVM args...]"
  echo "  grafana-endpoint:   Grafana URL, e.g. http://localhost:3000"
  echo "  tempo-mcp-endpoint: Tempo MCP URL, e.g. http://localhost:3200"
  echo "  ports:              (optional) comma-separated list of app ports, e.g. 8081,8082"
  echo "                      Only useful if apps are running in Quarkus dev mode (Dev MCP)"
  exit 1
fi

GRAFANA_ENDPOINT=$1
TEMPO_MCP_ENDPOINT=$2
PORTS=${3:-}
shift 3 2>/dev/null || shift 2

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR=$(find "$SCRIPT_DIR/ai/target" -name "telemetry-ai-core-*-runner.jar" -not -name "*sources*" | head -1)

if [ -z "$JAR" ]; then
  echo "Runner jar not found in ai/target/. Build first: ./mvn.ai.sh package -DskipTests"
  exit 1
fi

ARGS=(-Dgrafana.endpoint="$GRAFANA_ENDPOINT" -Dtempo-mcp.endpoint="$TEMPO_MCP_ENDPOINT")
if [ -n "$PORTS" ]; then
  ARGS+=(-Dapp.ports="$PORTS")
fi

java "${ARGS[@]}" "$@" -jar "$JAR"
