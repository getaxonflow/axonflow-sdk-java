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

import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for circuit breaker observability methods.
 */
@WireMockTest
@DisplayName("Circuit Breaker Observability")
class CircuitBreakerTest {

    private AxonFlow axonflow;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        axonflow = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .clientId("test-client").clientSecret("test-secret")
            .build());
    }

    // ========================================================================
    // getCircuitBreakerStatus
    // ========================================================================

    @Test
    @DisplayName("should get circuit breaker status with active circuits")
    void shouldGetCircuitBreakerStatus() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/status"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"active_circuits\":[{\"scope\":\"provider\",\"scope_id\":\"openai\",\"state\":\"open\",\"error_count\":15}],\"count\":1,\"emergency_stop_active\":false}}")));

        CircuitBreakerStatusResponse status = axonflow.getCircuitBreakerStatus();

        assertThat(status).isNotNull();
        assertThat(status.getCount()).isEqualTo(1);
        assertThat(status.isEmergencyStopActive()).isFalse();
        assertThat(status.getActiveCircuits()).hasSize(1);
        assertThat(status.getActiveCircuits().get(0).get("scope")).isEqualTo("provider");
        assertThat(status.getActiveCircuits().get(0).get("scope_id")).isEqualTo("openai");

        verify(getRequestedFor(urlEqualTo("/api/v1/circuit-breaker/status")));
    }

    @Test
    @DisplayName("should get circuit breaker status with no active circuits")
    void shouldGetCircuitBreakerStatusEmpty() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/status"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"active_circuits\":[],\"count\":0,\"emergency_stop_active\":false}}")));

        CircuitBreakerStatusResponse status = axonflow.getCircuitBreakerStatus();

        assertThat(status).isNotNull();
        assertThat(status.getCount()).isEqualTo(0);
        assertThat(status.getActiveCircuits()).isEmpty();
    }

    @Test
    @DisplayName("should get circuit breaker status with emergency stop active")
    void shouldGetCircuitBreakerStatusEmergencyStop() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/status"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"active_circuits\":[],\"count\":0,\"emergency_stop_active\":true}}")));

        CircuitBreakerStatusResponse status = axonflow.getCircuitBreakerStatus();

        assertThat(status.isEmergencyStopActive()).isTrue();
    }

    @Test
    @DisplayName("getCircuitBreakerStatusAsync should return future")
    void getCircuitBreakerStatusAsyncShouldReturnFuture() throws Exception {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/status"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"active_circuits\":[],\"count\":0,\"emergency_stop_active\":false}}")));

        CompletableFuture<CircuitBreakerStatusResponse> future = axonflow.getCircuitBreakerStatusAsync();
        CircuitBreakerStatusResponse status = future.get();

        assertThat(status).isNotNull();
        assertThat(status.getCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should handle server error on status")
    void shouldHandleServerErrorOnStatus() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/status"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"internal server error\"}")));

        assertThatThrownBy(() -> axonflow.getCircuitBreakerStatus())
            .isInstanceOf(AxonFlowException.class);
    }

    // ========================================================================
    // getCircuitBreakerHistory
    // ========================================================================

    @Test
    @DisplayName("should get circuit breaker history")
    void shouldGetCircuitBreakerHistory() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/history?limit=10"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"history\":[{\"id\":\"cb_001\",\"org_id\":\"org_1\",\"scope\":\"provider\",\"scope_id\":\"openai\",\"state\":\"open\",\"trip_reason\":\"error_threshold\",\"tripped_by\":\"system\",\"tripped_at\":\"2026-03-16T10:00:00Z\",\"expires_at\":\"2026-03-16T10:05:00Z\",\"reset_by\":null,\"reset_at\":null,\"error_count\":15,\"violation_count\":0}],\"count\":1}}")));

        CircuitBreakerHistoryResponse history = axonflow.getCircuitBreakerHistory(10);

        assertThat(history).isNotNull();
        assertThat(history.getCount()).isEqualTo(1);
        assertThat(history.getHistory()).hasSize(1);

        CircuitBreakerHistoryEntry entry = history.getHistory().get(0);
        assertThat(entry.getId()).isEqualTo("cb_001");
        assertThat(entry.getOrgId()).isEqualTo("org_1");
        assertThat(entry.getScope()).isEqualTo("provider");
        assertThat(entry.getScopeId()).isEqualTo("openai");
        assertThat(entry.getState()).isEqualTo("open");
        assertThat(entry.getTripReason()).isEqualTo("error_threshold");
        assertThat(entry.getTrippedBy()).isEqualTo("system");
        assertThat(entry.getTrippedAt()).isEqualTo("2026-03-16T10:00:00Z");
        assertThat(entry.getExpiresAt()).isEqualTo("2026-03-16T10:05:00Z");
        assertThat(entry.getResetBy()).isNull();
        assertThat(entry.getResetAt()).isNull();
        assertThat(entry.getErrorCount()).isEqualTo(15);
        assertThat(entry.getViolationCount()).isEqualTo(0);

        verify(getRequestedFor(urlEqualTo("/api/v1/circuit-breaker/history?limit=10")));
    }

    @Test
    @DisplayName("should get empty circuit breaker history")
    void shouldGetEmptyCircuitBreakerHistory() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/history?limit=50"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"history\":[],\"count\":0}}")));

        CircuitBreakerHistoryResponse history = axonflow.getCircuitBreakerHistory(50);

        assertThat(history).isNotNull();
        assertThat(history.getCount()).isEqualTo(0);
        assertThat(history.getHistory()).isEmpty();
    }

    @Test
    @DisplayName("should reject invalid limit")
    void shouldRejectInvalidLimit() {
        assertThatThrownBy(() -> axonflow.getCircuitBreakerHistory(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit must be at least 1");

        assertThatThrownBy(() -> axonflow.getCircuitBreakerHistory(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit must be at least 1");
    }

    @Test
    @DisplayName("getCircuitBreakerHistoryAsync should return future")
    void getCircuitBreakerHistoryAsyncShouldReturnFuture() throws Exception {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/history?limit=25"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"history\":[],\"count\":0}}")));

        CompletableFuture<CircuitBreakerHistoryResponse> future = axonflow.getCircuitBreakerHistoryAsync(25);
        CircuitBreakerHistoryResponse history = future.get();

        assertThat(history).isNotNull();
        assertThat(history.getCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should handle server error on history")
    void shouldHandleServerErrorOnHistory() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/history?limit=10"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"internal server error\"}")));

        assertThatThrownBy(() -> axonflow.getCircuitBreakerHistory(10))
            .isInstanceOf(AxonFlowException.class);
    }

    // ========================================================================
    // getCircuitBreakerConfig
    // ========================================================================

    @Test
    @DisplayName("should get circuit breaker config for tenant")
    void shouldGetCircuitBreakerConfig() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/config?tenant_id=tenant_123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"source\":\"tenant_override\",\"error_threshold\":10,\"violation_threshold\":5,\"window_seconds\":300,\"default_timeout_seconds\":60,\"max_timeout_seconds\":600,\"enable_auto_recovery\":true,\"tenant_id\":\"tenant_123\",\"overrides\":{\"provider_openai\":{\"error_threshold\":20}}}}")));

        CircuitBreakerConfig config = axonflow.getCircuitBreakerConfig("tenant_123");

        assertThat(config).isNotNull();
        assertThat(config.getSource()).isEqualTo("tenant_override");
        assertThat(config.getErrorThreshold()).isEqualTo(10);
        assertThat(config.getViolationThreshold()).isEqualTo(5);
        assertThat(config.getWindowSeconds()).isEqualTo(300);
        assertThat(config.getDefaultTimeoutSeconds()).isEqualTo(60);
        assertThat(config.getMaxTimeoutSeconds()).isEqualTo(600);
        assertThat(config.isEnableAutoRecovery()).isTrue();
        assertThat(config.getTenantId()).isEqualTo("tenant_123");
        assertThat(config.getOverrides()).isNotNull();
        assertThat(config.getOverrides()).containsKey("provider_openai");

        verify(getRequestedFor(urlEqualTo("/api/v1/circuit-breaker/config?tenant_id=tenant_123")));
    }

    @Test
    @DisplayName("should get circuit breaker config with defaults")
    void shouldGetCircuitBreakerConfigDefaults() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/config?tenant_id=new_tenant"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"source\":\"default\",\"error_threshold\":5,\"violation_threshold\":3,\"window_seconds\":60,\"default_timeout_seconds\":30,\"max_timeout_seconds\":300,\"enable_auto_recovery\":false,\"tenant_id\":\"new_tenant\"}}")));

        CircuitBreakerConfig config = axonflow.getCircuitBreakerConfig("new_tenant");

        assertThat(config).isNotNull();
        assertThat(config.getSource()).isEqualTo("default");
        assertThat(config.getOverrides()).isNull();
    }

    @Test
    @DisplayName("should reject null tenantId for getConfig")
    void shouldRejectNullTenantIdForGetConfig() {
        assertThatThrownBy(() -> axonflow.getCircuitBreakerConfig(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId cannot be null");
    }

    @Test
    @DisplayName("should reject empty tenantId for getConfig")
    void shouldRejectEmptyTenantIdForGetConfig() {
        assertThatThrownBy(() -> axonflow.getCircuitBreakerConfig(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId cannot be empty");
    }

    @Test
    @DisplayName("getCircuitBreakerConfigAsync should return future")
    void getCircuitBreakerConfigAsyncShouldReturnFuture() throws Exception {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/config?tenant_id=async_tenant"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"source\":\"default\",\"error_threshold\":5,\"violation_threshold\":3,\"window_seconds\":60,\"default_timeout_seconds\":30,\"max_timeout_seconds\":300,\"enable_auto_recovery\":false,\"tenant_id\":\"async_tenant\"}}")));

        CompletableFuture<CircuitBreakerConfig> future = axonflow.getCircuitBreakerConfigAsync("async_tenant");
        CircuitBreakerConfig config = future.get();

        assertThat(config).isNotNull();
        assertThat(config.getTenantId()).isEqualTo("async_tenant");
    }

    @Test
    @DisplayName("should handle server error on getConfig")
    void shouldHandleServerErrorOnGetConfig() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/config?tenant_id=bad_tenant"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"internal server error\"}")));

        assertThatThrownBy(() -> axonflow.getCircuitBreakerConfig("bad_tenant"))
            .isInstanceOf(AxonFlowException.class);
    }

    // ========================================================================
    // updateCircuitBreakerConfig
    // ========================================================================

    @Test
    @DisplayName("should update circuit breaker config")
    void shouldUpdateCircuitBreakerConfig() {
        stubFor(put(urlEqualTo("/api/v1/circuit-breaker/config"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"tenant_id\":\"tenant_123\",\"message\":\"Circuit breaker config updated for tenant\"}}")));

        CircuitBreakerConfigUpdate update = CircuitBreakerConfigUpdate.builder()
            .tenantId("tenant_123")
            .errorThreshold(10)
            .violationThreshold(5)
            .windowSeconds(300)
            .defaultTimeoutSeconds(60)
            .maxTimeoutSeconds(600)
            .enableAutoRecovery(true)
            .build();

        CircuitBreakerConfigUpdateResponse result = axonflow.updateCircuitBreakerConfig(update);

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo("tenant_123");
        assertThat(result.getMessage()).isNotEmpty();

        verify(putRequestedFor(urlEqualTo("/api/v1/circuit-breaker/config"))
            .withRequestBody(matchingJsonPath("$.tenant_id", equalTo("tenant_123")))
            .withRequestBody(matchingJsonPath("$.error_threshold", equalTo("10")))
            .withRequestBody(matchingJsonPath("$.violation_threshold", equalTo("5"))));
    }

    @Test
    @DisplayName("should update circuit breaker config with partial fields")
    void shouldUpdateCircuitBreakerConfigPartial() {
        stubFor(put(urlEqualTo("/api/v1/circuit-breaker/config"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"tenant_id\":\"tenant_456\",\"message\":\"Circuit breaker config updated for tenant\"}}")));

        CircuitBreakerConfigUpdate update = CircuitBreakerConfigUpdate.builder()
            .tenantId("tenant_456")
            .errorThreshold(20)
            .build();

        CircuitBreakerConfigUpdateResponse result = axonflow.updateCircuitBreakerConfig(update);

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo("tenant_456");

        verify(putRequestedFor(urlEqualTo("/api/v1/circuit-breaker/config"))
            .withRequestBody(matchingJsonPath("$.tenant_id", equalTo("tenant_456")))
            .withRequestBody(matchingJsonPath("$.error_threshold", equalTo("20"))));
    }

    @Test
    @DisplayName("should reject null config for update")
    void shouldRejectNullConfigForUpdate() {
        assertThatThrownBy(() -> axonflow.updateCircuitBreakerConfig(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("config cannot be null");
    }

    @Test
    @DisplayName("should reject null tenantId in config update builder")
    void shouldRejectNullTenantIdInConfigUpdateBuilder() {
        assertThatThrownBy(() -> CircuitBreakerConfigUpdate.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId cannot be null");
    }

    @Test
    @DisplayName("should reject empty tenantId in config update builder")
    void shouldRejectEmptyTenantIdInConfigUpdateBuilder() {
        assertThatThrownBy(() -> CircuitBreakerConfigUpdate.builder().tenantId("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId cannot be empty");
    }

    @Test
    @DisplayName("updateCircuitBreakerConfigAsync should return future")
    void updateCircuitBreakerConfigAsyncShouldReturnFuture() throws Exception {
        stubFor(put(urlEqualTo("/api/v1/circuit-breaker/config"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"tenant_id\":\"tenant_async\",\"message\":\"Circuit breaker config updated for tenant\"}}")));

        CircuitBreakerConfigUpdate update = CircuitBreakerConfigUpdate.builder()
            .tenantId("tenant_async")
            .errorThreshold(10)
            .build();

        CompletableFuture<CircuitBreakerConfigUpdateResponse> future = axonflow.updateCircuitBreakerConfigAsync(update);
        CircuitBreakerConfigUpdateResponse result = future.get();

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo("tenant_async");
    }

    @Test
    @DisplayName("should handle server error on updateConfig")
    void shouldHandleServerErrorOnUpdateConfig() {
        stubFor(put(urlEqualTo("/api/v1/circuit-breaker/config"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"internal server error\"}")));

        CircuitBreakerConfigUpdate update = CircuitBreakerConfigUpdate.builder()
            .tenantId("failing_tenant")
            .errorThreshold(10)
            .build();

        assertThatThrownBy(() -> axonflow.updateCircuitBreakerConfig(update))
            .isInstanceOf(AxonFlowException.class);
    }

    // ========================================================================
    // Response without wrapper (fallback)
    // ========================================================================

    @Test
    @DisplayName("should handle unwrapped response for status")
    void shouldHandleUnwrappedResponseForStatus() {
        stubFor(get(urlEqualTo("/api/v1/circuit-breaker/status"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"active_circuits\":[],\"count\":0,\"emergency_stop_active\":false}")));

        CircuitBreakerStatusResponse status = axonflow.getCircuitBreakerStatus();

        assertThat(status).isNotNull();
        assertThat(status.getCount()).isEqualTo(0);
    }
}
