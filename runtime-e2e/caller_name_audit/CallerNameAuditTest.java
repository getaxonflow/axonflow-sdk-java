/*
 * runtime-e2e/caller_name_audit/CallerNameAuditTest.java
 *
 * Real-stack assertion for getaxonflow/axonflow-enterprise#2912 (sub-issue of
 * epic #2905): `AuditToolCallRequest.callerName` (wire: `caller_name`) reaches
 * `policy_details.caller_name` on the persisted audit row, through the SDK's
 * real public `auditToolCall()` surface against a live AxonFlow agent +
 * orchestrator — no mocks.
 *
 * Background: audit_tool_call's `tool_type` field was misleadingly named —
 * every real caller (claude_code/codex/cursor/openclaw) used it to identify
 * WHICH CLIENT made the call, not any property of the tool. `caller_name` is
 * the field that actually matches that contract; `tool_type` is kept as a
 * deprecated input fallback (NOT removed). The server resolves: caller_name
 * if supplied -> legacy tool_type if supplied -> a default.
 *
 * This test drives three scenarios entirely through
 * `com.getaxonflow.sdk.AxonFlow#auditToolCall`:
 *   1. callerName alone            -> policy_details.caller_name = callerName
 *   2. legacy toolType alone       -> policy_details.caller_name = toolType (fallback)
 *   3. both callerName AND toolType -> callerName wins
 *
 * The SDK's typed `AuditLogEntry` does not surface `policy_details` (it's not
 * part of the SDK's public read contract yet), so this test reads the row
 * back with a raw HTTP GET against `/api/v1/audit/{id}` through the same
 * agent, authenticated with the identical Basic-auth credentials the SDK's
 * own transport sends — the same "read back what the SDK can't parse yet"
 * pattern used by runtime-e2e/decision_context_transfer_basis for
 * `/api/v1/decide`.
 *
 * Run (against a real agent+orchestrator, Community mode needs no license):
 *
 *   mvn -q -DskipTests package
 *   mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   AXONFLOW_ENDPOINT=http://localhost:8080 \
 *     java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *       runtime-e2e/caller_name_audit/CallerNameAuditTest.java
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.AuditToolCallRequest;
import com.getaxonflow.sdk.types.AuditToolCallResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("deprecation") // exercises the deprecated toolType(...) fallback intentionally
public class CallerNameAuditTest {

  static int assertionsRun = 0;
  static boolean failed = false;

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    failed = true;
  }

  static void pass(String msg) {
    assertionsRun++;
    System.out.println("PASS: " + msg);
  }

  public static void main(String[] args) throws Exception {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_ENDPOINT", "http://localhost:8080");
    String clientId =
        System.getenv()
            .getOrDefault("AXONFLOW_CLIENT_ID", "javasdk-2912-e2e-" + System.currentTimeMillis());
    String clientSecret = System.getenv().getOrDefault("AXONFLOW_CLIENT_SECRET", "");

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());

    ObjectMapper mapper = new ObjectMapper();
    HttpClient http = HttpClient.newHttpClient();
    String basicAuth =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

    // ------------------------------------------------------------------
    // 1. callerName alone (the new field) -> policy_details.caller_name
    //    carries it, and the legacy tool_type key is no longer written for
    //    new rows.
    // ------------------------------------------------------------------
    System.out.println("[1/3] auditToolCall with callerName only ...");
    AuditToolCallResponse resp1 =
        client.auditToolCall(
            AuditToolCallRequest.builder()
                .toolName("rte2912-newfield")
                .callerName("cursor")
                .build());
    JsonNode policyDetails1 = fetchPolicyDetails(http, mapper, endpoint, basicAuth, resp1);
    assertCallerName(policyDetails1, "cursor", "callerName alone");
    assertNoLegacyToolTypeKey(policyDetails1, "callerName alone");

    // ------------------------------------------------------------------
    // 2. legacy toolType alone (no callerName) -> falls back correctly into
    //    policy_details.caller_name (backward compatible).
    // ------------------------------------------------------------------
    System.out.println("[2/3] auditToolCall with legacy toolType only ...");
    AuditToolCallResponse resp2 =
        client.auditToolCall(
            AuditToolCallRequest.builder().toolName("rte2912-legacy").toolType("codex").build());
    JsonNode policyDetails2 = fetchPolicyDetails(http, mapper, endpoint, basicAuth, resp2);
    assertCallerName(policyDetails2, "codex", "legacy toolType alone");

    // ------------------------------------------------------------------
    // 3. BOTH callerName and legacy toolType supplied -> callerName wins;
    //    the stale toolType value never leaks into policy_details.
    // ------------------------------------------------------------------
    System.out.println("[3/3] auditToolCall with BOTH callerName and legacy toolType ...");
    AuditToolCallResponse resp3 =
        client.auditToolCall(
            AuditToolCallRequest.builder()
                .toolName("rte2912-both")
                .toolType("stale_legacy_value")
                .callerName("openclaw")
                .build());
    JsonNode policyDetails3 = fetchPolicyDetails(http, mapper, endpoint, basicAuth, resp3);
    assertCallerName(policyDetails3, "openclaw", "both supplied, callerName wins");

    int expectedAssertions = 4;
    if (assertionsRun != expectedAssertions) {
      fail("anti-skip guard tripped: ran " + assertionsRun + " of " + expectedAssertions + " assertions");
    }
    if (failed) {
      System.out.println("RESULT: FAIL");
      System.exit(1);
    }
    System.out.println("RESULT: PASS (" + assertionsRun + "/" + expectedAssertions + ")");
  }

  /**
   * Reads the persisted row back via a raw HTTP GET to /api/v1/audit/{id}
   * through the agent (the SDK does not wrap this endpoint, and its typed
   * AuditLogEntry does not surface policy_details), authenticated with the
   * same Basic-auth credentials the SDK's own transport used for the write.
   * Polls briefly since the audit write is async.
   */
  static JsonNode fetchPolicyDetails(
      HttpClient http,
      ObjectMapper mapper,
      String endpoint,
      String basicAuth,
      AuditToolCallResponse response)
      throws Exception {
    if (response == null || response.getAuditId() == null || response.getAuditId().isEmpty()) {
      fail("auditToolCall did not return an audit_id");
      return mapper.createObjectNode();
    }
    String auditId = response.getAuditId();
    System.out.println("  auditToolCall -> audit_id=" + auditId);

    for (int i = 0; i < 10; i++) {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(endpoint + "/api/v1/audit/" + auditId))
              .header("Authorization", basicAuth)
              .GET()
              .build();
      HttpResponse<String> httpResp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (httpResp.statusCode() == 200) {
        JsonNode node = mapper.readTree(httpResp.body());
        JsonNode policyDetails = node.get("policy_details");
        if (policyDetails != null && policyDetails.has("caller_name")) {
          System.out.println("  GET /api/v1/audit/" + auditId + " -> policy_details=" + policyDetails);
          return policyDetails;
        }
      } else {
        System.out.println(
            "  GET /api/v1/audit/" + auditId + " -> HTTP " + httpResp.statusCode() + ": " + httpResp.body());
      }
      Thread.sleep(1500);
    }
    fail("GET /api/v1/audit/" + auditId + " never returned policy_details.caller_name");
    return mapper.createObjectNode();
  }

  static void assertCallerName(JsonNode policyDetails, String expected, String label) {
    String actual = policyDetails.has("caller_name") ? policyDetails.get("caller_name").asText() : null;
    if (expected.equals(actual)) {
      pass(label + ": policy_details.caller_name=\"" + actual + "\"");
    } else {
      fail(label + ": policy_details.caller_name=\"" + actual + "\", want \"" + expected + "\"");
    }
  }

  static void assertNoLegacyToolTypeKey(JsonNode policyDetails, String label) {
    if (!policyDetails.has("tool_type")) {
      pass(label + ": policy_details.tool_type key absent (no longer written for new rows, #2912)");
    } else {
      fail(label + ": policy_details.tool_type unexpectedly present: " + policyDetails.get("tool_type"));
    }
  }
}
