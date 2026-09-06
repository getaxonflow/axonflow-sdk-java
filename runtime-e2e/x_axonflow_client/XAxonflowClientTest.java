/*
 * runtime-e2e/x_axonflow_client/XAxonflowClientTest.java
 *
 * Real-wire proof that THIS build's version is the one the live platform
 * expects, and that it is what the SDK puts on the wire.
 *
 * A release bump changes exactly one product fact: the version in pom.xml, which
 * the packaged JAR exposes as AxonFlowConfig.SDK_VERSION (read from Maven's
 * pom.properties at runtime; "unknown" when the class is not loaded from a
 * packaged JAR) and which every governed request carries as
 * `X-Axonflow-Client: sdk-java/<version>` (ADR-050 §4). The platform's /health
 * names the version it recommends per SDK under
 * `sdk_compatibility.recommended_sdk_version`. A bump PR that forgot pom.xml, a
 * tag cut against the wrong pom, or a JAR packaged without pom.properties ships
 * an SDK the platform reports as behind - or one that calls itself
 * `sdk-java/unknown`. Those are the defects a runtime leg on a release PR can
 * catch, and nothing else in this repository's CI can see the platform.
 *
 * TWO REAL ENDPOINTS, nothing mocked:
 *
 *   1. THE LIVE PLATFORM. GET /health on the real agent (default
 *      https://try.getaxonflow.com, the hosted sandbox; read-only) and read
 *      `sdk_compatibility.recommended_sdk_version.java` and `min_sdk_version.java`.
 *      SDK_VERSION must EQUAL the recommended version and not be below the minimum.
 *
 *   2. THE WIRE. A real com.sun.net.httpserver.HttpServer on 127.0.0.1 receives ONE
 *      governed request from a real AxonFlow.create(...) client in this process and
 *      records the X-Axonflow-Client header exactly as sent. It must be
 *      `sdk-java/<SDK_VERSION>`. What the SDK makes of the minimal reply is not the
 *      subject - the header on the request is.
 *
 * Run (the JAR must be the PACKAGED one, or SDK_VERSION reads "unknown" and the
 * first assertion fails - which is the point):
 *   ./mvnw -q -DskipTests package
 *   ./mvnw -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
 *   java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" runtime-e2e/x_axonflow_client/XAxonflowClientTest.java
 *
 * Optional: AXONFLOW_E2E_PLATFORM_ENDPOINT=http://localhost:8080
 *
 * Exits 0 only when every assertion holds; every finding is printed.
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class XAxonflowClientTest {
  static int failures = 0;

  static void check(String name, String problem) {
    if (problem != null) {
      System.out.println("FAIL  " + name + ": " + problem);
      failures++;
    } else {
      System.out.println("ok    " + name);
    }
  }

  /** a < b for dotted numeric versions; unparsable parts compare as strings. */
  static boolean semverLess(String a, String b) {
    String[] as = a.split("\\."), bs = b.split("\\.");
    for (int i = 0; i < as.length && i < bs.length; i++) {
      try {
        int ai = Integer.parseInt(as[i]), bi = Integer.parseInt(bs[i]);
        if (ai != bi) return ai < bi;
      } catch (NumberFormatException e) {
        return a.compareTo(b) < 0;
      }
    }
    return as.length < bs.length;
  }

  public static void main(String[] args) throws Exception {
    String platform = System.getenv("AXONFLOW_E2E_PLATFORM_ENDPOINT");
    if (platform == null || platform.isEmpty()) platform = "https://try.getaxonflow.com";
    String built = AxonFlowConfig.SDK_VERSION;
    System.out.println("built AxonFlowConfig.SDK_VERSION = " + built);
    System.out.println("live platform                    = " + platform);

    // -- 1. the live platform's expectation of this SDK ----------------------
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    HttpResponse<String> resp =
        http.send(
            HttpRequest.newBuilder(URI.create(platform.replaceAll("/+$", "") + "/health"))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      check("the live platform answers /health", "HTTP " + resp.statusCode() + ": " + resp.body());
      System.exit(1);
    }
    JsonNode health = new ObjectMapper().readTree(resp.body());
    check("the live platform answers /health", null);
    JsonNode compat = health.path("sdk_compatibility");
    String recommended = compat.path("recommended_sdk_version").path("java").asText("");
    String minimum = compat.path("min_sdk_version").path("java").asText("");
    System.out.println(
        "platform version "
            + health.path("version").asText("?")
            + "; recommended sdk-java \""
            + recommended
            + "\"; minimum sdk-java \""
            + minimum
            + "\"");

    if ("unknown".equals(built) || built.isEmpty()) {
      check(
          "the packaged JAR knows its own version",
          "SDK_VERSION is \"" + built + "\": the class was not loaded from a packaged JAR carrying pom.properties, so every request would say sdk-java/unknown");
    } else {
      check("the packaged JAR knows its own version", null);
    }
    if (recommended.isEmpty()) {
      check("the platform publishes a recommended sdk-java version", "no \"java\" entry under sdk_compatibility.recommended_sdk_version: " + resp.body());
    } else if (!built.equals(recommended)) {
      check(
          "the built version equals the version the live platform recommends",
          "pom.xml/JAR says " + built + ", the platform recommends " + recommended + " - the bump and the platform disagree");
    } else {
      check("the built version equals the version the live platform recommends", null);
    }
    if (!minimum.isEmpty() && semverLess(built, minimum)) {
      check("the built version is not below the platform's minimum", built + " < " + minimum);
    } else {
      check("the built version is not below the platform's minimum", null);
    }

    // -- 2. the header this build puts on the wire ---------------------------
    AtomicReference<String> clientHeader = new AtomicReference<>();
    AtomicReference<String> userAgent = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();
    HttpServer receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    receiver.createContext(
        "/",
        exchange -> {
          clientHeader.compareAndSet(null, exchange.getRequestHeaders().getFirst("X-Axonflow-Client"));
          userAgent.compareAndSet(null, exchange.getRequestHeaders().getFirst("User-Agent"));
          path.compareAndSet(null, exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
          byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    receiver.start();
    try {
      String endpoint = "http://127.0.0.1:" + receiver.getAddress().getPort();
      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .endpoint(endpoint)
                  .clientId("e2e-x-axonflow-client")
                  .clientSecret("e2e-secret")
                  .build());
      try {
        // A governed request; the receiver records it, and what the SDK makes
        // of the minimal reply is not the subject.
        client.mcpCheckInput("postgres", "SELECT 1");
      } catch (RuntimeException ignored) {
        // expected: the reply is not a real check-input document
      }
    } finally {
      receiver.stop(0);
    }

    if (path.get() == null) {
      check("the SDK sent a governed request to the receiver", "no request arrived");
      System.exit(1);
    }
    check("the SDK sent a governed request to the receiver", null);
    System.out.println(
        "wire: " + path.get() + "  X-Axonflow-Client=\"" + clientHeader.get() + "\"  User-Agent=\"" + userAgent.get() + "\"");
    String want = "sdk-java/" + built;
    if (!want.equals(clientHeader.get())) {
      check("X-Axonflow-Client carries this build's version", "got \"" + clientHeader.get() + "\", want \"" + want + "\"");
    } else {
      check("X-Axonflow-Client carries this build's version", null);
    }

    System.out.println();
    if (failures > 0) {
      System.out.println(failures + " check(s) failed");
      System.exit(1);
    }
    System.out.println("PASS: sdk-java/" + built + " is what the live platform recommends and what this build sends.");
  }
}
