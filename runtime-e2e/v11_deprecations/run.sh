#!/usr/bin/env bash
# Drives runtime-e2e/v11_deprecations/V11DeprecationsTest against a LIVE v11 stack on the
# Evaluation licence or above (the simulation routes are registered from Evaluation up), using the
# LOCAL SDK build.
#
#   set -a; source /tmp/axonflow-e2e-env.sh; set +a
#   AXONFLOW_AGENT_URL=http://localhost:8080 ./runtime-e2e/v11_deprecations/run.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
# This pom sets surefire's skipTests from its own skipUnitTests property, which overrides a
# command-line -DskipTests, so -DskipTests alone still runs the whole unit suite.
mvn -q -DskipTests -DskipUnitTests=true -Dfmt.skip=true package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/v11-deprecations-cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
CP="${SDK_JAR}:$(cat target/v11-deprecations-cp.txt)"
AXONFLOW_TELEMETRY=off java -cp "$CP" runtime-e2e/v11_deprecations/V11DeprecationsTest.java
