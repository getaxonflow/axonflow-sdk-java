/*
 * Copyright 2026 AxonFlow
 * Licensed under the Business Source License 1.1.
 */
package com.getaxonflow.sdk.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Adapter registry, relay caps and redirect refusal (axonflow-enterprise#3682 items 1-2).
 *
 * <p>WHAT THESE TESTS CAN AND CANNOT VARY. The HTTP cases run against real WireMock servers on
 * loopback and drive the SDK's own OkHttp calls, so the redirect axis is varied end to end —
 * which matters here more than in Python, because OkHttp FOLLOWS redirects by default and this is a
 * live defect rather than a pin. The redirect cases use TWO servers, and the second one records.
 *
 * <p>They CANNOT vary two axes, stated rather than left implied:
 *
 * <ul>
 *   <li>The RECEIVER: {@code NormalizeAdapterFeature} folds an unrecognised adapter name into
 *       {@code adapter:unknown} at READ time, in another repo, and is asserted there.
 *   <li>The SCHEME: both servers are local {@code http}, so an {@code https -> http} downgrade is
 *       not exercised. It does not apply to this path (no credential is sent) but a future change
 *       adding one would not be caught by these fixtures.
 * </ul>
 */
@WireMockTest
class AdapterRegistryTest {

  // NO local @BeforeEach reset here: AdapterRegistryIsolationExtension does it globally,
  // and a second copy would be a duplicated decision that can drift from the one every
  // other test relies on.

  private static List<String> featuresOf(String payload) throws Exception {
    JsonNode root = new ObjectMapper().readTree(payload);
    JsonNode features = root.get("features");
    assertThat(features).as("the features key must always be present").isNotNull();
    java.util.List<String> out = new java.util.ArrayList<>();
    features.forEach(n -> out.add(n.asText()));
    return out;
  }

  // -------------------------------------------------------------------------
  // Item 1 — the registry
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("features is present and empty by default — the positive control for every absence")
  void featuresPresentAndEmptyByDefault() throws Exception {
    // "features did not contain adapter:x" is only evidence if the field exists at all.
    // An absent key and an empty array are different facts.
    assertThat(featuresOf(TelemetryReporter.buildPayload("production", null))).isEmpty();
  }

  @Test
  @DisplayName("a registered adapter reaches the payload")
  void registeredAdapterReachesThePayload() throws Exception {
    TelemetryReporter.registerAdapter("langchain");
    assertThat(featuresOf(TelemetryReporter.buildPayload("production", null)))
        .containsExactly("adapter:langchain");
  }

  @Test
  @DisplayName("an unregistered adapter does not")
  void unregisteredAdapterDoesNot() throws Exception {
    TelemetryReporter.registerAdapter("langchain");
    List<String> features = featuresOf(TelemetryReporter.buildPayload("production", null));
    assertThat(features).doesNotContain("adapter:langgraph");
    // Without this the assertion above is satisfied by an empty array.
    assertThat(features).containsExactly("adapter:langchain");
  }

  @Test
  @DisplayName("names are lowercased, trimmed, deduplicated and sorted — and NOT filtered")
  void normalisation() {
    TelemetryReporter.registerAdapter("LangChain");
    TelemetryReporter.registerAdapter("  langchain\t\n");
    TelemetryReporter.registerAdapter("LANGCHAIN");
    // Registration order must not change the bytes.
    TelemetryReporter.registerAdapter("langgraph");
    // An unrecognised name is NOT dropped: an SDK-side allowlist would be a second
    // vocabulary that drifts from the receiver's.
    TelemetryReporter.registerAdapter("some-framework-we-have-never-heard-of");

    assertThat(TelemetryReporter.registeredFeatures())
        .containsExactly(
            "adapter:langchain",
            "adapter:langgraph",
            "adapter:some-framework-we-have-never-heard-of");
  }

  @Test
  @DisplayName("an unusable name is refused silently rather than thrown")
  void unusableNamesRefused() {
    // A fire-and-forget telemetry declaration must never fail a caller's startup.
    for (String bad : Arrays.asList("", "   ", "\t\n", null)) {
      TelemetryReporter.registerAdapter(bad);
    }
    assertThat(TelemetryReporter.registeredFeatures()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Item 2 — the caps
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("64 bytes is kept, 65 is DROPPED WHOLE")
  void adapterNameCapDropsWhole() {
    TelemetryReporter.registerAdapter("a".repeat(64));
    assertThat(TelemetryReporter.registeredFeatures()).containsExactly("adapter:" + "a".repeat(64));

    TelemetryReporter.resetAdapterRegistryForTest();
    TelemetryReporter.registerAdapter("a".repeat(65));
    assertThat(TelemetryReporter.registeredFeatures())
        .as("a truncated adapter name is a name nothing is running")
        .isEmpty();
  }

  @Test
  @DisplayName("the cap counts BYTES, not UTF-16 code units")
  void capCountsBytes() {
    // U+1F600 is length 2 in Java (a surrogate pair), ONE code point, FOUR bytes.
    // 20 of them are 40 code units and 80 bytes — under the cap by String.length(),
    // over it by bytes. A cap written with length() admits this.
    String name = "😀".repeat(20);
    // Literals for the same reason as MAX_FEATURES above: comparing against the constant
    // moves both sides under a mutant.
    assertThat(TelemetryReporter.MAX_RELAYED_VALUE_BYTES).isEqualTo(64);
    assertThat(name.length()).isLessThanOrEqualTo(64);
    assertThat(name.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(64);

    TelemetryReporter.registerAdapter(name);
    assertThat(TelemetryReporter.registeredFeatures()).isEmpty();
  }

  @Test
  @DisplayName("the features array is bounded to 32 entries, deterministically")
  void featuresArrayBounded() {
    for (int i = 0; i < 40; i++) {
      TelemetryReporter.registerAdapter(String.format("%02d", i));
    }
    List<String> features = TelemetryReporter.registeredFeatures();
    // THE LITERAL 32, NOT TelemetryReporter.MAX_FEATURES.
    //
    // Asserting against the constant is a tautology: mutating MAX_FEATURES to 33 moves
    // BOTH sides of the comparison, so the mutant survives and the cap is unpinned. This
    // survived the first mutation run for exactly that reason. The number is part of the
    // contract with the receiver (checkpoint-service MaxFeatures), so it is written out.
    assertThat(features).hasSize(32);
    assertThat(TelemetryReporter.MAX_FEATURES)
        .as("the constant and the receiver's MaxFeatures must agree")
        .isEqualTo(32);
    // Sorted-then-truncated, so "which 32 survive" is a defined answer rather than a
    // set-iteration accident.
    assertThat(features.get(0)).isEqualTo("adapter:00");
    assertThat(features.get(31)).isEqualTo("adapter:31");
  }

  @Test
  @DisplayName("boundFeatures drops an over-long entry whole — tested directly, and why")
  void boundFeaturesDropsOverlong() {
    // registerAdapter already refuses a name over 64 bytes, so the longest entry it can
    // emit is "adapter:".length() + 64 = 72 — well under 128. A test driven through the
    // registry could not express this defect and would read as disproof of a bound that
    // was never exercised.
    assertThat("adapter:".length() + TelemetryReporter.MAX_RELAYED_VALUE_BYTES)
        .isLessThanOrEqualTo(TelemetryReporter.MAX_FEATURE_BYTES);

    assertThat(TelemetryReporter.MAX_FEATURE_BYTES)
        .as("the constant and the receiver's MaxFeatureBytes must agree")
        .isEqualTo(128);
    String within = "adapter:" + "b".repeat(128 - "adapter:".length());
    String over = within + "b";
    assertThat(TelemetryReporter.boundFeatures(Arrays.asList(within, over))).containsExactly(within);
  }

  // -------------------------------------------------------------------------
  // Item 2 (relay) — edition and platform_deployment_mode
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("every relayed dimension rides ONE /health fetch")
  void relayRidesOneFetch(WireMockRuntimeInfo wm) {
    stubFor(
        get("/health")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"status\":\"healthy\",\"version\":\"10.4.0\",\"tier\":\"Enterprise\","
                            + "\"edition\":\"enterprise\",\"deployment_mode\":\"in-vpc-enterprise\"}")));

    TelemetryReporter.PlatformHealthProbe probe =
        TelemetryReporter.probePlatformHealth(wm.getHttpBaseUrl(), 2000);

    assertThat(probe.platformVersion).isEqualTo("10.4.0");
    assertThat(probe.licenseTier).isEqualTo("Enterprise");
    assertThat(probe.edition).isEqualTo("enterprise");
    assertThat(probe.platformDeploymentMode).isEqualTo("in-vpc-enterprise");
    // The COUNT is what makes "no new request" a measurement rather than a claim.
    assertThat(WireMock.findAll(getRequestedFor(urlEqualTo("/health"))))
        .as("every relayed dimension must ride ONE /health fetch")
        .hasSize(1);
  }

  @Test
  @DisplayName("the platform's mode never overwrites the SDK's own deployment_mode")
  void platformModeNeverOverwritesTopology() throws Exception {
    // The trap this contract is most likely to be got wrong on: /health's member is
    // `deployment_mode` (the platform describing itself); the ping's `deployment_mode` is
    // the TOPOLOGY this SDK derives from the endpoint URL.
    String payload =
        TelemetryReporter.buildPayload(
            "production",
            "10.4.0",
            "localhost",
            "self_hosted",
            "Enterprise",
            "enterprise",
            "in-vpc-enterprise");
    JsonNode root = new ObjectMapper().readTree(payload);
    assertThat(root.get("deployment_mode").asText()).isEqualTo("self_hosted");
    assertThat(root.get("platform_deployment_mode").asText()).isEqualTo("in-vpc-enterprise");
    assertThat(root.get("edition").asText()).isEqualTo("enterprise");
  }

  @Test
  @DisplayName("an unlearned relay field is OMITTED, never sent as an empty string")
  void unlearnedFieldsOmitted() throws Exception {
    String payload =
        TelemetryReporter.buildPayload(
            "production", "10.4.0", "localhost", "self_hosted", null, "", null);
    JsonNode root = new ObjectMapper().readTree(payload);
    assertThat(root.has("edition")).as("an explicit \"\" is not a learned value").isFalse();
    assertThat(root.has("platform_deployment_mode")).isFalse();
    assertThat(root.has("license_tier")).isFalse();
    // Positive control: the run produced a real payload, so the absences above are real.
    assertThat(root.get("platform_version").asText()).isEqualTo("10.4.0");
  }

  @Test
  @DisplayName("an oversized relayed value is dropped ALONE, keeping the rest")
  void oversizedRelayedValueDroppedAlone(WireMockRuntimeInfo wm) {
    stubFor(
        get("/health")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"version\":\"10.4.0\",\"edition\":\"" + "e".repeat(65) + "\"}")));

    TelemetryReporter.PlatformHealthProbe probe =
        TelemetryReporter.probePlatformHealth(wm.getHttpBaseUrl(), 2000);

    assertThat(probe.edition).isNull();
    assertThat(probe.platformVersion)
        .as("an oversized value must be dropped ALONE, not take the probe with it")
        .isEqualTo("10.4.0");
  }

  // -------------------------------------------------------------------------
  // Item 3 — redirects, with TWO servers
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("a /health redirect is refused and the target is never read")
  void healthRedirectRefused(WireMockRuntimeInfo wm) {
    // TWO servers, and the second one RECORDS. A single-server fixture cannot express
    // this defect: if the redirector and the target are the same process, a followed
    // redirect and a refused one are indistinguishable. The target serves a complete,
    // plausible /health with DIFFERENT values so that following would be visible.
    WireMockServer target = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    target.start();
    try {
      target.stubFor(
          get("/health")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"version\":\"6.6.6-REDIRECT-TARGET\",\"tier\":\"Plus\"}")));

      stubFor(
          get("/health")
              .willReturn(
                  aResponse()
                      .withStatus(302)
                      .withHeader("Location", target.baseUrl() + "/health")));

      TelemetryReporter.PlatformHealthProbe probe =
          TelemetryReporter.probePlatformHealth(wm.getHttpBaseUrl(), 2000);

      // POSITIVE CONTROL: the first server was actually asked. Without it, "the target
      // saw nothing" is equally true of a run that never happened.
      assertThat(WireMock.findAll(getRequestedFor(urlEqualTo("/health"))))
          .as("the redirector was never contacted, so nothing below proves anything")
          .hasSize(1);
      assertThat(target.findAll(getRequestedFor(urlEqualTo("/health"))))
          .as(
              "the 30x was followed; every relayed value would describe a platform the "
                  + "caller never pointed at")
          .isEmpty();
      assertThat(probe.platformVersion).isNull();
      assertThat(probe.licenseTier).isNull();
    } finally {
      target.stop();
    }
  }

  @Test
  @DisplayName("a checkpoint redirect is not treated as delivery")
  void checkpointRedirectIsNotDelivery(WireMockRuntimeInfo wm) {
    // The more dangerous half. OkHttp does not re-POST across a 301/302/303: it converts
    // the request to a bodyless GET. So a followed redirect yields a 200 for a request
    // that carried NOTHING, sendPingNow reports delivery, and the caller advances the
    // 7-day stamp — the installation goes silent for a week.
    WireMockServer target = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    target.start();
    try {
      target.stubFor(post("/v1/ping").willReturn(ok("{\"latest_version\":\"0.0.0\"}")));
      stubFor(
          post("/v1/ping")
              .willReturn(
                  aResponse()
                      .withStatus(302)
                      .withHeader("Location", target.baseUrl() + "/v1/ping")));

      boolean delivered =
          TelemetryReporter.sendPingNow(
              "production", "", false, wm.getHttpBaseUrl() + "/v1/ping");

      assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping"))))
          .as("the redirector was never contacted")
          .hasSize(1);
      assertThat(target.findAll(anyRequestedFor(anyUrl())))
          .as("a followed redirect reports DELIVERY for a ping that was never sent")
          .isEmpty();
      assertThat(delivered).isFalse();
    } finally {
      target.stop();
    }
  }

  @Test
  @DisplayName("the registered adapter reaches the wire through the real POST")
  void adapterReachesTheWire(WireMockRuntimeInfo wm) throws Exception {
    TelemetryReporter.registerAdapter("langgraph");
    stubFor(post("/v1/ping").willReturn(ok()));

    boolean delivered =
        TelemetryReporter.sendPingNow("production", "", false, wm.getHttpBaseUrl() + "/v1/ping");

    assertThat(delivered).isTrue();
    var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
    assertThat(requests).hasSize(1);
    JsonNode root = new ObjectMapper().readTree(requests.get(0).getBodyAsString());
    assertThat(root.get("telemetry_type").asText()).isEqualTo("sdk");
    List<String> features = new java.util.ArrayList<>();
    root.get("features").forEach(n -> features.add(n.asText()));
    assertThat(features).containsExactly("adapter:langgraph");
  }

  // -------------------------------------------------------------------------
  // The shipped adapter, and the request-site census
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("the shipped LangGraphAdapter declares itself from its constructor")
  void langGraphAdapterDeclaresItself() {
    // THE CENSUS CORRECTION. Grepping for the wire string `adapter:` answers "does any
    // code build the string", NOT "does this SDK ship an adapter" — a census is bounded
    // by the shape you search for. Asked of the EXPORTED TYPE, this SDK ships
    // LangGraphAdapter, which registered nothing.
    //
    // Registration is in the CONSTRUCTOR, not a static initializer: loading the class
    // says the adapter is on the classpath, constructing one says it is IN USE.
    //
    // Positive control: nothing is registered before the adapter is built, so the
    // assertion below is about the CONSTRUCTOR rather than about class-loading having
    // already happened.
    assertThat(TelemetryReporter.registeredFeatures()).isEmpty();

    // A mock client, because the Builder rejects null — which incidentally proves the
    // registration runs AFTER validation: a construction that throws declares nothing,
    // and a registration is a claim that the adapter is IN USE.
    com.getaxonflow.sdk.AxonFlow client =
        org.mockito.Mockito.mock(com.getaxonflow.sdk.AxonFlow.class);
    com.getaxonflow.sdk.adapters.LangGraphAdapter.builder(client, "wf").build();

    assertThat(TelemetryReporter.registeredFeatures()).containsExactly("adapter:langgraph");
  }

  @Test
  @DisplayName("a construction that FAILS validation declares nothing")
  void failedConstructionDeclaresNothing() {
    // A registration is a claim that the adapter is in use, and an object that failed to
    // build is not in use. The Builder's null check runs before the constructor, so this
    // is structurally guaranteed here rather than a convention — asserted so a future
    // refactor that moves validation into the constructor cannot silently break it.
    assertThat(TelemetryReporter.registeredFeatures()).isEmpty();
    try {
      com.getaxonflow.sdk.adapters.LangGraphAdapter.builder(null, "wf").build();
    } catch (NullPointerException expected) {
      // the Builder rejects a null client
    }
    assertThat(TelemetryReporter.registeredFeatures())
        .as("a construction that threw must not have declared the adapter")
        .isEmpty();
  }

  @Test
  @DisplayName("every outbound request path passes the heartbeat trigger")
  void everyRequestSitePassesTheTrigger() throws Exception {
    // The heartbeat fires on the client's FIRST OUTBOUND REQUEST, which makes "which code
    // paths count as a request" a correctness question. A gate placed at some callers is
    // not a gate on the others: the Go SDK had one bypass (StreamExecutionStatus) and the
    // Python SDK had TEN, each of which meant a process using only that path never pinged.
    //
    // This SDK routes every public request through executeHttp. This census is what keeps
    // that true: a new raw newCall() fails here until its author accounts for it.
    //
    // DECLARED LIMIT: a source scan is only as wide as the syntax it matches. It sees
    // `.newCall(`, which is how every OkHttp request in this SDK is issued. It would not
    // see a request issued through a future helper that hides the call.
    java.util.Map<String, Integer> allowed = new java.util.LinkedHashMap<>();
    // executeHttp itself — THE wrapper. It calls the trigger immediately before this call.
    allowed.put("AxonFlow.java", 1);
    // The telemetry path itself: the /health probe and the checkpoint POST. These MUST
    // NOT call the trigger, or the heartbeat triggers itself, recursively.
    allowed.put("TelemetryReporter.java", 2);

    java.nio.file.Path srcRoot = java.nio.file.Paths.get("src/main/java");
    java.util.Map<String, Integer> found = new java.util.LinkedHashMap<>();
    try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(srcRoot)) {
      for (java.nio.file.Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        int count = 0;
        for (String line : java.nio.file.Files.readAllLines(f)) {
          String trimmed = line.trim();
          // Prose mentioning a call is not a call — a marker string colliding with the
          // comment beside it is its own failure mode.
          if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
            continue;
          }
          if (line.contains(".newCall(")) {
            count++;
          }
        }
        if (count > 0) {
          found.put(f.getFileName().toString(), count);
        }
      }
    }

    // POSITIVE CONTROL: a scan finding nothing has stopped working, and an empty result
    // would otherwise read as "no bypasses" — the most dangerous way for a source guard
    // to fail.
    assertThat(found).as("the scan found ZERO request sites, which cannot be true").isNotEmpty();

    assertThat(found)
        .as(
            "a request path outside executeHttp never reaches the heartbeat trigger, so a "
                + "process using only that path would never ping. Route it through "
                + "executeHttp, or call the trigger and update this census.")
        .isEqualTo(allowed);
  }

  @Test
  @DisplayName("the census needle has no false positives")
  void censusNeedleHasNoFalsePositives() {
    // A guard that cries wolf is not a stricter guard: it trains the next reader to add a
    // bogus exemption, after which the census means nothing. Ported from the Go review,
    // where widening a needle to a bare `.Get(` flagged three `Header.Get(...)` sites.
    for (String notARequest :
        Arrays.asList(
            "    String scope = response.header(\"X-Axonflow-Read-Scope\");",
            "    Call call = client.newBuilder().build().newCall2(request);",
            "    // return client.newCall(request).execute();")) {
      boolean matches =
          notARequest.contains(".newCall(")
              && !notARequest.trim().startsWith("//");
      assertThat(matches).as("false positive on: %s", notARequest).isFalse();
    }
    // And it must still match the real spelling.
    assertThat("      return client.newCall(request).execute();".contains(".newCall(")).isTrue();
  }
}
