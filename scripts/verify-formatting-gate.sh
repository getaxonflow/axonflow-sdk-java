#!/usr/bin/env bash
#
# Drive the CI formatting step against stub runners and print what it does.
#
# WHY THIS EXISTS. The step it checks could not fail: both arms of its retry
# loop broke on attempt 1, so it exited 0 whether the check passed or failed and
# a violation printed "Code formatting check skipped" under a green tick. A
# claim that a CI step now fails correctly is only worth as much as the
# experiment behind it, so the experiment is committed rather than pasted.
#
# THE STEP IS EXTRACTED FROM THE PARSED YAML, not copied into this file. A
# hand-copied shell block is a LOOKALIKE: it can drift from the workflow and
# then this harness certifies a step that is not the one that ships. What runs
# below is `jobs.*.steps[name == "Check code formatting"].run`, verbatim.
#
# Usage:
#   ./scripts/verify-formatting-gate.sh              # the working tree's ci.yml
#   ./scripts/verify-formatting-gate.sh origin/main  # any git ref, for comparison
#
# Exit 0 if the step behaves correctly in all four cases, 1 otherwise. Run it
# against origin/main before the fix and it FAILS, which is the positive control
# that this harness can tell the two apart.
set -uo pipefail

cd "$(dirname "$0")/.."
REF="${1:-}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [ -n "$REF" ]; then
  git show "$REF:.github/workflows/ci.yml" > "$WORK/ci.yml" || {
    echo "cannot read .github/workflows/ci.yml at $REF" >&2; exit 1; }
  LABEL="$REF"
else
  cp .github/workflows/ci.yml "$WORK/ci.yml"
  LABEL="working tree"
fi

python3 - "$WORK/ci.yml" "$WORK/step.sh" <<'PY'
import sys, yaml, pathlib
doc = yaml.safe_load(open(sys.argv[1]))
found = [s["run"] for j in doc["jobs"].values()
         for s in j.get("steps", []) if s.get("name") == "Check code formatting"]
if len(found) != 1:
    print(f"expected exactly one 'Check code formatting' step, found {len(found)}", file=sys.stderr)
    sys.exit(1)
pathlib.Path(sys.argv[2]).write_text("#!/usr/bin/env bash\n" + found[0])
PY
[ -f "$WORK/step.sh" ] || exit 1
chmod +x "$WORK/step.sh"

mkdir -p "$WORK/bin"
rc_of() {  # <stub-body> -> prints exit code of the step under that stub
  # Every invocation is RECORDED. Counting "Attempt N:" lines counts what the
  # step PRINTED, which a broken loop can still print three times while calling
  # the runner once. The count that means "it retried" is the number of times
  # the runner actually ran.
  rm -f "$WORK/invocations"
  printf '#!/usr/bin/env bash\necho x >> "%s"\n%s\n' "$WORK/invocations" "$1" > "$WORK/bin/mvn"
  chmod +x "$WORK/bin/mvn"
  # The transient case sleeps 30s between attempts; cap it so the harness is quick.
  # STDOUT AND STDERR ARE KEPT SEPARATE, and that is load bearing. The step
  # echoes the runner's whole output on stdout, so a marker asserted against the
  # merged stream is satisfied by that echo no matter what the step's own
  # diagnostics did. Deleting the `grep "Non complying file" >&2` line — the one
  # the commit message calls the only actionable output — SURVIVED an assertion
  # written against the merged stream, because the string was still present in
  # the echoed input. The dedicated diagnostics go to stderr; assert there.
  PATH="$WORK/bin:$PATH" timeout 200 bash "$WORK/step.sh" >"$WORK/out" 2>"$WORK/err"
  echo $?
}

