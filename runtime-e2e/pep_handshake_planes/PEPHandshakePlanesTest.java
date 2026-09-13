// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.authzen.Attribute;
import com.getaxonflow.sdk.authzen.AuthZENAction;
import com.getaxonflow.sdk.authzen.AuthZENBulk;
import com.getaxonflow.sdk.authzen.AuthZENEvaluation;
import com.getaxonflow.sdk.authzen.AuthZENObligationType;
import com.getaxonflow.sdk.authzen.AuthZENRequest;
import com.getaxonflow.sdk.authzen.AuthZENResource;
import com.getaxonflow.sdk.authzen.AuthZENSubject;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;
import com.getaxonflow.sdk.types.Obligation;
import com.getaxonflow.sdk.types.ObligationFulfillment;
import com.getaxonflow.sdk.types.PEPCapability;
import com.getaxonflow.sdk.types.PEPHandshake;
import com.getaxonflow.sdk.types.PolicyApprovalRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real-stack proof that the platform READS the SDK's PEP capability declaration on every plane
 * that resolves it. After each governed call the driver reads the agent's own counter, {@code
 * axonflow_pep_handshake_total{outcome, plane}} on {@code /prometheus}, which the agent moves once
 * per inbound request on the four planes that resolve the declaration. Nothing is mocked.
 *
 * <pre>
 *   decide                                      accepted on decision
 *   evaluate, evaluateAll                       accepted on access_evaluation
 *   mcpCheckInput, mcpCheckOutput,
 *   fulfillRequest's engine round-trip          accepted on mcp
 *   getPolicyApprovedContext                    accepted on gateway
 *   decide on a withPEPHandshake client         over_advertised on decision on a Community
 *     naming approval_challenge                 agent (which drops that family), accepted on
 *                                               any other
 *   decide from a client with no declaration    absent on decision
 * </pre>
 *
 * <p>Each call must move exactly the one series listed, by exactly one. "accepted" means the agent
 * decoded the SDK's bytes, validated the document and admitted the enforcement point; a malformed
 * or repeated header would have been refused with a 400 and counted as "malformed". Run it against
 * an agent no other client is using, since a concurrent request on one of the four planes would
 * move the counter too. See README.md for how to run it.
 */
public class PEPHandshakePlanesTest {

  static final String AGENT = env("AXONFLOW_AGENT_URL", "http://localhost:8080");
  static final List<String> FAILURES = new ArrayList<>();
  static final HttpClient HTTP = HttpClient.newHttpClient();
  static final Pattern SERIES =
      Pattern.compile("^axonflow_pep_handshake_total\\{([^}]*)\\}\\s+(\\S+)$");
  static final Pattern LABEL = Pattern.compile("(\\w+)=\"([^\"]*)\"");

  interface Call {
    void run() throws Exception;
  }

  static String env(String name, String fallback) {
    String v = System.getenv(name);
    return v == null || v.isEmpty() ? fallback : v;
  }

  static void check(boolean ok, String description) {
    System.out.println((ok ? "PASS: " : "FAIL: ") + description);
    if (!ok) {
      FAILURES.add(description);
    }
  }

  static String get(String path) throws Exception {
    HttpResponse<String> response =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(AGENT + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("GET " + path + ": HTTP " + response.statusCode());
    }
    return response.body();
  }

  /** The agent's handshake counter, keyed "outcome@plane". */
  static Map<String, Double> scrape() throws Exception {
    Map<String, Double> counts = new HashMap<>();
    for (String line : get("/prometheus").split("\n")) {
      Matcher m = SERIES.matcher(line);
      if (!m.matches()) {
        continue;
      }
      Map<String, String> labels = new HashMap<>();
      Matcher l = LABEL.matcher(m.group(1));
      while (l.find()) {
        labels.put(l.group(1), l.group(2));
      }
      counts.put(labels.get("outcome") + "@" + labels.get("plane"), Double.parseDouble(m.group(2)));
    }
    return counts;
  }

  static Map<String, Double> moved(Map<String, Double> before, Map<String, Double> after) {
    Map<String, Double> out = new TreeMap<>();
    for (Map.Entry<String, Double> e : after.entrySet()) {
      double delta = e.getValue() - before.getOrDefault(e.getKey(), 0.0);
      if (delta != 0) {
        out.put(e.getKey(), delta);
      }
    }
    for (Map.Entry<String, Double> e : before.entrySet()) {
      if (!after.containsKey(e.getKey())) {
        out.put(e.getKey(), -e.getValue());
      }
    }
    return out;
  }

