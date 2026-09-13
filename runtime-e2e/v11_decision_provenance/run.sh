#!/usr/bin/env bash
# Drives runtime-e2e/v11_decision_provenance/V11DecisionProvenanceTest against a LIVE v11 stack
# (agent and orchestrator on the application database role), using the LOCAL SDK build.
#
#   set -a; source /tmp/axonflow-e2e-env.sh; set +a
#   ./runtime-e2e/v11_decision_provenance/run.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
mvn -q -DskipTests -Dfmt.skip=true package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/v11-provenance-cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
# A binding so the SDK's WARN fallback would be visible: the SDK depends on slf4j-api alone, which
# is right for a library, and without a binding its logger is a no-op.
SLF4J_SIMPLE="${HOME}/.m2/repository/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar"
if [ ! -f "$SLF4J_SIMPLE" ]; then
  mvn -q dependency:get -Dartifact=org.slf4j:slf4j-simple:2.0.12
fi
CP="${SDK_JAR}:${SLF4J_SIMPLE}:$(cat target/v11-provenance-cp.txt)"
AXONFLOW_TELEMETRY=off java -cp "$CP" runtime-e2e/v11_decision_provenance/V11DecisionProvenanceTest.java
