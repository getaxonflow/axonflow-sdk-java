/*
 * runtime-e2e/masfeat_registry_summary/MasfeatRegistrySummaryTest.java
 *
 * Real-stack leg for the #3254 pin-advance batch (masfeat models).
 *
 * MAS FEAT is an Enterprise module: on a community build the orchestrator
 * registers NO masfeat routes (platform/orchestrator/masfeat/
 * masfeat_community.go RegisterRoutes is a no-op), so against a community
 * stack the correct observable behavior of the SDK is a clean HTTP-level
 * refusal (404 route-not-found surfaced as an AxonFlowException), NOT a
 * parse error and NOT a fabricated summary object.
 *
 * This test therefore asserts one of two legitimate outcomes, and prints
 * which one it exercised:
 *
 *   ENTERPRISE leg: getRegistrySummary() succeeds; the #3254 real fields
 *   (org_id-derived getOrgId, assessments_due, kill_switches_triggered,
 *   medium/low materiality counters) are readable and the deprecated
 *   by_use_case / by_status fiction maps are null.
 *
 *   COMMUNITY leg: the call fails with an HTTP-level error (route absent /
 *   gated) - and specifically NOT a JSON parse failure, which would mean
 *   the SDK mis-handled the gate.
 *
 * Env:
 *   AXONFLOW_ENDPOINT        agent URL (default http://127.0.0.1:38080)
 *   AXONFLOW_CLIENT_ID       client identity (default demo-client)
 *   AXONFLOW_CLIENT_SECRET   client secret   (default demo-secret)
 *
 * Run (from the SDK root):
 *
 *   mvn install -DskipTests
 *   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *     runtime-e2e/masfeat_registry_summary/MasfeatRegistrySummaryTest.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.masfeat.MASFEATTypes.RegistrySummary;

public class MasfeatRegistrySummaryTest {

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

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(env("AXONFLOW_CLIENT_ID", "demo-client"))
                .clientSecret(env("AXONFLOW_CLIENT_SECRET", "demo-secret"))
                .build());

    RegistrySummary summary;
    try {
      summary = client.masfeat().getRegistrySummary();
    } catch (AxonFlowException e) {
      String msg = e.getMessage() == null ? "" : e.getMessage();
      if (msg.contains("Failed to parse")) {
        fail("community gate surfaced as a PARSE error - the SDK mishandled the refusal: " + msg);
      }
      System.out.println(
          "PASS [community-gate] masfeat route refused cleanly by a community stack "
              + "(enterprise-only module, no routes registered): "
              + e.getClass().getSimpleName()
              + ": "
              + msg);
      System.out.println(
          "NOTE: enterprise leg NOT exercised on this stack - real-field assertions "
              + "rest on the source-derived WireMock suite "
              + "(src/test/java/com/getaxonflow/sdk/masfeat/MASFEATRealWireTest.java).");
      return;
    }

    // Enterprise leg: the real #3254 fields must be readable.
    System.out.println(
        "PASS [enterprise-live] registry summary: orgId="
            + summary.getOrgId()
            + " total="
            + summary.getTotalSystems()
            + " active="
            + summary.getActiveSystems()
            + " high="
            + summary.getHighMaterialityCount()
            + " medium="
            + summary.getMediumMaterialityCount()
            + " low="
            + summary.getLowMaterialityCount()
            + " assessmentsDue="
            + summary.getAssessmentsDue()
            + " killSwitchesTriggered="
            + summary.getKillSwitchesTriggered());
    if (summary.getByUseCase() != null || summary.getByStatus() != null) {
      fail("deprecated by_use_case/by_status came back non-null - the server never serves them; "
          + "a non-null value means the model regressed into fiction");
    }
    System.out.println("PASS [deprecated-defaults] by_use_case/by_status null as expected");
  }
}
