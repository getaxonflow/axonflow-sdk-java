/*
 * runtime-e2e/decide_fulfill_obligation/DecideFulfillObligationTest.java
 *
 * Real-wire test of the Decision Mode PEP surface (ADR-056, epic #2563,
 * tracking #2571) against a running AxonFlow enterprise agent.
 *
 * Proves, with NO mocks, that the SDK can run the decide -> fulfill -> forward
 * path end-to-end:
 *
 *   1. decide() on a PII-bearing query returns an allow verdict carrying a
 *      request-phase redact_pii obligation whose fulfillment names the
 *      request-redaction engine endpoint.
 *   2. fulfillRequest() discharges that obligation through the engine and
 *      returns engine-masked content in which neither the email
 *      (john.doe@example.com) nor the card (4111111111111111) survives, and
 *      the content differs from the original. (No local redaction exists in
 *      the SDK — only the engine can produce this.)
 *   3. decideAndFulfill() yields the same masked content in one call.
 *   4. Demo credentials (demo-org / demo-license-not-real) are refused with an
 *      AuthenticationException (HTTP 401).
 *
 * Run:
 *   source /tmp/axonflow-e2e-env.sh
 *   mvn -q -DskipTests package
 *   mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *     runtime-e2e/decide_fulfill_obligation/DecideFulfillObligationTest.java
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.Pep;
import com.getaxonflow.sdk.exceptions.AuthenticationException;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;

public class DecideFulfillObligationTest {

  static final String EMAIL = "john.doe@example.com";
  static final String CARD = "4111111111111111";
  static final String QUERY = "Send the receipt to " + EMAIL + " and charge card " + CARD;

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    System.exit(1);
  }

  static void check(boolean cond, String msg) {
    if (!cond) {
      fail(msg);
    }
  }

  public static void main(String[] args) {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_ENDPOINT", "http://localhost:8080");
    String clientId = System.getenv("AXONFLOW_CLIENT_ID");
    String clientSecret = System.getenv("AXONFLOW_CLIENT_SECRET");
    String tenantId = System.getenv("AXONFLOW_TENANT_ID");
    String userToken = System.getenv("AXONFLOW_USER_TOKEN");
    if (clientId == null || clientSecret == null) {
      fail("AXONFLOW_CLIENT_ID / AXONFLOW_CLIENT_SECRET unset — source /tmp/axonflow-e2e-env.sh");
    }

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());

    DecideRequest req =
        DecideRequest.builder("tool", QUERY)
            .target(new DecisionTarget("tool", null, null, "send_receipt"))
            .userToken(userToken)
            .build();

    // 1. decide -> allow + request-phase redact_pii obligation.
    DecideResponse decision = client.decide(req);
    System.out.println(
        "decide -> verdict="
            + decision.getVerdict()
            + " decision_id="
            + decision.getDecisionId()
            + " obligations="
            + decision.getObligations().size()
            + " evaluated_policies="
            + decision.getEvaluatedPolicies());
    check(Pep.VERDICT_ALLOW.equals(decision.getVerdict()), "expected allow, got " + decision.getVerdict());
    check(
        Pep.hasRequestRedaction(decision.getObligations()),
        "expected a request-phase redact_pii obligation, got " + decision.getObligations());
    System.out.println("PASS step 1: decide returned allow + redact_pii request-phase obligation");

    // 2. fulfillRequest -> engine-masked content; PII must NOT survive.
    AxonFlow.FulfillResult fr = client.fulfillRequest(decision, QUERY);
    System.out.println("fulfillRequest -> didRedact=" + fr.didRedact() + " content=" + fr.getContent());
    assertMasked(fr.getContent());
    check(fr.didRedact(), "expected the engine to have changed the content (didRedact=true)");
    System.out.println("PASS step 2: fulfillRequest masked email + card via the engine (no local redaction)");

    // 3. decideAndFulfill -> same masked content in one call.
    AxonFlow.DecideAndFulfillResult daf = client.decideAndFulfill(req);
    System.out.println(
        "decideAndFulfill -> verdict=" + daf.getVerdict() + " content=" + daf.getContent());
    check(Pep.VERDICT_ALLOW.equals(daf.getVerdict()), "decideAndFulfill verdict=" + daf.getVerdict());
    assertMasked(daf.getContent());
    System.out.println("PASS step 3: decideAndFulfill returned engine-masked content in one call");

    // 4. Demo credentials are refused with 401.
    AxonFlow demo =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId("demo-org")
                .clientSecret("demo-license-not-real")
                .build());
    try {
      demo.decide(DecideRequest.builder("tool", "ping").build());
      fail("expected demo credentials to be refused with AuthenticationException");
    } catch (AuthenticationException e) {
      System.out.println("PASS step 4: demo credentials refused -> AuthenticationException: " + e.getMessage());
    }

    System.out.println("ALL PASS: decide -> fulfill -> forward verified through the SDK against the live agent");
  }

  static void assertMasked(String content) {
    check(content != null, "content is null");
    check(!content.contains(EMAIL), "email '" + EMAIL + "' survived in: " + content);
    check(!content.contains(CARD), "card '" + CARD + "' survived in: " + content);
    check(!content.equals(QUERY), "content equals the original (no redaction happened): " + content);
  }
}
