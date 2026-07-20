# caller_name_audit (getaxonflow/axonflow-enterprise#2912, epic #2905)

Real-stack proof that `AuditToolCallRequest.callerName` (wire: `caller_name`)
reaches `policy_details.caller_name` on the persisted audit row, driven
entirely through the SDK's real public `AxonFlow#auditToolCall()` against a
live agent + orchestrator. No mocks.

## Background

`audit_tool_call`'s `tool_type` field was misleadingly named — every real
caller (claude_code/codex/cursor/openclaw) used it to identify WHICH CLIENT
made the call, not any property of the tool. `callerName` is the field that
actually matches that contract. `toolType` is kept as a **deprecated input
fallback** — not removed, not renamed. The server resolves: `caller_name` if
supplied, else the legacy `tool_type`, else a default.

## What this proves

1. **`callerName` alone** — `policy_details.caller_name` carries it, and the
   legacy `tool_type` key is no longer written for new rows.
2. **Legacy `toolType` alone** (no `callerName`) — falls back correctly into
   `policy_details.caller_name` (backward compatible).
3. **Both supplied** — `callerName` wins; the stale `toolType` value never
   leaks into `policy_details`.

The SDK's typed `AuditLogEntry` doesn't surface `policy_details` yet, so the
read-back uses a raw HTTP GET to `/api/v1/audit/{id}` through the same agent,
authenticated with the identical Basic-auth credentials the SDK's own
transport sent for the write — the same pattern
`runtime-e2e/decision_context_transfer_basis` uses for `/api/v1/decide`.

## Platform support

`caller_name` support (axonflow-enterprise#2953) has merged to
`axonflow-enterprise` main and shipped in v9.11.0 — any stack built from
current `axonflow-enterprise` main (or later) has it. That same merge folded
in #2903: when neither `callerName` nor the legacy `toolType` is supplied,
`policy_details.caller_name` now defaults to `"unknown"` (previously
`"claude_code"`). This test doesn't assert on that no-input default case, so
it is unaffected either way — but if you add a fourth scenario for it, expect
`"unknown"` against current `axonflow-enterprise` main.

## Run

Community mode needs no license — any client ID is its own tenant:

```bash
./mvnw -q -DskipTests package
./mvnw -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
AXONFLOW_ENDPOINT=http://localhost:8080 \
  java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
    runtime-e2e/caller_name_audit/CallerNameAuditTest.java
```

Override `AXONFLOW_CLIENT_ID` / `AXONFLOW_CLIENT_SECRET` for enterprise /
community-saas stacks that need real credentials. Exits non-zero (and prints
`FAIL: ...`) if any of the 4 assertions fails, or if the anti-skip guard trips
(fewer assertions ran than expected).

## Companion coverage

`src/test/java/com/getaxonflow/sdk/AuditToolCallTest.java` exercises the same
wire contract through WireMock: `callerName` serializes to `caller_name`,
legacy `toolType` still serializes standalone, and both together — the
runtime proof here is the redundant real-stack confirmation that the field
actually lands in `policy_details.caller_name` server-side.

## Cross-repo parity

Platform-side runtime proof (agent MCP `audit_tool_call` tool -> orchestrator
`POST /api/v1/audit/tool-call` -> `audit_logs.policy_details`) lives at
`axonflow-enterprise/runtime-e2e/2912_caller_name_deprecation/test.sh`.
