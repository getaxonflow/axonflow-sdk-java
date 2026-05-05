# Runtime End-to-End Tests — Java SDK

Tests in this directory MUST exercise the published `axonflow-sdk` Java
library through its real user-facing surface — a real JVM loading the
built `axonflow-sdk-<version>.jar`, a real `AxonFlow` client constructed
through the public builder, and a real HTTP request to a real running
AxonFlow agent. Calling internal classes, package-private helpers, or
WireMock/MockWebServer fixtures is not a runtime test — those belong
under `tests/` (unit + integration).

If the Java SDK can't reach a feature through its public API, the feature
isn't ready to ship.

## Why this directory exists

A May 3, 2026 audit found multiple AxonFlow capabilities (audit search,
decision explain, override CRUD) where the platform endpoint and SDK
method existed for months but no host integration ever wired them up.
Users running with the AxonFlow Java SDK could not reach the capability.
The fix: every user-facing AxonFlow feature exposed via this SDK must
have a test in this directory that invokes through the SDK's real public
API hitting a real running agent.

The single rule:

> **If a user cannot reach the feature from their runtime, we did not
> ship a feature, we shipped a library.**

## What "runtime" means here

The runtime is a real JVM with the built SDK JAR on the classpath, where
the test:

- Constructs `AxonFlow.create(AxonFlowConfig.builder()...)` exactly as a
  consumer would.
- Issues real HTTP requests through the SDK to a real AxonFlow agent
  reachable over the network.
- Asserts on the wire-level behaviour observable to that consumer
  (response body, exception message, agent-side audit) — not on internal
  fields of mock objects.

If a test imports `com.getaxonflow.sdk.internal.*` or pulls in any
HTTP-stubbing fixture library or `mockito-*`, it is a unit/integration
test. That belongs under `tests/`, not here.

## Layout

```
runtime-e2e/
  README.md                    # this file
  <feature-name>/              # one folder per feature
    <Feature>Test.java         # `java` script, run via classpath
    README.md                  # 5 lines: prereqs, what it asserts, how to run
```

## Running

Each test folder has its own README. Most tests assume:

- An AxonFlow community-saas-style stack is reachable (default
  `http://localhost:8080`, override with `AXONFLOW_AGENT_URL`).
- The SDK is built locally: `mvn -DskipTests package` produces
  `target/axonflow-sdk-<version>.jar`.
- A working JDK 17+ on `$PATH` (use `java <File>.java` single-file mode
  with `-cp` pointing at the SDK JAR + Maven runtime classpath).

Typical invocation:

```bash
mvn -DskipTests dependency:build-classpath \
    -Dmdep.outputFile=/tmp/cp.txt -q
SDK_JAR=$(ls target/axonflow-sdk-*.jar | head -1)
CP="$SDK_JAR:$(cat /tmp/cp.txt)"

AXONFLOW_AGENT_URL=http://localhost:8080 \
AXONFLOW_TENANT_ID=cs_... \
AXONFLOW_TENANT_SECRET=... \
AXONFLOW_E2E_PLUGIN_TOKEN=AXON-... \
  java -cp "$CP" runtime-e2e/x-axonflow-client/SdkClientHeaderTest.java
```

Note: like the Go SDK, the Java SDK does not currently expose a public
hook for injecting `X-License-Token` per-request. Tests that need to
prove a particular header reaches the agent should chain through a small
local logging proxy that adds the token before forwarding to the real
agent. See `x-axonflow-client/README.md` for the proxy snippet.

## Adding a test

1. Confirm you can invoke the feature through the real published
   `AxonFlow` client. If you can't, the answer is to fix the SDK's
   public surface, not to write a `tests/`-style integration test that
   imports internals.
2. Create the folder, write `<Feature>Test.java` and `README.md`.
3. Update
   `axonflow-internal-docs/engineering/FEATURE_RUNTIME_COVERAGE.md`
   (private; engineering team only) to mark the new green cell under
   the Java SDK column.
4. Reference the test in the PR that wires the feature.
