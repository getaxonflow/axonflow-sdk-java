#!/usr/bin/env bash
# Drives runtime-e2e/static_policy_categories/StaticPolicyCategoriesTest against a LIVE agent
# (community mode is enough), using the LOCAL SDK build. It writes nothing to the stack.
#
#   AXONFLOW_AGENT_URL=http://localhost:8080 ./runtime-e2e/static_policy_categories/run.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
# This pom sets surefire's skipTests from its own skipUnitTests property, which overrides a
# command-line -DskipTests, so -DskipTests alone still runs the whole unit suite.
mvn -q -DskipTests -DskipUnitTests=true -Dfmt.skip=true package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/static-policy-categories-cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
CP="${SDK_JAR}:$(cat target/static-policy-categories-cp.txt)"
AXONFLOW_TELEMETRY=off java -cp "$CP" runtime-e2e/static_policy_categories/StaticPolicyCategoriesTest.java
