/*
 * Copyright 2026 AxonFlow
 * Licensed under the Business Source License 1.1.
 */
package com.getaxonflow.sdk.telemetry;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlow;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adapter registry, relay caps and redirect refusal (axonflow-enterprise#3682 items 1-2).
 *
 * <p>WHAT THESE TESTS CAN AND CANNOT VARY. The HTTP cases run against real WireMock servers on
 * loopback and drive the SDK's own OkHttp calls, so the redirect axis is varied end to end — which
 * matters here more than in Python, because OkHttp FOLLOWS redirects by default and this is a live
 * defect rather than a pin. The redirect cases use TWO servers, and the second one records.
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
    assertThat(TelemetryReporter.boundFeatures(Arrays.asList(within, over)))
        .containsExactly(within);
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
          TelemetryReporter.sendPingNow("production", "", false, wm.getHttpBaseUrl() + "/v1/ping");

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

  /**
   * Files allowed to issue HTTP requests, and the CLOSED category each is in.
   *
   * <p>A free-text reason let a new site claim any justification it liked; naming a category from a
   * fixed set makes "which kind of exemption is this" answerable.
   */
  private enum RequestSiteCategory {
    /** {@code executeHttp} itself — THE wrapper, which calls the trigger. */
    WRAPPER,
    /** The telemetry path. MUST NOT trigger, or the heartbeat triggers itself recursively. */
    TELEMETRY_PATH,
    /**
     * A package-level function with no client and no configured endpoint, so there is nothing for a
     * heartbeat to describe. The Go SDK exempts {@code register.go} on identical grounds.
     */
    NO_CLIENT
  }

  private static final java.util.Map<String, RequestSiteCategory> REQUEST_SITES =
      java.util.Map.of(
          "AxonFlow.java", RequestSiteCategory.WRAPPER,
          "TelemetryReporter.java", RequestSiteCategory.TELEMETRY_PATH,
          "AxonFlowTry.java", RequestSiteCategory.NO_CLIENT);

  /**
   * Evidence that a FILE can issue an HTTP request.
   *
   * <p>THE FILE IS THE UNIT, NOT THE CALL SITE, and that is a correction. A call-shaped needle has
   * to guess the receiver's spelling, and every guess leaves a hole: {@code (?i:client)\.send\(}
   * requires the literal word "client", so {@code HttpClient.newHttpClient().send(...)}, a field
   * named {@code http} calling {@code .send(}, and {@code openConnection()} all slipped past it —
   * while {@code mailClient.send(message)} matched and was a false positive. A needle wrong in both
   * directions is worse than none, because it reads as coverage.
   *
   * <p>Asking "can this file issue an HTTP request at all" is answerable from its IMPORTS, which a
   * receiver rename cannot change. A file that imports {@code java.net.http} can issue one however
   * it spells the call.
   *
   * <p>DECLARED LIMIT: a source scan is still only as wide as what it matches. A request issued
   * through a helper in ANOTHER file leaves no evidence here — but that helper's own file would be
   * caught, which is the property that matters.
   */
  private static final java.util.List<java.util.regex.Pattern> REQUEST_CAPABILITY =
      java.util.List.of(
          // OkHttp: the call itself, since the import is `okhttp3.*` and is far more widespread.
          java.util.regex.Pattern.compile("\\.newCall\\("),
          // java.net.http, however the send is spelled — and matched ANYWHERE in
          // code, not only on an `import` line. Re-planting the review's three
          // bypasses showed why: a fully-qualified
          // `java.net.http.HttpClient.newHttpClient()` needs no import at all, so
          // an import-only pattern let two of the three straight through.
          java.util.regex.Pattern.compile("java\\.net\\.http\\."),
          // The legacy JDK client.
          java.util.regex.Pattern.compile("HttpURLConnection|\\.openConnection\\("),
          // A send on a chained expression: `HttpClient.newHttpClient().send(...)`.
          // Deliberately anchored on the closing paren rather than on a receiver
          // NAME — that is what made the previous needle miss this and match
          // `mailClient.send(message)` at the same time.
          java.util.regex.Pattern.compile("\\)\\.send\\("));

  /** True when {@code line} is evidence the enclosing file can issue an HTTP request. */
  private static boolean isRequestCapability(String line) {
    String trimmed = line.trim();
    // Prose mentioning an API is not a use of it — a marker string colliding with
    // the comment beside it is its own failure mode.
    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
      return false;
    }
    return REQUEST_CAPABILITY.stream().anyMatch(pat -> pat.matcher(line).find());
  }

  @Test
  @DisplayName("every file that can issue an HTTP request is accounted for")
  void everyRequestSitePassesTheTrigger() throws Exception {
    // The heartbeat fires on the client's FIRST OUTBOUND REQUEST, which makes "which code
    // paths count as a request" a correctness question. A gate placed at some callers is
    // not a gate on the others: the Go SDK had one bypass (StreamExecutionStatus) and the
    // Python SDK had TEN, each of which meant a process using only that path never pinged.
    java.nio.file.Path srcRoot = java.nio.file.Paths.get("src/main/java");
    java.util.Set<String> found = new java.util.TreeSet<>();
    try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(srcRoot)) {
      for (java.nio.file.Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        for (String line : java.nio.file.Files.readAllLines(f)) {
          if (isRequestCapability(line)) {
            found.add(f.getFileName().toString());
            break;
          }
        }
      }
    }

    // POSITIVE CONTROL: a scan finding nothing has stopped working, and an empty result
    // would otherwise read as "no bypasses" — the most dangerous way for a source guard
    // to fail.
    assertThat(found)
        .as("the scan found ZERO request-capable files, which cannot be true")
        .isNotEmpty();

    assertThat(found)
        .as(
            "a file that can issue an HTTP request outside executeHttp is a path on which the "
                + "SDK never pings, so a process using only that path would be invisible. Route "
                + "it through executeHttp, or call the trigger and add it to REQUEST_SITES with "
                + "a category.")
        .isEqualTo(new java.util.TreeSet<>(REQUEST_SITES.keySet()));
  }

  @Test
  @DisplayName("the AxonFlowTry exclusion premise still holds")
  void theNoClientExclusionPremiseHolds() throws Exception {
    // AxonFlowTry is excluded on the grounds that it is a package-level registration
    // helper with no client and no configured endpoint — pinging there would report a
    // deployment that does not exist yet. If that stops being true the exclusion is
    // stale, so the premise is ASSERTED rather than trusted.
    assertThat(REQUEST_SITES.get("AxonFlowTry.java")).isEqualTo(RequestSiteCategory.NO_CLIENT);

    String src =
        java.nio.file.Files.readString(
            java.nio.file.Paths.get("src/main/java/com/getaxonflow/sdk/AxonFlowTry.java"));
    assertThat(src)
        .as("register() must still be static — an instance method would have a client")
        .contains("public static TryRegistration register(");
    assertThat(src)
        .as("AxonFlowTry must not construct an AxonFlow client; if it does, the exclusion is stale")
        .doesNotContain("AxonFlow.create(");
    assertThat(src).as("nor hold one").doesNotContain("private final AxonFlow ");
  }

  @Test
  @DisplayName("the detector is driven by the REAL predicate, in both directions")
  void theDetectorIsDrivenByTheRealPredicate() {
    // THE EARLIER VERSION OF THIS TEST DID NOT USE THE DETECTOR. It re-implemented
    // `.contains(".newCall(")` inline, so it would have passed with the regex list
    // emptied — a control that cannot fail is not a control, which is the exact defect
    // this file exists to prevent elsewhere.
    for (String notARequestCapable :
        Arrays.asList(
            "    String scope = response.header(\"X-Axonflow-Read-Scope\");",
            "    Call call = client.newBuilder().build().newCall2(request);",
            "    queue.send(message);",
            // Previously a FALSE POSITIVE: the old needle matched any receiver whose
            // name ended in "client". Naming the capability by IMPORT rather than by
            // call shape removes the whole class.
            "    mailClient.send(message);",
            "    this.emitter.send(event);")) {
      assertThat(isRequestCapability(notARequestCapable))
          .as("false positive on: %s", notARequestCapable)
          .isFalse();
    }

    // And every real spelling must be caught — including the three that slipped past
    // the call-shaped needle in review.
    for (String requestCapable :
        Arrays.asList(
            "      return client.newCall(request).execute();",
            "import java.net.http.HttpClient;",
            "      var r = HttpClient.newHttpClient().send(req, BodyHandlers.ofString());",
            // Fully qualified, no import — one of the two that survived the
            // import-only version of this detector.
            "    private static final java.net.http.HttpClient http =",
            "        return java.net.http.HttpClient.newHttpClient()",
            "      HttpURLConnection conn = (HttpURLConnection) url.openConnection();")) {
      assertThat(isRequestCapability(requestCapable))
          .as("detector MISSES a request-capable line: %s", requestCapable)
          .isTrue();
    }

    // Comments are not code.
    assertThat(isRequestCapability("      // return client.newCall(request).execute();")).isFalse();
    assertThat(isRequestCapability("   * import java.net.http.HttpClient;")).isFalse();

    // A FIELD receiver — `http.send(req, ...)` — is deliberately NOT matched
    // line-by-line: no spelling of a receiver name can be enumerated safely, which
    // is the lesson from the previous needle. It is caught at FILE level by the
    // import, which is how the detector is actually used, so the control has to be
    // file-shaped too.
    java.util.List<String> fileWithFieldReceiver =
        Arrays.asList(
            "package com.example;",
            "import java.net.http.HttpClient;",
            "class Sneaky {",
            "  private final HttpClient http = HttpClient.newHttpClient();",
            "  void go() throws Exception { http.send(req, BodyHandlers.ofString()); }",
            "}");
    assertThat(fileWithFieldReceiver.stream().anyMatch(AdapterRegistryTest::isRequestCapability))
        .as("a file with a field-named HttpClient must be caught by its import")
        .isTrue();

    // ...and a file that merely mentions HTTP in prose must not be.
    java.util.List<String> fileWithOnlyProse =
        Arrays.asList(
            "package com.example;",
            "/** Talks to the platform, but only through AxonFlow's own client. */",
            "class Innocent {",
            "  // import java.net.http.HttpClient; -- deliberately not used",
            "}");
    assertThat(fileWithOnlyProse.stream().anyMatch(AdapterRegistryTest::isRequestCapability))
        .as("prose about HTTP is not the ability to issue HTTP")
        .isFalse();
  }

  @Test
  @DisplayName("followSslRedirects(false) is pinned SEPARATELY from followRedirects(false)")
  void followSslRedirectsIsPinnedOnItsOwn() throws Exception {
    // These are two different settings and only one of them governs the hop that
    // matters. OkHttp's followSslRedirects controls http<->https specifically —
    // exactly the redirect a captive portal or a TLS-terminating proxy produces —
    // and it defaults to TRUE. Removing it alone leaves every other redirect test
    // green, because those fixtures are http->http.
    //
    // The e2e cannot vary the scheme (both listeners are local http), so this is a
    // SOURCE assertion: honest about being weaker than a behavioural one, and
    // present because the alternative is no coverage of that axis at all.
    String src =
        java.nio.file.Files.readString(
            java.nio.file.Paths.get(
                "src/main/java/com/getaxonflow/sdk/telemetry/TelemetryReporter.java"));
    assertThat(countOccurrences(src, ".followRedirects(false)"))
        .as("both telemetry clients — the /health probe and the checkpoint POST")
        .isEqualTo(2);
    assertThat(countOccurrences(src, ".followSslRedirects(false)"))
        .as(
            "followRedirects(false) alone still permits an http<->https hop, which is the "
                + "redirect a captive portal produces and the one that would relay a platform "
                + "the caller never pointed at")
        .isEqualTo(2);
  }

  private static int countOccurrences(String haystack, String needle) {
    int n = 0;
    int i = haystack.indexOf(needle);
    while (i >= 0) {
      n++;
      i = haystack.indexOf(needle, i + needle.length());
    }
    return n;
  }

  /** Captures what a logger emitted during one block, so a diagnostic can be asserted. */
  private static java.util.List<String> captureDebug(Class<?> loggerFor, Runnable body) {
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(loggerFor);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    ch.qos.logback.classic.Level previous = logger.getLevel();
    logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    logger.addAppender(appender);
    try {
      body.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previous);
    }
    return appender.list.stream()
        .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
        .toList();
  }

  @Test
  @DisplayName("a refused /health redirect is LOGGED, without leaking the Location")
  void aRefusedHealthRedirectIsLogged(WireMockRuntimeInfo wm) {
    // A refused redirect is the one failure on this path that would otherwise look
    // like an ordinary non-2xx, so the diagnostic naming it is part of the contract —
    // and an unasserted log line is a claim, not a behaviour.
    stubFor(
        get("/health")
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", "http://elsewhere.test/health")));

    java.util.List<String> logged =
        captureDebug(
            TelemetryReporter.class,
            () -> TelemetryReporter.probePlatformHealth(wm.getHttpBaseUrl(), 2000));

    assertThat(logged)
        .as("MUTATION GATE: delete the isRedirect branch in probePlatformHealth and this fails")
        .anyMatch(m -> m.contains("redirect") && m.contains("302"));
    assertThat(logged)
        .as(
            "the Location value is remote-controlled text; the diagnostic only needs to say "
                + "WHAT was refused, not where it pointed")
        .noneMatch(m -> m.contains("elsewhere.test"));
  }

  @Test
  @DisplayName("a refused checkpoint redirect is LOGGED — the leg that would look like success")
  void aRefusedCheckpointRedirectIsLogged(WireMockRuntimeInfo wm) {
    stubFor(
        post("/v1/ping")
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", "http://elsewhere.test/v1/ping")));

    java.util.List<String> logged =
        captureDebug(
            TelemetryReporter.class,
            () ->
                TelemetryReporter.sendPingNow(
                    "production", "", false, wm.getHttpBaseUrl() + "/v1/ping"));

    assertThat(logged)
        .as("MUTATION GATE: delete the isRedirect branch in sendPingNow and this fails")
        .anyMatch(m -> m.contains("redirect") && m.contains("302"));
    assertThat(logged).noneMatch(m -> m.contains("elsewhere.test"));
  }

  /**
   * A whole JVM that constructs a client, makes ONE call, and returns from main.
   *
   * <p>Spawned as a real subprocess by the test below. In-process is not an option and the reason
   * is structural, not stylistic: the checkpoint URL is read from {@code System.getenv}, surefire
   * pins {@code AXONFLOW_TELEMETRY=off} for the whole run, and neither can be changed from inside a
   * running JVM. The Go SDK's #1693 regression test compiles and runs a binary for exactly this
   * reason.
   */
  public static final class ShortLivedMain {
    public static void main(String[] args) {
      AxonFlow client =
          AxonFlow.create(
              AxonFlow.builder().endpoint(args[0]).clientId("id").clientSecret("secret").build());
      try {
        client.listConnectors();
        System.out.println("CHILD: listConnectors returned");
      } catch (RuntimeException e) {
        // The API response shape is irrelevant — the heartbeat rides the ATTEMPT,
        // so a caller whose first call fails is still a caller. Printed so a
        // harness failure is distinguishable from SDK behaviour.
        System.out.println(
            "CHILD: listConnectors threw " + e.getClass().getName() + ": " + e.getMessage());
      }
      // No sleep, no join. This is the shape of a real short-lived caller, and
      // it is the shape that drops a backgrounded POST.
    }
  }

  @Test
  @DisplayName("a process that makes ONE call and exits still delivers the ping")
  void aShortLivedProcessStillDelivers(WireMockRuntimeInfo wm, @TempDir Path tmp) throws Exception {
    // R3 round 1, H2: `invokeHeartbeatOnRequest() { invokeHeartbeatAsync(); }`
    // left ALL 1522 tests green. Surefire runs with telemetry off, and the
    // existing short-lived fixture's instant listeners cannot express the
    // defect — a backgrounded ping wins that race every time, so the fixture
    // reads as a disproof of a bug it never gave itself a chance to see.
    //
    // The constructor's ping was SYNCHRONOUS on purpose: a JVM that exits
    // promptly would otherwise drop a POST left on the daemon executor. Moving
    // the trigger to the first request had to preserve that, which is what
    // `invokeHeartbeatOnRequest` does by running the gate inline when COLD.
    //
    // THE 700 ms DELAY IS WHAT MAKES THE DEFECT EXPRESSIBLE. It is on the
    // /health probe, so the telemetry path takes long enough that a
    // backgrounded ping is still in flight when main returns. With no delay
    // both implementations pass and the fixture proves nothing.
    stubFor(
        get("/health")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(700)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"healthy\",\"version\":\"10.4.0\"}")));
    stubFor(post("/v1/ping").willReturn(ok()));
    stubFor(get(urlPathMatching("/api/.*")).willReturn(ok("{\"connectors\":[]}")));

    ProcessBuilder pb =
        new ProcessBuilder(
            System.getProperty("java.home") + "/bin/java",
            // -Duser.home, NOT just the HOME env var. HeartbeatState.resolveStampPath
            // reads System.getProperty("user.home") on macOS, so a developer machine
            // that pinged recently has a FRESH STAMP and the gate suppresses the ping —
            // which would make this test pass vacuously if the assertion were inverted,
            // and fail confusingly as it stands. Found by this test failing with the
            // child exiting too fast to have made the 700 ms probe.
            "-Duser.home=" + tmp,
            "-cp",
            System.getProperty("java.class.path"),
            ShortLivedMain.class.getName(),
            wm.getHttpBaseUrl());
    // The two settings that cannot be changed from inside a JVM, which is why
    // this is a subprocess at all.
    pb.environment().put("AXONFLOW_CHECKPOINT_URL", wm.getHttpBaseUrl() + "/v1/ping");
    pb.environment().remove("AXONFLOW_TELEMETRY");
    // A private stamp dir, so a developer machine that pinged recently does not
    // silence the run — the 7-day stamp would suppress it and the test would
    // pass vacuously.
    pb.environment().put("HOME", tmp.toString());
    pb.environment().put("XDG_CACHE_HOME", tmp.resolve(".cache").toString());
    pb.redirectErrorStream(true);

    // RESET THE REQUEST JOURNAL FIRST. Other tests in this class POST to /v1/ping
    // and the journal is shared across them, so `hasSize(1)` below could be
    // satisfied by SOMEONE ELSE'S ping — passing even with the cold path made
    // async. The assertion must read only what THIS child produced.
    Process proc = pb.start();
    String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    boolean exited = proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(exited).as("the child did not exit; harness failure, not SDK behaviour").isTrue();
    assertThat(proc.exitValue())
        .as(
            "the child died (exit %s) — HARNESS failure, not SDK fail-open.%n%s",
            proc.exitValue(), output)
        .isZero();

    assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping"))))
        .as(
            "MUTATION GATE: make invokeHeartbeatOnRequest delegate straight to "
                + "invokeHeartbeatAsync and this is empty. The cold path must run inline, or a "
                + "JVM that exits after one call drops the POST — the defect issue #1693 fixed "
                + "for the constructor, reintroduced by moving the trigger.%n%s",
            output)
        .hasSize(1);
  }
}
