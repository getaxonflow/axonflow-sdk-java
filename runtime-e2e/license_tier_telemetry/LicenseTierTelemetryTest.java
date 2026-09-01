/*
 * runtime-e2e/license_tier_telemetry/LicenseTierTelemetryTest.java
 *
 * Real-wire proof of the SDK's license_tier telemetry field (#3619).
 *
 * Runs real JDK HttpServer listeners on both sides of the telemetry path: a
 * stand-in platform serving /health, and a stand-in checkpoint receiver
 * capturing the outgoing POST. Bytes flow real -> real through the JDK's
 * HttpServer and the SDK's outbound OkHttpClient. Nothing is mocked.
 *
 * TWO MODES:
 *
 *   # 1. MATRIX (default) — every tier value and every fail-open path.
 *   java -cp "<sdk-jar>:<deps>" \
 *     runtime-e2e/license_tier_telemetry/LicenseTierTelemetryTest.java
 *
 *   # 2. REAL PLATFORM — drive the SDK at a live agent and cross-check the
 *   #    wire value against that agent's own /health.
 *   AXONFLOW_E2E_PLATFORM_ENDPOINT=http://localhost:8080 \
 *     java -cp "<sdk-jar>:<deps>" \
 *       runtime-e2e/license_tier_telemetry/LicenseTierTelemetryTest.java
 *
 * Mode 2 proves the contract end to end: it reads the tier from the live
 * platform independently, then asserts the SDK put THAT value on the wire
 * verbatim. If the endpoint is unreachable it asserts the platform-DOWN
 * contract instead — ping still delivered, field omitted.
 *
 * Mutation proof: drop the `if (licenseTier != null)` guard in buildPayload
 * (write the key unconditionally) and case 2 fails with "license_tier
 * present". Delete the root.put("license_tier", ...) call and case 1 fails
 * with "license_tier absent from wire".
 *
 * Sister CI coverage: TelemetryLicenseTierTest (26 tests).
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.telemetry.TelemetryReporter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class LicenseTierTelemetryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static int failures = 0;

  static void fail(String msg) {
    failures++;
    System.err.println("FAIL: " + msg);
  }

  static void pass(String msg) {
    System.out.println("PASS: " + msg);
  }

  /** A stand-in platform whose /health returns a fixed status and raw body. */
  static HttpServer startStandInPlatform(int status, String body) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/health",
        exchange -> {
          byte[] payload = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, payload.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
          }
        });
    server.start();
    return server;
  }

  static String baseUrl(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Run one real ping against platformEndpoint; return the raw wire body. */
  static String captureOnePing(String platformEndpoint) throws IOException {
    AtomicReference<String> captured = new AtomicReference<>("");

    HttpServer checkpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    checkpoint.createContext(
        "/v1/ping",
        exchange -> {
          try (InputStream is = exchange.getRequestBody()) {
            captured.set(new String(is.readAllBytes(), StandardCharsets.UTF_8));
          }
          byte[] resp = "{\"latest_version\":null,\"alerts\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });
    checkpoint.start();

    try {
      TelemetryReporter.sendPingNow(
          "production", platformEndpoint, false, baseUrl(checkpoint) + "/v1/ping");
    } finally {
      checkpoint.stop(0);
    }
    return captured.get();
  }

  /** Returns {present, value}; value is null when absent. */
  static String[] tierOnWire(String body) throws IOException {
    JsonNode payload = MAPPER.readTree(body);
    if (!payload.has("license_tier")) {
      return new String[] {"false", null};
    }
    return new String[] {"true", payload.get("license_tier").asText()};
  }

  /** A port with nothing listening on it. */
  static String deadEndpoint() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return "http://127.0.0.1:" + socket.getLocalPort();
    }
  }

  public static void main(String[] args) throws Exception {
    String realEndpoint = System.getenv("AXONFLOW_E2E_PLATFORM_ENDPOINT");
    if (realEndpoint != null && !realEndpoint.isEmpty()) {
      runAgainstRealPlatform(realEndpoint);
    } else {
      runMatrix();
    }

    if (failures > 0) {
      System.err.printf("%n%d assertion(s) FAILED%n", failures);
      System.exit(1);
    }
    System.out.println("\nAll assertions passed.");
  }

  static void runAgainstRealPlatform(String endpoint) throws Exception {
    System.out.printf("=== REAL PLATFORM MODE: %s ===%n%n", endpoint);

    String rawHealth;
    try {
      HttpURLConnection conn =
          (HttpURLConnection) URI.create(endpoint + "/health").toURL().openConnection();
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      // getInputStream() throws IOException for ANY non-2xx, so a live platform
      // answering 503 was reported as "unreachable". This does NOT change the
      // classification — non-2xx already reached the DOWN branch, which is the
      // right contract for it — it changes the DIAGNOSIS, so the operator is
      // told the platform answered 503 rather than that it could not be
      // reached. It does fix one real case: a 3xx (HttpURLConnection does not
      // auto-follow) previously yielded an empty body and an NPE-shaped read
      // downstream; it now reaches the DOWN branch cleanly.
      int status = conn.getResponseCode();
      if (status < 200 || status >= 300) {
        throw new IOException("platform answered HTTP " + status);
      }
      try (InputStream is = conn.getInputStream()) {
        rawHealth = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (IOException err) {
      // Platform DOWN — a first-class real-world case, not a harness error.
      System.out.printf(
          "No usable /health at %s (%s)%n  -> asserting the DOWN contract instead.%n%n",
          endpoint, err);
      String body = captureOnePing(endpoint);
      if (body.isEmpty()) {
        fail("platform down: the ping was SUPPRESSED — telemetry must degrade, not stop");
        return;
      }
      System.out.printf("Telemetry wire body: %s%n%n", body);
      String[] tier = tierOnWire(body);
      if ("true".equals(tier[0])) {
        fail("platform down: license_tier present as \"" + tier[1] + "\" — must be omitted");
        return;
      }
      pass("platform down: ping still delivered, license_tier omitted (not defaulted)");
      return;
    }

    JsonNode health = MAPPER.readTree(rawHealth);
    String liveTier = health.has("tier") ? health.get("tier").asText() : "";
    System.out.printf("Live /health tier: \"%s\"%n%n", liveTier);
    if (liveTier.isEmpty()) {
      // A platform predating the `tier` field is a LEGITIMATE contract case,
      // not a harness error: the SDK must degrade to omission. Assert that
      // instead of failing the run.
      System.out.println("Live platform reports no tier -> asserting the omission contract.\n");
      String noTierBody = captureOnePing(endpoint);
      if (noTierBody.isEmpty()) {
        fail("the ping was SUPPRESSED — telemetry must degrade, not stop");
        return;
      }
      System.out.printf("Telemetry wire body: %s%n%n", noTierBody);
      if ("true".equals(tierOnWire(noTierBody)[0])) {
        fail("license_tier present though the live platform reported none");
        return;
      }
      pass("platform reports no tier: ping delivered, license_tier omitted (not defaulted)");
      return;
    }

    String body = captureOnePing(endpoint);
    if (body.isEmpty()) {
      fail("no telemetry ping captured against the live platform");
      return;
    }
    System.out.printf("Telemetry wire body: %s%n%n", body);

    String[] tier = tierOnWire(body);
    if (!"true".equals(tier[0])) {
      fail("license_tier absent from wire; the live platform reported tier=\"" + liveTier + "\"");
    } else if (!tier[1].equals(liveTier)) {
      fail("license_tier on wire = \"" + tier[1] + "\", live platform said \"" + liveTier + "\"");
    } else {
      pass(
          "license_tier=\""
              + tier[1]
              + "\" on the wire matches the live platform's own /health verbatim");
    }
  }

  static void runMatrix() throws Exception {
    System.out.println("=== MATRIX MODE (stand-in platform) ===\n");

    System.out.println("-- 1. verbatim round-trip of every platform-emitted tier --");
    for (String tier : new String[] {"community", "evaluation", "Enterprise", "Plus", "starting"}) {
      HttpServer platform =
          startStandInPlatform(
              200, "{\"status\":\"healthy\",\"version\":\"10.3.0\",\"tier\":\"" + tier + "\"}");
      String body;
      try {
        body = captureOnePing(baseUrl(platform));
      } finally {
        platform.stop(0);
      }

      if (body.isEmpty()) {
        fail("tier=" + tier + ": no ping captured");
        continue;
      }
      String[] got = tierOnWire(body);
      if (!"true".equals(got[0])) {
        fail("tier=" + tier + ": license_tier absent from wire; body: " + body);
      } else if (!got[1].equals(tier)) {
        fail("tier=" + tier + ": license_tier on wire = \"" + got[1] + "\", want verbatim");
      } else {
        System.out.printf("PASS: tier=%-13s forwarded verbatim%n", "\"" + got[1] + "\"");
      }
    }

    System.out.println("\n-- 2. fail-open paths: field omitted, ping still delivered --");
    checkOmitted("endpoint not configured", captureOnePing(""));
    checkOmitted("platform unreachable", captureOnePing(deadEndpoint()));

    String[][] specs = {
      {"health returns 500", "500", "{\"tier\":\"Enterprise\"}"},
      {"health returns malformed JSON", "200", "{\"tier\":\"Enterprise\""},
      {"health has no tier key", "200", "{\"status\":\"healthy\",\"version\":\"10.3.0\"}"},
      {"health has an empty tier", "200", "{\"version\":\"10.3.0\",\"tier\":\"\"}"},
      {"health has a numeric tier", "200", "{\"version\":\"10.3.0\",\"tier\":42}"},
      {"health has a null tier", "200", "{\"version\":\"10.3.0\",\"tier\":null}"},
    };
    for (String[] spec : specs) {
      HttpServer platform = startStandInPlatform(Integer.parseInt(spec[1]), spec[2]);
      try {
        checkOmitted(spec[0], captureOnePing(baseUrl(platform)));
      } finally {
        platform.stop(0);
      }
    }

    System.out.println("\n-- 3. deployment_mode is independent of the tier --");
    String[][] modeSpecs = {
      {"with tier", "{\"version\":\"10.3.0\",\"tier\":\"Enterprise\"}"},
      {"without tier", "{\"version\":\"10.3.0\"}"},
    };
    for (String[] spec : modeSpecs) {
      HttpServer platform = startStandInPlatform(200, spec[1]);
      String body;
      try {
        body = captureOnePing(baseUrl(platform));
      } finally {
        platform.stop(0);
      }
      String mode = MAPPER.readTree(body).get("deployment_mode").asText();
      if (!"self_hosted".equals(mode)) {
        fail(spec[0] + ": deployment_mode = \"" + mode + "\", want \"self_hosted\"");
        continue;
      }
      System.out.printf("PASS: %-14s deployment_mode=\"%s\" unchanged%n", spec[0], mode);
    }
  }

  static void checkOmitted(String name, String body) throws IOException {
    if (body.isEmpty()) {
      fail(name + ": the ping was SUPPRESSED — telemetry must degrade, not stop");
      return;
    }
    if (!body.contains("\"telemetry_type\":\"sdk\"")) {
      fail(name + ": ping body is not a well-formed sdk ping: " + body);
      return;
    }
    String[] tier = tierOnWire(body);
    if ("true".equals(tier[0])) {
      fail(name + ": license_tier present as \"" + tier[1] + "\" — must be omitted when not learned");
      return;
    }
    System.out.printf("PASS: %-32s ping delivered, license_tier omitted%n", name);
  }
}
