/*
 * runtime-e2e/audit_model_real_wire/AuditModelRealWireTest.java
 *
 * Real-stack assertion for the #3254 audit-model interim
 * (getaxonflow/axonflow-enterprise#3254): the SDK's audit read model
 * carries the fields the server actually serves, and the seven fiction
 * fields stay at their defaults against a real agent.
 *
 * Per runtime-e2e/README.md this runs a real JVM + built SDK jar against
 * a real AxonFlow agent - no mocks. It asserts:
 *
 *   1. searchAuditLogs() through the SDK's public surface returns entries
 *      whose policyDecision is populated from the wire and whose
 *      responseTimeMs is present (non-null), while the deprecated
 *      blocked / success / riskScore fields sit at their defaults -
 *      the server never sends them.
 *   2. The new AuditSearchRequest.action filter is READ by the server:
 *      action("blocked") returns only non-"allowed" verdict rows.
 *
 * Env:
 *   AXONFLOW_ENDPOINT        agent URL (default http://127.0.0.1:38080)
 *   AXONFLOW_CLIENT_ID       client identity (default demo-client)
 *   AXONFLOW_CLIENT_SECRET   client secret   (default demo-secret)
 *
 * Run (from the SDK root, against a live community/enterprise agent):
 *
 *   mvn install -DskipTests
 *   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *     runtime-e2e/audit_model_real_wire/AuditModelRealWireTest.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.AuditLogEntry;
import com.getaxonflow.sdk.types.AuditSearchRequest;
import com.getaxonflow.sdk.types.AuditSearchResponse;

public class AuditModelRealWireTest {

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    System.exit(1);
  }

  static String env(String name, String dflt) {
    String v = System.getenv(name);
    return (v == null || v.isEmpty()) ? dflt : v;
  }

  @SuppressWarnings("deprecation")
  public static void main(String[] args) {
    String endpoint = env("AXONFLOW_ENDPOINT", "http://127.0.0.1:38080");
    String clientId = env("AXONFLOW_CLIENT_ID", "demo-client");
    String clientSecret = env("AXONFLOW_CLIENT_SECRET", "demo-secret");

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());

    // 1. Unfiltered search: policy_decision / response_time_ms come off
    //    the real wire; the fiction fields stay at defaults.
    AuditSearchResponse all =
        client.searchAuditLogs(AuditSearchRequest.builder().limit(50).build());
    if (all.getEntries().isEmpty()) {
      fail("no audit entries on the stack - write one first (POST /api/v1/audit/tool-call)");
    }

    int withDecision = 0;
    int withResponseTime = 0;
    for (AuditLogEntry e : all.getEntries()) {
      if (!e.getPolicyDecision().isEmpty()) {
        withDecision++;
      }
      if (e.getResponseTimeMs() != null) {
        withResponseTime++;
      }
      // The deprecated trio must sit at defaults: a real 9.x server never
      // sends success/blocked/risk_score, so a non-default value here
      // means the model regressed into trusting fiction again.
      if (e.isBlocked()) {
        fail("entry " + e.getId() + " has blocked=true - the 9.x wire never sends 'blocked'");
      }
      if (!e.isSuccess()) {
        fail("entry " + e.getId() + " has success=false - the 9.x wire never sends 'success'");
      }
      if (e.getRiskScore() != 0.0) {
        fail("entry " + e.getId() + " has risk_score=" + e.getRiskScore()
            + " - the 9.x wire never sends 'risk_score'");
      }
    }
    if (withDecision == 0) {
      fail("no entry carried a policy_decision - new field not bound to the wire");
    }
    if (withResponseTime == 0) {
      fail("no entry carried response_time_ms - new field not bound to the wire");
    }
    AuditLogEntry sample = all.getEntries().get(0);
    System.out.println(
        "PASS [real-wire-fields] "
            + all.getEntries().size()
            + " entries; "
            + withDecision
            + " with policy_decision, "
            + withResponseTime
            + " with response_time_ms. Sample: id="
            + sample.getId()
            + " policyDecision="
            + sample.getPolicyDecision()
            + " responseTimeMs="
            + sample.getResponseTimeMs()
            + " policyDetailsKeys="
            + sample.getPolicyDetails().keySet()
            + " | deprecated defaults held: blocked="
            + sample.isBlocked()
            + " success="
            + sample.isSuccess()
            + " riskScore="
            + sample.getRiskScore());

    // 2. The action filter is read server-side (request_type is not).
    AuditSearchResponse blocked =
        client.searchAuditLogs(
            AuditSearchRequest.builder().action("blocked").limit(50).build());
    for (AuditLogEntry e : blocked.getEntries()) {
      if (e.getPolicyDecision().isEmpty() || "allowed".equals(e.getPolicyDecision())) {
        fail(
            "action=\"blocked\" returned entry "
                + e.getId()
                + " with policy_decision="
                + e.getPolicyDecision()
                + " - the server did not apply the filter");
      }
    }
    System.out.println(
        "PASS [action-filter] action=\"blocked\" returned "
            + blocked.getEntries().size()
            + " of "
            + all.getEntries().size()
            + " entries, none with an allowed/empty verdict");

    System.out.println("ALL PASS");
  }
}
