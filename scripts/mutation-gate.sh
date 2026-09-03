#!/usr/bin/env bash
#
# Mutation gate for the telemetry heartbeat.
#
# Plants a known defect, asserts a NAMED test goes red, restores the file. A
# mutant that survives means the test suite cannot see that defect, and the
# gate fails.
#
# WHY THIS FILE EXISTS AT ALL. Its results were pasted into a PR body as
# evidence while the harness itself lived in /tmp. That is testimony, not
# something the repo can re-run — a reviewer could not reproduce it and a
# future change could not be checked against it. Modelled on sdk-rust's
# scripts/mutation-gate.sh, which had the same lesson applied first.
#
# THREE REFUSALS THAT ARE THE POINT OF THE HARNESS, not incidental hardening.
# Each exists because a degenerate experiment otherwise scores as a RESULT:
#
#   1. A mutant whose search pattern is ABSENT from the file was never applied.
#      Reporting that as "survived" describes an experiment that did not run.
#   2. A test selector that matches NOTHING exits 0, which reads exactly like a
#      survivor. This is not hypothetical: a rewrite of AdapterRegistryTest
#      deleted `aShortLivedProcessStillDelivers`, and the harness reported
#      "SURVIVED [Tests run: 0]" for the cold-path mutant. The test it needed
#      had ceased to exist.
#   3. Counts that disagree with the EXIT CODE. Reading "Failures: 1" alone
#      means any runner that prints failures and still exits 0 scores every
#      mutant as killed and the whole gate as PASS. The verdict requires both,
#      and a disagreement between them is an ERROR rather than a verdict.
#
# The runner is ./mvnw, not a system `mvn`, so the gate cannot be silently
# pointed at a different toolchain than the build uses.
#
# Usage: ./scripts/mutation-gate.sh
set -uo pipefail

cd "$(dirname "$0")/.."

pass=0
fail=0

# mutant <label> <file> <test-selector> <find> <replace>
mutant() {
  local label="$1" file="$2" selector="$3" find="$4" replace="$5"
  printf '\n=== mutant: %s\n    expecting RED: %s\n' "$label" "$selector"

  if ! FIND="$find" python3 -c '
import os,sys,pathlib
p=pathlib.Path(sys.argv[1])
sys.exit(0 if os.environ["FIND"] in p.read_text() else 1)' "$file"; then
    printf '    ERROR — pattern absent from %s; the mutant was never applied\n' "$file" >&2
    fail=$((fail + 1))
    return
  fi

  cp "$file" "$file.mutbak"
  FIND="$find" REPLACE="$replace" python3 -c '
import os,sys,pathlib
p=pathlib.Path(sys.argv[1])
p.write_text(p.read_text().replace(os.environ["FIND"], os.environ["REPLACE"], 1))' "$file"

  local out rc
  out=$(./mvnw test -Dtest="$selector" -DfailIfNoTests=true 2>&1) && rc=0 || rc=$?
  mv -f "$file.mutbak" "$file"

  local line run fails errs
  line=$(printf '%s' "$out" | grep -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+" | tail -1)
  if [ -z "$line" ]; then
    printf '    ERROR — no test-count line; the mutant probably did not compile\n' >&2
    fail=$((fail + 1))
    return
  fi
  run=$(printf '%s' "$line" | sed -E 's/.*Tests run: ([0-9]+).*/\1/')
  fails=$(printf '%s' "$line" | sed -E 's/.*Failures: ([0-9]+).*/\1/')
  errs=$(printf '%s' "$line" | sed -E 's/.*Errors: ([0-9]+).*/\1/')

  if [ "$run" -eq 0 ]; then
    printf '    ERROR — ZERO tests ran; selector %s matches nothing\n' "$selector" >&2
    fail=$((fail + 1))
    return
  fi
  # THE EXIT CODE IS PART OF THE VERDICT, not a formality. Reading the counts alone
  # means a build that prints "Tests run: 5, Failures: 1" and exits 0 scores as a kill,
  # so a broken or stubbed runner reports a clean gate. Require both.
  if [ "$rc" -ne 0 ] && { [ "$fails" -gt 0 ] || [ "$errs" -gt 0 ]; }; then
    printf '    killed  [run=%s fail=%s err=%s]\n' "$run" "$fails" "$errs"
    pass=$((pass + 1))
  elif [ "$rc" -eq 0 ] && { [ "$fails" -gt 0 ] || [ "$errs" -gt 0 ]; }; then
    printf '    ERROR — counts report failures but the build exited 0; runner is untrustworthy\n' >&2
    fail=$((fail + 1))
  else
    printf '    SURVIVED — the suite does not detect this defect  [run=%s]\n' "$run"
    fail=$((fail + 1))
  fi
}

AX=src/main/java/com/getaxonflow/sdk/AxonFlow.java
HS=src/main/java/com/getaxonflow/sdk/telemetry/HeartbeatState.java
TR=src/main/java/com/getaxonflow/sdk/telemetry/TelemetryReporter.java
AT=src/test/java/com/getaxonflow/sdk/telemetry/AdapterRegistryTest.java

# The cold path must run INLINE. Spawning it drops the ping in a process that
# does not outlive the dispatch — the #1693 regression, reintroduced by moving
# the trigger off the constructor.
mutant "cold path made async" "$AX" AdapterRegistryTest \
  '    if (HeartbeatState.shared().isGuardWarm()) {
      invokeHeartbeatAsync();
      return;
    }
    invokeHeartbeat();' \
  '    invokeHeartbeatAsync();'

# The backoff ceiling. Without the clamp, a long shift distance masks to its low
# 6 bits and the interval snaps back to one hour at exactly 64 failures.
mutant "backoff clamp deleted" "$HS" HeartbeatStateTest \
  '    int doublings = Math.min(Math.max(consecutiveFailures, 0), MAX_BACKOFF_DOUBLINGS);' \
  '    int doublings = Math.max(consecutiveFailures, 0);'

# Both redirect refusals must be observable, not just present.
mutant "checkpoint redirect log deleted" "$TR" AdapterRegistryTest \
  '        if (isRedirect(response.code())) {' \
  '        if (false) {'

mutant "/health redirect log deleted" "$TR" AdapterRegistryTest \
  '          if (isRedirect(response.code())) {' \
  '          if (false) {'

# The request-site census. Each needle covers a spelling that slipped past an
# earlier revision of the detector; removing any one must red the census.
mutant "census: java.net.http needle removed" "$AT" AdapterRegistryTest \
  '          java.util.regex.Pattern.compile("java\\.net\\.http\\."),' ''

mutant "census: .newCall needle removed" "$AT" AdapterRegistryTest \
  '          java.util.regex.Pattern.compile("\\.newCall\\("),' ''

mutant "census: openConnection needle removed" "$AT" AdapterRegistryTest \
  '          java.util.regex.Pattern.compile("HttpURLConnection|\\.openConnection\\("),' ''

mutant "census: openStream needle removed" "$AT" AdapterRegistryTest \
  '          java.util.regex.Pattern.compile("\\.openStream\\("),' ''

printf '\n=== mutation gate: %d killed, %d survived\n' "$pass" "$fail"
if [ "$fail" -ne 0 ]; then
  echo "FAIL: a planted defect went undetected, or an experiment did not run" >&2
  exit 1
fi
echo "PASS"
