#!/usr/bin/env bash
# Runs examples/pep-handshake and examples/typed-policies, built against the LOCAL SDK, against a
# LIVE Community agent, in the order the README gives, and asserts what each proves:
#
#   1. pep-handshake on a fresh stack: exits 0, the first decide is allowed, and
#      the declaration the platform would refuse fails in the client at /pep_id.
#   2. typed-policies with AXONFLOW_TYPED_POLICY_PUBLISH=1: exits 0, and the document is activated.
#   3. pep-handshake again: printed as an OBSERVATION, not asserted. After step 2 activates a
#      document with an organization-scope constraint, a decide that does not supply the attribute
#      the constraint conditions on is denied fail-closed with reasons ["unknown_constraint"].
#
# It changes the organization's active policy (step 2), so run it on a fresh stack.
#
#   AXONFLOW_AGENT_URL=http://localhost:8080 ./runtime-e2e/v11_examples/run.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
AGENT="${AXONFLOW_AGENT_URL:-http://localhost:8080}"
CLIENT_ID="${AXONFLOW_CLIENT_ID:-runtime-e2e}"
CLIENT_SECRET="${AXONFLOW_CLIENT_SECRET:-runtime-e2e-secret}"
# This pom sets surefire's skipTests from its own skipUnitTests property, which overrides a
# command-line -DskipTests, so -DskipTests alone still runs the whole unit suite.
mvn -q -DskipTests -DskipUnitTests=true -Dfmt.skip=true package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/v11-examples-cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
# A binding so the SDK's WARN output is visible: the SDK depends on slf4j-api alone, and without a
# binding its logger is a no-op.
SLF4J_SIMPLE="${HOME}/.m2/repository/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar"
if [ ! -f "$SLF4J_SIMPLE" ]; then
  mvn -q dependency:get -Dartifact=org.slf4j:slf4j-simple:2.0.12
fi
CP="${SDK_JAR}:${SLF4J_SIMPLE}:$(cat target/v11-examples-cp.txt)"
OUT=target/v11-examples
rm -rf "$OUT"
for ex in pep-handshake typed-policies; do
  mkdir -p "$OUT/$ex"
  javac -d "$OUT/$ex" -cp "$CP" examples/$ex/src/main/java/com/getaxonflow/examples/*.java
done

FAILURES=0
check() {
  if [ "$1" = ok ]; then echo "PASS: $2"; else echo "FAIL: $2"; FAILURES=$((FAILURES + 1)); fi
}
# Runs one example with the given environment, captures its output, and returns its exit code.
run_example() {
  local ex=$1 main=$2 log=$3; shift 3
  set +e
  env AXONFLOW_AGENT_URL="$AGENT" AXONFLOW_TELEMETRY=off AXONFLOW_CLIENT_ID="$CLIENT_ID" AXONFLOW_CLIENT_SECRET="$CLIENT_SECRET" "$@" \
    java -cp "$OUT/$ex:$CP" "$main" >"$log" 2>&1
  local rc=$?
  set -e
  grep -vE '^[[:space:]]*$| DEBUG | INFO ' "$log" | sed 's/^/  | /'
  return $rc
}

echo "=== 1. pep-handshake on a fresh stack"
rc=0; run_example pep-handshake com.getaxonflow.examples.PepHandshake "$OUT/1.log" || rc=$?
check "$([ $rc = 0 ] && echo ok)" "pep-handshake exits 0 (exit $rc)"
first=$(grep -m1 -oE '^verdict=[a-z_]+' "$OUT/1.log" || true)
check "$([ "$first" = verdict=allow ] && echo ok)" "the first decide is allowed on a fresh stack ($first)"
check "$(grep -q '^refused at /pep_id: ' "$OUT/1.log" && echo ok)" \
  "the declaration the platform would refuse fails in the client, at /pep_id, before anything is sent"

echo "=== 2. typed-policies: validate, publish and activate"
rc=0; run_example typed-policies com.getaxonflow.examples.TypedPolicies "$OUT/2.log" \
  AXONFLOW_TYPED_POLICY_PUBLISH=1 AXONFLOW_TYPED_POLICY_BODY="$ROOT/tests/fixtures/typed_policy_publish_body.json" || rc=$?
check "$([ $rc = 0 ] && echo ok)" "typed-policies exits 0 (exit $rc)"
check "$(grep -qx 'activated' "$OUT/2.log" && echo ok)" "the document is published and activated"

echo "=== 3. OBSERVATION, not asserted: pep-handshake after the activation"
run_example pep-handshake com.getaxonflow.examples.PepHandshake "$OUT/3.log" || true
echo "  observed: $(grep -m1 -oE '^verdict=.*' "$OUT/3.log" || echo 'no verdict printed')"

if [ "$FAILURES" -gt 0 ]; then
  echo; echo "FAIL: v11_examples ($FAILURES assertion(s))"; exit 1
fi
echo; echo "PASS: v11_examples"
