#!/usr/bin/env bash
# ci-wiring-exempt: a code GENERATOR, not a gate. CI does not regenerate; it verifies, via AuthZENGeneratedTypesAreCurrentTest, which fails if a committed type is not what this script would produce. Running the generator in CI would test the generator against itself.
# Emits src/main/java/com/getaxonflow/sdk/authzen/*.java from the platform's
# canonical contract artifact, testdata/authzen-surface.json.
#
#   ./scripts/gen-authzen-types.sh            # write the sources
#   ./scripts/gen-authzen-types.sh --check    # fail if they are out of date
#
# WHY THIS COMPILES THE GENERATOR BY HAND RATHER THAN RUNNING `mvn test-compile`.
#
# The generator's OUTPUT lives in src/main. Running it through Maven's test
# classpath would require src/main to compile first — so the one situation where
# you most need the generator, a generated file that does not compile, is
# exactly the situation in which it could not be run. Compiling the three
# codegen sources against Jackson alone breaks that cycle: the generator depends
# on nothing it produces.
#
# The dependency classpath still comes from Maven, so the Jackson version the
# generator parses with is the one the SDK ships with, not one picked here.

set -euo pipefail

cd "$(dirname "$0")/.."

MVN="./mvnw"
[ -x "$MVN" ] || MVN="mvn"

CP_FILE="target/authzen-codegen-classpath.txt"
BUILD_DIR="target/authzen-codegen"

echo ">>> Resolving the dependency classpath"
"$MVN" -q -B dependency:build-classpath -Dmdep.outputFile="$CP_FILE" -DincludeScope=test

echo ">>> Compiling the generator"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
javac -nowarn -d "$BUILD_DIR" -cp "$(cat "$CP_FILE")" \
  src/test/java/com/getaxonflow/sdk/authzen/codegen/*.java

echo ">>> Running the generator"
java -cp "$BUILD_DIR:$(cat "$CP_FILE")" \
  com.getaxonflow.sdk.authzen.codegen.AuthZENCodegen "$@"
