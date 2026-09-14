#!/usr/bin/env bash
# Runs examples/pep-handshake and examples/typed-policies, built against the LOCAL SDK, against a
# LIVE Community agent, in the order the README gives, and asserts what each proves. Every example
# runs from a temporary directory, outside the tree, with no body file: typed-policies finds its
# default document on its classpath.
#
# Precondition, checked with curl rather than the SDK: the agent answers /health within 60
# seconds, and no typed document is active (GET /api/v1/typed-policies/active answers 404
# nothing_active). Otherwise the leg stops with exit 2: it needs a live agent, and it changes the
# organization's active policy, so it needs a fresh stack. Exit 2 means only that; every other
# failure is exit 1.
#
#   1. pep-handshake: exits 0, the first decide is allowed, and the declaration the platform would
#      refuse fails in the client at /pep_id, before anything is sent.
#   2. typed-policies without publishing (the README's default): exits 0, and active() reads the
#      platform's nothing_active as nothing active.
#   3. typed-policies with AXONFLOW_TYPED_POLICY_PUBLISH=1: exits 0, prints the publication's
#      template-omission report BEFORE it activates, and the document is published and activated.
#   4. typed-policies asked to publish a document the save-time checks reject: exits 1, printing the
#      platform's typed 422 document_refused.
#   5. typed-policies publishing the same document again: the publication is a new artifact of the
#      same document version, so the activation is refused (a typed 409 activation_refused, since
#      activation promotes), and the example exits 1.
#   6. pep-handshake again: printed as an OBSERVATION, not asserted. After run 3 activates a
#      document with an organization-scope constraint, a decide that does not supply the attribute
#      the constraint conditions on is denied fail-closed with reasons ["unknown_constraint"]. From
#      v11.0.0 the deny's first reason is that code, followed by one naming each constraint it could
#      not evaluate and the attribute it needed (getaxonflow/axonflow-enterprise#4247).
#
# Credentials are left unset, so the examples present none, and on Community the organization is
# the deployment's.
#
#   AXONFLOW_AGENT_URL=http://localhost:8080 ./runtime-e2e/v11_examples/run.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AGENT="${AXONFLOW_AGENT_URL:-http://localhost:8080}"
# The credentials and the examples' own switches come only from the runs below, never from the
# environment the leg was started in.
unset AXONFLOW_CLIENT_ID AXONFLOW_CLIENT_SECRET AXONFLOW_TYPED_POLICY_PUBLISH AXONFLOW_TYPED_POLICY_BODY
OUT="$(mktemp -d "${TMPDIR:-/tmp}/v11-examples.XXXXXX")"
trap 'rm -rf "$OUT"' EXIT

echo "=== precondition: the agent answers, and no typed document is active"
# A deadline, not an attempt count: an agent that accepts the connection and never answers must not
# hold the loop past 60 seconds.
healthy=0
deadline=$((SECONDS + 60))
while [ "$SECONDS" -lt "$deadline" ]; do
  if [ "$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' "$AGENT/health" || true)" = 200 ]; then
    healthy=1
    break
  fi
  sleep 1
done
if [ "$healthy" != 1 ]; then
  echo "FAIL: $AGENT/health did not answer 200 within 60 seconds: start the stack first"
  exit 2
fi
code=$(curl -s --max-time 10 -o "$OUT/active.json" -w '%{http_code}' "$AGENT/api/v1/typed-policies/active" || true)
reason=$(grep -o '"reason": *"[^"]*"' "$OUT/active.json" 2>/dev/null | head -1 | sed 's/.*"\([^"]*\)"$/\1/' || true)
if [ "$code" != 404 ] || [ "$reason" != nothing_active ]; then
  echo "FAIL: a typed document is already active, or the route did not answer nothing_active (HTTP $code, reason '$reason'): run this leg on a fresh stack"
  exit 2
fi
echo "ok: HTTP 404 nothing_active"

