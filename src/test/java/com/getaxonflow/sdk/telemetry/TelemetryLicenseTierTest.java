/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.getaxonflow.sdk.telemetry;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * license_tier telemetry field (#3619).
 *
 * <p>Contract under test: the platform's licence tier rides along on the {@code /health} response
 * the SDK ALREADY fetches for {@code platform_version}, is forwarded to the checkpoint receiver
 * verbatim, and is OMITTED — never defaulted — whenever it could not be learned.
 *
 * <p>These tests drive a real WireMock HTTP server on both legs (the stand-in platform's {@code
 * /health} and the checkpoint receiver), so the assertions are about bytes that actually crossed a
 * socket rather than about a mocked client object.
 */
@WireMockTest
class TelemetryLicenseTierTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Stub the platform's /health with a status and raw body, and the checkpoint receiver. */
  private void stubPlatform(int status, String healthBody) {
    stubFor(
        get("/health")
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(healthBody)));
    stubFor(post("/v1/ping").willReturn(ok()));
  }

  /** Run one real ping and return the captured wire body. */
  private String captureWire(WireMockRuntimeInfo wm, String sdkEndpoint) {
    TelemetryReporter.sendPingNow(
        "production", sdkEndpoint, false, wm.getHttpBaseUrl() + "/v1/ping");
    var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
    assertThat(requests)
        .as("the ping must still be delivered — telemetry degrades, it does not stop")
        .hasSize(1);
    return requests.get(0).getBodyAsString();
  }

  /** A port with nothing listening on it. */
  private static String deadEndpoint() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return "http://127.0.0.1:" + socket.getLocalPort();
    }
  }

  // -------------------------------------------------------------------------
  // Verbatim round-trip
  // -------------------------------------------------------------------------

  /**
   * Exactly the values platform/agent/run.go currentLicenseTier() can return, plus the csaas "Plus"
   * alias its health serializer emits. Each must reach the wire byte-for-byte — normalization is
   * the receiver's job, and folding here would mask a tier this SDK build predates.
   */
  @ParameterizedTest
  @ValueSource(strings = {"community", "evaluation", "Enterprise", "Plus", "starting"})
  @DisplayName("forwards every platform-emitted tier to the wire verbatim")
  void forwardsEveryPlatformEmittedTierVerbatim(String tier, WireMockRuntimeInfo wm)
      throws Exception {
    stubPlatform(200, "{\"status\":\"healthy\",\"version\":\"10.3.0\",\"tier\":\"" + tier + "\"}");

    String wire = captureWire(wm, wm.getHttpBaseUrl());

    JsonNode body = objectMapper.readTree(wire);
    assertThat(body.has("license_tier")).as("field present on the wire").isTrue();
    assertThat(body.get("license_tier").asText()).isEqualTo(tier);
    // Literal-JSON assertion too: a mutation renaming the key would still
    // round-trip through a decode of a differently-named field.
    assertThat(wire).contains("\"license_tier\":\"" + tier + "\"");
  }

  // -------------------------------------------------------------------------
  // Fail-open — the load-bearing half
  // -------------------------------------------------------------------------

  /**
   * For every way the health probe can fail, the ping must still be delivered and the field must be
   * ABSENT from the JSON — never {@code ""}, never {@code null}, never a substituted default.
   * Emitting "community" for a platform we could not reach would be a false claim about a
   * customer's deployment.
   */
  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "health returns 500                | 500 | {\"tier\":\"Enterprise\"}",
        "health returns malformed JSON     | 200 | {\"tier\":\"Enterprise\"",
        "health has no tier key            | 200 | {\"status\":\"healthy\",\"version\":\"10.3.0\"}",
        "health has an empty tier          | 200 | {\"version\":\"10.3.0\",\"tier\":\"\"}",
        "health has a numeric tier         | 200 | {\"version\":\"10.3.0\",\"tier\":42}",
        "health has a boolean tier         | 200 | {\"version\":\"10.3.0\",\"tier\":true}",
        "health has a null tier            | 200 | {\"version\":\"10.3.0\",\"tier\":null}",
        "health returns a JSON array       | 200 | [1,2,3]",
      })
  @DisplayName("omits the field whenever the tier was not learned")
  void omitsTheFieldWheneverTheTierWasNotLearned(
      String name, int status, String healthBody, WireMockRuntimeInfo wm) throws Exception {
    stubPlatform(status, healthBody);

    String wire = captureWire(wm, wm.getHttpBaseUrl());

    JsonNode body = objectMapper.readTree(wire);
    assertThat(body.get("telemetry_type").asText()).isEqualTo("sdk");
    assertThat(body.has("license_tier"))
        .as("%s: license_tier must be OMITTED when not learned, got: %s", name, wire)
        .isFalse();
    assertThat(wire).doesNotContain("license_tier");
  }

  @Test
  @DisplayName("platform unreachable → ping still sent, field absent")
  void platformUnreachableStillSendsThePing(WireMockRuntimeInfo wm) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));

    String wire = captureWire(wm, deadEndpoint());

    assertThat(objectMapper.readTree(wire).get("telemetry_type").asText()).isEqualTo("sdk");
    assertThat(wire).doesNotContain("license_tier");
  }

  @Test
  @DisplayName("endpoint not configured → ping still sent, field absent")
  void endpointNotConfiguredStillSendsThePing(WireMockRuntimeInfo wm) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));

    String wire = captureWire(wm, "");

    assertThat(objectMapper.readTree(wire).get("telemetry_type").asText()).isEqualTo("sdk");
    assertThat(wire).doesNotContain("license_tier");
  }

  // -------------------------------------------------------------------------
  // Independence of the two health-derived fields
  // -------------------------------------------------------------------------

  /**
   * One field's absence must never discard the other. The pre-#3619 probe returned as soon as it
   * had read {@code version}; had the tier been read after that, a platform answering with a tier
   * but no version would have reported no tier at all.
   */
  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "both present     | {\"version\":\"10.3.0\",\"tier\":\"Enterprise\"} | 10.3.0 | Enterprise",
        "tier only        | {\"tier\":\"Enterprise\"}                        |        | Enterprise",
        "version only     | {\"version\":\"10.3.0\"}                         | 10.3.0 |",
        "neither          | {\"status\":\"healthy\"}                         |        |",
      })
  @DisplayName("learns version and tier independently")
  void learnsVersionAndTierIndependently(
      String name, String healthBody, String wantVersion, String wantTier, WireMockRuntimeInfo wm) {
    stubPlatform(200, healthBody);

    TelemetryReporter.PlatformHealthProbe probe =
        TelemetryReporter.probePlatformHealth(wm.getHttpBaseUrl(), 2000L);

    assertThat(probe.platformVersion).as("%s: platformVersion", name).isEqualTo(wantVersion);
    assertThat(probe.licenseTier).as("%s: licenseTier", name).isEqualTo(wantTier);
  }

  // -------------------------------------------------------------------------
  // buildPayload: omission, not null
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("a null tier omits the key entirely rather than writing JSON null")
  void nullTierOmitsTheKey() throws Exception {
    String payload =
        TelemetryReporter.buildPayload("production", "10.3.0", "remote", "self_hosted", null);

    JsonNode body = objectMapper.readTree(payload);
    assertThat(body.has("license_tier")).isFalse();
    assertThat(payload).doesNotContain("license_tier");
  }

  @Test
  @DisplayName("a learned tier is carried unchanged")
  void learnedTierIsCarriedUnchanged() throws Exception {
    String payload =
        TelemetryReporter.buildPayload("production", "10.3.0", "remote", "self_hosted", "Plus");

    assertThat(objectMapper.readTree(payload).get("license_tier").asText()).isEqualTo("Plus");
  }

  @Test
  @DisplayName("platform_version keeps its explicit-null wire shape")
  void platformVersionKeepsItsExplicitNullWireShape() throws Exception {
    // platform_version has always been written as an explicit JSON null when
    // unknown. That long-standing shape is deliberately NOT changed to match
    // license_tier's omission.
    String payload =
        TelemetryReporter.buildPayload("production", null, "remote", "self_hosted", null);

    JsonNode body = objectMapper.readTree(payload);
    assertThat(body.has("platform_version")).isTrue();
    assertThat(body.get("platform_version").isNull()).isTrue();
  }

  @Test
  @DisplayName("the pre-existing 4-arg overload still builds a tier-free payload")
  void fourArgOverloadRemainsTierFree() throws Exception {
    String payload =
        TelemetryReporter.buildPayload("production", "10.3.0", "remote", "self_hosted");

    assertThat(objectMapper.readTree(payload).has("license_tier")).isFalse();
  }

  // -------------------------------------------------------------------------
  // Shared deadline + dimension separation
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("a stalled /health does not stack a second timeout onto the shared budget")
  void stalledHealthDoesNotStackASecondTimeout(WireMockRuntimeInfo wm) {
    // Answer far later than the budget the probe is handed.
    stubFor(
        get("/health")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(5000)
                    .withBody("{\"version\":\"10.3.0\",\"tier\":\"Enterprise\"}")));

    long started = System.nanoTime();
    TelemetryReporter.PlatformHealthProbe probe =
        TelemetryReporter.probePlatformHealth(wm.getHttpBaseUrl(), 400L);
    long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

    assertThat(probe.platformVersion).isNull();
    assertThat(probe.licenseTier).isNull();
    // Bounded by the supplied budget, not by an independent per-probe timeout.
    // Generous slack for CI scheduling, far below the ~2x a stacked second
    // timeout would produce.
    assertThat(elapsedMs)
        .as("probe took %dms — a second timeout is stacking", elapsedMs)
        .isLessThan(400L + 2000L);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "with tier    | {\"version\":\"10.3.0\",\"tier\":\"Enterprise\"}",
        "without tier | {\"version\":\"10.3.0\"}",
      })
  @DisplayName("deployment_mode is unchanged by the tier")
  void deploymentModeIsUnchangedByTheTier(String name, String healthBody, WireMockRuntimeInfo wm)
      throws Exception {
    stubPlatform(200, healthBody);

    String wire = captureWire(wm, wm.getHttpBaseUrl());

    // A 127.0.0.1 WireMock stand-in classifies as self_hosted topology; the
    // licence tier must not touch that dimension.
    assertThat(objectMapper.readTree(wire).get("deployment_mode").asText())
        .as("%s", name)
        .isEqualTo("self_hosted");
  }
}
