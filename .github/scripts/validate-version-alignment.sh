#!/usr/bin/env bash
# Validates that the Java SDK's declared version (pom.xml) matches the
# latest released version in CHANGELOG.md. Patterned on the AxonFlow
# platform's and Go SDK's script of the same name.
#
# Purpose: keep the repo's manifest (pom.xml) in lock-step with the
# CHANGELOG so the state on `main` always matches the most recent
# published tag. Prevents two drift patterns:
#   - repo says 5.7.0 but 5.7.1 has already shipped to Maven Central
#   - repo says 5.7.1 but CHANGELOG still shows 5.7.0 as latest released
#
# Run locally:
#   ./.github/scripts/validate-version-alignment.sh
#
# CI: runs on every PR and push to main that touches CHANGELOG.md or
# pom.xml (see .github/workflows/validate-version-alignment.yml).

set -euo pipefail

ERRORS=0

# Latest RELEASED version = first `## [x.y.z]` line that isn't the
# Keep-a-Changelog "[Unreleased]" placeholder. The Unreleased section
# accumulates in-flight changes between tags and must not be used as
# the expected-version target — the manifest only gets bumped when we
# actually cut a tag.
LATEST_VERSION=$(grep -m1 -E '^## \[[0-9]' CHANGELOG.md | sed 's/## \[\(.*\)\].*/\1/' | sed 's/^v//')

if [ -z "${LATEST_VERSION:-}" ]; then
    echo "❌ Could not extract a released version (## [X.Y.Z]) from CHANGELOG.md"
    exit 1
fi

echo "📋 Latest CHANGELOG version: $LATEST_VERSION"
echo ""

# Check pom.xml <version>. We match the first top-level <version> (the
# project's own declaration), not any <version> inside <dependencies>
# or <plugins>. The project version is the first match in Maven's
# standard layout.
echo "🔧 Checking pom.xml..."
POM_VERSION=$(grep -m1 -E '^\s*<version>[0-9]+\.[0-9]+\.[0-9]+.*</version>' pom.xml \
    | sed -E 's|.*<version>(.*)</version>.*|\1|' || true)

if [ -z "${POM_VERSION:-}" ]; then
    echo "  ❌ pom.xml — could not extract <version> element"
    ERRORS=$((ERRORS + 1))
elif [ "$POM_VERSION" != "$LATEST_VERSION" ]; then
    echo "  ❌ pom.xml — <version> is \"$POM_VERSION\", expected \"$LATEST_VERSION\""
    ERRORS=$((ERRORS + 1))
else
    echo "  ✅ pom.xml — $POM_VERSION"
fi

echo ""

if [ "$ERRORS" -gt 0 ]; then
    echo "❌ Found $ERRORS version misalignment(s)."
    echo ""
    echo "Fix: bump the stale file to match CHANGELOG v$LATEST_VERSION."
    echo "Or, if CHANGELOG is behind a tag you already pushed, add the"
    echo "missing '## [${POM_VERSION:-X.Y.Z}] - YYYY-MM-DD' section."
    exit 1
fi

echo "✅ All version declarations match CHANGELOG v$LATEST_VERSION."
