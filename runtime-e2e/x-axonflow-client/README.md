# runtime-e2e — `X-Axonflow-Client` wire assertion (Java SDK)

## What this asserts

The published `axonflow-sdk` Java client, when constructed via the
public `AxonFlow.create(AxonFlowConfig.builder()...)` surface and used
to call `mcpCheckInput`, emits the header

```
X-Axonflow-Client: sdk-java/<SDK_VERSION>
```

on every governed request, where `<SDK_VERSION>` is the value of
`com.getaxonflow.sdk.AxonFlowConfig.SDK_VERSION` baked into the JAR.

The agent is configured to reject requests whose
`X-License-Token` scope does not match that header. The test asserts
the agent's rejection message echoes the header value — proving the
header travelled across the wire and was read by the agent, not just
set on the local request object.

## Prereqs

- Java 17+ on `$PATH` (`java --version`).
- `mvn -DskipTests package` has been run; `target/axonflow-sdk-*.jar`
  exists.
- A running AxonFlow agent reachable at `$AXONFLOW_AGENT_URL`
  (default `http://localhost:8080`) — typically a local
  community-saas stack brought up by `./scripts/setup-e2e-testing.sh
  community`.
- Tenant credentials: `AXONFLOW_TENANT_ID` + `AXONFLOW_TENANT_SECRET`.
- A scoped license token: `AXONFLOW_E2E_PLUGIN_TOKEN` (issue via the
  community-saas `/license/issue` flow with a scope that is _not_
  `sdk-java/*`, so the agent rejects with the expected error).

## How to run

```bash
cd <axonflow-sdk-java repo>

# Build SDK + materialise runtime classpath
mvn -DskipTests package
mvn -DskipTests dependency:build-classpath \
    -Dmdep.outputFile=/tmp/cp.txt -q
SDK_JAR=$(ls target/axonflow-sdk-*.jar | head -1)
CP="$SDK_JAR:$(cat /tmp/cp.txt)"

# (1) Bring up local logging proxy on :18080 that injects
#     `X-License-Token: $AXONFLOW_E2E_PLUGIN_TOKEN` and forwards to
#     $REAL_AGENT (e.g. http://localhost:8080). The simplest form is a
#     ~25-line Python http.server with do_POST forwarding via urllib;
#     a worked example is checked in at
#     /tmp/axonflow-e2e/proxy.py from the May 4 2026 wire-shape session.
#
# (2) Point the SDK at the proxy:
AXONFLOW_AGENT_URL=http://localhost:18080 \
AXONFLOW_TENANT_ID=cs_... \
AXONFLOW_TENANT_SECRET=... \
AXONFLOW_E2E_PLUGIN_TOKEN=AXON-... \
  java -cp "$CP" runtime-e2e/x-axonflow-client/SdkClientHeaderTest.java
```

PASS exits 0 with `PASS: agent reflected sdk-java/<v> ...`. Any other
result fails with a clear message.

## Why through a proxy?

`sdk-java` does not (today) expose a public `requestInterceptor` /
header-builder hook for callers, so the
`X-License-Token` header — which is the agent's input for the
scope-match check — must be added by an out-of-process agent. A small
local proxy is the correct shape: it changes nothing about how the SDK
constructs the request, only adds one header on the way out, mirroring
how a sidecar would behave in production. Adding a public
`requestInterceptor` to the SDK would let this test skip the proxy; that
work is tracked separately.
