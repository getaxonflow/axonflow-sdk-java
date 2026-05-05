/*
 * runtime-e2e/x-axonflow-client/SdkClientHeaderTest.java
 *
 * Per CLAUDE.md HARD RULE #0: real wire test of the SDK's
 * getClientHeader() + addAuthHeaders() emitting
 *   X-Axonflow-Client: sdk-java/<SDK_VERSION>
 * to a real running AxonFlow agent.
 *
 * Run:
 *   mvn -DskipTests dependency:build-classpath \
 *       -Dmdep.outputFile=/tmp/cp.txt -q
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | head -1)
 *   CP="$SDK_JAR:$(cat /tmp/cp.txt)"
 *   AXONFLOW_AGENT_URL=http://localhost:8080 \
 *     AXONFLOW_TENANT_ID=cs_... AXONFLOW_TENANT_SECRET=... \
 *     AXONFLOW_E2E_PLUGIN_TOKEN=AXON-... \
 *     java -cp "$CP" \
 *       runtime-e2e/x-axonflow-client/SdkClientHeaderTest.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.MCPCheckInputResponse;

public class SdkClientHeaderTest {
    public static void main(String[] args) {
        String endpoint = System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
        String tenant = System.getenv("AXONFLOW_TENANT_ID");
        String secret = System.getenv("AXONFLOW_TENANT_SECRET");
        String token = System.getenv("AXONFLOW_E2E_PLUGIN_TOKEN");
        if (tenant == null || secret == null || token == null) {
            System.err.println("AXONFLOW_TENANT_ID + AXONFLOW_TENANT_SECRET + AXONFLOW_E2E_PLUGIN_TOKEN must be set; see ../README.md");
            System.exit(2);
        }

        String expected = "sdk-java/" + AxonFlowConfig.SDK_VERSION;
        System.out.println("Asserting wire X-Axonflow-Client = " + expected);

        AxonFlow client = AxonFlow.create(
            AxonFlowConfig.builder()
                .agentUrl(endpoint)
                .clientId(tenant)
                .clientSecret(secret)
                .build()
        );
        // NOTE: like sdk-go, sdk-java does not currently expose a public way
        // to inject X-License-Token into requests. The driver script that
        // runs this test should chain through a small local logging proxy
        // that injects the token before forwarding to the agent. See
        // ../README.md "How to run" for the proxy snippet. The assertion
        // below relies on the proxy chain producing a scope_mismatch
        // response that echoes the client header.
        try {
            MCPCheckInputResponse r = client.mcpCheckInput("postgres", "SELECT 1");
            System.err.println("UNEXPECTED 200: allowed=" + r.isAllowed());
            System.exit(1);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            if (msg.contains("client \"" + expected + "\"")) {
                System.out.println("PASS: agent reflected " + expected + " in scope_mismatch response");
                System.exit(0);
            }
            System.err.println("FAIL: error did not echo expected client header; got: " + msg);
            System.exit(1);
        }
    }
}
