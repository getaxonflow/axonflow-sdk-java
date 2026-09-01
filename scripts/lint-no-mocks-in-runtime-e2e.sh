#!/usr/bin/env bash
# lint-no-mocks-in-runtime-e2e.sh
#
# Per HARD RULE #0 in CLAUDE.md: runtime-e2e/ tests MUST hit a real endpoint
# (real plugin in real host CLI; real SDK with real fetch against a real
# running agent). Mocks, stubs, simulators, capture-stub harnesses do NOT
# count as runtime proof.
#
# This script greps runtime-e2e/ for forbidden mock-pattern strings and
# fails the build if any are found. It runs in CI as part of the
# definition-of-done.yml gate.
#
# To bypass for a specific LINE (rare, must justify in PR):
#   put `allow-mocks-here: <reason>` in a comment ON that line. The bypass is
#   line-scoped on purpose: it used to be file-scoped while the error text below
#   told reviewers it was per-line, so one marker anywhere in a file - a README
#   sentence included - disarmed all of the patterns for the whole file.

set -uo pipefail

SCAN_DIR="${1:-runtime-e2e}"

if [ ! -d "$SCAN_DIR" ]; then
  echo "lint-no-mocks: $SCAN_DIR not present, nothing to scan."
  exit 0
fi

# Forbidden patterns. Each one represents a way to fake a runtime response.
# Add to this list as new mock libraries arrive in the codebase.
PATTERNS=(
  'mockFetch'                    # jest fetch mock
  'jest\.mock'                   # jest module mock
  'jest\.fn'                     # jest stub
  'sinon\.stub'                  # sinon test double
  'unittest\.mock'               # python stdlib mock
  'MagicMock'                    # python mock class
  'httpx_mock\.add_response'     # python httpx mock
  'wiremock'                     # java/jvm wiremock
  'WireMockServer'               # wiremock builder
  'stubFor'                      # wiremock stub
  'Mockito\.'                    # the mock library this repo actually declares
  '@Mock\b'                      # mockito field/parameter injection
  '@Spy\b'                       # mockito partial double
  'MockWebServer'                 # okhttp's stub server, named in definition-of-done.yml
  'mockwebserver'                 # its package
  'httptest\.NewServer'          # go httptest stub server
  'capture-stub\.py'             # local capture harness
  'fixture-server'               # generic fixture server
  'msw\.setupServer'             # jsdom mock service worker
  'nock\.'                       # nock http stubs (node)
)

EXIT=0
COUNT=0

# Build a regex from PATTERNS; escape literal dots already in the pattern source
REGEX=$(IFS='|'; echo "${PATTERNS[*]}")

# Use plain grep -r so we catch untracked files too (CI sees tracked PR
# content, but local dev/pre-commit may run against new files not yet added).
# Prose is EXCLUDED; everything else is still scanned.
#
# A README that DESCRIBES what is forbidden - this directory's own does, in the
# sentence "WireMock/MockWebServer fixtures is not a runtime test" - is not a
# mock, and matching it makes the guard's marker phrase collide with the
# documentation beside it.
#
# The first attempt at this used an --include ALLOWLIST of source extensions,
# and that silently narrowed the guard far past prose: a `docker-compose.yml`
# pulling `wiremock/wiremock`, a `package.json` declaring `nock`, a Kotlin file
# and an extensionless harness script were all caught by the previous version
# and missed by the allowlist. A runtime-e2e harness that boots a mock server
# from its compose file is exactly what this gate exists to stop, so the
# exclusion is of MARKDOWN, not an allowlist of what to look at.
#
# `*.rst` and `*.txt` used to be excluded here too, which overshot the comment
# directly above by two extensions and reopened the hole the allowlist had
# already been rejected for: a `requirements.txt` under runtime-e2e/ pinning
# `wiremock` is a harness declaring a mock server, not prose describing one, and
# it was invisible. Only the two MARKDOWN extensions are excluded. An .rst or
# .txt that genuinely names one of these in prose uses the `allow-mocks-here:`
# marker, same as any other file.
matches=$(grep -rnE "$REGEX" "$SCAN_DIR" \
  --exclude='*.md' --exclude='*.markdown' \
  2>/dev/null || true)

if [ -z "$matches" ]; then
  echo "lint-no-mocks: $SCAN_DIR is clean (no forbidden mock patterns found)."
  exit 0
fi

# Filter out lines explicitly allowed via the inline marker, on THAT line.
while IFS= read -r line; do
  # grep -n output is `path:lineno:content`; the marker must be in the content.
  content=$(echo "$line" | cut -d: -f3-)
  if printf '%s' "$content" | grep -q "allow-mocks-here:"; then
    continue
  fi
  echo "  $line"
  COUNT=$((COUNT + 1))
  EXIT=1
done <<< "$matches"

if [ "$EXIT" -ne 0 ]; then
  echo ""
  echo "lint-no-mocks: $COUNT forbidden mock-pattern hit(s) in $SCAN_DIR." >&2
  echo "" >&2
  echo "Per CLAUDE.md HARD RULE #0, runtime-e2e/ tests MUST hit a real endpoint." >&2
  echo "Mocks, stubs, fixture-servers, and capture harnesses do NOT count as" >&2
  echo "runtime proof. The runtime-e2e/ test for a feature must invoke the" >&2
  echo "feature through its actual user-facing surface (host CLI tool/skill," >&2
  echo "real SDK fetch to a running agent, etc.)." >&2
  echo "" >&2
  echo "If a specific test legitimately needs a stub (rare — usually means" >&2
  echo "it's not actually a runtime test and belongs elsewhere), add a" >&2
  echo "  # allow-mocks-here: <reason>" >&2
  echo "comment on the line and a reviewer must explicitly approve it." >&2
fi

exit "$EXIT"
