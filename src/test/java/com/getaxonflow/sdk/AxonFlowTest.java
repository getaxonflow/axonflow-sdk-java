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

import com.getaxonflow.sdk.exceptions.*;
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
@DisplayName("AxonFlow Client")
class AxonFlowTest {

    private AxonFlow axonflow;
    private String baseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        axonflow = AxonFlow.create(AxonFlowConfig.builder()
            .agentUrl(baseUrl)
            .build());
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    @Test
    @DisplayName("builder should return AxonFlowConfig.Builder")
    void builderShouldReturnConfigBuilder() {
        AxonFlowConfig.Builder builder = AxonFlow.builder();
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("create should require non-null config")
    void createShouldRequireConfig() {
        assertThatThrownBy(() -> AxonFlow.create(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("sandbox should create client in sandbox mode")
    void sandboxShouldCreateSandboxClient(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow sandbox = AxonFlow.sandbox(wmRuntimeInfo.getHttpBaseUrl());
        assertThat(sandbox.getConfig().getMode()).isEqualTo(Mode.SANDBOX);
    }

    // ========================================================================
    // Health Check
    // ========================================================================

    @Test
    @DisplayName("healthCheck should return status")
    void healthCheckShouldReturnStatus() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"healthy\",\"version\":\"1.0.0\"}")));

        HealthStatus health = axonflow.healthCheck();

        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("healthCheckAsync should return future")
    void healthCheckAsyncShouldReturnFuture() throws Exception {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"healthy\"}")));

        CompletableFuture<HealthStatus> future = axonflow.healthCheckAsync();
        HealthStatus health = future.get();

        assertThat(health.isHealthy()).isTrue();
    }

    // ========================================================================
    // Gateway Mode - Pre-check
    // ========================================================================

    @Test
    @DisplayName("getPolicyApprovedContext should require non-null request")
    void getPolicyApprovedContextShouldRequireRequest() {
        assertThatThrownBy(() -> axonflow.getPolicyApprovedContext(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("preCheck should be alias for getPolicyApprovedContext")
    void preCheckShouldBeAlias() {
        stubFor(post(urlEqualTo("/api/v1/gateway/pre-check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

        PolicyApprovalRequest request = PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("test")
            .build();

        PolicyApprovalResult result = axonflow.preCheck(request);

        assertThat(result.isApproved()).isTrue();
    }

    @Test
    @DisplayName("getPolicyApprovedContextAsync should return future")
    void getPolicyApprovedContextAsyncShouldReturnFuture() throws Exception {
        stubFor(post(urlEqualTo("/api/v1/gateway/pre-check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

        PolicyApprovalRequest request = PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("test")
            .build();

        CompletableFuture<PolicyApprovalResult> future = axonflow.getPolicyApprovedContextAsync(request);
        PolicyApprovalResult result = future.get();

        assertThat(result.isApproved()).isTrue();
    }

    // ========================================================================
    // Gateway Mode - Audit
    // ========================================================================

    @Test
    @DisplayName("auditLLMCall should require non-null options")
    void auditLLMCallShouldRequireOptions() {
        assertThatThrownBy(() -> axonflow.auditLLMCall(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("auditLLMCallAsync should return future")
    void auditLLMCallAsyncShouldReturnFuture() throws Exception {
        stubFor(post(urlEqualTo("/api/v1/gateway/audit"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"audit_id\":\"audit_123\"}")));

        AuditOptions options = AuditOptions.builder()
            .contextId("ctx_123")
            .build();

        CompletableFuture<AuditResult> future = axonflow.auditLLMCallAsync(options);
        AuditResult result = future.get();

        assertThat(result.isSuccess()).isTrue();
    }

    // ========================================================================
    // Proxy Mode - Execute Query
    // ========================================================================

    @Test
    @DisplayName("executeQuery should require non-null request")
    void executeQueryShouldRequireRequest() {
        assertThatThrownBy(() -> axonflow.executeQuery(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("executeQueryAsync should return future")
    void executeQueryAsyncShouldReturnFuture() throws Exception {
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"blocked\":false}")));

        ClientRequest request = ClientRequest.builder()
            .query("test")
            .build();

        CompletableFuture<ClientResponse> future = axonflow.executeQueryAsync(request);
        ClientResponse response = future.get();

        assertThat(response.isSuccess()).isTrue();
    }

    // ========================================================================
    // Multi-Agent Planning
    // ========================================================================

    @Test
    @DisplayName("generatePlan should require non-null request")
    void generatePlanShouldRequireRequest() {
        assertThatThrownBy(() -> axonflow.generatePlan(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("generatePlanAsync should return future")
    void generatePlanAsyncShouldReturnFuture() throws Exception {
        stubFor(post(urlEqualTo("/api/v1/orchestrator/plan"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"plan_id\":\"plan_123\",\"steps\":[]}")));

        PlanRequest request = PlanRequest.builder()
            .objective("test")
            .build();

        CompletableFuture<PlanResponse> future = axonflow.generatePlanAsync(request);
        PlanResponse response = future.get();

        assertThat(response.getPlanId()).isEqualTo("plan_123");
    }

    @Test
    @DisplayName("executePlan should require non-null planId")
    void executePlanShouldRequirePlanId() {
        assertThatThrownBy(() -> axonflow.executePlan(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("executePlan should execute plan")
    void executePlanShouldExecutePlan() {
        stubFor(post(urlEqualTo("/api/v1/orchestrator/plan/plan_123/execute"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"plan_id\":\"plan_123\",\"status\":\"completed\"}")));

        PlanResponse response = axonflow.executePlan("plan_123");

        assertThat(response.getPlanId()).isEqualTo("plan_123");
        assertThat(response.getStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("getPlanStatus should require non-null planId")
    void getPlanStatusShouldRequirePlanId() {
        assertThatThrownBy(() -> axonflow.getPlanStatus(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getPlanStatus should return plan status")
    void getPlanStatusShouldReturnStatus() {
        stubFor(get(urlEqualTo("/api/v1/orchestrator/plan/plan_123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"plan_id\":\"plan_123\",\"status\":\"pending\"}")));

        PlanResponse response = axonflow.getPlanStatus("plan_123");

        assertThat(response.getStatus()).isEqualTo("pending");
    }

    // ========================================================================
    // MCP Connectors
    // ========================================================================

    @Test
    @DisplayName("listConnectorsAsync should return future")
    void listConnectorsAsyncShouldReturnFuture() throws Exception {
        stubFor(get(urlEqualTo("/api/v1/connectors"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));

        CompletableFuture<List<ConnectorInfo>> future = axonflow.listConnectorsAsync();
        List<ConnectorInfo> connectors = future.get();

        assertThat(connectors).isEmpty();
    }

    @Test
    @DisplayName("installConnector should require non-null connectorId")
    void installConnectorShouldRequireConnectorId() {
        assertThatThrownBy(() -> axonflow.installConnector(null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("installConnector should install connector")
    void installConnectorShouldInstall() {
        stubFor(post(urlEqualTo("/api/v1/connectors/install"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"salesforce\",\"name\":\"Salesforce\",\"installed\":true}")));

        ConnectorInfo info = axonflow.installConnector("salesforce", Map.of("key", "value"));

        assertThat(info.getId()).isEqualTo("salesforce");
        assertThat(info.isInstalled()).isTrue();
    }

    @Test
    @DisplayName("installConnector should handle null config")
    void installConnectorShouldHandleNullConfig() {
        stubFor(post(urlEqualTo("/api/v1/connectors/install"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"salesforce\",\"name\":\"Salesforce\",\"installed\":true}")));

        ConnectorInfo info = axonflow.installConnector("salesforce", null);

        assertThat(info.getId()).isEqualTo("salesforce");
    }

    @Test
    @DisplayName("queryConnector should require non-null query")
    void queryConnectorShouldRequireQuery() {
        assertThatThrownBy(() -> axonflow.queryConnector(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("queryConnector should throw on failure")
    void queryConnectorShouldThrowOnFailure() {
        stubFor(post(urlEqualTo("/api/v1/connectors/query"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":false,\"error\":\"Connector not found\"}")));

        ConnectorQuery query = ConnectorQuery.builder()
            .connectorId("unknown")
            .operation("test")
            .build();

        assertThatThrownBy(() -> axonflow.queryConnector(query))
            .isInstanceOf(ConnectorException.class)
            .hasMessageContaining("Connector not found");
    }

    @Test
    @DisplayName("queryConnectorAsync should return future")
    void queryConnectorAsyncShouldReturnFuture() throws Exception {
        stubFor(post(urlEqualTo("/api/v1/connectors/query"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":[]}")));

        ConnectorQuery query = ConnectorQuery.builder()
            .connectorId("salesforce")
            .operation("list")
            .build();

        CompletableFuture<ConnectorResponse> future = axonflow.queryConnectorAsync(query);
        ConnectorResponse response = future.get();

        assertThat(response.isSuccess()).isTrue();
    }

    // ========================================================================
    // Error Handling
    // ========================================================================

    @Test
    @DisplayName("should handle 401 Unauthorized")
    void shouldHandle401() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(401)
                .withBody("{\"error\":\"Invalid credentials\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("should handle 403 Forbidden")
    void shouldHandle403() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(403)
                .withBody("{\"error\":\"Access denied\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("should handle 403 with policy violation")
    void shouldHandle403PolicyViolation() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(403)
                .withBody("{\"error\":\"blocked by policy\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("should handle 429 Rate Limit")
    void shouldHandle429() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(429)
                .withBody("{\"error\":\"Rate limit exceeded\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("should handle 408 Timeout")
    void shouldHandle408() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(408)
                .withBody("{\"error\":\"Request timeout\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("should handle 504 Gateway Timeout")
    void shouldHandle504() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(504)
                .withBody("{\"error\":\"Gateway timeout\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("should handle 500 Internal Server Error")
    void shouldHandle500() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"message\":\"Internal error\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(AxonFlowException.class)
            .hasMessageContaining("Internal error");
    }

    @Test
    @DisplayName("should handle non-JSON error body")
    void shouldHandleNonJsonErrorBody() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Service unavailable")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(AxonFlowException.class)
            .hasMessageContaining("Service unavailable");
    }

    @Test
    @DisplayName("should handle empty error body")
    void shouldHandleEmptyErrorBody() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(AxonFlowException.class);
    }

    @Test
    @DisplayName("should handle block_reason in error body")
    void shouldHandleBlockReason() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"block_reason\":\"PII detected\"}")));

        assertThatThrownBy(() -> axonflow.healthCheck())
            .isInstanceOf(AxonFlowException.class)
            .hasMessageContaining("PII detected");
    }

    // ========================================================================
    // Cache Operations
    // ========================================================================

    @Test
    @DisplayName("getCacheStats should return stats")
    void getCacheStatsShouldReturnStats() {
        String stats = axonflow.getCacheStats();
        assertThat(stats).isNotEmpty();
    }

    @Test
    @DisplayName("clearCache should clear cache")
    void clearCacheShouldClearCache() {
        axonflow.clearCache();
        // Should not throw
    }

    // ========================================================================
    // Configuration
    // ========================================================================

    @Test
    @DisplayName("getConfig should return configuration")
    void getConfigShouldReturnConfig() {
        AxonFlowConfig config = axonflow.getConfig();
        assertThat(config.getAgentUrl()).isEqualTo(baseUrl);
    }

    // ========================================================================
    // Close
    // ========================================================================

    @Test
    @DisplayName("close should release resources")
    void closeShouldReleaseResources() {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .agentUrl(baseUrl)
            .build());
        client.close();
        // Should not throw
    }

    // ========================================================================
    // Authentication Headers (note: localhost URLs skip auth by design)
    // ========================================================================

    @Test
    @DisplayName("should skip auth headers for localhost")
    void shouldSkipAuthForLocalhost(WireMockRuntimeInfo wmRuntimeInfo) {
        // Localhost URLs intentionally skip authentication
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license-key")
            .build());

        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\":\"healthy\"}")));

        client.healthCheck();

        // Verify auth headers are NOT sent for localhost
        verify(getRequestedFor(urlEqualTo("/health"))
            .withoutHeader("X-License-Key"));
    }

    @Test
    @DisplayName("should include mode header")
    void shouldIncludeModeHeader(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
            .mode(Mode.SANDBOX)
            .build());

        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\":\"healthy\"}")));

        client.healthCheck();

        verify(getRequestedFor(urlEqualTo("/health"))
            .withHeader("X-AxonFlow-Mode", equalTo("sandbox")));
    }

    @Test
    @DisplayName("should store credentials in config for non-localhost")
    void shouldStoreCredentialsInConfig() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("https://api.axonflow.com")
            .licenseKey("test-license")
            .clientId("test-client")
            .clientSecret("test-secret")
            .build();

        assertThat(config.getLicenseKey()).isEqualTo("test-license");
        assertThat(config.getClientId()).isEqualTo("test-client");
        assertThat(config.getClientSecret()).isEqualTo("test-secret");
        assertThat(config.isLocalhost()).isFalse();
    }
}
