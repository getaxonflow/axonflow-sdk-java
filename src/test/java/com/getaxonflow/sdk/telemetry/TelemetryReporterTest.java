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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.getaxonflow.sdk.AxonFlowConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("TelemetryReporter")
@WireMockTest
class TelemetryReporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- isEnabled tests (using the 5-arg package-private method) ---

    @Test
    @DisplayName("should disable telemetry when DO_NOT_TRACK=1")
    void testTelemetryDisabledByDoNotTrack() {
        assertThat(TelemetryReporter.isEnabled("production", null, true, "1", null)).isFalse();
        assertThat(TelemetryReporter.isEnabled("production", Boolean.TRUE, true, "1", null)).isFalse();
        assertThat(TelemetryReporter.isEnabled("sandbox", null, true, "1", null)).isFalse();
    }

    @Test
    @DisplayName("should disable telemetry when AXONFLOW_TELEMETRY=off")
    void testTelemetryDisabledByAxonflowEnv() {
        assertThat(TelemetryReporter.isEnabled("production", null, true, null, "off")).isFalse();
        assertThat(TelemetryReporter.isEnabled("production", null, true, null, "OFF")).isFalse();
        assertThat(TelemetryReporter.isEnabled("production", Boolean.TRUE, true, null, "off")).isFalse();
    }

    @Test
    @DisplayName("should default telemetry OFF for sandbox mode")
    void testTelemetryDefaultOffForSandbox() {
        assertThat(TelemetryReporter.isEnabled("sandbox", null, true, null, null)).isFalse();
    }

    @Test
    @DisplayName("should default telemetry ON for production mode with credentials")
    void testTelemetryDefaultOnForProductionWithCredentials() {
        assertThat(TelemetryReporter.isEnabled("production", null, true, null, null)).isTrue();
    }

    @Test
    @DisplayName("should default telemetry ON for production mode even without credentials")
    void testTelemetryDefaultOnForProductionWithoutCredentials() {
        assertThat(TelemetryReporter.isEnabled("production", null, false, null, null)).isTrue();
    }

    @Test
    @DisplayName("should default telemetry ON for enterprise mode with credentials")
    void testTelemetryDefaultOnForEnterpriseWithCredentials() {
        assertThat(TelemetryReporter.isEnabled("enterprise", null, true, null, null)).isTrue();
    }

    @Test
    @DisplayName("should allow config override to enable telemetry in sandbox")
    void testTelemetryConfigOverrideEnable() {
        assertThat(TelemetryReporter.isEnabled("sandbox", Boolean.TRUE, false, null, null)).isTrue();
    }

    @Test
    @DisplayName("should allow config override to disable telemetry in production")
    void testTelemetryConfigOverrideDisable() {
        assertThat(TelemetryReporter.isEnabled("production", Boolean.FALSE, true, null, null)).isFalse();
    }

    @Test
    @DisplayName("DO_NOT_TRACK takes precedence over config override")
    void testDoNotTrackPrecedence() {
        assertThat(TelemetryReporter.isEnabled("production", Boolean.TRUE, true, "1", null)).isFalse();
    }

    @Test
    @DisplayName("AXONFLOW_TELEMETRY=off takes precedence over config override")
    void testAxonflowTelemetryPrecedence() {
        assertThat(TelemetryReporter.isEnabled("production", Boolean.TRUE, true, null, "off")).isFalse();
    }

    // --- Payload format test ---

    @Test
    @DisplayName("should produce correct payload JSON format")
    void testPayloadFormat() throws Exception {
        String payload = TelemetryReporter.buildPayload("production", null);
        JsonNode root = objectMapper.readTree(payload);

        assertThat(root.get("sdk").asText()).isEqualTo("java");
        assertThat(root.get("sdk_version").asText()).isEqualTo(AxonFlowConfig.SDK_VERSION);
        assertThat(root.get("platform_version").isNull()).isTrue();
        assertThat(root.get("os").asText()).isEqualTo(System.getProperty("os.name"));
        assertThat(root.get("arch").asText()).isEqualTo(System.getProperty("os.arch"));
        assertThat(root.get("runtime_version").asText()).isEqualTo(System.getProperty("java.version"));
        assertThat(root.get("deployment_mode").asText()).isEqualTo("production");
        assertThat(root.get("features").isArray()).isTrue();
        assertThat(root.get("features").size()).isEqualTo(0);
        assertThat(root.get("instance_id").asText()).isNotEmpty();
        // instance_id should be a valid UUID format
        assertThat(root.get("instance_id").asText()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("payload should reflect the given mode")
    void testPayloadModeReflection() throws Exception {
        String payload = TelemetryReporter.buildPayload("sandbox", null);
        JsonNode root = objectMapper.readTree(payload);
        assertThat(root.get("deployment_mode").asText()).isEqualTo("sandbox");
    }

    // --- HTTP integration tests ---

    @Test
    @DisplayName("should send telemetry ping to custom endpoint")
    void testCustomEndpoint(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        // Call sendPing with custom checkpoint URL, no env opt-outs, with credentials
        TelemetryReporter.sendPing(
                "production",
                "http://localhost:8080",
                null,
                false,
                true,   // hasCredentials
                null,   // doNotTrack
                null,   // axonflowTelemetry
                customUrl  // checkpointUrl
        );

        // Give the async call time to complete
        Thread.sleep(2000);

        verify(postRequestedFor(urlEqualTo("/v1/ping"))
                .withHeader("Content-Type", containing("application/json")));

        // Verify the request body has expected fields
        var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
        assertThat(requests).hasSize(1);

        JsonNode body = objectMapper.readTree(requests.get(0).getBodyAsString());
        assertThat(body.get("sdk").asText()).isEqualTo("java");
        assertThat(body.get("sdk_version").asText()).isEqualTo(AxonFlowConfig.SDK_VERSION);
        assertThat(body.get("deployment_mode").asText()).isEqualTo("production");
        assertThat(body.get("instance_id").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("should not send ping when telemetry is disabled")
    void testNoRequestWhenDisabled(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        // Disable via DO_NOT_TRACK
        TelemetryReporter.sendPing(
                "production",
                "http://localhost:8080",
                null,
                false,
                true,   // hasCredentials
                "1",    // doNotTrack = disabled
                null,
                customUrl
        );

        Thread.sleep(1000);

        verify(exactly(0), postRequestedFor(urlEqualTo("/v1/ping")));
    }

    @Test
    @DisplayName("should silently handle connection failure")
    void testSilentFailure() {
        // Point to a port that is almost certainly not listening
        assertThatCode(() -> {
            TelemetryReporter.sendPing(
                    "production",
                    "http://localhost:8080",
                    null,
                    false,
                    true,   // hasCredentials
                    null,
                    null,
                    "http://127.0.0.1:1" // port 1 - connection refused
            );

            // Give the async call time to run and fail
            Thread.sleep(4000);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should not send ping in sandbox mode without explicit enable")
    void testSandboxModeDefaultOff(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        TelemetryReporter.sendPing(
                "sandbox",
                "http://localhost:8080",
                null,   // no override
                false,
                true,   // hasCredentials
                null,
                null,
                customUrl
        );

        Thread.sleep(1000);

        verify(exactly(0), postRequestedFor(urlEqualTo("/v1/ping")));
    }

    @Test
    @DisplayName("should send ping in sandbox mode when explicitly enabled via config")
    void testSandboxModeExplicitEnable(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        TelemetryReporter.sendPing(
                "sandbox",
                "http://localhost:8080",
                Boolean.TRUE,   // explicit enable
                false,
                false,  // hasCredentials (doesn't matter with explicit override)
                null,
                null,
                customUrl
        );

        Thread.sleep(2000);

        verify(exactly(1), postRequestedFor(urlEqualTo("/v1/ping")));
    }

    @Test
    @DisplayName("should send ping in production mode even without credentials")
    void testProductionModeWithoutCredentials(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        TelemetryReporter.sendPing(
                "production",
                "http://localhost:8080",
                null,   // no override
                false,
                false,  // no credentials — no longer affects default
                null,
                null,
                customUrl
        );

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

    @Test
    @DisplayName("config false in production should skip POST even with credentials")
    void testConfigDisableInProduction(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        TelemetryReporter.sendPing(
                "production",
                "http://localhost:8080",
                Boolean.FALSE,  // config override disables
                false,
                true,   // hasCredentials (would normally enable)
                null,
                null,
                customUrl
        );

        Thread.sleep(1000);

        verify(exactly(0), postRequestedFor(urlEqualTo("/v1/ping")));
    }

    @Test
    @DisplayName("should silently handle server timeout without crashing")
    void testSilentFailureOnTimeout(WireMockRuntimeInfo wmRuntimeInfo) {
        // Delay response for 5 seconds, exceeding the 3s timeout
        stubFor(post("/v1/ping").willReturn(ok().withFixedDelay(5000)));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        assertThatCode(() -> {
            TelemetryReporter.sendPing(
                    "production",
                    "http://localhost:8080",
                    null,
                    false,
                    true,   // hasCredentials
                    null,
                    null,
                    customUrl
            );

            // Wait long enough for the async call to hit the timeout and fail
            Thread.sleep(5000);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should not crash when server returns HTTP 500")
    void testNon200ResponseNoCrash(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(post("/v1/ping").willReturn(serverError().withBody("Internal Server Error")));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        assertThatCode(() -> {
            TelemetryReporter.sendPing(
                    "production",
                    "http://localhost:8080",
                    null,
                    false,
                    true,   // hasCredentials
                    null,
                    null,
                    customUrl
            );

            // Give the async call time to complete
            Thread.sleep(2000);
        }).doesNotThrowAnyException();

        // Verify the request was still made (the server just returned 500)
        verify(exactly(1), postRequestedFor(urlEqualTo("/v1/ping")));
    }

    @Test
    @DisplayName("AXONFLOW_TELEMETRY=off should skip POST even with credentials in production")
    void testAxonflowTelemetrySkipsPost(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        TelemetryReporter.sendPing(
                "production",
                "http://localhost:8080",
                null,
                false,
                true,   // hasCredentials
                null,
                "off",  // AXONFLOW_TELEMETRY=off
                customUrl
        );

        Thread.sleep(1000);

        verify(exactly(0), postRequestedFor(urlEqualTo("/v1/ping")));
    }

    @Test
    @DisplayName("should send correct payload fields in enterprise mode via HTTP")
    void testPayloadDeploymentModeEnterprise(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/v1/ping").willReturn(ok()));

        String customUrl = wmRuntimeInfo.getHttpBaseUrl() + "/v1/ping";

        TelemetryReporter.sendPing(
                "enterprise",
                "http://localhost:8080",
                null,
                false,
                true,   // hasCredentials
                null,
                null,
                customUrl
        );

        Thread.sleep(2000);

        verify(exactly(1), postRequestedFor(urlEqualTo("/v1/ping"))
                .withHeader("Content-Type", containing("application/json")));

        var requests = WireMock.findAll(postRequestedFor(urlEqualTo("/v1/ping")));
        assertThat(requests).hasSize(1);

        JsonNode body = objectMapper.readTree(requests.get(0).getBodyAsString());
        assertThat(body.get("sdk").asText()).isEqualTo("java");
        assertThat(body.get("sdk_version").asText()).isEqualTo(AxonFlowConfig.SDK_VERSION);
        assertThat(body.get("deployment_mode").asText()).isEqualTo("enterprise");
        assertThat(body.get("os").asText()).isEqualTo(System.getProperty("os.name"));
        assertThat(body.get("arch").asText()).isEqualTo(System.getProperty("os.arch"));
        assertThat(body.get("runtime_version").asText()).isEqualTo(System.getProperty("java.version"));
        assertThat(body.get("platform_version").isNull()).isTrue();
        assertThat(body.get("features").isArray()).isTrue();
        assertThat(body.get("instance_id").asText()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
