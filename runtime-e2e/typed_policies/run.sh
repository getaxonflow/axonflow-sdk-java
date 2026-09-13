#!/usr/bin/env bash
# Runs the typed policy authoring runtime leg against a real AxonFlow agent.
# Usage: AXONFLOW_AGENT_URL=http://localhost:8080 AXONFLOW_CLIENT_ID=... \
#        AXONFLOW_CLIENT_SECRET=... bash runtime-e2e/typed_policies/run.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# -DskipTests alone does not skip the unit suite here (the pom binds surefire's
# skipTests to skipUnitTests; see #237), so pass both.
mvn -q -DskipTests -DskipUnitTests=true -Dfmt.skip=true package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/typed-policies-cp.txt

SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
SLF4J_SIMPLE="${HOME}/.m2/repository/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar"
if [ ! -f "$SLF4J_SIMPLE" ]; then
  mvn -q dependency:get -Dartifact=org.slf4j:slf4j-simple:2.0.12
fi
CP="${SDK_JAR}:${SLF4J_SIMPLE}:$(cat target/typed-policies-cp.txt)"

AXONFLOW_TELEMETRY=off java -cp "$CP" runtime-e2e/typed_policies/TypedPolicyAuthoringTest.java
