#!/usr/bin/env bash
# Runtime proof — Java SDK v8 sandbox-mode telemetry fires with stream=sandbox.
#
# Builds the local SDK with Maven (`mvn install -DskipTests`), then a tiny
# consumer that depends on `com.getaxonflow:axonflow-sdk:8.0.0`, constructs
# a Mode.SANDBOX client against an unreachable agent endpoint, and waits for
# the anonymous telemetry ping to fire. We then query the deployed
# checkpoint Lambda's CloudWatch logs for the audit line that should record
# stream=sandbox in DynamoDB.
#
# Pre-v8 this test would have produced ZERO pings (sandbox-mode silent
# suppression). Post-v8 we expect exactly one ping with stream=sandbox.
#
# Stack-state assumptions:
#   - axonflow-enterprise PR #2005 is deployed (server-side stream allowlist
#     accepts and persists "sandbox" — without that, this row is stored
#     as stream=heartbeat, defeating the test's purpose).
#   - AWS credentials with read access on /aws/lambda/prod-axonflow-checkpoint.
#
# Usage:
#   AWS_REGION=us-east-1 ./test.sh

set -uo pipefail

REGION=${AWS_REGION:-us-east-1}
LOG_GROUP=${LOG_GROUP:-/aws/lambda/prod-axonflow-checkpoint}
RUN_TAG=$(date -u +%s)
SDK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }

# 1. Install the local SDK to the Maven local repo so the consumer can
# resolve `com.getaxonflow:axonflow-sdk:8.0.0` from `~/.m2/repository`.
echo "Installing local SDK to Maven local repo..."
(
  cd "$SDK_ROOT"
  ./mvnw -q -DskipTests install
) || {
  red "FAIL: mvn install of local SDK failed"
  exit 1
}

# 2. Build a transient consumer pom + main class. The unreachable :65530
# endpoint is intentional — we only want the anonymous heartbeat to fire,
# not any platform call.
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/pom.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.getaxonflow.runtime-e2e</groupId>
  <artifactId>sandbox-telemetry-stream-tag</artifactId>
  <version>0.0.1</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.getaxonflow</groupId>
      <artifactId>axonflow-sdk</artifactId>
      <version>8.0.0</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.1.0</version>
        <configuration>
          <mainClass>SandboxRuntimeProof</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
EOF

mkdir -p "$WORK/src/main/java"
cat > "$WORK/src/main/java/SandboxRuntimeProof.java" <<'EOF'
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.Mode;

public class SandboxRuntimeProof {
  public static void main(String[] args) throws Exception {
    System.out.println("[runtime-e2e] Constructing Sandbox client (unreachable agent)...");
    AxonFlowConfig config = AxonFlowConfig.builder()
        .endpoint("http://localhost:65530")
        .clientId("rt-test")
        .clientSecret("rt-test")
        .mode(Mode.SANDBOX)
        .build();
    // Construction triggers the synchronous heartbeat ping to checkpoint.
    @SuppressWarnings("unused")
    AxonFlow client = AxonFlow.create(config);
    System.out.println("[runtime-e2e] AxonFlow.create returned. Sleeping 2s for inflight HTTP...");
    Thread.sleep(2000);
    System.out.println("[runtime-e2e] Done.");
  }
}
EOF

T0_MS=$(($(date -u +%s)*1000))
echo "Run tag: $RUN_TAG"
echo "T0 (ms): $T0_MS"
echo

# Note: the SDK reads AXONFLOW_TELEMETRY directly via System.getenv, so we
# explicitly clear it for this run. Pre-v8 dev envs commonly had it set to
# `off` to suppress noise.
unset AXONFLOW_TELEMETRY

(
  cd "$WORK"
  mvn -q -DskipTests package 2>&1 | tail -3
  mvn -q exec:java 2>&1
)

echo
echo "Waiting 10s for CloudWatch log delivery..."
sleep 10

# Look for the audit row our run produced — match by sdk=java and a fresh
# correlation_id stamped within the last ~1 minute window.
echo "Querying CloudWatch logs since T0 for sdk=java event_stored entries..."
HITS=$(aws --region "$REGION" logs filter-log-events \
  --log-group-name "$LOG_GROUP" \
  --start-time "$T0_MS" \
  --filter-pattern '"event_stored" "sdk=java/8"' \
  --query 'events[*].message' \
  --output text 2>&1)

if [ -z "$HITS" ]; then
  red "FAIL: no event_stored sdk=java/8 row landed in checkpoint logs since T0"
  red "  Expected: one audit row tagged stream=sandbox"
  red "  CloudWatch query window: $T0_MS → now"
  exit 1
fi

echo "Audit rows found:"
echo "$HITS"
echo

if echo "$HITS" | grep -q 'stream=sandbox'; then
  green "PASS: Java SDK sandbox-mode ping landed with stream=sandbox"
else
  red "FAIL: audit row did not include stream=sandbox"
  red "  This usually means PR #2005 (server-side allowlist) is not yet deployed —"
  red "  the server still hardcodes stream=heartbeat regardless of payload."
  exit 1
fi
