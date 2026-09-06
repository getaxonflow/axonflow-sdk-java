/*
 * Runtime proof — the AuthZEN-native surface (ADR-065, enterprise #3603 / #3616),
 * driven through the Java SDK's real public API against a live agent. No mocks.
 *
 * WHAT THIS PROVES THAT THE UNIT SUITE CANNOT.
 *
 *   1. AGREEMENT. POST /api/v1/access/evaluation is an ADAPTER over the
 *      evaluation that serves POST /api/v1/decide. A stubbed transport can
 *      assert what the client does with a given body; only a live stack can
 *      assert the two surfaces agree about a real policy decision, in both
 *      directions.
 *
 *   2. THE REFUSAL VOCABULARY IS SHARED. The SDK refuses an incomplete subject
 *      locally and the server refuses the same bytes on the wire. A unit test
 *      pins the local half; only a live server establishes that the two name
 *      the SAME member.
 *
 *   3. THE BARE-BOOLEAN CASE IS REAL. The SDK refuses a 200 carrying no profile
 *      payload. That guard is only worth something if a server can produce such
 *      a body, so this sends one un-negotiated request and asserts it does.
 *
 *   4. AN UNRESOLVABLE ATTRIBUTE NEVER REACHES THE NETWORK. Asserted by pointing
 *      the real client at a port nothing is listening on: a typed refusal from
 *      that client is proof the check ran before any I/O.
 *
 *   5. THE PROFILE NEGOTIATION REFUSES. The route's header contract has one
 *      refusal of its own - a profile the caller named and this build does not
 *      emit, answered 406 before anything is evaluated. Every other leg sends
 *      AuthZENContract.PROFILE_V1 or no header, both of which the server
 *      accepts, so nothing else here reaches the negotiation's failing side.
 *
 * Run:
 *   ./mvnw -q -DskipTests package
 *   ./mvnw -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   AXONFLOW_AGENT_URL=http://localhost:8080 \
 *     java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
 *       runtime-e2e/authzen_evaluation/AuthZENEvaluationTest.java
 *
 * Optional: AXONFLOW_SAAS_URL — a deployment that actually refuses an
 * unregistered caller, for the one assertion plain community mode cannot make.
 *
 * Exits 0 on success, non-zero on the first failure or a shrunken run.
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.authzen.Attribute;
import com.getaxonflow.sdk.authzen.AuthZENAction;
import com.getaxonflow.sdk.authzen.AuthZENContract;
import com.getaxonflow.sdk.authzen.AuthZENDecision;
import com.getaxonflow.sdk.authzen.AuthZENErrorCode;
import com.getaxonflow.sdk.authzen.AuthZENEvaluation;
import com.getaxonflow.sdk.authzen.AuthZENEvaluationException;
import com.getaxonflow.sdk.authzen.AuthZENOperationalState;
import com.getaxonflow.sdk.authzen.AuthZENRefusedException;
import com.getaxonflow.sdk.authzen.AuthZENRequest;
import com.getaxonflow.sdk.authzen.AuthZENResource;
import com.getaxonflow.sdk.authzen.AuthZENSubject;
import com.getaxonflow.sdk.authzen.AuthZENTransportException;
import com.getaxonflow.sdk.authzen.AuthZENUnresolvedException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthZENEvaluationTest {

  static final int EXPECTED_ASSERTIONS = 16;

  /** A query the default community policy set permits. */
  static final String ALLOWED_QUERY = "what is our refund policy?";

  /** A query the default community policy set denies (SQL injection). */
  static final String DENIED_QUERY = "'; DROP TABLE users; --";

  /**
   * The refusal codes that mean "the adapter READ this envelope and could not evaluate a member of
   * it".
   *
   * <p>{@code malformed_envelope} is deliberately absent: it is what the route sends when the body
   * never became an envelope, so accepting it would let a stack that rejects everything at the door
   * satisfy an assertion about the mapping layer. {@code evaluation_unavailable} is absent for the
   * same reason in the other direction - it is the evaluator being unreachable, not a member being
   * unevaluable.
   */
  static final List<String> MAPPING_LAYER_CODES =
      Arrays.asList(
          "incomplete_evaluation",
          "unsupported_subject",
          "unsupported_action",
          "unsupported_resource",
          "unevaluable_attribute",
          "missing_evaluable_content");

  /** A well-formed envelope whose subject names no type, which the SDK also refuses locally. */
  static final String INCOMPLETE_SUBJECT_ENVELOPE =
      "{\"evaluation\":{\"subject\":{\"id\":\"llm-gateway-01\"},"
          + "\"action\":{\"name\":\"llm.completion\"},"
          + "\"resource\":{\"type\":\"llm\",\"id\":\"llm\"},"
          + "\"context\":{\"args\":{\"query\":\""
          + ALLOWED_QUERY
          + "\"}}}}";

  static final MediaType JSON = MediaType.parse("application/json");
  static final ObjectMapper MAPPER = new ObjectMapper();
  static final OkHttpClient RAW = new OkHttpClient();

  /** How many assertions actually EXECUTED, compared against `expected` at the end. */
  static int ran = 0;
  /** How many of those FAILED, counted separately so a full floor cannot hide a red run. */
  static int failedCount = 0;

  static int expected = EXPECTED_ASSERTIONS;

  static String agent;
  static String clientId;
  static String secret;

  public static void main(String[] args) throws Exception {
    agent = env("AXONFLOW_AGENT_URL", "http://localhost:8080");
    clientId = env("AXONFLOW_CLIENT_ID", "s2-authzen-java-e2e");
    secret = env("AXONFLOW_CLIENT_SECRET", "");

    System.out.println("=== runtime-e2e: authzen_evaluation (Java SDK) ===");
    System.out.println("agent: " + agent);

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(agent)
                .clientId(clientId)
                .clientSecret(secret)
                .build());

    // --- 1 / 2: a decision in both directions, through the SDK ------------
    check(
        "an evaluable request yields a readable ALLOW",
        () -> {
          AuthZENDecision d = evaluate(client, ALLOWED_QUERY);
          require(d.isAllowed(), "isAllowed() was false");
          require(
              AuthZENOperationalState.ALLOW.equals(d.getState()), "state was " + d.getState());
          require(!d.getDecisionId().isEmpty(), "decision_id was empty");
        });

    check(
        "a policy-denied request yields a readable DENY, not an error",
        () -> {
          AuthZENDecision d = evaluate(client, DENIED_QUERY);
          require(!d.isAllowed(), "isAllowed() was true");
          require(AuthZENOperationalState.DENY.equals(d.getState()), "state was " + d.getState());
        });

    // --- 3 / 4: agreement with the legacy Decision API --------------------
    //
    // The release constraint is that this surface answers with the SAME
    // evaluation. Agreement in ONE direction would be satisfied by a route that
    // always allows, so both are asserted.
    for (String[] arm : new String[][] {{ALLOWED_QUERY, "allow"}, {DENIED_QUERY, "deny"}}) {
      final String query = arm[0];
      final String wantVerdict = arm[1];
      check(
          "the AuthZEN verdict agrees with /api/v1/decide (" + wantVerdict + ")",
          () -> {
            String verdict = decideVerdict(query);
            AuthZENDecision d = evaluate(client, query);
            require(
                d.isAllowed() == "allow".equals(verdict) && verdict.equals(wantVerdict),
                "authzen allowed="
                    + d.isAllowed()
                    + " state="
                    + d.getState()
                    + " but /decide verdict="
                    + verdict);
          });
    }

    // --- 5: several preconditions, one decision ---------------------------
    check(
        "a plural envelope yields ONE decision over two preconditions",
        () -> {
          AuthZENDecision d =
              client.evaluateAll(
                  AuthZENEvaluation.over(
                          new AuthZENRequest()
                              .setResource(new AuthZENResource("tool", "jira/move_issue")),
                          new AuthZENRequest()
                              .setResource(new AuthZENResource("tool", "jira/update_project")))
                      .subject(gateway())
                      .action(new AuthZENAction("tool.call"))
                      .query(Attribute.known(ALLOWED_QUERY))
                      .build());
          require(!d.getDecisionId().isEmpty(), "no decision_id");
          require(d.getState().isKnown(), "unreadable state " + d.getState());
        });

    // --- 6 / 7 / 8: the three attribute states, against the real server ---
    //
    // ABSENT is resolved data and evaluates; KNOWN reaches the server and is
    // refused by name; UNKNOWN never leaves the process. Three OBSERVABLY
    // different outcomes for one member, which is the whole argument for the
    // three-valued type.
    check(
        "an ABSENT attribute is omitted and the request is evaluated",
        () -> {
          AuthZENSubject subject = gateway();
          subject.getProperties().putAbsent("department");
          AuthZENDecision d = client.evaluate(requestWith(subject, ALLOWED_QUERY));
          require(d.isAllowed(), "the evaluation did not allow");
        });

    check(
        "a KNOWN attribute reaches the server and is refused BY NAME",
        () -> {
          AuthZENSubject subject = gateway();
          subject.getProperties().putKnown("department", "finance");
          expectRefusal(
              () -> client.evaluate(requestWith(subject, ALLOWED_QUERY)),
              AuthZENErrorCode.UNEVALUABLE_ATTRIBUTE,
              "/evaluation/subject/properties",
              false);
        });

    // Pointed at a port nothing is listening on. A typed refusal from THIS
    // client is proof the check ran before any I/O; a transport error would
    // mean the envelope had already been handed to the network.
    check(
        "an UNKNOWN attribute is refused before any network I/O",
        () -> {
          AxonFlow offline =
              AxonFlow.create(
                  AxonFlowConfig.builder()
                      .endpoint("http://127.0.0.1:1")
                      .clientId(clientId)
                      .build());
          AuthZENSubject subject = gateway();
          subject.getProperties().putUnknown("department", "the directory timed out after 2s");
          try {
            AuthZENDecision d = offline.evaluate(requestWith(subject, ALLOWED_QUERY));
            throw new AssertionError(
                "expected a refusal, got a decision (allowed=" + d.isAllowed() + ")");
          } catch (AuthZENUnresolvedException unresolved) {
            require(
                "/evaluation/subject/properties/department".equals(unresolved.getPointer()),
                "pointer was " + unresolved.getPointer());
            require(!unresolved.isRetryable(), "a frozen refusal must not be reported retryable");
          }
        });

    // --- 9: the SDK and the server name the SAME member -------------------
    check(
        "the SDK's local refusal names the same member the server names",
        () -> {
          String local;
          try {
            client.evaluate(
                requestWith(new AuthZENSubject(null, "llm-gateway-01"), ALLOWED_QUERY));
            throw new AssertionError("the SDK accepted a subject with no type");
          } catch (AuthZENRefusedException refused) {
            local = refused.getPointer();
          }
          Refusal remote = rawRefusal(INCOMPLETE_SUBJECT_ENVELOPE);
          String remotePointer = remote.pointer();
          require(
              "/evaluation/subject/type".equals(local) && local.equals(remotePointer),
              "local pointer " + local + " vs server pointer " + remotePointer);
        });

    // The code is READ, not equated. It is reported either way so a divergence
    // that matters - a code this build cannot name - is visible in the log
    // rather than hidden behind a pointer-only assertion. The two are NOT equal
    // and that is not a defect: this client knows only that a required member is
    // missing; the server additionally knows the supported set and narrows it.
    //
    // isKnown() ALONE would be satisfied by malformed_envelope, which is what a
    // stack that rejects every body at the door sends - so on its own this
    // assertion would pass against a server that never reached the adapter.
    // Two things narrow it: the 422, which only the mapping layer emits (the
    // door answers 400, an unreadable profile 406, an oversized body 413), and
    // the accepted set, which is the adapter's own member-naming vocabulary
    // with malformed_envelope deliberately left out.
    check(
        "the server's refusal is a MAPPING-layer 422, with a code this build knows",
        () -> {
          Refusal remote = rawRefusal(INCOMPLETE_SUBJECT_ENVELOPE);
          String remoteCode = remote.code();
          System.out.println(
              "       local code=incomplete_evaluation  server status="
                  + remote.status
                  + " code="
                  + remoteCode);
          require(
              remote.status == 422,
              "status was " + remote.status + ", wanted 422 (a mapping-layer refusal)");
          require(
              AuthZENErrorCode.of(remoteCode).isKnown(),
              "the server sent " + remoteCode + ", which is not in this build's enumeration");
          require(
              MAPPING_LAYER_CODES.contains(remoteCode),
              "the server sent "
                  + remoteCode
                  + "; a refusal that reached the adapter names one of "
                  + MAPPING_LAYER_CODES);
        });

    // --- 10b: the one refusal unique to the header contract ---------------
    check(
        "an unrecognised profile is refused with 406, naming the profile it does emit",
        AuthZENEvaluationTest::rawUnrecognisedProfileIs406);

    // --- 11: the bare boolean an un-negotiated caller receives ------------
    check(
        "an un-negotiated request really does come back with NO profile payload",
        AuthZENEvaluationTest::rawUnnegotiatedHasNoContext);

    // --- 12 / 13: the served route and header NAME are the generated ones ---
    //
    // AxonFlow.AUTHZEN_PATH and AxonFlow.AUTHZEN_PROFILE_HEADER come from the
    // platform's surface artifact (axonflow-enterprise#3603), not from a literal
    // here. A raw request puts both on the wire: with the generated header name
    // the server returns the negotiated profile context; with the name altered
    // by one character it must NOT - the bare boolean is the proof that the NAME
    // is what the handler reads, and that this SDK's constant is that name. Leg
    // 11 sends NO header; this pair proves the header's identity, not its
    // presence.
    check(
        "the generated route and header name negotiate the profile on the live wire",
        () -> {
          JsonNode node = rawEvaluate(AxonFlow.AUTHZEN_PROFILE_HEADER);
          require(
              AuthZENContract.PROFILE_V1.equals(node.path("context").path("profile").asText("")),
              "POST "
                  + AxonFlow.AUTHZEN_PATH
                  + " with "
                  + AxonFlow.AUTHZEN_PROFILE_HEADER
                  + " returned "
                  + node);
        });

    check(
        "a header name one character off is not read, so the constant is the name",
        () -> {
          String offByOne =
              AxonFlow.AUTHZEN_PROFILE_HEADER.substring(
                  0, AxonFlow.AUTHZEN_PROFILE_HEADER.length() - 1);
          JsonNode node = rawEvaluate(offByOne);
          require(
              !node.has("context"),
              "header " + offByOne + " still negotiated a context: " + node);
          require(node.has("decision"), "header " + offByOne + " returned no decision at all");
        });

    // --- 14: an auth failure stays observable -----------------------------
    //
    // Needs a deployment that actually refuses an unregistered caller. Plain
    // community mode treats any client id as its own tenant and answers 200, so
    // running this there would assert nothing.
    String saas = System.getenv("AXONFLOW_SAAS_URL");
    if (saas == null || saas.isEmpty()) {
      skip(
          "an auth failure surfaces as an error, never as a denial",
          "AXONFLOW_SAAS_URL is unset; plain community mode never refuses a caller");
    } else {
      check(
          "an auth failure surfaces as an error, never as a denial",
          () -> {
            AxonFlow bad =
                AxonFlow.create(
                    AxonFlowConfig.builder()
                        .endpoint(saas)
                        .clientId("s2-not-registered")
                        .clientSecret("wrong")
                        .build());
            try {
              AuthZENDecision d = evaluate(bad, ALLOWED_QUERY);
              throw new AssertionError(
                  "an unauthenticated call produced a decision (allowed=" + d.isAllowed() + ")");
            } catch (AuthZENTransportException e) {
              require(e.getStatusCode() == 401, "the error did not name the status: " + e);
              require(!e.isRetryable(), "a credentials failure must not be retried");
            }
          });
    }

    // --- 13: the legacy surface is untouched ------------------------------
    check(
        "POST /api/v1/decide still answers, so this surface is purely additive",
        () -> {
          String verdict = decideVerdict(ALLOWED_QUERY);
          require("allow".equals(verdict), "verdict was " + verdict);
        });

    System.out.println();
    if (ran != expected) {
      System.out.println(
          "FAIL: " + ran + " assertion(s) ran but " + expected + " were expected"
              + " — checks stopped executing");
      System.exit(1);
    }
    if (failedCount > 0) {
      System.out.println("FAIL: " + failedCount + " of " + ran + " assertions failed");
      System.exit(1);
    }
    System.out.println("ALL PASS: " + ran + "/" + expected + " assertions");
  }

  // -------------------------------------------------------------------------
  // Harness
  // -------------------------------------------------------------------------

  interface Assertion {
    void run() throws Exception;
  }

  static void check(String what, Assertion assertion) {
    ran++;
    try {
      assertion.run();
      System.out.println("  PASS: " + what);
    } catch (Throwable t) {
      failedCount++;
      System.out.println("  FAIL: " + what + " — " + t);
    }
  }

  /**
   * A prerequisite missing for ONE assertion, discovered after others have run. It lowers the floor
   * by exactly one rather than exiting, so earlier failures are still reported and a shrunken run
   * is still loud.
   */
  static void skip(String what, String why) {
    expected--;
    System.out.println("  SKIP: " + what + " (" + why + ")");
  }

  static void require(boolean ok, String detail) {
    if (!ok) {
      throw new AssertionError(detail);
    }
  }

  static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isEmpty() ? fallback : value;
  }

  static AuthZENSubject gateway() {
    return new AuthZENSubject("gateway", "llm-gateway-01");
  }

  static AuthZENRequest requestWith(AuthZENSubject subject, String query) {
    return AuthZENEvaluation.of(
            subject, new AuthZENAction("llm.completion"), new AuthZENResource("llm", "llm"))
        .query(Attribute.known(query))
        .build();
  }

  static AuthZENDecision evaluate(AxonFlow client, String query) {
    return client.evaluate(requestWith(gateway(), query));
  }

  static void expectRefusal(
      java.util.function.Supplier<AuthZENDecision> call,
      AuthZENErrorCode code,
      String pointer,
      boolean retryable) {
    try {
      AuthZENDecision d = call.get();
      throw new AssertionError("expected a refusal, got a decision (allowed=" + d.isAllowed() + ")");
    } catch (AuthZENRefusedException refused) {
      require(code.equals(refused.getCode()), "code was " + refused.getCode() + ", wanted " + code);
      require(
          pointer.equals(refused.getPointer()),
          "pointer was " + refused.getPointer() + ", wanted " + pointer);
      require(
          refused.isRetryable() == retryable,
          "retryable was " + refused.isRetryable() + ", wanted " + retryable);
    } catch (AuthZENEvaluationException other) {
      throw new AssertionError("expected a typed refusal, got " + other, other);
    }
  }

  static String basic() {
    return Base64.getEncoder()
        .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * The legacy Decision API's verdict for the same query.
   *
   * <p>Raw HTTP because /api/v1/decide is deliberately not SDK-wrapped (ADR-056), with the
   * identical Basic-auth credentials the SDK's own transport sends.
   */
  static String decideVerdict(String query) throws Exception {
    String body =
        "{\"stage\":\"llm\",\"query\":"
            + MAPPER.writeValueAsString(query)
            + ",\"target\":{\"type\":\"llm\"},"
            + "\"caller_identity\":{\"gateway_id\":\"llm-gateway-01\"}}";
    Request request =
        new Request.Builder()
            .url(agent + "/api/v1/decide")
            .header("Authorization", "Basic " + basic())
            .header("X-Client-ID", clientId)
            .post(RequestBody.create(body, JSON))
            .build();
    try (Response response = RAW.newCall(request).execute()) {
      String text = response.body() == null ? "" : response.body().string();
      require(response.isSuccessful(), "/api/v1/decide HTTP " + response.code() + ": " + text);
      JsonNode node = MAPPER.readTree(text);
      require(node.hasNonNull("verdict"), "no verdict in " + text);
      return node.get("verdict").asText();
    }
  }

  /** A refusal the SERVER returned: its HTTP status alongside its document. */
  static final class Refusal {
    final int status;
    final JsonNode body;

    Refusal(int status, JsonNode body) {
      this.status = status;
      this.body = body;
    }

    String code() {
      return body.path("code").asText("");
    }

    String pointer() {
      return body.path("pointer").asText(null);
    }
  }

  /**
   * The refusal the SERVER returns for an envelope the SDK refuses locally.
   *
   * <p>The STATUS travels with the document because the code alone does not say WHERE the refusal
   * happened. {@code malformed_envelope} at 400 is the door; a mapping code at 422 is the adapter
   * having read the envelope and found a member it cannot evaluate. An assertion that reads only
   * the code passes against a stack that rejects every body at the door.
   */
  static Refusal rawRefusal(String envelope) throws Exception {
    Request request =
        new Request.Builder()
            .url(agent + AxonFlow.AUTHZEN_PATH)
            .header("Authorization", "Basic " + basic())
            .header("X-Client-ID", clientId)
            .header(AxonFlow.AUTHZEN_PROFILE_HEADER, AuthZENContract.PROFILE_V1)
            .post(RequestBody.create(envelope, JSON))
            .build();
    try (Response response = RAW.newCall(request).execute()) {
      String text = response.body() == null ? "" : response.body().string();
      require(
          !response.isSuccessful(),
          "the server ACCEPTED an envelope the SDK refuses; the two have diverged");
      return new Refusal(response.code(), MAPPER.readTree(text));
    }
  }

  /**
   * A profile this build does not emit is refused with 406, before anything is evaluated.
   *
   * <p>This is the ONE refusal unique to the route's header contract, and the only one no other
   * assertion here can reach: every other leg sends {@link AuthZENContract#PROFILE_V1} or no header
   * at all, and the server accepts both. Raw HTTP because the SDK deliberately has no way to name a
   * profile it cannot read - that is what the constant is for - so the header has to be set by hand
   * to exercise the server's half of the negotiation.
   */
  static void rawUnrecognisedProfileIs406() throws Exception {
    // The same envelope the un-negotiated leg sends and gets a 200 for, so a
    // 406 here can only be the header. That is what makes this evidence about
    // the negotiation rather than about the body.
    String envelope =
        "{\"evaluation\":{\"subject\":{\"type\":\"gateway\",\"id\":\"llm-gateway-01\"},"
            + "\"action\":{\"name\":\"llm.completion\"},"
            + "\"resource\":{\"type\":\"llm\",\"id\":\"llm\"},"
            + "\"context\":{\"args\":{\"query\":\""
            + ALLOWED_QUERY
            + "\"}}}}";
    Request request =
        new Request.Builder()
            .url(agent + AxonFlow.AUTHZEN_PATH)
            .header("Authorization", "Basic " + basic())
            .header("X-Client-ID", clientId)
            .header(AxonFlow.AUTHZEN_PROFILE_HEADER, "axonflow-authzen-profile-2099-01-01")
            .post(RequestBody.create(envelope, JSON))
            .build();
    try (Response response = RAW.newCall(request).execute()) {
      String text = response.body() == null ? "" : response.body().string();
      require(response.code() == 406, "status was " + response.code() + ", wanted 406; body=" + text);
      JsonNode node = MAPPER.readTree(text);
      boolean namesV1 = false;
      for (JsonNode s : node.path("supported")) {
        if (AuthZENContract.PROFILE_V1.equals(s.asText())) {
          namesV1 = true;
        }
      }
      require(namesV1, "the refusal did not name " + AuthZENContract.PROFILE_V1 + ": " + text);
    }
  }

  /** A request that does NOT negotiate the profile gets the bare boolean. */
  /**
   * POST the evaluable envelope to the GENERATED path with the given profile header NAME.
   *
   * <p>Bypasses the SDK client on purpose: legs 12 and 13 prove that the constants this SDK
   * generated are the ones the server reads, and the client would use the same constants, so
   * sending through it would prove nothing.
   */
  static JsonNode rawEvaluate(String headerName) throws Exception {
    String envelope =
        "{\"evaluation\":{\"subject\":{\"type\":\"gateway\",\"id\":\"llm-gateway-01\"},"
            + "\"action\":{\"name\":\"llm.completion\"},"
            + "\"resource\":{\"type\":\"llm\",\"id\":\"llm\"},"
            + "\"context\":{\"args\":{\"query\":\""
            + ALLOWED_QUERY
            + "\"}}}}";
    Request request =
        new Request.Builder()
            .url(agent + AxonFlow.AUTHZEN_PATH)
            .header("Authorization", "Basic " + basic())
            .header("X-Client-ID", clientId)
            .header(headerName, AuthZENContract.PROFILE_V1)
            .post(RequestBody.create(envelope, JSON))
            .build();
    try (Response response = RAW.newCall(request).execute()) {
      String text = response.body() == null ? "" : response.body().string();
      require(response.isSuccessful(), "HTTP " + response.code() + ": " + text);
      return MAPPER.readTree(text);
    }
  }

  static void rawUnnegotiatedHasNoContext() throws Exception {
    String envelope =
        "{\"evaluation\":{\"subject\":{\"type\":\"gateway\",\"id\":\"llm-gateway-01\"},"
            + "\"action\":{\"name\":\"llm.completion\"},"
            + "\"resource\":{\"type\":\"llm\",\"id\":\"llm\"},"
            + "\"context\":{\"args\":{\"query\":\""
            + ALLOWED_QUERY
            + "\"}}}}";
    Request request =
        new Request.Builder()
            .url(agent + AxonFlow.AUTHZEN_PATH)
            .header("Authorization", "Basic " + basic())
            .header("X-Client-ID", clientId)
            .post(RequestBody.create(envelope, JSON))
            .build();
    try (Response response = RAW.newCall(request).execute()) {
      String text = response.body() == null ? "" : response.body().string();
      require(response.isSuccessful(), "HTTP " + response.code() + ": " + text);
      JsonNode node = MAPPER.readTree(text);
      require(node.has("decision"), "the bare response carried no decision at all");
      require(
          !node.has("context"),
          "the un-negotiated response carried a profile payload: " + text);
    }
  }
}
