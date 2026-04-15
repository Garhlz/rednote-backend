#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE_DIR="${ROOT_DIR}/services/platform-java"
TOOLS_DIR="${ROOT_DIR}/.tools"
AGENT_JAR="${TOOLS_DIR}/opentelemetry-javaagent.jar"
AGENT_VERSION="${OTEL_JAVA_AGENT_VERSION:-2.16.0}"
AGENT_URL="${OTEL_JAVA_AGENT_URL:-https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${AGENT_VERSION}/opentelemetry-javaagent.jar}"

mkdir -p "${TOOLS_DIR}"

if [[ ! -f "${AGENT_JAR}" ]]; then
  echo "Downloading OpenTelemetry Java agent ${AGENT_VERSION}..."
  curl -fL "${AGENT_URL}" -o "${AGENT_JAR}"
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-platform-java}"
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"
export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"
export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-none}"
export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-none}"
export OTEL_PROPAGATORS="${OTEL_PROPAGATORS:-tracecontext,baggage}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-deployment.environment=dev}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -javaagent:${AGENT_JAR}"

cd "${SERVICE_DIR}"
exec mvn spring-boot:run | tspin
