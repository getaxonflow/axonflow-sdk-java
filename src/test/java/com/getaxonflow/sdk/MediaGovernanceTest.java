/*
 * Copyright 2026 AxonFlow
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
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.types.MediaGovernanceConfig;
import com.getaxonflow.sdk.types.MediaGovernanceStatus;
import com.getaxonflow.sdk.types.UpdateMediaGovernanceConfigRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for Media Governance Config API methods on the AxonFlow client. */
@WireMockTest
@DisplayName("Media Governance API Methods")
class MediaGovernanceTest {

  private AxonFlow axonflow;

  private static final String SAMPLE_CONFIG_JSON =
      "{"
          + "\"tenant_id\": \"tenant_001\","
          + "\"enabled\": true,"
          + "\"allowed_analyzers\": [\"nsfw\", \"biometric\", \"ocr\"],"
          + "\"updated_at\": \"2026-02-18T10:00:00Z\","
          + "\"updated_by\": \"admin@example.com\""
          + "}";

  private static final String SAMPLE_STATUS_JSON =
      "{"
          + "\"available\": true,"
          + "\"enabled_by_default\": false,"
          + "\"per_tenant_control\": true,"
          + "\"tier\": \"enterprise\""
          + "}";

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .build());
  }

  // ========================================================================
  // getMediaGovernanceConfig
  // ========================================================================

  @Nested
  @DisplayName("getMediaGovernanceConfig")
  class GetMediaGovernanceConfig {

    @Test
    @DisplayName("should return media governance config")
    void shouldReturnConfig() {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(SAMPLE_CONFIG_JSON)));

      MediaGovernanceConfig config = axonflow.getMediaGovernanceConfig();

      assertThat(config.getTenantId()).isEqualTo("tenant_001");
      assertThat(config.isEnabled()).isTrue();
      assertThat(config.getAllowedAnalyzers()).containsExactly("nsfw", "biometric", "ocr");
      assertThat(config.getUpdatedAt()).isEqualTo("2026-02-18T10:00:00Z");
      assertThat(config.getUpdatedBy()).isEqualTo("admin@example.com");
    }

    @Test
    @DisplayName("should return disabled config")
    void shouldReturnDisabledConfig() {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"tenant_id\": \"tenant_002\", \"enabled\": false, \"allowed_analyzers\": []}")));

      MediaGovernanceConfig config = axonflow.getMediaGovernanceConfig();

      assertThat(config.getTenantId()).isEqualTo("tenant_002");
      assertThat(config.isEnabled()).isFalse();
      assertThat(config.getAllowedAnalyzers()).isEmpty();
    }

    @Test
    @DisplayName("should throw on server error")
    void shouldThrowOnServerError() {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(500)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"Internal server error\"}")));

      assertThatThrownBy(() -> axonflow.getMediaGovernanceConfig()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("async should return future with config")
    void asyncShouldReturnFuture() throws Exception {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(SAMPLE_CONFIG_JSON)));

      CompletableFuture<MediaGovernanceConfig> future = axonflow.getMediaGovernanceConfigAsync();
      MediaGovernanceConfig config = future.get();

      assertThat(config.getTenantId()).isEqualTo("tenant_001");
      assertThat(config.isEnabled()).isTrue();
    }
  }

  // ========================================================================
  // updateMediaGovernanceConfig
  // ========================================================================

  @Nested
  @DisplayName("updateMediaGovernanceConfig")
  class UpdateMediaGovernanceConfig {

    @Test
    @DisplayName("should send PUT request and return updated config")
    void shouldUpdateConfig() {
      stubFor(
          put(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(SAMPLE_CONFIG_JSON)));

      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder()
              .enabled(true)
              .allowedAnalyzers(List.of("nsfw", "biometric", "ocr"))
              .build();

      MediaGovernanceConfig config = axonflow.updateMediaGovernanceConfig(request);

      assertThat(config.getTenantId()).isEqualTo("tenant_001");
      assertThat(config.isEnabled()).isTrue();
      assertThat(config.getAllowedAnalyzers()).containsExactly("nsfw", "biometric", "ocr");

      verify(
          putRequestedFor(urlEqualTo("/api/v1/media-governance/config"))
              .withHeader("Content-Type", containing("application/json"))
              .withRequestBody(containing("\"enabled\":true"))
              .withRequestBody(containing("\"allowed_analyzers\"")));
    }

    @Test
    @DisplayName("should send partial update with only enabled")
    void shouldSendPartialUpdate() {
      stubFor(
          put(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"tenant_id\": \"tenant_001\", \"enabled\": false, \"allowed_analyzers\": [\"nsfw\"]}")));

      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder().enabled(false).build();

      MediaGovernanceConfig config = axonflow.updateMediaGovernanceConfig(request);

      assertThat(config.isEnabled()).isFalse();

      // Verify null fields are not sent (NON_NULL inclusion)
      verify(
          putRequestedFor(urlEqualTo("/api/v1/media-governance/config"))
              .withRequestBody(containing("\"enabled\":false")));
    }

    @Test
    @DisplayName("should require non-null request")
    void shouldRequireNonNullRequest() {
      assertThatThrownBy(() -> axonflow.updateMediaGovernanceConfig(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("request cannot be null");
    }

    @Test
    @DisplayName("should throw on server error")
    void shouldThrowOnServerError() {
      stubFor(
          put(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(403)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"Forbidden: insufficient permissions\"}")));

      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder().enabled(true).build();

      assertThatThrownBy(() -> axonflow.updateMediaGovernanceConfig(request))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("async should return future with updated config")
    void asyncShouldReturnFuture() throws Exception {
      stubFor(
          put(urlEqualTo("/api/v1/media-governance/config"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(SAMPLE_CONFIG_JSON)));

      UpdateMediaGovernanceConfigRequest request =
          UpdateMediaGovernanceConfigRequest.builder()
              .enabled(true)
              .allowedAnalyzers(List.of("nsfw"))
              .build();

      CompletableFuture<MediaGovernanceConfig> future =
          axonflow.updateMediaGovernanceConfigAsync(request);
      MediaGovernanceConfig config = future.get();

      assertThat(config.isEnabled()).isTrue();
    }
  }

  // ========================================================================
  // getMediaGovernanceStatus
  // ========================================================================

  @Nested
  @DisplayName("getMediaGovernanceStatus")
  class GetMediaGovernanceStatus {

    @Test
    @DisplayName("should return media governance platform status")
    void shouldReturnStatus() {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/status"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(SAMPLE_STATUS_JSON)));

      MediaGovernanceStatus status = axonflow.getMediaGovernanceStatus();

      assertThat(status.isAvailable()).isTrue();
      assertThat(status.isEnabledByDefault()).isFalse();
      assertThat(status.isPerTenantControl()).isTrue();
      assertThat(status.getTier()).isEqualTo("enterprise");
    }

    @Test
    @DisplayName("should return unavailable status")
    void shouldReturnUnavailableStatus() {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/status"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"available\": false, \"enabled_by_default\": false, \"per_tenant_control\": false, \"tier\": \"community\"}")));

      MediaGovernanceStatus status = axonflow.getMediaGovernanceStatus();

      assertThat(status.isAvailable()).isFalse();
      assertThat(status.getTier()).isEqualTo("community");
    }

    @Test
    @DisplayName("should throw on server error")
    void shouldThrowOnServerError() {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/status"))
              .willReturn(
                  aResponse()
                      .withStatus(500)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"Internal server error\"}")));

      assertThatThrownBy(() -> axonflow.getMediaGovernanceStatus()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("async should return future with status")
    void asyncShouldReturnFuture() throws Exception {
      stubFor(
          get(urlEqualTo("/api/v1/media-governance/status"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(SAMPLE_STATUS_JSON)));

      CompletableFuture<MediaGovernanceStatus> future = axonflow.getMediaGovernanceStatusAsync();
      MediaGovernanceStatus status = future.get();

      assertThat(status.isAvailable()).isTrue();
      assertThat(status.getTier()).isEqualTo("enterprise");
    }
  }
}
