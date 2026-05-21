/*
 * runtime-e2e/v91_org_id_telemetry/V91OrgIdTelemetryTest.java
 *
 * Real-wire test of the SDK's v9.1 org_id telemetry field (#2277).
 *
 * Spins up a tiny in-process HttpServer that pretends to be the
 * checkpoint receiver, inspects the raw JSON body for the org_id
 * field, and exits with the verdict. Bytes flow real → real through
 * the JDK's HttpServer + the SDK's outbound OkHttpClient.
 *
 * Run:
 *   # ORG_ID set — operator-supplied or cs_<uuid>:
 *   ORG_ID=acme-corp java -cp "<sdk-jar>:<deps>" \
 *     runtime-e2e/v91_org_id_telemetry/V91OrgIdTelemetryTest.java
 *
 *   # ORG_ID unset — sentinel:
 *   unset ORG_ID && java -cp "<sdk-jar>:<deps>" \
 *     runtime-e2e/v91_org_id_telemetry/V91OrgIdTelemetryTest.java
 *
 * Sister coverage runs in CI via TelemetryReporterTest's
 * functional-E2E test (uses WireMock — equivalent shape).
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.telemetry.TelemetryReporter;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class V91OrgIdTelemetryTest {
  public static void main(String[] args) throws Exception {
    String orgIdEnv = System.getenv("ORG_ID");
    String expected = (orgIdEnv == null || orgIdEnv.isEmpty()) ? "local-dev-org" : orgIdEnv;

    AtomicReference<String> captured = new AtomicReference<>("");

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/ping", exchange -> {
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        exchange.getRequestBody().transferTo(bos);
        captured.set(bos.toString(StandardCharsets.UTF_8));
      }
      byte[] resp = "{\"latest_version\":null,\"alerts\":[]}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, resp.length);
      exchange.getResponseBody().write(resp);
      exchange.close();
    });
    server.createContext("/health", exchange -> {
      byte[] resp = "{\"version\":\"8.0.0-runtime-e2e\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, resp.length);
      exchange.getResponseBody().write(resp);
      exchange.close();
    });
    server.start();

    int port = server.getAddress().getPort();
    String checkpoint = "http://127.0.0.1:" + port + "/v1/ping";
    String agent = "http://127.0.0.1:" + port;

    System.out.println("Asserting wire org_id = " + expected);

    TelemetryReporter.sendPing("production", agent, false, null, checkpoint);
    Thread.sleep(2000);

    server.stop(0);

    String body = captured.get();
    if (body.isEmpty()) {
      System.err.println("FAIL: no telemetry body captured");
      System.exit(1);
    }

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(body);
    String got = root.has("org_id") ? root.get("org_id").asText() : "<MISSING>";

    if (!expected.equals(got)) {
      System.err.println("FAIL: wire org_id = \"" + got + "\", want \"" + expected + "\"");
      System.err.println("Body: " + body);
      System.exit(1);
    }
    System.out.println("PASS: telemetry wire payload carries org_id=\"" + got + "\" (expected=\"" + expected + "\")");
    System.out.println("Wire body: " + body);
  }
}
