/*
 * runtime-e2e/decision_context_transfer_basis/DecisionContextTransferBasisTest.java
 *
 * Real-wire test of the v8.4.0 SDK surface (platform #2509, epic #2508)
 * against a running AxonFlow agent:
 *
 *   1. DecisionSummary.getContext() / DecisionExplanation.getContext() surface
 *      the sanitized request context a PEP attaches to a Decision Mode call. We
 *      act as the PEP via a raw POST /api/v1/decide (that endpoint is not
 *      SDK-wrapped per ADR-056), then read the decision back through the SDK's
 *      listDecisions + explainDecision and assert getContext() is populated.
 *   2. AuditLogEntry transfer_basis = "pasal_56b_dpa" round-trips through
 *      Jackson serialize -> deserialize verbatim.
 *
 * Run:
 *   mvn -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
 *   mvn -DskipTests -q package   # build the SDK jar
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | head -1)
 *   CP="$SDK_JAR:$(cat /tmp/cp.txt)"
 *   AXONFLOW_AGENT_URL=http://localhost:8080 \
 *     AXONFLOW_TENANT_ID=buku-e-java-e2e AXONFLOW_TENANT_SECRET=buku-e-secret \
 *     java -cp "$CP" \
 *       runtime-e2e/decision_context_transfer_basis/DecisionContextTransferBasisTest.java
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.AuditLogEntry;
import com.getaxonflow.sdk.types.DecisionExplanation;
import com.getaxonflow.sdk.types.DecisionSummary;
import com.getaxonflow.sdk.types.ListDecisionsOptions;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class DecisionContextTransferBasisTest {

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
    String endpoint = System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
    String clientId = System.getenv().getOrDefault("AXONFLOW_TENANT_ID", "buku-e-java-e2e");
    String secret = System.getenv().getOrDefault("AXONFLOW_TENANT_SECRET", "buku-e-secret");
    Map<String, String> want =
        Map.of(
            "x_ai_agent", "refund-bot",
            "x_session_id", "sess-buku-42",
            "x_leader_identity", "ops-lead");

    // 1. PEP: create a decision carrying request context (body 'context' map).
    String decisionId = createDecision(endpoint, clientId, secret);
    System.out.println("PEP decide -> decision_id=" + decisionId);

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .clientSecret(secret)
                .build());

    // 2. Read it back through the SDK.
    List<DecisionSummary> rows =
        client.listDecisions(ListDecisionsOptions.builder().limit(5).build());
    DecisionSummary found =
        rows.stream().filter(r -> decisionId.equals(r.getDecisionId())).findFirst().orElse(null);
    if (found == null) {
      fail("listDecisions did not return " + decisionId + " (got " + rows.size() + " rows)");
    }
    System.out.println("SDK listDecisions -> context=" + found.getContext());
    if (found.getContext() == null || !found.getContext().entrySet().containsAll(want.entrySet())) {
      fail("listDecisions context = " + found.getContext() + ", want superset of " + want);
    }
    System.out.println(
        "PASS: listDecisions DecisionSummary.getContext() populated with "
            + found.getContext().size()
            + " PEP-forwarded keys");

    DecisionExplanation exp = client.explainDecision(decisionId);
    System.out.println(
        "SDK explainDecision -> context="
            + exp.getContext()
            + " contextTruncated="
            + exp.isContextTruncated());
    if (exp.getContext() == null || !exp.getContext().entrySet().containsAll(want.entrySet())) {
      fail("explainDecision context = " + exp.getContext());
    }
    System.out.println(
        "PASS: explainDecision returned full context (contextTruncated="
            + exp.isContextTruncated()
            + ")");

    // 3. transfer_basis = pasal_56b_dpa round-trip (Pasal 56(b)).
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    String json =
        "{\"id\":\"e2e-audit\",\"timestamp\":\"2026-05-30T10:00:00Z\","
            + "\"data_residency\":\"ID\",\"transfer_basis\":\""
            + AuditLogEntry.TRANSFER_BASIS_PASAL_56B_DPA
            + "\"}";
    AuditLogEntry entry = mapper.readValue(json, AuditLogEntry.class);
    String reserialized = mapper.writeValueAsString(entry);
    AuditLogEntry back = mapper.readValue(reserialized, AuditLogEntry.class);
    if (!"pasal_56b_dpa".equals(back.getTransferBasis())) {
      fail("transfer_basis round-trip = " + back.getTransferBasis() + ", want pasal_56b_dpa");
    }
    System.out.println("SDK AuditLogEntry round-trip -> " + reserialized);
    System.out.println(
        "PASS: AuditLogEntry.getTransferBasis() = \"" + back.getTransferBasis() + "\" round-trips verbatim");

    System.out.println("ALL PASS: v8.4.0 context + pasal_56b_dpa verified through SDK runtime");
  }

  /** Acts as the PEP: the request context lives in the body's 'context' map. */
  static String createDecision(String endpoint, String clientId, String secret) throws Exception {
    String body =
        "{\"stage\":\"llm\",\"query\":\"summarize this support ticket\","
            + "\"target\":{\"type\":\"llm\",\"model\":\"gpt-4\",\"provider\":\"openai\"},"
            + "\"context\":{\"x-ai-agent\":\"refund-bot\",\"x-session-id\":\"sess-buku-42\","
            + "\"x-leader-identity\":\"ops-lead\"}}";
    String auth = Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes());
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(endpoint + "/api/v1/decide"))
            .header("Content-Type", "application/json")
            .header("X-Client-ID", clientId)
            .header("Authorization", "Basic " + auth)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> resp =
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      fail("decide HTTP " + resp.statusCode() + ": " + resp.body());
    }
    System.out.println("server /decide response: " + resp.body());
    String marker = "\"decision_id\":\"";
    int i = resp.body().indexOf(marker);
    if (i < 0) {
      fail("no decision_id in response: " + resp.body());
    }
    int start = i + marker.length();
    return resp.body().substring(start, resp.body().indexOf('"', start));
  }
}
