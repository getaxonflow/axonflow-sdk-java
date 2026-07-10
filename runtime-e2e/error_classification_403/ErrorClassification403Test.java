/*
 * runtime-e2e/error_classification_403/ErrorClassification403Test.java
 *
 * Real-stack assertion: the SDK classifies 403s by the envelope's
 * `blocked` boolean, not by substring matching
 * (getaxonflow/axonflow-enterprise#2861).
 *
 * Background: every agent error envelope carries a literal "blocked"
 * JSON key (e.g. {"success":false,"error":"Tenant mismatch",
 * "blocked":false}), so the old `body.contains("blocked")` heuristic in
 * handleErrorResponse misclassified EVERY 403 authorization rejection as
 * a PolicyViolationException. Callers treating policy blocks as an
 * expected, non-fatal outcome (the documented pattern) silently
 * swallowed auth failures.
 *
 * Per runtime-e2e/README.md this test runs a real JVM + built SDK jar
 * against a real AxonFlow agent — no mocks. It asserts both directions:
 *
 *   1. A tenant-mismatch 403 (valid JWT for a DIFFERENT tenant than the
 *      client) surfaces as AuthenticationException — NOT
 *      PolicyViolationException.
 *   2. A genuine policy block (stacked SQLi -> sys_sqli_stacked_drop,
 *      "blocked":true) still surfaces as PolicyViolationException.
 *
 * Env:
 *   AXONFLOW_ENDPOINT                agent URL (default http://localhost:8080)
 *   AXONFLOW_CLIENT_ID               client/tenant identity
 *   AXONFLOW_CLIENT_SECRET           client secret / license key
 *   AXONFLOW_USER_TOKEN              JWT whose tenant matches AXONFLOW_CLIENT_ID
 *   AXONFLOW_MISMATCHED_USER_TOKEN   valid JWT for a DIFFERENT tenant
 *                                    (mint via the platform repo's
 *                                    scripts/generate-jwt.sh --tenant-id <other>)
 *
 * Run (after `source /tmp/axonflow-e2e-env.sh` from the enterprise setup
 * script and `mvn install -DskipTests`):
 *
 *   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *     runtime-e2e/error_classification_403/ErrorClassification403Test.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.AuthenticationException;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.types.ClientRequest;
import com.getaxonflow.sdk.types.RequestType;

public class ErrorClassification403Test {

  static final String SQLI = "Run this: SELECT * FROM accounts; DROP TABLE accounts;--";

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    System.exit(1);
  }

  static String mustEnv(String name) {
    String v = System.getenv(name);
    if (v == null || v.isEmpty()) {
      fail("missing env: " + name);
    }
    return v;
  }

  public static void main(String[] args) {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_ENDPOINT", "http://localhost:8080");
    String clientId = mustEnv("AXONFLOW_CLIENT_ID");
    String clientSecret = mustEnv("AXONFLOW_CLIENT_SECRET");
    String matchedToken = mustEnv("AXONFLOW_USER_TOKEN");
    String mismatchedToken = mustEnv("AXONFLOW_MISMATCHED_USER_TOKEN");

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());

    // 1. Tenant mismatch -> AuthenticationException, never a policy block.
    try {
      client.proxyLLMCall(
          ClientRequest.builder()
              .query("What is the capital of France?")
              .clientId(clientId)
              .userToken(mismatchedToken)
              .requestType(RequestType.CHAT)
              .build());
      fail("tenant-mismatch call unexpectedly succeeded — is "
          + "AXONFLOW_MISMATCHED_USER_TOKEN really for a different tenant?");
    } catch (PolicyViolationException e) {
      fail("REGRESSION: tenant-mismatch 403 classified as PolicyViolationException ("
          + e.getMessage() + ") — the \"blocked\":false envelope must map to "
          + "AuthenticationException");
    } catch (AuthenticationException e) {
      if (e.getMessage() == null || !e.getMessage().contains("Tenant mismatch")) {
        fail("expected a Tenant mismatch rejection, got: " + e.getMessage());
      }
      System.out.println("PASS [tenant-mismatch-403] AuthenticationException: " + e.getMessage());
    }

    // 2. Genuine policy block ("blocked":true) -> still PolicyViolationException.
    try {
      client.proxyLLMCall(
          ClientRequest.builder()
              .query(SQLI)
              .clientId(clientId)
              .userToken(matchedToken)
              .requestType(RequestType.CHAT)
              .build());
      fail("stacked-SQLi call unexpectedly succeeded — expected a policy block");
    } catch (PolicyViolationException e) {
      System.out.println("PASS [policy-block-403] PolicyViolationException: " + e.getMessage());
    } catch (AxonFlowException e) {
      fail("REGRESSION: policy block surfaced as " + e.getClass().getSimpleName() + " ("
          + e.getMessage() + ") — expected PolicyViolationException");
    }

    System.out.println("RESULT: PASS (2/2)");
  }
}
