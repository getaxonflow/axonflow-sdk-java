/*
 * runtime-e2e/mcp_server_tool_split/McpServerToolSplitTest.java
 *
 * Real-stack proof that the platform's two-field MCP identity contract
 * (connector_type + tool, epic getaxonflow/axonflow-enterprise#2905, #2904)
 * is accepted end-to-end through the Java SDK's real public surface, for
 * getaxonflow/axonflow-sdk-java#2909:
 *
 *   LangGraphAdapter.mcpToolInterceptor() used to derive a single
 *   connectorType string as "{serverName}.{toolName}" because the wire
 *   contract only carried one identity field. It now sends
 *   MCPToolRequest#getServerName() as connectorType and
 *   MCPToolRequest#getName() as a separate "tool" field.
 *
 * This test runs a real JVM with the built SDK jar on the classpath and
 * issues real HTTP requests to a real running AxonFlow agent — no mocks,
 * no WireMock, no stubs. It asserts:
 *
 *   1. Interceptor path: MCPToolInterceptor#intercept() (reached only via
 *      LangGraphAdapter#mcpToolInterceptor(), the actual consumer-facing
 *      surface) round-trips a clean tool call through check-input ->
 *      handler -> check-output against the live agent, proving the agent
 *      accepts and processes the split server+tool identity on both the
 *      input and output check calls without erroring.
 *   2. Direct low-level parity: AxonFlow#mcpCheckInput(connectorType,
 *      statement, options) with an explicit "tool" option is accepted
 *      (connector_type and tool arrive as two distinct wire fields).
 *   3. Backward compatibility: the original two-argument
 *      mcpCheckInput(connectorType, statement) overload — which carries
 *      NO tool field at all — still works unchanged against the same
 *      live agent, proving the new field is additive, not breaking.
 *
 * Run (after `mvn -DskipTests package` to produce the SDK jar):
 *
 *   mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   AXONFLOW_AGENT_URL=http://localhost:8080 \
 *     java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *       runtime-e2e/mcp_server_tool_split/McpServerToolSplitTest.java
 *
 * Defaults to http://localhost:8080 (community mode, no auth) if
 * AXONFLOW_AGENT_URL is unset. Uses a dedicated tenant/clientId
 * ("java-sdk-2909-runtime-e2e") to avoid colliding with other tests
 * sharing the same agent.
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.adapters.LangGraphAdapter;
import com.getaxonflow.sdk.adapters.MCPToolInterceptor;
import com.getaxonflow.sdk.adapters.MCPToolRequest;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;
import java.util.Map;

public class McpServerToolSplitTest {

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    System.exit(1);
  }

  static void check(boolean cond, String msg) {
    if (!cond) {
      fail(msg);
    }
  }

  public static void main(String[] args) throws Exception {
    String endpoint =
        System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
    String tenantId = "java-sdk-2909-runtime-e2e";

    try (AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder().endpoint(endpoint).clientId(tenantId).build())) {

      // 1. Interceptor path: the real consumer-facing surface for #2909.
      // MCPToolRequest#getServerName() -> connectorType, #getName() -> tool,
      // threaded through as two distinct fields instead of being
      // concatenated into one opaque connectorType string.
      LangGraphAdapter adapter =
          LangGraphAdapter.builder(client, "mcp-server-tool-split-e2e").build();
      MCPToolInterceptor interceptor = adapter.mcpToolInterceptor();

      MCPToolRequest request =
          new MCPToolRequest("orders-server", "list_orders", Map.of("status", "open"));

      Object result = interceptor.intercept(request, req -> Map.of("orders", java.util.List.of()));
      check(result != null, "interceptor.intercept() returned null for a clean tool call");
      System.out.println(
          "PASS [interceptor-server-tool-split] server=orders-server tool=list_orders "
              + "-> result=" + result);

      // 2. Direct low-level call: explicit "tool" option is accepted and
      // processed by the live agent as a distinct field from connectorType.
      MCPCheckInputResponse withTool =
          client.mcpCheckInput(
              "orders-server",
              "orders-server.list_orders({\"status\":\"open\"})",
              Map.of("operation", "execute", "tool", "list_orders"));
      check(
          withTool.isAllowed(),
          "expected clean statement with explicit tool option to be allowed, got blocked: "
              + withTool.getBlockReason());
      System.out.println("PASS [direct-check-input-with-tool] allowed=" + withTool.isAllowed());

      // 3. Backward compatibility: the pre-#2909 two-arg overload has no
      // tool field on the wire at all. It must still work unchanged.
      MCPCheckInputResponse withoutTool =
          client.mcpCheckInput("orders-server", "SELECT 1");
      check(
          withoutTool.isAllowed(),
          "expected clean statement with no tool field (old shape) to be allowed, got blocked: "
              + withoutTool.getBlockReason());
      System.out.println(
          "PASS [backward-compat-no-tool] allowed=" + withoutTool.isAllowed());
    }

    System.out.println("RESULT: PASS (3/3)");
  }
}
