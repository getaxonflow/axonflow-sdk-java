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
package com.getaxonflow.sdk;

import com.getaxonflow.sdk.exceptions.ConfigurationException;
import com.getaxonflow.sdk.types.Mode;
import com.getaxonflow.sdk.util.CacheConfig;
import com.getaxonflow.sdk.util.RetryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AxonFlowConfig")
class AxonFlowConfigTest {

    @Test
    @DisplayName("should create config with minimal localhost settings")
    void shouldCreateMinimalLocalhostConfig() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .build();

        assertThat(config.getAgentUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.isLocalhost()).isTrue();
        assertThat(config.getMode()).isEqualTo(Mode.PRODUCTION);
        assertThat(config.getTimeout()).isEqualTo(AxonFlowConfig.DEFAULT_TIMEOUT);
    }

    @Test
    @DisplayName("should create config with all settings")
    void shouldCreateFullConfig() {
        RetryConfig retryConfig = RetryConfig.builder()
            .maxAttempts(5)
            .initialDelay(Duration.ofMillis(500))
            .build();

        CacheConfig cacheConfig = CacheConfig.builder()
            .ttl(Duration.ofMinutes(5))
            .maxSize(500)
            .build();

        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("https://api.example.com")
            .clientId("test-client")
            .clientSecret("test-secret")
            .licenseKey("test-license")
            .mode(Mode.SANDBOX)
            .timeout(Duration.ofSeconds(30))
            .debug(true)
            .insecureSkipVerify(true)
            .retryConfig(retryConfig)
            .cacheConfig(cacheConfig)
            .userAgent("custom-agent/1.0")
            .build();

        assertThat(config.getAgentUrl()).isEqualTo("https://api.example.com");
        assertThat(config.getClientId()).isEqualTo("test-client");
        assertThat(config.getClientSecret()).isEqualTo("test-secret");
        assertThat(config.getLicenseKey()).isEqualTo("test-license");
        assertThat(config.getMode()).isEqualTo(Mode.SANDBOX);
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.isDebug()).isTrue();
        assertThat(config.isInsecureSkipVerify()).isTrue();
        assertThat(config.getRetryConfig()).isEqualTo(retryConfig);
        assertThat(config.getCacheConfig()).isEqualTo(cacheConfig);
        assertThat(config.getUserAgent()).isEqualTo("custom-agent/1.0");
    }

    @Test
    @DisplayName("should normalize URL by removing trailing slash")
    void shouldNormalizeUrl() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080/")
            .build();

        assertThat(config.getAgentUrl()).isEqualTo("http://localhost:8080");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080"})
    @DisplayName("should detect localhost URLs")
    void shouldDetectLocalhost(String url) {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl(url)
            .build();

        assertThat(config.isLocalhost()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://api.example.com", "https://staging.getaxonflow.com"})
    @DisplayName("should detect non-localhost URLs")
    void shouldDetectNonLocalhost(String url) {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl(url)
            .licenseKey("test-key")
            .build();

        assertThat(config.isLocalhost()).isFalse();
    }

    @Test
    @DisplayName("should require auth for non-localhost without license key")
    void shouldRequireAuthForNonLocalhost() {
        assertThatThrownBy(() -> AxonFlowConfig.builder()
            .agentUrl("https://api.example.com")
            .build())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("licenseKey")
            .hasMessageContaining("clientId");
    }

    @Test
    @DisplayName("should accept license key for non-localhost")
    void shouldAcceptLicenseKeyForNonLocalhost() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("https://api.example.com")
            .licenseKey("test-license")
            .build();

        assertThat(config.getLicenseKey()).isEqualTo("test-license");
    }

    @Test
    @DisplayName("should accept client credentials for non-localhost")
    void shouldAcceptClientCredentialsForNonLocalhost() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("https://api.example.com")
            .clientId("test-client")
            .clientSecret("test-secret")
            .build();

        assertThat(config.getClientId()).isEqualTo("test-client");
        assertThat(config.getClientSecret()).isEqualTo("test-secret");
    }

    @Test
    @DisplayName("should use default retry config")
    void shouldUseDefaultRetryConfig() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .build();

        assertThat(config.getRetryConfig()).isNotNull();
        assertThat(config.getRetryConfig().isEnabled()).isTrue();
        assertThat(config.getRetryConfig().getMaxAttempts()).isEqualTo(RetryConfig.DEFAULT_MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("should use default cache config")
    void shouldUseDefaultCacheConfig() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .build();

        assertThat(config.getCacheConfig()).isNotNull();
        assertThat(config.getCacheConfig().isEnabled()).isTrue();
        assertThat(config.getCacheConfig().getTtl()).isEqualTo(CacheConfig.DEFAULT_TTL);
    }

    @Test
    @DisplayName("should use default user agent")
    void shouldUseDefaultUserAgent() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .build();

        assertThat(config.getUserAgent()).startsWith("axonflow-java-sdk/");
    }

    @Test
    @DisplayName("should implement equals and hashCode")
    void shouldImplementEqualsAndHashCode() {
        AxonFlowConfig config1 = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .clientId("test")
            .build();

        AxonFlowConfig config2 = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .clientId("test")
            .build();

        AxonFlowConfig config3 = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8081")
            .build();

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        assertThat(config1).isNotEqualTo(config3);
    }

    @Test
    @DisplayName("should have meaningful toString")
    void shouldHaveMeaningfulToString() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .clientId("test-client")
            .mode(Mode.SANDBOX)
            .build();

        String str = config.toString();
        assertThat(str).contains("localhost:8080");
        assertThat(str).contains("test-client");
        assertThat(str).contains("SANDBOX");
        // Should not contain secrets
        assertThat(str).doesNotContain("secret");
    }
}