cd "$ROOT"
# This pom sets surefire's skipTests from its own skipUnitTests property, which overrides a
# command-line -DskipTests, so -DskipTests alone still runs the whole unit suite.
mvn -q -DskipTests -DskipUnitTests=true -Dfmt.skip=true package || { echo "FAIL: the SDK build"; exit 1; }
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile="$OUT/cp.txt" || { echo "FAIL: the classpath"; exit 1; }
# typed-policies' default document, put on its classpath by the example's own build (its pom's
# resources), so a wrong path there fails run 2. Only the resources phase runs: compiling the example
# with its pom would resolve the released SDK, not this tree's.
mvn -q -f "$ROOT/examples/typed-policies/pom.xml" resources:resources || { echo "FAIL: the example's resources"; exit 1; }
[ -f "$ROOT/examples/typed-policies/target/classes/typed_policy_publish_body.json" ] || { echo "FAIL: the example's build did not put its default document on its classpath"; exit 1; }
# The jar this build produced, by its version: a glob would also match a jar an earlier build left.
VERSION=$(awk '/<artifactId>axonflow-sdk<\/artifactId>/ { found = 1 } found && /<version>/ { gsub(/.*<version>|<\/version>.*/, ""); print; exit }' pom.xml)
SDK_JAR="$ROOT/target/axonflow-sdk-${VERSION}.jar"
[ -f "$SDK_JAR" ] || { echo "FAIL: no $SDK_JAR after the build"; exit 1; }
# A binding so the SDK's WARN output is visible: the SDK depends on slf4j-api alone, and without a
# binding its logger is a no-op.
SLF4J_SIMPLE="${HOME}/.m2/repository/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar"
if [ ! -f "$SLF4J_SIMPLE" ]; then
  mvn -q dependency:get -Dartifact=org.slf4j:slf4j-simple:2.0.12 || { echo "FAIL: slf4j-simple"; exit 1; }
