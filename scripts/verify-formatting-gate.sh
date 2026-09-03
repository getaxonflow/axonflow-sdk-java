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
  printf '#!/usr/bin/env bash\n%s\n' "$1" > "$WORK/bin/mvn"
  chmod +x "$WORK/bin/mvn"
  # The transient case sleeps 30s between attempts; cap it so the harness is quick.
  PATH="$WORK/bin:$PATH" timeout 200 bash "$WORK/step.sh" >"$WORK/out" 2>&1
  echo $?
}

echo "=== formatting step behaviour: $LABEL"
bad=0
check() {  # <label> <stub> <expected-rc>
  local got; got=$(rc_of "$2")
  local verdict="ok"
  if [ "$got" != "$3" ]; then verdict="WRONG (expected $3)"; bad=1; fi
  printf '  %-26s exit=%-3s %s\n' "$1" "$got" "$verdict"
}

check "clean tree"            'exit 0'                                                        0
check "formatting violation"  'echo "[ERROR] Non complying file: /x/Foo.java"; exit 1'        1
check "resolution failure"    'echo "[ERROR] Could not resolve dependencies"; exit 1'         1
check "unrecognised failure"  'echo "[ERROR] something new and weird"; exit 1'                1

if [ "$bad" -ne 0 ]; then
  echo "FAIL: the formatting step does not fail when it must" >&2
  exit 1
fi
echo "PASS: the step fails on a violation and on an unrecognised failure"
