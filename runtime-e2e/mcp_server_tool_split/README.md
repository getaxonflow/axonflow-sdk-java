# mcp_server_tool_split (#2909 — MCP server/tool identity split, epic #2905/#2904)

Real-stack proof, against a live AxonFlow agent, that:

1. `LangGraphAdapter.mcpToolInterceptor()` (the real consumer-facing MCP
   interceptor) sends `MCPToolRequest#getServerName()` as `connector_type`
   and `MCPToolRequest#getName()` as a separate `tool` field — no longer
   concatenated into one opaque `"{serverName}.{toolName}"` string — and a
   clean tool call round-trips through check-input -> handler -> check-output
   successfully.
2. `AxonFlow#mcpCheckInput(connectorType, statement, options)` with an
   explicit `"tool"` option is accepted by the live agent.
3. The pre-#2909 two-argument `mcpCheckInput(connectorType, statement)`
   overload — which carries no `tool` field at all — still works unchanged
   (backward compatibility).

## Run

```
mvn -q -DskipTests package
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
AXONFLOW_AGENT_URL=http://localhost:8080 \
  java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
    runtime-e2e/mcp_server_tool_split/McpServerToolSplitTest.java
```

Defaults to `http://localhost:8080` (community mode, no auth) if
`AXONFLOW_AGENT_URL` is unset. Uses a dedicated `clientId`/tenant
(`java-sdk-2909-runtime-e2e`) to avoid collisions with other tests sharing
the same agent. Exits non-zero (and prints `FAIL: ...`) if any step fails.
