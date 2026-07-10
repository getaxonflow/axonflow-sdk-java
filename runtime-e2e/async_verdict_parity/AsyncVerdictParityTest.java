/*
 * runtime-e2e/async_verdict_parity/AsyncVerdictParityTest.java
 *
 * Real-stack assertion: the async client variants deliver the SAME
 * enforcement verdict as their sync counterparts (async-adapter-bypass
 * class, getaxonflow/axonflow-enterprise#2861).
 *
 * Per runtime-e2e/README.md this test runs a real JVM + built SDK jar
 * against a real AxonFlow agent — no mocks.
 *
 * The Java SDK's *Async methods wrap the sync call in
 * CompletableFuture.supplyAsync(...): the enforcement verdict (or
 * exception) materializes INSIDE the future. This test proves that a
 * caller who joins the future observes identical enforcement to the sync
 * path, for both the Decision Mode PDP (deny verdict for a stacked-SQLi
 * LLM stage) and the MCP check plane (allowed=false). The residual
 * caller-side hazard — never joining the future — is documented in the
 * method javadocs; the SDK itself never leaves a governance future
 * unjoined.
 *
 * Run (after `source /tmp/axonflow-e2e-env.sh` from the enterprise setup
 * script and `mvn install -DskipTests`):
 *
 *   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *     runtime-e2e/async_verdict_parity/AsyncVerdictParityTest.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;

public class AsyncVerdictParityTest {

  static final String SQLI = "SELECT * FROM users WHERE 1=1; DROP TABLE users;--";

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
    String endpoint = System.getenv().getOrDefault("AXONFLOW_ENDPOINT", "http://localhost:8080");
    String clientId = System.getenv("AXONFLOW_CLIENT_ID");
    String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");
    if (clientId == null || clientSecret == null) {
      fail("AXONFLOW_CLIENT_ID and AXONFLOW_CLIENT_SECRET must be set");
    }

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());

    // 1. Decision Mode: sync vs joined-async verdict parity on a deny.
    DecideRequest req =
        DecideRequest.builder("llm", SQLI)
            .target(new DecisionTarget("llm", "gpt-4", "openai", null))
            .build();

    DecideResponse syncResp = client.decide(req);
    DecideResponse asyncResp = client.decideAsync(req).join();
    check(
        syncResp.getVerdict().equals(asyncResp.getVerdict()),
        "decide verdict mismatch: sync=" + syncResp.getVerdict()
            + " async=" + asyncResp.getVerdict());
    check(
        "deny".equals(syncResp.getVerdict()),
        "expected deny for stacked SQLi at llm stage, got " + syncResp.getVerdict());
    System.out.println("PASS [decide-parity] sync=async verdict=" + syncResp.getVerdict());

    // 2. MCP check plane: sync vs joined-async allowed parity on a block.
    MCPCheckInputResponse syncCheck = client.mcpCheckInput("postgres", SQLI);
    MCPCheckInputResponse asyncCheck = client.mcpCheckInputAsync("postgres", SQLI).join();
    check(
        syncCheck.isAllowed() == asyncCheck.isAllowed(),
        "mcpCheckInput allowed mismatch: sync=" + syncCheck.isAllowed()
            + " async=" + asyncCheck.isAllowed());
    check(!syncCheck.isAllowed(), "expected stacked SQLi to be blocked on check-input");
    System.out.println("PASS [check-input-parity] sync=async allowed=" + syncCheck.isAllowed());

    System.out.println("RESULT: PASS (2/2)");
  }
}
