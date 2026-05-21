/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.getaxonflow.sdk.telemetry;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

@DisplayName("TelemetryReporter")
@WireMockTest
class TelemetryReporterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // --- isEnabled tests ---
  // v8: AXONFLOW_TELEMETRY=off is the SOLE opt-out signal. The v7.x mode-based default
  // suppression and the Boolean configOverride parameter were both removed.
  // DO_NOT_TRACK is intentionally NOT honored.

  @Test
  @DisplayName("AXONFLOW_TELEMETRY=off disables telemetry")
  void testTelemetryDisabledByAxonflowEnv() {
    assertThat(TelemetryReporter.isEnabled("off")).isFalse();
    assertThat(TelemetryReporter.isEnabled("OFF")).isFalse();
    assertThat(TelemetryReporter.isEnabled("  off  ")).isFalse();
  }

  @Test
  @DisplayName("v8: telemetry is ON by default for every mode (no env opt-out)")
  void testTelemetryOnByDefault() {
    // null env (unset) → telemetry is ON. The mode-specific suppression
    // that used to disable sandbox-mode pings was removed in v8 — sandbox
    // pings now fire and are tagged stream="sandbox" in the payload.
    assertThat(TelemetryReporter.isEnabled(null)).isTrue();
    assertThat(TelemetryReporter.isEnabled("")).isTrue();
    assertThat(TelemetryReporter.isEnabled("on")).isTrue();
    assertThat(TelemetryReporter.isEnabled("anything-not-off")).isTrue();
  }

  // --- Payload format test ---

  @Test
  @DisplayName("should produce correct payload JSON format")
  void testPayloadFormat() throws Exception {
    String payload = TelemetryReporter.buildPayload("production", null);
    JsonNode root = objectMapper.readTree(payload);

    assertThat(root.get("telemetry_type").asText()).isEqualTo("sdk");
    assertThat(root.get("sdk").asText()).isEqualTo("java");
    assertThat(root.get("sdk_version").asText()).isEqualTo(AxonFlowConfig.SDK_VERSION);
    assertThat(root.get("platform_version").isNull()).isTrue();
    assertThat(root.get("os").asText())
        .isEqualTo(TelemetryReporter.normalizeOS(System.getProperty("os.name")));
    assertThat(root.get("arch").asText())
        .isEqualTo(TelemetryReporter.normalizeArch(System.getProperty("os.arch")));
    assertThat(root.get("runtime_version").asText()).isEqualTo(System.getProperty("java.version"));
    // v1 schema: 2-arg buildPayload defaults deployment_mode to "unknown".
    assertThat(root.get("deployment_mode").asText()).isEqualTo("unknown");
    assertThat(root.has("profile")).isFalse();
    assertThat(root.get("features").isArray()).isTrue();
    assertThat(root.get("features").size()).isEqualTo(0);
    assertThat(root.get("instance_id").asText()).isNotEmpty();
    // instance_id should be a valid UUID format
    assertThat(root.get("instance_id").asText())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    // v8: production-mode payloads OMIT the `stream` field entirely so the wire shape is
    // byte-identical to v7.x for the production-mode case. The server defaults empty/missing
    // to "heartbeat".
    assertThat(root.has("stream")).isFalse();
  }

  @Test
  @DisplayName("payload deployment_mode reflects the v1 schema classifier output")
  void testPayloadDeploymentModeReflection() throws Exception {
    String payload =
        TelemetryReporter.buildPayload(
            "sandbox", null, TelemetryReporter.EndpointType.LOCALHOST,
            TelemetryReporter.DeploymentMode.SELF_HOSTED);
    JsonNode root = objectMapper.readTree(payload);
    assertThat(root.get("deployment_mode").asText()).isEqualTo("self_hosted");
  }

  @Test
  @DisplayName("v8: sandbox-mode payload carries stream=\"sandbox\"")
  void testPayloadStreamTagSandbox() throws Exception {
    String payload = TelemetryReporter.buildPayload("sandbox", null);
    JsonNode root = objectMapper.readTree(payload);
    assertThat(root.get("stream")).isNotNull();
    assertThat(root.get("stream").asText()).isEqualTo("sandbox");
  }

  @Test
  @DisplayName("v8: production-mode payload omits the stream field")
  void testPayloadStreamTagProductionOmitted() throws Exception {
    String payload = TelemetryReporter.buildPayload("production", null);
    JsonNode root = objectMapper.readTree(payload);
    assertThat(root.has("stream")).isFalse();
  }

  @Test
  @DisplayName("v8: enterprise / staging / empty modes also omit the stream field")
  void testPayloadStreamTagOtherModesOmitted() throws Exception {
    for (String mode : new String[] {"enterprise", "staging", "", "unknown-mode"}) {
      String payload = TelemetryReporter.buildPayload(mode, null);
      JsonNode root = objectMapper.readTree(payload);
      assertThat(root.has("stream")).as("mode=%s should omit stream", mode).isFalse();
    }
  }

  // --- HTTP integration tests ---

  @Test
  @DisplayName("should send telemetry ping to custom endpoint")
  void testCustomEndpoint(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    // Call sendPing with custom checkpoint URL, no env opt-outs
    TelemetryReporter.sendPing(
        "production",
        "http://localhost:8080",
        false,
        null, // axonflowTelemetry
        customUrl // checkpointUrl
        );

    // Give the async call time to complete
    Thread.sleep(2000);

    verify(
        postRequestedFor(urlEqualTo("/v1/ping"))
            .withHeader("Content-Type", containing("application/json")));

    // Verify the request body has expected fields
    var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
    assertThat(requests).hasSize(1);

    JsonNode body = objectMapper.readTree(requests.get(0).getBodyAsString());
    assertThat(body.get("telemetry_type").asText()).isEqualTo("sdk");
    assertThat(body.get("sdk").asText()).isEqualTo("java");
    assertThat(body.get("sdk_version").asText()).isEqualTo(AxonFlowConfig.SDK_VERSION);
    // v1 schema: deployment_mode classifies from sdk endpoint host; localhost
    // resolves to self_hosted (the v1 allowlist removes the production label).
    assertThat(body.get("deployment_mode").asText()).isEqualTo("self_hosted");
    assertThat(body.has("profile")).isFalse();
    assertThat(body.get("instance_id").asText()).isNotEmpty();
    // production-mode payloads still omit stream on the wire.
    assertThat(body.has("stream")).isFalse();
  }

  @Test
  @DisplayName("should not send ping when telemetry is disabled via AXONFLOW_TELEMETRY=off")
  void testNoRequestWhenDisabled(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    TelemetryReporter.sendPing(
        "production",
        "http://localhost:8080",
        false,
        "off", // axonflowTelemetry = canonical opt-out
        customUrl);

    Thread.sleep(1000);

    verify(exactly(0), postRequestedFor(urlEqualTo("/v1/ping")));
  }

  @Test
  @DisplayName("should STILL send ping when only DO_NOT_TRACK=1 is set (DNT no longer honored)")
  void testRequestSentEvenWithDoNotTrackInProcessEnv(WireMockRuntimeInfo wmRuntimeInfo)
      throws Exception {
    // Note: this test passes axonflowTelemetry=null, which is what the public
    // sendPing wrapper would supply if DO_NOT_TRACK=1 were the only env signal.
    // The SDK does not read DO_NOT_TRACK at all, so a null axonflowTelemetry
    // means "no opt-out env" and telemetry should fire.
    stubFor(post("/v1/ping").willReturn(ok()));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    TelemetryReporter.sendPing(
        "production",
        "http://localhost:8080",
        false,
        null, // axonflowTelemetry = not set, telemetry should fire
        customUrl);

    Thread.sleep(1000);

    verify(exactly(1), postRequestedFor(urlEqualTo("/v1/ping")));
  }

  @Test
  @DisplayName("should silently handle connection failure")
  void testSilentFailure() {
    // Point to a port that is almost certainly not listening
    assertThatCode(
            () -> {
              TelemetryReporter.sendPing(
                  "production",
                  "http://localhost:8080",
                  false,
                  null,
                  "http://127.0.0.1:1" // port 1 - connection refused
                  );

              // Give the async call time to run and fail
              Thread.sleep(4000);
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "v8: ping fires in sandbox mode AND payload carries stream=\"sandbox\"")
  void shouldFirePingWithStreamSandboxInSandboxMode(WireMockRuntimeInfo wmRuntimeInfo)
      throws Exception {
    // v8 contract: sandbox-mode clients fire telemetry (v7 silently suppressed them) and
    // tag their payload with stream="sandbox" so analytics can distinguish dev/test pings
    // from production heartbeat. This is the headline behavioral flip.
    stubFor(post("/v1/ping").willReturn(ok()));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    TelemetryReporter.sendPing(
        "sandbox",
        "http://localhost:8080",
        false,
        null, // no env opt-out
        customUrl);

    Thread.sleep(2000);

    // Both the ping fires AND the stream tag is on the wire.
    verify(exactly(1), postRequestedFor(urlEqualTo("/v1/ping")));

    var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
    assertThat(requests).hasSize(1);
    JsonNode body = objectMapper.readTree(requests.get(0).getBodyAsString());
    // v1 schema: deployment_mode classifies from endpoint host (localhost ->
    // self_hosted), NOT from config.Mode. The sandbox marker lives on `stream`.
    assertThat(body.get("deployment_mode").asText()).isEqualTo("self_hosted");
    assertThat(body.get("stream")).isNotNull();
    assertThat(body.get("stream").asText()).isEqualTo("sandbox");
  }

  @Test
  @DisplayName("should send ping in production mode even without credentials")
  void testProductionModeWithoutCredentials(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    TelemetryReporter.sendPing(
        "production",
        "http://localhost:8080",
        false,
        null,
        customUrl);

    Thread.sleep(2000);

    verify(exactly(1), postRequestedFor(urlEqualTo("/v1/ping")));
  }

  // --- Additional tests for parity with Python SDK ---

  @Test
  @DisplayName("each buildPayload call should generate a unique instance_id")
  void testUniqueInstanceId() throws Exception {
    String payload1 = TelemetryReporter.buildPayload("production", null);
    String payload2 = TelemetryReporter.buildPayload("production", null);
    String payload3 = TelemetryReporter.buildPayload("production", null);

    JsonNode root1 = objectMapper.readTree(payload1);
    JsonNode root2 = objectMapper.readTree(payload2);
    JsonNode root3 = objectMapper.readTree(payload3);

    String id1 = root1.get("instance_id").asText();
    String id2 = root2.get("instance_id").asText();
    String id3 = root3.get("instance_id").asText();

    // All three should be valid UUIDs
    assertThat(id1).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(id2).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(id3).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    // All three should be distinct
    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).isNotEqualTo(id3);
    assertThat(id2).isNotEqualTo(id3);
  }

  // testConfigDisableInProduction and testSandboxModeDefaultOff were removed in v8.0 along
  // with the AxonFlowConfig.telemetry(Boolean) builder method and the mode-based default
  // suppression. AXONFLOW_TELEMETRY=off is the SOLE opt-out path; programmatic suppression
  // is no longer supported. See CHANGELOG v8.0.0.

  @Test
  @DisplayName("should silently handle server timeout without crashing")
  void testSilentFailureOnTimeout(WireMockRuntimeInfo wmRuntimeInfo) {
    // Delay response for 5 seconds, exceeding the 3s timeout
    stubFor(post("/v1/ping").willReturn(ok().withFixedDelay(5000)));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    assertThatCode(
            () -> {
              TelemetryReporter.sendPing(
                  "production",
                  "http://localhost:8080",
                  false,
                  null,
                  customUrl);

              // Wait long enough for the async call to hit the timeout and fail
              Thread.sleep(5000);
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should not crash when server returns HTTP 500")
  void testNon200ResponseNoCrash(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(post("/v1/ping").willReturn(serverError().withBody("Internal Server Error")));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    assertThatCode(
            () -> {
              TelemetryReporter.sendPing(
                  "production",
                  "http://localhost:8080",
                  false,
                  null,
                  customUrl);

              // Give the async call time to complete
              Thread.sleep(2000);
            })
        .doesNotThrowAnyException();

    // Verify the request was still made (the server just returned 500)
    verify(exactly(1), postRequestedFor(urlEqualTo("/v1/ping")));
  }

  @Test
  @DisplayName("AXONFLOW_TELEMETRY=off should skip POST in production")
  void testAxonflowTelemetrySkipsPost(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    TelemetryReporter.sendPing(
        "production",
        "http://localhost:8080",
        false,
        "off", // AXONFLOW_TELEMETRY=off
        customUrl);

    Thread.sleep(1000);

    verify(exactly(0), postRequestedFor(urlEqualTo("/v1/ping")));
  }

  @Test
  @DisplayName("should send correct payload fields in enterprise mode via HTTP")
  void testPayloadDeploymentModeEnterprise(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));

    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    // Use localhost:1 so detectPlatformVersion gets immediate connection-refused
    // (localhost:8080 may have a running service that returns a version)
    TelemetryReporter.sendPing(
        "enterprise",
        "http://localhost:1",
        false,
        null,
        customUrl);

    Thread.sleep(2000);

    verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/v1/ping"))
            .withHeader("Content-Type", containing("application/json")));

    var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
    assertThat(requests).hasSize(1);

    JsonNode body = objectMapper.readTree(requests.get(0).getBodyAsString());
    assertThat(body.get("sdk").asText()).isEqualTo("java");
    assertThat(body.get("sdk_version").asText()).isEqualTo(AxonFlowConfig.SDK_VERSION);
    // v1 schema: deployment_mode is endpoint-derived; localhost -> self_hosted.
    assertThat(body.get("deployment_mode").asText()).isEqualTo("self_hosted");
    assertThat(body.get("os").asText())
        .isEqualTo(TelemetryReporter.normalizeOS(System.getProperty("os.name")));
    assertThat(body.get("arch").asText())
        .isEqualTo(TelemetryReporter.normalizeArch(System.getProperty("os.arch")));
    assertThat(body.get("runtime_version").asText()).isEqualTo(System.getProperty("java.version"));
    assertThat(body.get("platform_version").isNull()).isTrue();
    assertThat(body.get("features").isArray()).isTrue();
    assertThat(body.get("instance_id").asText())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    // enterprise mode is not "sandbox", so stream is omitted
    assertThat(body.has("stream")).isFalse();
  }

  // --- v9.1 org_id tests (issue #2277) ---

  @Test
  @SetEnvironmentVariable(key = "ORG_ID", value = "acme-corp")
  @DisplayName("v9.1: telemetryOrgId returns ORG_ID env when set (operator-supplied)")
  void testTelemetryOrgIdEnvWins() {
    assertThat(TelemetryReporter.telemetryOrgId()).isEqualTo("acme-corp");
  }

  @Test
  @ClearEnvironmentVariable(key = "ORG_ID")
  @DisplayName("v9.1: telemetryOrgId returns local-dev-org sentinel when ORG_ID unset")
  void testTelemetryOrgIdSentinelWhenUnset() {
    assertThat(TelemetryReporter.telemetryOrgId())
        .isEqualTo(TelemetryReporter.ORG_ID_LOCAL_DEV_SENTINEL);
    assertThat(TelemetryReporter.ORG_ID_LOCAL_DEV_SENTINEL).isEqualTo("local-dev-org");
  }

  @Test
  @SetEnvironmentVariable(key = "ORG_ID", value = "")
  @DisplayName("v9.1: telemetryOrgId treats empty ORG_ID as unset")
  void testTelemetryOrgIdEmptyFallsThrough() {
    assertThat(TelemetryReporter.telemetryOrgId())
        .isEqualTo(TelemetryReporter.ORG_ID_LOCAL_DEV_SENTINEL);
  }

  @Test
  @SetEnvironmentVariable(key = "ORG_ID", value = "cs_e3a4b5c6-d7e8-4f90-a1b2-c3d4e5f6a7b8")
  @DisplayName("v9.1: telemetryOrgId passes through cs_<uuid> Community SaaS tenant identifier")
  void testTelemetryOrgIdCsPrefixedPassesThrough() {
    assertThat(TelemetryReporter.telemetryOrgId())
        .isEqualTo("cs_e3a4b5c6-d7e8-4f90-a1b2-c3d4e5f6a7b8");
  }

  @Test
  @SetEnvironmentVariable(key = "ORG_ID", value = "acme-corp")
  @DisplayName("v9.1: buildPayload includes ORG_ID env on the wire")
  void testPayloadIncludesOrgIdFromEnv() throws Exception {
    String payload = TelemetryReporter.buildPayload("production", null);
    JsonNode root = objectMapper.readTree(payload);
    assertThat(root.get("org_id").asText()).isEqualTo("acme-corp");
    // Wire-literal substring assertion defends against tag-removal mutations.
    assertThat(payload).contains("\"org_id\":\"acme-corp\"");
  }

  @Test
  @ClearEnvironmentVariable(key = "ORG_ID")
  @DisplayName("v9.1: buildPayload emits local-dev-org sentinel when ORG_ID unset")
  void testPayloadIncludesSentinelWhenUnset() throws Exception {
    String payload = TelemetryReporter.buildPayload("production", null);
    JsonNode root = objectMapper.readTree(payload);
    assertThat(root.get("org_id").asText()).isEqualTo("local-dev-org");
    assertThat(payload).contains("\"org_id\":\"local-dev-org\"");
  }

  @Test
  @SetEnvironmentVariable(key = "ORG_ID", value = "cs_f29e9c5c-5c5b-4e0d-8e0d-aabbccddeeff")
  @DisplayName("v9.1: buildPayload passes through cs_<uuid> on the wire")
  void testPayloadIncludesCsPrefixedTenant() throws Exception {
    String payload = TelemetryReporter.buildPayload("production", null);
    JsonNode root = objectMapper.readTree(payload);
    assertThat(root.get("org_id").asText())
        .isEqualTo("cs_f29e9c5c-5c5b-4e0d-8e0d-aabbccddeeff");
    assertThat(payload).contains("\"org_id\":\"cs_f29e9c5c-5c5b-4e0d-8e0d-aabbccddeeff\"");
  }

  @Test
  @SetEnvironmentVariable(key = "ORG_ID", value = "acme-corp")
  @DisplayName(
      "v9.1: functional E2E — ORG_ID arrives on the wire at the receiver (WireMock real HTTP)")
  void testOrgIdReachesReceiverOverHttp(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));
    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    TelemetryReporter.sendPing(
        "production", "http://localhost:8080", false, null, customUrl);
    Thread.sleep(2000);

    var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
    assertThat(requests).hasSize(1);
    String body = requests.get(0).getBodyAsString();
    JsonNode root = objectMapper.readTree(body);
    assertThat(root.get("org_id").asText()).isEqualTo("acme-corp");
    assertThat(body).contains("\"org_id\":\"acme-corp\"");
  }

  @Test
  @ClearEnvironmentVariable(key = "ORG_ID")
  @DisplayName("v9.1: functional E2E — sentinel arrives on the wire when ORG_ID unset")
  void testOrgIdSentinelReachesReceiver(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(post("/v1/ping").willReturn(ok()));
    String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

    TelemetryReporter.sendPing(
        "production", "http://localhost:8080", false, null, customUrl);
    Thread.sleep(2000);

    var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
    assertThat(requests).hasSize(1);
    String body = requests.get(0).getBodyAsString();
    JsonNode root = objectMapper.readTree(body);
    assertThat(root.get("org_id").asText()).isEqualTo("local-dev-org");
  }
}