  /** Runs {@code call} and requires it to move exactly the one series {@code want}. */
  static void counted(String description, Call call, String want) {
    System.out.println("== " + description);
    try {
      Map<String, Double> before = scrape();
      call.run();
      Map<String, Double> got = moved(before, scrape());
      System.out.println("  the agent counted " + (got.isEmpty() ? "nothing" : got));
      check(
          got.size() == 1 && Double.valueOf(1.0).equals(got.get(want)),
          description + ": " + want + " +1");
    } catch (Exception e) {
      check(false, description + " threw " + e);
    }
  }

  public static void main(String[] args) throws Exception {
    JsonNode health = new ObjectMapper().readTree(get("/health"));
    String edition = health.path("edition").asText("");
    System.out.println("agent: " + AGENT + " (edition \"" + edition + "\")");
    // The platform drops approval-family capabilities only for a Community enforcement point; any
    // other edition admits the derived client's document whole.
    String overrideOutcome = "community".equals(edition) ? "over_advertised" : "accepted";

    PEPHandshake declared =
        PEPHandshake.of(
            "sdk-java-e2e",
            "https://pep.example.test",
            List.of(PEPCapability.of(AuthZENObligationType.FIELD_REDACT, 1)));
    PEPHandshake override =
        PEPHandshake.of(
            "sdk-java-e2e-override",
            "https://pep.example.test",
            List.of(
                PEPCapability.of(AuthZENObligationType.APPROVAL_CHALLENGE, 1),
                PEPCapability.of(AuthZENObligationType.FIELD_REDACT, 1)));
    String clientId = env("AXONFLOW_CLIENT_ID", "runtime-e2e");
    String clientSecret = env("AXONFLOW_CLIENT_SECRET", "runtime-e2e-secret");
    AxonFlow bare =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(AGENT)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(AGENT)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .pepHandshake(declared)
                .build());

    String query = "look up the weather";
    DecideRequest decide =
        DecideRequest.builder("tool", query)
            .target(new DecisionTarget("tool", null, null, "search"))
            .build();
    AuthZENRequest evaluation =
        AuthZENEvaluation.of(
                new AuthZENSubject("gateway", "sdk-java-e2e"),
                new AuthZENAction("llm.completion"),
                new AuthZENResource("llm", "llm"))
            .query(Attribute.known(query))
            .build();
    // A decision carrying the request-phase redaction obligation, so fulfillRequest makes its
    // engine round-trip to the real agent.
    DecideResponse redacting =
        new DecideResponse(
            "allow",
            "sdk-java-e2e",
            null,
            null,
            List.of(
                new Obligation(
                    "redact_pii",
                    null,
                    new ObligationFulfillment(
                        "/api/v1/mcp/check-input", "POST", "request", List.of("text/plain")))),
            List.of(),
            "tool",
            null,
            null);
    // An enterprise agent validates the pre-check's user token as a JWT.
    PolicyApprovalRequest preCheck =
        PolicyApprovalRequest.builder()
            .userToken(env("AXONFLOW_USER_TOKEN", "tok"))
            .query("hello")
            .build();

    counted("decide", () -> client.decide(decide), "accepted@decision");
    counted("evaluate", () -> client.evaluate(evaluation), "accepted@access_evaluation");
    counted(
        "evaluateAll",
        () -> client.evaluateAll(new AuthZENBulk(List.of(evaluation))),
        "accepted@access_evaluation");
    counted(
        "mcpCheckInput", () -> client.mcpCheckInput("postgres", "SELECT 1"), "accepted@mcp");
    counted(
        "mcpCheckOutput",
        () -> client.mcpCheckOutput("postgres", null, Map.of("message", "hello")),
        "accepted@mcp");
    counted(
        "getPolicyApprovedContext",
        () -> client.getPolicyApprovedContext(preCheck),
        "accepted@gateway");
    counted(
        "fulfillRequest",
        () -> client.fulfillRequest(redacting, "email the receipt to jane.doe@example.com"),
        "accepted@mcp");
    counted(
        "decide on a withPEPHandshake client",
        () -> client.withPEPHandshake(override).decide(decide),
        overrideOutcome + "@decision");
    counted("decide with no declaration", () -> bare.decide(decide), "absent@decision");

    client.close();
    bare.close();
    if (!FAILURES.isEmpty()) {
      System.out.println("\nFAIL: pep_handshake_planes (" + FAILURES.size() + " assertion(s))");
      System.exit(1);
    }
    System.out.println("\nPASS: pep_handshake_planes");
  }
}
