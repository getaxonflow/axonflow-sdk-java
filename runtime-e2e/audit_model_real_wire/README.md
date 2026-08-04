# audit_model_real_wire (audit model real-wire fields, #3254)

Real-stack proof for the getaxonflow/axonflow-enterprise#3254 additive
interim: the SDK's audit read model now carries the fields a 9.x server
actually serves, and the seven never-served fields stay at their
defaults against a live agent.

Background: `AuditLogEntry` modeled `query_summary`, `success`,
`blocked`, `risk_score`, `latency_ms`, `policy_violations` and
`metadata` - none of which any 9.x server has ever sent. Consumers
reading `isBlocked()` on a genuinely blocked request saw `false`
(the default), because the wire carries the verdict in
`policy_decision`, the context in `policy_details` and the latency in
`response_time_ms`. Similarly, `AuditSearchRequest.request_type` is a
silent server-side no-op; the real filter is `action`.

This test asserts, through the SDK's real public surface
(`searchAuditLogs`), against a real running agent with NO mocks:

1. **Real fields are bound.** At least one returned entry carries a
   populated `policyDecision` and a present (non-null)
   `responseTimeMs`, while `isBlocked()` / `isSuccess()` /
   `getRiskScore()` sit at their documented defaults on every entry.
2. **`action` is read server-side.** `action("blocked")` returns only
   entries whose verdict is not `allowed`/empty.

## Run

```bash
# from the SDK root, against a live agent
export AXONFLOW_ENDPOINT=http://127.0.0.1:38080   # default
export AXONFLOW_CLIENT_ID=demo-client             # default
export AXONFLOW_CLIENT_SECRET=demo-secret         # default

mvn install -DskipTests
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
  runtime-e2e/audit_model_real_wire/AuditModelRealWireTest.java
```

The stack must hold at least one audit row; write one via
`POST /api/v1/audit/tool-call` through the agent proxy if empty.

Expected output shape:

```
PASS [real-wire-fields] N entries; N with policy_decision, N with response_time_ms. Sample: ...
PASS [action-filter] action="blocked" returned M of N entries, none with an allowed/empty verdict
ALL PASS
```