# SELF-CHECK: this script must not carry the idiom it exists to catch.
#
# Round 2 found two `cat a b | grep -q` assertions in here — the same pipefail
# race the step was fixed for. They were latent because the asserted strings sit
# at the end of the large stream, so `cat` finished before `grep -q` exited. A
# harness carrying the defect it checks for is worse than no harness, so the
# rule is enforced rather than remembered.
#
# Code lines only: a `grep -c` on the whole file would be satisfied by the prose
# above, which names the idiom in order to forbid it.
offenders=$(grep -nE '^[^#]*\|[[:space:]]*grep[[:space:]]+-[a-zA-Z]*q' "$0" || true)
if [ -n "$offenders" ]; then
  echo "SELF-CHECK FAILED: piping into 'grep -q' under pipefail is the race this harness tests for:" >&2
  printf '%s\n' "$offenders" >&2
  exit 1
fi

echo "=== formatting step behaviour: $LABEL"
bad=0
# check <label> <stub> <expected-rc> <expected-message> [stub-sentinel]
#
# THE STUB MUST PROVE IT RAN. A stub with a syntax error prints a bash
# diagnostic and exits nonzero, which the step then classifies as an
# unrecognised failure and exits 1 — so every case expecting 1 PASSES, for a
# reason that has nothing to do with the step. That is not hypothetical: the
# first version of the 60k-line case had a nested heredoc that broke the stub,
# and it reported a result about a step it never exercised.
#
# So each case names a SENTINEL its stub is supposed to emit, and the sentinel
# must appear in what the step captured. A silent or broken stub is reported as
# an INVALID EXPERIMENT, never as a verdict — the same refusal the mutation gate
# makes for a mutant that was never applied.
# THE FORBIDDEN MESSAGE IS NOT DECORATION. Asserting only that the right branch
# fired leaves "the right branch fired AND SO DID A WRONG ONE" indistinguishable
# from correct. Deleting the `exit 1` from the violation branch does exactly
# that: the step prints the violation diagnostic, falls through, and exits 1 via
# the unrecognised-failure branch — right code, right message, plus a second
# message contradicting it. That mutant SURVIVED until this argument existed.
check() {
  local label="$1" stub="$2" want_rc="$3" want_msg="$4" sentinel="${5:-}" forbid="${6:-}"
  local got; got=$(rc_of "$stub")
  local verdict="ok"

  if [ -n "$sentinel" ] && ! grep -qF -- "$sentinel" "$WORK/out" "$WORK/err"; then
    printf '  %-26s INVALID — stub produced no %s; it did not run\n' "$label" "$sentinel" >&2
    bad=1
    return
  fi
  if [ "$got" != "$want_rc" ]; then
    verdict="WRONG (expected exit $want_rc)"; bad=1
  # grep READS BOTH FILES DIRECTLY. It must never be `cat a b | grep -q`: under
  # `set -o pipefail` that is the very race this harness exists to catch. grep -q
  # exits at the first match, cat dies of SIGPIPE, and pipefail reports the
  # pipeline failed although the MATCH SUCCEEDED. Measured with these options:
  # `cat big small | grep -qF <marker near the start>` gives rc 141 with
  # PIPESTATUS "141 0". Two of these assertions were written that way and were
  # latent only because the asserted strings sit at the END of the large stream,
  # so cat finishes before grep exits. A harness carrying the idiom it checks for
  # is worse than no harness.
  elif [ -n "$want_msg" ] && ! grep -qF -- "$want_msg" "$WORK/out" "$WORK/err"; then
    # The exit code is not the whole verdict. A step that fails for the WRONG
    # stated reason sends the next reader after a phantom network problem
    # instead of a formatting violation — which is exactly how the step's own
    # pipefail race hid: correct exit code, wrong branch.
    verdict="WRONG (exit right, but did not say: $want_msg)"; bad=1
  elif [ -n "$forbid" ] && grep -qF -- "$forbid" "$WORK/out" "$WORK/err"; then
    verdict="WRONG (also said: $forbid — a second branch fired)"; bad=1
  fi
  printf '  %-26s exit=%-3s %s\n' "$label" "$got" "$verdict"
}

check "clean tree" \
  'echo "[INFO] BUILD SUCCESS"; exit 0' \
  0 "Formatting check passed" "BUILD SUCCESS" "::error::"