fi
CP="${SDK_JAR}:${SLF4J_SIMPLE}:$(cat "$OUT/cp.txt")"
for ex in pep-handshake typed-policies; do
  mkdir -p "$OUT/classes/$ex"
  # A javac usage error exits 2, which here means only the precondition: map every build failure to 1.
  javac -d "$OUT/classes/$ex" -cp "$CP" "$ROOT"/examples/$ex/src/main/java/com/getaxonflow/examples/*.java || { echo "FAIL: javac $ex"; exit 1; }
done
TYPED_CP="$OUT/classes/typed-policies:$ROOT/examples/typed-policies/target/classes:$CP"
HANDSHAKE_CP="$OUT/classes/pep-handshake:$CP"

FAILURES=0
check() {
  if [ "$1" = ok ]; then echo "PASS: $2"; else echo "FAIL: $2"; FAILURES=$((FAILURES + 1)); fi
}
# Runs one example from $OUT, outside the tree, with the given environment, prints its output, and
# returns its exit code.
run_example() {
  local cp=$1 main=$2 log=$3; shift 3
  local rc=0
  (cd "$OUT" && env AXONFLOW_AGENT_URL="$AGENT" AXONFLOW_TELEMETRY=off "$@" java -cp "$cp" "$main") >"$log" 2>&1 || rc=$?
  grep -vE '^[[:space:]]*$| DEBUG | INFO ' "$log" | sed 's/^/  | /'
  return $rc
}
# The first line number matching a pattern in a log, or empty when none does.
line_of() { grep -n -m1 -E "$2" "$1" | cut -d: -f1 || true; }

echo "=== 1. pep-handshake on a fresh stack"
rc=0; run_example "$HANDSHAKE_CP" com.getaxonflow.examples.PepHandshake "$OUT/1.log" || rc=$?
check "$([ $rc = 0 ] && echo ok)" "pep-handshake exits 0 (exit $rc)"
first=$(grep -m1 -oE '^verdict=[a-z_]+' "$OUT/1.log" || true)
check "$([ "$first" = verdict=allow ] && echo ok)" "the first decide is allowed on a fresh stack ($first)"
check "$(grep -q '^refused at /pep_id: ' "$OUT/1.log" && echo ok)" \
  "the declaration the platform would refuse fails in the client, at /pep_id, before anything is sent"

echo "=== 2. typed-policies without publishing, as the README runs it"
rc=0; run_example "$TYPED_CP" com.getaxonflow.examples.TypedPolicies "$OUT/2.log" || rc=$?
check "$([ $rc = 0 ] && echo ok)" "typed-policies exits 0 without publishing (exit $rc)"
check "$(grep -qx 'nothing is active' "$OUT/2.log" && echo ok)" \
  "active() reads the platform's nothing_active as nothing active"

echo "=== 3. typed-policies with AXONFLOW_TYPED_POLICY_PUBLISH=1, as the README runs it"
rc=0; run_example "$TYPED_CP" com.getaxonflow.examples.TypedPolicies "$OUT/3.log" AXONFLOW_TYPED_POLICY_PUBLISH=1 || rc=$?
check "$([ $rc = 0 ] && echo ok)" "typed-policies exits 0 (exit $rc)"
omissions=$(line_of "$OUT/3.log" '^template omissions: [0-9]+ of [0-9]+ template controls: ')
activated=$(line_of "$OUT/3.log" '^activated$')
check "$([ -n "$omissions" ] && [ -n "$activated" ] && [ "$omissions" -lt "$activated" ] && echo ok)" \
  "it prints the publication's template-omission report before it activates"
check "$([ -n "$activated" ] && echo ok)" "the document is published and activated"

echo "=== 4. typed-policies asked to publish a document the save-time checks reject"
# The vendored body with one action the registry does not contain: the same edit
# runtime-e2e/typed_policies makes to it.
python3 - "$ROOT/tests/fixtures/typed_policy_publish_body.json" "$OUT/refused-body.json" <<'PY'
import json, sys
body = json.load(open(sys.argv[1]))
body["document"]["metadata"]["document_id"] = "v11-examples-refused"
body["document"]["policy"]["policies"][0]["actions"]["actions"][0]["local"] = "tool.not_registered"
json.dump(body, open(sys.argv[2], "w"))
PY
rc=0; run_example "$TYPED_CP" com.getaxonflow.examples.TypedPolicies "$OUT/4.log" \
  AXONFLOW_TYPED_POLICY_PUBLISH=1 AXONFLOW_TYPED_POLICY_BODY="$OUT/refused-body.json" || rc=$?
check "$([ $rc = 1 ] && echo ok)" "typed-policies exits 1 when the publication it asked for is refused (exit $rc)"
check "$(grep -q '^refused: HTTP 422 document_refused: ' "$OUT/4.log" && echo ok)" \
  "it prints the platform's typed 422 document_refused"

echo "=== 5. typed-policies publishing the same document again"
rc=0; run_example "$TYPED_CP" com.getaxonflow.examples.TypedPolicies "$OUT/5.log" AXONFLOW_TYPED_POLICY_PUBLISH=1 || rc=$?
check "$([ $rc = 1 ] && echo ok)" "typed-policies exits 1 when the activation it asked for is refused (exit $rc)"
check "$(grep -q '^published ' "$OUT/5.log" && echo ok)" \
  "the second publication is accepted: a new artifact of the same document version"
check "$(grep -q '^activation refused: HTTP 409 activation_refused: ' "$OUT/5.log" && echo ok)" \
  "it prints the platform's typed 409 activation_refused"

echo "=== 6. OBSERVATION, not asserted: pep-handshake after the activation"
run_example "$HANDSHAKE_CP" com.getaxonflow.examples.PepHandshake "$OUT/6.log" || true
echo "  observed: $(grep -m1 -oE '^verdict=.*' "$OUT/6.log" || echo 'no verdict printed')"

if [ "$FAILURES" -gt 0 ]; then
  echo; echo "FAIL: v11_examples ($FAILURES assertion(s))"; exit 1
fi
echo; echo "PASS: v11_examples"
