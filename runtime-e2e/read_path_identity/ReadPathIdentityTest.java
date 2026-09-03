/*
 * runtime-e2e/read_path_identity/ReadPathIdentityTest.java
 *
 * Real-wire proof of the read-path per-user identity (platform #2922) through
 * the Java SDK's own runtime, against a LIVE enterprise agent + orchestrator.
 *
 * The defect this pins: every SDK carried `user_token` as a write-path body
 * field only, so explainDecision and listDecisions asked the platform
 * anonymously. On an enterprise stack that is not "a caller who sees
 * everything" — it is a caller the platform cannot scope, so explain answered
 * not-found for ids that plainly existed and list answered a confident empty
 * page.
 *
 * What this asserts, and why each assertion cannot pass vacuously:
 *
 *   1. WRITE       three decisions through the real /decide plane as dev-a.
 *   2. LIST        as dev-a: the page must contain AT LEAST the three ids this
 *                  run wrote, each checked BY ID. Then DEV-B writes one and
 *                  dev-a's page must NOT grow — which is what separates
 *                  own-rows from a broken narrowing that returns the tenant.
 *   3. EXPLAIN     as dev-a: must carry the id asked for AND the context value
 *                  THIS RUN chose, so a populated-looking stub cannot satisfy it.
 *   4. NO IDENTITY the same list, unscoped: must be REFUSED as a typed
 *                  ReadScopeException with isIdentityMissing, not answered [].
 *   5. OTHER USER  explain dev-a's decision as dev-b: must be refused, and must
 *                  NOT report a missing identity — dev-b presented one.
 *   6. MALFORMED / EXPIRED / WRONG-ORG: each must fail CLOSED, never degrade to
 *                  the tenant credential's visibility, and never echo the token.
 *   7. TENANT-WIDE as admin: must see dev-a's decision, which is what makes
 *                  step 5 falsifiable — a read broken for everyone also
 *                  "refuses dev-b".
 *   8. AS_USER     a derived client must be scoped to the identity it was
 *                  derived FOR, on a method with no per-call overload. This is
 *                  the step that catches a derived client silently keeping the
 *                  ORIGINAL identity; the Python sibling had exactly that bug.
 *   9. NO LEAK     the token must appear in NO captured log line and in NO
 *                  request reaching the telemetry collector this driver hosts.
 *                  A positive control asserts SDK output IS present first.
 *  10. OBSERVABLE  the platform must leave a record of the unscoped read.
 *
 * Identities are minted at @example.com, never @axonflow.local: the platform
 * reserves that whole domain (and @axonflow.internal) for SHARED, non-personal
 * identities and censuses them to nothing before scoping, so a perfectly valid
 * developer token minted there reads ZERO rows and reports scope `none` —
 * identical to presenting no token at all. generate-jwt.sh's own default
 * (demo-user@axonflow.local) lands in the reserved domain.
 *
 * Run:
 *   set -a; source /tmp/axonflow-e2e-env.sh; set +a
 *   ./runtime-e2e/read_path_identity/run.sh
 */
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.exceptions.ReadScopeException;
import com.getaxonflow.sdk.identity.ReadIdentity;
import com.getaxonflow.sdk.identity.ReadScope;
import com.getaxonflow.sdk.types.DecisionExplanation;
import com.getaxonflow.sdk.types.DecisionSummary;
import com.getaxonflow.sdk.types.ListDecisionsOptions;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ReadPathIdentityTest {

  static final String AGENT_URL =
      System.getenv().getOrDefault("AXONFLOW_AGENT_URL", "http://localhost:8080");
  static final String CLIENT_ID = System.getenv("AXONFLOW_CLIENT_ID");
  static final String SECRET = System.getenv("AXONFLOW_CLIENT_SECRET");
  static final String JWT_SECRET =
      System.getenv("AXONFLOW_JWT_SECRET") != null
          ? System.getenv("AXONFLOW_JWT_SECRET")
          : System.getenv("JWT_SECRET");
  static final String ORCH =
      System.getenv().getOrDefault("AXONFLOW_ORCH_CONTAINER", "axonflow-orchestrator");

  /** Makes every assertion specific to THIS run. */
  static final String RUN_TAG = "s3-java-" + System.nanoTime();

  static final int WROTE = 3;

  static final List<String> collected = new ArrayList<>();
  static ByteArrayOutputStream capturedErr;

  static void fail(String msg) {
    System.err.println("FAIL: " + msg);
    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
    if (CLIENT_ID == null || SECRET == null || JWT_SECRET == null) {
      fail(
          "AXONFLOW_CLIENT_ID, AXONFLOW_CLIENT_SECRET and JWT_SECRET must be set "
              + "(source /tmp/axonflow-e2e-env.sh after ./scripts/setup-e2e-testing.sh enterprise)");
    }

    // A real listener standing in for the telemetry checkpoint — a THIRD PARTY.
    // allow-mocks-here: not a stand-in for the system under test. It is the far
    // end of a request the SDK sends on its own initiative, and the assertion is
    // about what actually arrives there, which cannot be observed at all without
    // owning that end.
    // The port is chosen by run.sh and handed in: AXONFLOW_CHECKPOINT_URL is
    // read from the ENVIRONMENT, and a JVM cannot set one for itself. Binding a
    // random port here and calling System.setProperty would read back fine and
    // change nothing about where telemetry actually goes — a passing assertion
    // about an unreachable property.
    String collectorPort = System.getenv("S3_COLLECTOR_PORT");
    if (collectorPort == null) {
      fail(
          "S3_COLLECTOR_PORT is unset — run this through runtime-e2e/read_path_identity/run.sh,"
              + " which chooses the port and exports AXONFLOW_CHECKPOINT_URL to match. Without it"
              + " the telemetry leak assertions in step 9 would have nothing to assert about.");
    }
    HttpServer collector =
        HttpServer.create(
            new InetSocketAddress("127.0.0.1", Integer.parseInt(collectorPort)), 0);
    collector.createContext(
        "/",
        exchange -> {
          String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          collected.add(exchange.getRequestHeaders().toString() + body);
          byte[] out = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    collector.start();

    // Capture stderr, where the SDK's SLF4J simple logger writes, for step 9.
    capturedErr = new ByteArrayOutputStream();
    PrintStream realErr = System.err;
    System.setErr(new PrintStream(new TeeStream(realErr, capturedErr), true));

    String devA = mint("dev-a-" + RUN_TAG + "@example.com", CLIENT_ID, "developer", 3600);
    String devB = mint("dev-b-" + RUN_TAG + "@example.com", CLIENT_ID, "developer", 3600);
    String admin = mint("admin-" + RUN_TAG + "@example.com", CLIENT_ID, "admin", 3600);
    String expired = mint("old-" + RUN_TAG + "@example.com", CLIENT_ID, "developer", -3600);
    String wrongOrg = mint("out-" + RUN_TAG + "@example.com", "other-org-" + RUN_TAG, "admin", 3600);
    String malformed = "not.a.jwt";

    // ======================================================== 1. WRITE
    // Three, not one: the floor in step 2 is "at least the number this run
    // wrote", and a floor of one is satisfied by almost any page.
    List<String> written = new ArrayList<>();
    for (int i = 0; i < WROTE; i++) {
      written.add(decideAs(devA, i));
    }
    System.out.println("step 1 PASS: wrote " + written.size() + " decisions as dev-a: " + written);

    AxonFlow asDevA = client(devA);
    waitForVisible(asDevA, written.get(0));

    // ========================================================= 2. LIST
    List<DecisionSummary> rows = asDevA.listDecisions(ListDecisionsOptions.builder().limit(50).build());
    if (rows.size() < WROTE) {
      fail(
          "step 2: dev-a's page has "
              + rows.size()
              + " rows, want at least the "
              + WROTE
              + " this run wrote — a page smaller than what we just wrote cannot be a"
              + " correctly-scoped read");
    }
    for (String id : written) {
      if (rows.stream().noneMatch(r -> id.equals(r.getDecisionId()))) {
        fail("step 2: dev-a's page does not contain " + id + ", which dev-a wrote in this run");
      }
    }
    // The floor alone cannot tell own-rows from tenant-wide: a broken narrowing
    // returning the WHOLE tenant would clear it comfortably.
    decideAs(devB, 99);
    Thread.sleep(3000);
    List<DecisionSummary> after = asDevA.listDecisions(ListDecisionsOptions.builder().limit(50).build());
    if (after.size() != rows.size()) {
      fail(
          "step 2: dev-a's page grew from "
              + rows.size()
              + " to "
              + after.size()
              + " rows after DEV-B wrote one — the read is not narrowed to dev-a's own rows, so"
              + " every scoping assertion below is vacuous");
    }
    System.out.println(
        "step 2 PASS: dev-a's page ("
            + rows.size()
            + " rows) is exactly its own; dev-b's write did not appear");

    // ====================================================== 3. EXPLAIN
    DecisionExplanation explanation = asDevA.explainDecision(written.get(0));
    if (!written.get(0).equals(explanation.getDecisionId())) {
      fail("step 3: explanation decision_id = " + explanation.getDecisionId());
    }
    // A field THIS RUN controls. "Non-empty" would pass on any stub.
    String session =
        explanation.getContext() == null ? null : explanation.getContext().get("x_session_id");
    if (!RUN_TAG.equals(session)) {
      fail(
          "step 3: explanation context[x_session_id] = "
              + session
              + ", want "
              + RUN_TAG
              + " — the explanation must carry the value this run wrote, not merely be non-empty");
    }
    System.out.println(
        "step 3 PASS: explanation for "
            + written.get(0)
            + " is populated and carries this run's context (x_session_id="
            + RUN_TAG
            + ", decision="
            + explanation.getDecision()
            + ")");

    // ================================================= 4. NO IDENTITY
    try {
      List<DecisionSummary> anon =
          client(null).listDecisions(ListDecisionsOptions.builder().limit(50).build());
      if (!anon.isEmpty()) {
        fail(
            "step 4: the unscoped list returned "
                + anon.size()
                + " rows — this stack is not enforcing role-scoped reads, so every scoping"
                + " assertion in this driver is vacuous");
      }
      fail(
          "step 4: the unscoped list returned 0 rows and NO error. That is the defect: the read"
              + " could not have returned a row, and reporting it as an empty page is a confident"
              + " lie");
    } catch (ReadScopeException e) {
      if (!e.isIdentityMissing()) {
        fail("step 4: the unscoped list was refused with scope " + e.getScope() + ", want none");
      }
      System.out.println("step 4 PASS: the unscoped list is refused, not answered empty");
    }

    // ================================================== 5. OTHER USER
    try {
      client(devB).explainDecision(written.get(0));
      fail(
          "step 5: dev-b explained dev-a's decision "
              + written.get(0)
              + " — that is the cross-user leak #2922 closed");
    } catch (ReadScopeException e) {
      if (e.isIdentityMissing()) {
        fail(
            "step 5: dev-b's refusal reports a MISSING identity; dev-b presented one. Reporting"
                + " the wrong cause is the confidently-wrong-diagnosis class (scope="
                + e.getScope()
                + ")");
      }
      if (!ReadScope.OWN_ROWS.equals(e.getScope())) {
        fail("step 5: dev-b's refusal reports scope " + e.getScope() + ", want own-rows");
      }
      System.out.println("step 5 PASS: dev-b is refused dev-a's decision, with the RIGHT cause");
    }

    // ================================ 6. MALFORMED / EXPIRED / WRONG-ORG
    // The common real-world state, not the exception. Each must fail CLOSED: a
    // rejected token must never degrade into "no token", which would hand the
    // caller the tenant credential's visibility.
    for (String[] bad :
        new String[][] {
          {"malformed", malformed}, {"expired", expired}, {"another org", wrongOrg}
        }) {
      try {
        client(bad[1]).listDecisions(ListDecisionsOptions.builder().limit(5).build());
        fail(
            "step 6 ("
                + bad[0]
                + "): a rejected per-user token produced a SUCCESSFUL read. A"
                + " present-but-invalid identity must fail closed, never degrade to the unscoped"
                + " path");
      } catch (ReadScopeException e) {
        fail(
            "step 6 ("
                + bad[0]
                + "): a REJECTED token was reported as a scoping outcome, which means it degraded"
                + " to the unscoped path instead of failing closed");
      } catch (RuntimeException e) {
        String text = String.valueOf(e.getMessage());
        if (!text.contains("invalid user token")) {
          fail("step 6 (" + bad[0] + "): the refusal is not the platform's token rejection: " + text);
        }
        if (text.contains(bad[1])) {
          fail("step 6 (" + bad[0] + "): the error message echoes the rejected credential");
        }
        System.out.println(
            "step 6 PASS (" + bad[0] + "): rejected fail-closed by the platform, credential not echoed");
      }
    }

    // ================================================= 7. TENANT-WIDE
    // Without this, step 5 is unfalsifiable: a read broken for everyone would
    // also "refuse dev-b".
    AxonFlow asAdmin = client(admin);
    if (!written.get(0).equals(asAdmin.explainDecision(written.get(0)).getDecisionId())) {
      fail("step 7: an admin identity could not explain dev-a's decision");
    }
    System.out.println(
        "step 7 PASS: an admin identity reads tenant-wide — step 5's refusal is scoping, not"
            + " breakage");

    // ====================================================== 8. AS_USER
    try {
      asAdmin.asUser(devB).explainDecision(written.get(0));
      fail(
          "step 8: asUser(dev-b) read dev-a's decision — the derived client kept the ADMIN"
              + " identity, which is the silent widening asUser exists to prevent");
    } catch (ReadScopeException e) {
      if (!ReadScope.OWN_ROWS.equals(e.getScope())) {
        fail("step 8: asUser(dev-b) reported scope " + e.getScope() + ", want own-rows");
      }
      System.out.println(
          "step 8 PASS: asUser(dev-b) is scoped to dev-b, not to the admin it derived from");
    }
    // ...and the client it came from is unchanged.
    if (!written.get(0).equals(asAdmin.explainDecision(written.get(0)).getDecisionId())) {
      fail("step 8: asUser mutated the client it was derived from");
    }

    // ====================================================== 9. NO LEAK
    Thread.sleep(1000);
    String logText = capturedErr.toString(StandardCharsets.UTF_8);
    // POSITIVE CONTROL. Without it the greps below are a negative assertion over
    // a haystack that may be empty, which passes for every string.
    if (!logText.toLowerCase().contains("axonflow")) {
      fail(
          "step 9: the captured log contains no SDK output at all ("
              + logText.length()
              + " chars), so asserting the token is absent from it asserts nothing");
    }
    for (String[] tok :
        new String[][] {{"dev-a", devA}, {"dev-b", devB}, {"admin", admin}}) {
      if (logText.contains(tok[1])) {
        fail("step 9: the " + tok[0] + " token appears in the SDK's log output");
      }
      for (int i = 0; i < collected.size(); i++) {
        if (collected.get(i).contains(tok[1])) {
          fail("step 9: the " + tok[0] + " token reached the telemetry collector in request " + i);
        }
      }
    }
    if (collected.isEmpty()) {
      fail(
          "step 9: the telemetry collector received NOTHING, so its leak assertions asserted"
              + " nothing. AXONFLOW_TELEMETRY must be on and the heartbeat must have fired —"
              + " run.sh sets both.");
    }
    System.out.println(
        "step 9 PASS: no token in "
            + logText.length()
            + " captured log chars (SDK output present) or in any of "
            + collected.size()
            + " telemetry requests");

    // =================================================== 10. OBSERVABLE
    // A fail-closed read the platform leaves no trace of is a read nobody can
    // audit; "it failed closed" is only half the property.
    Process docker =
        new ProcessBuilder("docker", "logs", "--tail", "500", ORCH)
            .redirectErrorStream(true)
            .start();
    String dockerLogs = new String(docker.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (docker.waitFor() != 0 || !dockerLogs.contains("[read-scope]")) {
      fail(
          "step 10: the orchestrator logged no [read-scope] line for the unscoped read in step 4,"
              + " or its logs were unreachable. The read failed closed but left no platform-side"
              + " record of having done so — set AXONFLOW_ORCH_CONTAINER, or run where the stack's"
              + " logs are reachable. An unverified observability claim is not evidence.");
    }
    System.out.println("step 10 PASS: the orchestrator recorded the unscoped read");

    collector.stop(0);
    System.setErr(realErr);
    System.out.println(
        "\nALL PASS: read-path identity verified end to end through the Java SDK runtime");
    System.exit(0);
  }

  static AxonFlow client(String userToken) {
    return AxonFlow.create(
        AxonFlowConfig.builder()
            .endpoint(AGENT_URL)
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .userToken(userToken)
            // Debug is ON deliberately, and step 9 depends on it: with it off,
            // the "the token does not appear in the log" grep runs against a
            // stream containing no SDK output at all — a negative assertion over
            // an empty haystack, true of every string.
            .debug(true)
            .build());
  }

  /**
   * Drive the real /decide plane as a given identity, over raw HTTP.
   *
   * <p>/api/v1/decide is intentionally not SDK-wrapped in this SDK (ADR-056), and it is also the
   * evidence for the "inert on the write path" claim: it is NOT proxied, so the X-User-Token a
   * client stamps is genuinely ignored there and attribution comes from the BODY's user_token.
   */
  static String decideAs(String userToken, int index) throws Exception {
    String body =
        "{\"stage\":\"llm\",\"query\":\"summarize support ticket "
            + index
            + " for run "
            + RUN_TAG
            + "\",\"user_token\":\""
            + userToken
            + "\",\"target\":{\"type\":\"llm\",\"model\":\"gpt-4\",\"provider\":\"openai\"},"
            + "\"context\":{\"x-session-id\":\""
            + RUN_TAG
            + "\",\"x-ai-agent\":\"read-path-identity-e2e\"}}";
    String auth =
        Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + SECRET).getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(AGENT_URL + "/api/v1/decide"))
            .header("Content-Type", "application/json")
            .header("X-Client-ID", CLIENT_ID)
            // ADR-050 §4. The SDK sets this automatically on every request that
            // goes THROUGH it; this raw POST bypasses that, and a driver that
            // exercises the platform should not be a phantom in the platform's
            // own adoption metrics. Measured: without it the agent counts
            // axonflow_client_version_dropped_total{reason="absent"} for each
            // of these writes.
            .header("X-Axonflow-Client", "runtime-e2e-read-path-identity/1")
            .header("Authorization", "Basic " + auth)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      fail("decide HTTP " + response.statusCode() + ": " + response.body());
    }
    String payload = response.body();
    int at = payload.indexOf("\"decision_id\":\"");
    if (at < 0) {
      fail("no decision_id in /decide response: " + payload);
    }
    int start = at + "\"decision_id\":\"".length();
    return payload.substring(start, payload.indexOf('"', start));
  }

  /** Poll until the asynchronous audit write has landed, so a later assertion fails on SCOPE. */
  static void waitForVisible(AxonFlow client, String decisionId) throws Exception {
    long deadline = System.currentTimeMillis() + 45_000;
    while (System.currentTimeMillis() < deadline) {
      try {
        client.explainDecision(decisionId);
        return;
      } catch (RuntimeException ignored) {
        Thread.sleep(2000);
      }
    }
    fail(
        "the decision "
            + decisionId
            + " never became visible to the identity that wrote it within 45s — the audit write"
            + " did not land, so every read assertion below would be about timing, not scope");
  }

  /**
   * The per-user HS256 JWT the platform's own validator requires — the same claim set {@code
   * scripts/generate-jwt.sh --kind user} emits.
   *
   * <p>Minted in-process rather than shelled out to, because the scoping assertions need SEVERAL
   * distinct identities and the setup script's single token is role=admin, which short-circuits to
   * tenant-wide and would make steps 4-8 untestable.
   */
  static String mint(String email, String orgId, String role, long validForSeconds)
      throws Exception {
    long now = System.currentTimeMillis() / 1000;
    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload =
        b64(
            "{\"iss\":\"axonflow-user-token-mint\",\"sub\":\""
                + email
                + "\",\"email\":\""
                + email
                + "\",\"user_id\":\""
                + email
                + "\",\"tenant_id\":\""
                + orgId
                + "\",\"org_id\":\""
                + orgId
                + "\",\"role\":\""
                + role
                + "\",\"region\":\"local\",\"jti\":\""
                + RUN_TAG
                + "-"
                + UUID.randomUUID()
                + "\",\"permissions\":[\"query\",\"llm\",\"mcp_query\"],\"iat\":"
                + (now - 60)
                + ",\"nbf\":"
                + (now - 60)
                + ",\"exp\":"
                + (now + validForSeconds)
                + "}");
    String signing = header + "." + payload;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(signing.getBytes(StandardCharsets.UTF_8)));
    return signing + "." + signature;
  }

  static String b64(String raw) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Writes to both the real stream and a capture buffer, so step 9 can grep what was logged. */
  static final class TeeStream extends java.io.OutputStream {
    private final PrintStream real;
    private final ByteArrayOutputStream capture;

    TeeStream(PrintStream real, ByteArrayOutputStream capture) {
      this.real = real;
      this.capture = capture;
    }

    @Override
    public void write(int b) {
      real.write(b);
      capture.write(b);
    }
  }
}