check "formatting violation" \
  'echo "[ERROR] Non complying file: /x/Foo.java"; exit 1' \
  1 "Formatting violations found" "Non complying file" "not a known transient"

# `Could not transfer` is the string a live resolution failure actually emits.
# `Could not resolve` is in the step's marker list but was never observed, so a
# stub built on it would test a branch the real world does not reach.
check "resolution failure" \
  'echo "[ERROR] Could not transfer artifact org.x:y:jar:1.0 from central"; exit 1' \
  1 "could not run after" "Could not transfer" "Formatting violations found"

# THE RETRY PATH MUST ACTUALLY RETRY. The message above is reachable from a
# single attempt if the loop is broken, so the attempt COUNT is asserted
# separately: three "Attempt N:" lines, no more and no fewer.
# grep reads both files; `wc -l` consumes ALL of its input, so unlike `grep -q`
# there is no early-exit consumer to trigger SIGPIPE.
attempts=$(grep -h "^Attempt " "$WORK/out" "$WORK/err" | wc -l | tr -d ' ' || true)
attempts=${attempts:-0}
# GUARDED, because `wc -l < missing 2>/dev/null` does NOT suppress the message:
# the redirection is set up before the `2>` applies, so bash prints a raw
# "No such file or directory" — exactly when someone is diagnosing a real
# failure and least needs unexplained noise. The value defaulted to 0 and the
# verdict was right; the noise was the defect.
if [ -f "$WORK/invocations" ]; then
  invocations=$(wc -l < "$WORK/invocations" | tr -d ' ')
else
  invocations=0
fi
if [ "$attempts" -ne 3 ] || [ "$invocations" -ne 3 ]; then
  echo "  ${LABEL}: transient failure printed ${attempts} attempts and CALLED the runner ${invocations} times, expected 3 and 3" >&2
  bad=1
fi

check "unrecognised failure" \
  'echo "[ERROR] something new and weird"; exit 1' \
  1 "not a known transient" "something new and weird" "Formatting violations found"

# THE SIZE AXIS, AND IT IS NOT PADDING. The four cases above all emit a few
# lines, and a step written with `printf ... | grep -q` under `set -o pipefail`
# PASSES all of them while failing on real output: grep -q exits at the first
# match, printf dies of SIGPIPE, and pipefail marks the pipeline failed even
# though the match succeeded. Short stubs finish printing before grep exits and
# never trigger it.
#
# That defect was in this very step and these very four cases did not see it. A
# harness that cannot vary an axis leaves it untested forever, so the axis is
# varied: the marker is emitted EARLY and followed by 60k lines of noise, which
# is what gives grep time to exit first.
#
# THIS IS A STRESS CASE, NOT A REPLAY OF THE REAL WORKLOAD, and saying so matters
# because the first version of this comment implied otherwise. Real `:check`
# output measures 1,648 bytes with one non-complying file and 9,883 with all 39
# — both under the 64 KiB pipe buffer, so the race never fired in CI. The case
# exists to hold the fix in place against a future where output grows, which is
# what a regression test is for.
check "violation, 60k lines" \
  'echo "[ERROR] Non complying file: /x/Foo.java"; i=0; while [ $i -lt 60000 ]; do echo "[INFO] padding line $i"; i=$((i+1)); done; exit 1' \
  1 "Formatting violations found" "Non complying file" "not a known transient"

# The file LIST is the actionable part, and it travels a different path from the
# headline (a herestring grep rather than a bash pattern test), so it is asserted
# separately. Under the pipefail race this line was never printed at all.
if ! grep -q "Non complying file: /x/Foo.java" "$WORK/err"; then
  echo "  ${LABEL}: the non-complying file LIST was not printed to stderr" >&2
  bad=1
fi

if [ "$bad" -ne 0 ]; then
  echo "FAIL: the formatting step does not fail when it must" >&2
  exit 1
fi
echo "PASS: the step fails on a violation and on an unrecognised failure"
