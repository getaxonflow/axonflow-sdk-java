#!/usr/bin/env bash
# Drives runtime-e2e/read_path_identity/ReadPathIdentityTest against a LIVE
# enterprise stack, using the LOCAL SDK build.
#
#   set -a; source /tmp/axonflow-e2e-env.sh; set +a
#   ./runtime-e2e/read_path_identity/run.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

mvn -q -DskipTests -Dfmt.skip=true package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/s3-java-cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)

# An SLF4J BINDING is added for the driver only. The SDK depends on slf4j-api
# alone — correct for a library, which must not choose a binding for its
# consumer — but it means that without one the logger is a NOP and the SDK logs
# NOTHING. Step 9's "the token does not appear in the log" grep would then run
# against an empty stream and pass for every string. Its positive control caught
# exactly that, which is why the control exists.
SLF4J_SIMPLE="${HOME}/.m2/repository/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar"
if [ ! -f "$SLF4J_SIMPLE" ]; then
  mvn -q dependency:get -Dartifact=org.slf4j:slf4j-simple:2.0.12
fi

CP="${SDK_JAR}:${SLF4J_SIMPLE}:$(cat /tmp/s3-java-cp.txt)"

# AXONFLOW_CHECKPOINT_URL is read from the ENVIRONMENT, which a JVM cannot set
# for itself — so the collector port is chosen here and handed in, rather than
# by the driver calling System.setProperty (which reads back fine and changes
# nothing about where telemetry actually goes: a passing test about an
# unreachable property).
COLLECTOR_PORT=$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')

# The 7-day telemetry stamp is PARKED for this run and restored on exit — not
# deleted. It lives in the developer's real cache dir, and deleting it would
# make their next unrelated SDK run fire a genuine ping at the PRODUCTION
# checkpoint: a test reaching outside its own sandbox to change the machine's
# state. Without the park, step 9's collector stays empty on every run after the
# first and the step FAILS loudly rather than passing on an unasserted absence.
case "$(uname -s)" in
  Darwin) STAMP="${HOME}/Library/Caches/axonflow/java-telemetry-last-sent" ;;
  *)      STAMP="${XDG_CACHE_HOME:-${HOME}/.cache}/axonflow/java-telemetry-last-sent" ;;
esac
if [ -f "$STAMP" ]; then
  mv "$STAMP" "${STAMP}.s3-parked"
  trap 'mv -f "${STAMP}.s3-parked" "$STAMP" 2>/dev/null || true' EXIT
fi

env \
  AXONFLOW_CHECKPOINT_URL="http://127.0.0.1:${COLLECTOR_PORT}/telemetry" \
  AXONFLOW_TELEMETRY=on \
  S3_COLLECTOR_PORT="${COLLECTOR_PORT}" \
  java \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
  -Dorg.slf4j.simpleLogger.logFile=System.err \
  -cp "$CP" runtime-e2e/read_path_identity/ReadPathIdentityTest.java
rc=$?
exit $rc
