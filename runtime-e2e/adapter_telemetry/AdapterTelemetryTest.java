/*
 * Copyright 2026 AxonFlow
 * Licensed under the Business Source License 1.1.
 *
 * runtime-e2e/adapter_telemetry/AdapterTelemetryTest.java
 *
 * Real-wire proof of the adapter registry (axonflow-enterprise#3682).
 *
 * Asserts, through the SDK's real public surface and over real sockets:
 *
 *   1. The SDK's OWN LangGraphAdapter declares itself with no telemetry code
 *      in the application.
 *   2. An unregistered adapter does not appear.
 *   3. A 65-byte name is dropped WHOLE, not truncated, and does not take the
 *      valid name with it.
 *   4. `edition` and `platform_deployment_mode` ride the SAME /health fetch,
 *      and the platform's mode does NOT overwrite the SDK's own topology.
 *   5. A redirect is refused on BOTH legs, each proven with TWO listeners
 *      where the second one records.
 *
 * WHY THERE ARE LISTENERS. The real checkpoint service is PRODUCTION — a
 * runtime proof must not deliver test pings to it. Bytes still flow real ->
 * real through the JDK's HttpServer and the SDK's outbound OkHttpClient; the
 * stand-ins are the two PEERS, exactly as in the neighbouring
 * license_tier_telemetry driver.
 *
 *   java -cp "<sdk-jar>:<deps>" runtime-e2e/adapter_telemetry/AdapterTelemetryTest.java
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.telemetry.TelemetryReporter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AdapterTelemetryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static int failures = 0;

  static void fail(String msg) {
    failures++;
    System.err.println("FAIL: " + msg);
  }

  private static int passes = 0;

  static void pass(String msg) {
    passes++;
    System.out.println("PASS: " + msg);
  }

  static final String HEALTH =
      "{\"status\":\"healthy\",\"version\":\"10.4.0\",\"tier\":\"Enterprise\","
          + "\"edition\":\"enterprise\",\"deployment_mode\":\"in-vpc-enterprise\"}";

  static HttpServer startStandInPlatform(int status, String body, AtomicInteger hits)
      throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/health",
        exchange -> {
          hits.incrementAndGet();
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

  static HttpServer startRedirector(String path, String target, AtomicInteger hits)
      throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        path,
        exchange -> {
          hits.incrementAndGet();
          exchange.getResponseHeaders().add("Location", target);
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.start();
    return server;
  }

  static String baseUrl(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  static String captureOnePing(String platformEndpoint) throws IOException {
    AtomicReference<String> captured = new AtomicReference<>("");
    HttpServer checkpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    checkpoint.createContext(
        "/v1/ping",
        exchange -> {
          try (InputStream is = exchange.getRequestBody()) {
            captured.set(new String(is.readAllBytes(), StandardCharsets.UTF_8));
          }
          byte[] resp = "{\"latest_version\":null}".getBytes(StandardCharsets.UTF_8);
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

  static List<String> featuresOf(String body) throws IOException {
    List<String> out = new ArrayList<>();
    if (body == null || body.isEmpty()) {
      return out;
    }
    JsonNode root = MAPPER.readTree(body);
    JsonNode features = root.get("features");
    if (features == null) {
      return null;
    }
    features.forEach(n -> out.add(n.asText()));
    return out;
  }

  public static void main(String[] args) throws Exception {
    System.out.println("=== MATRIX MODE (stand-in platform + checkpoint) ===\n");

    // --- 1. The shipped adapter declares itself. ---------------------------
    System.out.println("-- 1. the shipped LangGraphAdapter declares itself, no caller telemetry code --");
    // NO registry reset here, and that is deliberate rather than a limitation worked
    // around: resetAdapterRegistryForTest is package-private, which is correct — a test
    // helper must not become public API just so a driver can call it. This JVM starts
    // with an empty registry, so the cases below run in sequence and each asserts on the
    // specific names it cares about rather than on the array being otherwise empty.
    {
      // The REAL public surface, not a registerAdapter call.
      com.getaxonflow.sdk.adapters.LangGraphAdapter.builder(
              com.getaxonflow.sdk.AxonFlow.create(
                  com.getaxonflow.sdk.AxonFlow.builder()
                      .endpoint("http://127.0.0.1:1")
                      .clientId("rt-e2e")
                      .clientSecret("rt-e2e")
                      .build()),
              "rt-e2e")
          .build();

      AtomicInteger hits = new AtomicInteger();
      HttpServer platform = startStandInPlatform(200, HEALTH, hits);
      String body = captureOnePing(baseUrl(platform));
      platform.stop(0);
      List<String> f = featuresOf(body);
      if (body.isEmpty()) {
        fail("no ping captured");
      } else if (f == null) {
        fail("`features` key absent from the wire; body: " + body);
      } else if (!f.contains("adapter:langgraph")) {
        fail("features = " + f + "; constructing LangGraphAdapter must declare adapter:langgraph");
      } else {
        pass("constructing the adapter alone put features = " + f);
      }
    }

    // --- 2. An unregistered adapter does not appear. -----------------------
    System.out.println("\n-- 2. an unregistered adapter does not appear --");
    {
      AtomicInteger hits = new AtomicInteger();
      HttpServer platform = startStandInPlatform(200, HEALTH, hits);
      String body = captureOnePing(baseUrl(platform));
      platform.stop(0);
      List<String> f = featuresOf(body);
      if (f == null) {
        fail("`features` absent, so this case cannot distinguish absence from a failed run");
      } else if (f.contains("adapter:langchain")) {
        fail("features = " + f + " contains an adapter nothing registered");
      } else if (!f.contains("adapter:langgraph")) {
        fail("features = " + f + " lost the registered adapter — the absence check is vacuous");
      } else {
        pass("features = " + f + ": what was declared and nothing else");
      }
    }

    // --- 3. A 65-byte name is dropped WHOLE. -------------------------------
    System.out.println("\n-- 3. a 65-byte adapter name is dropped whole, not truncated --");
    {
      TelemetryReporter.registerAdapter("a".repeat(65));
      TelemetryReporter.registerAdapter("langchain");
      AtomicInteger hits = new AtomicInteger();
      HttpServer platform = startStandInPlatform(200, HEALTH, hits);
      String body = captureOnePing(baseUrl(platform));
      platform.stop(0);
      List<String> f = featuresOf(body);
      if (f == null) {
        fail("`features` absent; body: " + body);
      } else if (f.contains("adapter:" + "a".repeat(65))) {
        fail("the 65-byte name reached the wire in full");
      } else if (f.contains("adapter:" + "a".repeat(64))) {
        fail("the 65-byte name was TRUNCATED to 64 and sent — a name nothing is running");
      } else if (!f.contains("adapter:langchain")) {
        fail("features = " + f + " lost the VALID name too");
      } else {
        pass("features = " + f + ": over-cap dropped whole, the valid one kept");
      }
    }

    // --- 4. The relay rides ONE /health, and does not overwrite topology. --
    System.out.println("\n-- 4. edition and platform_deployment_mode ride the SAME /health fetch --");
    {
      AtomicInteger hits = new AtomicInteger();
      HttpServer platform = startStandInPlatform(200, HEALTH, hits);
      String body = captureOnePing(baseUrl(platform));
      platform.stop(0);
      JsonNode root = MAPPER.readTree(body);
      String edition = root.has("edition") ? root.get("edition").asText() : null;
      String pdm =
          root.has("platform_deployment_mode")
              ? root.get("platform_deployment_mode").asText()
              : null;
      String topology = root.get("deployment_mode").asText();
      if (!"enterprise".equals(edition)) {
        fail("edition = " + edition + ", want \"enterprise\"");
      } else if (!"in-vpc-enterprise".equals(pdm)) {
        fail("platform_deployment_mode = " + pdm);
      } else if ("in-vpc-enterprise".equals(topology)) {
        fail("the platform's mode OVERWROTE the SDK's own deployment_mode topology field");
      } else if (hits.get() != 1) {
        fail("/health was fetched " + hits.get() + " times; every dimension must ride ONE fetch");
      } else {
        pass(
            "edition=" + edition + " platform_deployment_mode=" + pdm + " from " + hits.get()
                + " /health fetch; SDK topology still " + topology);
      }
    }

    // --- 5. Redirects refused on BOTH legs, two listeners each. ------------
    System.out.println("\n-- 5. redirects are refused on both telemetry legs --");
    {
      AtomicInteger targetHits = new AtomicInteger();
      HttpServer target =
          startStandInPlatform(
              200, "{\"version\":\"6.6.6-REDIRECT-TARGET\",\"tier\":\"Plus\"}", targetHits);
      AtomicInteger redirectorHits = new AtomicInteger();
      HttpServer redirector =
          startRedirector("/health", baseUrl(target) + "/health", redirectorHits);

      String body = captureOnePing(baseUrl(redirector));
      redirector.stop(0);
      target.stop(0);

      if (body.isEmpty()) {
        fail("health redirect: no ping captured; it must still be DELIVERED, only unenriched");
      } else if (redirectorHits.get() == 0) {
        // POSITIVE CONTROL: the first listener was actually asked. Without it, "the
        // target saw nothing" is equally true of a run that never happened.
        fail("health redirect: the redirector was never contacted, so nothing below proves anything");
      } else if (targetHits.get() != 0) {
        fail("health redirect: the TARGET was fetched " + targetHits.get()
            + " times — the 30x was followed");
      } else if (body.contains("6.6.6-REDIRECT-TARGET")) {
        fail("health redirect: the target's version reached the wire");
      } else {
        pass("health 302 refused: redirector hit " + redirectorHits.get()
            + ", target hit 0, ping still delivered");
      }
    }

    {
      AtomicInteger targetHits = new AtomicInteger();
      HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      target.createContext(
          "/v1/ping",
          exchange -> {
            targetHits.incrementAndGet();
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(resp);
            }
          });
      target.start();
      AtomicInteger redirectorHits = new AtomicInteger();
      HttpServer redirector =
          startRedirector("/v1/ping", baseUrl(target) + "/v1/ping", redirectorHits);

      boolean delivered =
          TelemetryReporter.sendPingNow(
              "production", "", false, baseUrl(redirector) + "/v1/ping");
      redirector.stop(0);
      target.stop(0);

      if (redirectorHits.get() == 0) {
        fail("checkpoint redirect: the redirector was never contacted");
      } else if (targetHits.get() != 0) {
        fail("checkpoint redirect: the TARGET received " + targetHits.get()
            + " request(s). OkHttp turns a redirected POST into a bodyless GET, so a followed"
            + " redirect reports DELIVERY for a ping never sent and the stamp advances on it");
      } else if (delivered) {
        fail("checkpoint redirect: sendPingNow reported delivery for a refused redirect");
      } else {
        pass("checkpoint 302 refused: redirector hit " + redirectorHits.get()
            + ", target hit 0, delivered=false");
      }
    }

    if (failures > 0) {
      System.err.println("\n" + failures + " assertion(s) FAILED");
      System.exit(1);
    }

    // A PASS-COUNT FLOOR. "No failures" is also true of a driver that asserted
    // NOTHING — a case silently skipped, a listener that never started, an early
    // return. The floor is the number of `pass(...)` sites below, so a case that
    // stops running fails loudly instead of reading as green.
    final int expectedPasses = 6;
    if (passes != expectedPasses) {
      System.err.printf(
          "%nFAIL: %d assertions passed, expected %d. A case stopped running — "
              + "zero failures is not the same as everything having been checked.%n",
          passes, expectedPasses);
      System.exit(1);
    }

    System.out.printf("%nAll %d assertions passed.%n", passes);
  }
}
