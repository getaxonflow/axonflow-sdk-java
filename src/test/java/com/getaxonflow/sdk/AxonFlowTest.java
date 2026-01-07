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
        // Add credentials for Gateway Mode tests (enterprise features)
        axonflow = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(baseUrl)
            .licenseKey("test-license-key")
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
        stubFor(post(urlEqualTo("/api/policy/pre-check"))
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
        stubFor(post(urlEqualTo("/api/policy/pre-check"))
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

    @Test
    @DisplayName("getPolicyApprovedContext should auto-populate clientId from config")
    void getPolicyApprovedContextShouldAutoPopulateClientId(WireMockRuntimeInfo wmRuntimeInfo) {
        // Create client with clientId configured
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .clientId("my-client-id")
            .clientSecret("my-secret")
            .build());

        stubFor(post(urlEqualTo("/api/policy/pre-check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

        // Request WITHOUT explicit clientId - SDK should auto-populate from config
        PolicyApprovalRequest request = PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("What is the capital of France?")
            .build();

        PolicyApprovalResult result = client.getPolicyApprovedContext(request);

        assertThat(result.isApproved()).isTrue();

        // Verify clientId was sent in request body (server requires this)
        verify(postRequestedFor(urlEqualTo("/api/policy/pre-check"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("my-client-id"))));
    }

    @Test
    @DisplayName("getPolicyApprovedContext should use explicit clientId if provided")
    void getPolicyApprovedContextShouldUseExplicitClientId(WireMockRuntimeInfo wmRuntimeInfo) {
        // Create client with clientId configured
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .clientId("config-client-id")
            .clientSecret("my-secret")
            .build());

        stubFor(post(urlEqualTo("/api/policy/pre-check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

        // Request WITH explicit clientId - should use this one, not config
        PolicyApprovalRequest request = PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("What is the capital of France?")
            .clientId("explicit-client-id")
            .build();

        PolicyApprovalResult result = client.getPolicyApprovedContext(request);

        assertThat(result.isApproved()).isTrue();

        // Verify explicit clientId was sent (not the config one)
        verify(postRequestedFor(urlEqualTo("/api/policy/pre-check"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("explicit-client-id"))));
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
        stubFor(post(urlEqualTo("/api/audit/llm-call"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"audit_id\":\"audit_123\"}")));

        AuditOptions options = AuditOptions.builder()
            .contextId("ctx_123").clientId("test-client")
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
        // Now uses Agent API endpoint with request_type: multi-agent-plan
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"plan_id\":\"plan_123\",\"data\":{\"steps\":[]}}")));

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
    @DisplayName("executePlan should execute plan via Agent API")
    void executePlanShouldExecutePlan() {
        // executePlan now uses /api/request with request_type: "execute-plan" (matches Go SDK)
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"result\":\"Plan executed successfully\"}")));

        PlanResponse response = axonflow.executePlan("plan_123");

        assertThat(response.getPlanId()).isEqualTo("plan_123");
        assertThat(response.getStatus()).isEqualTo("completed");
        assertThat(response.getResult()).isEqualTo("Plan executed successfully");

        // Verify correct request format
        verify(postRequestedFor(urlEqualTo("/api/request"))
            .withRequestBody(matchingJsonPath("$.request_type", equalTo("execute-plan")))
            .withRequestBody(matchingJsonPath("$.context.plan_id", equalTo("plan_123"))));
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
        stubFor(get(urlEqualTo("/api/v1/plan/plan_123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"plan_id\":\"plan_123\",\"status\":\"pending\"}")));

        PlanResponse response = axonflow.getPlanStatus("plan_123");

        assertThat(response.getStatus()).isEqualTo("pending");
    }

    // ========================================================================
    // Orchestrator Health Check
    // ========================================================================

    @Test
    @DisplayName("orchestratorHealthCheck should return healthy status")
    void orchestratorHealthCheckShouldReturnHealthyStatus(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"healthy\",\"version\":\"2.5.0\"}")));

        HealthStatus health = client.orchestratorHealthCheck();

        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getVersion()).isEqualTo("2.5.0");
    }

    @Test
    @DisplayName("orchestratorHealthCheck should return unhealthy on non-200")
    void orchestratorHealthCheckShouldReturnUnhealthyOnError(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("{\"status\":\"unhealthy\"}")));

        HealthStatus health = client.orchestratorHealthCheck();

        assertThat(health.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("orchestratorHealthCheckAsync should return future")
    void orchestratorHealthCheckAsyncShouldReturnFuture(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"healthy\"}")));

        CompletableFuture<HealthStatus> future = client.orchestratorHealthCheckAsync();
        HealthStatus health = future.get();

        assertThat(health.isHealthy()).isTrue();
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
        stubFor(post(urlEqualTo("/api/v1/connectors/salesforce/install"))
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
        stubFor(post(urlEqualTo("/api/v1/connectors/salesforce/install"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"salesforce\",\"name\":\"Salesforce\",\"installed\":true}")));

        ConnectorInfo info = axonflow.installConnector("salesforce", null);

        assertThat(info.getId()).isEqualTo("salesforce");
    }

    @Test
    @DisplayName("uninstallConnector should require non-null connectorName")
    void uninstallConnectorShouldRequireConnectorName() {
        assertThatThrownBy(() -> axonflow.uninstallConnector(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("uninstallConnector should uninstall connector")
    void uninstallConnectorShouldUninstall(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(delete(urlEqualTo("/api/v1/connectors/salesforce"))
            .willReturn(aResponse()
                .withStatus(204)));

        // Should not throw
        client.uninstallConnector("salesforce");

        verify(deleteRequestedFor(urlEqualTo("/api/v1/connectors/salesforce")));
    }

    @Test
    @DisplayName("uninstallConnector should handle 200 response")
    void uninstallConnectorShouldHandle200(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(delete(urlEqualTo("/api/v1/connectors/postgres"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"success\":true}")));

        // Should not throw
        client.uninstallConnector("postgres");

        verify(deleteRequestedFor(urlEqualTo("/api/v1/connectors/postgres")));
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
        // MCP connector queries now use /api/request with request_type: "mcp-query"
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":false,\"error\":\"Connector not found\",\"blocked\":false}")));

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
        // MCP connector queries now use /api/request with request_type: "mcp-query"
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":[],\"blocked\":false}")));

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
        assertThat(config.getEndpoint()).isEqualTo(baseUrl);
    }

    // ========================================================================
    // Close
    // ========================================================================

    @Test
    @DisplayName("close should release resources")
    void closeShouldReleaseResources() {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .build());
        client.close();
        // Should not throw
    }

    // ========================================================================
    // Authentication Headers (note: localhost URLs skip auth by design)
    // ========================================================================

    @Test
    @DisplayName("should send auth headers when credentials are configured")
    void shouldSendAuthHeadersWithCredentials(WireMockRuntimeInfo wmRuntimeInfo) {
        // Auth headers are sent when credentials are configured
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license-key")
            .build());

        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\":\"healthy\"}")));

        client.healthCheck();

        // Verify auth headers ARE sent when credentials are configured
        verify(getRequestedFor(urlEqualTo("/health"))
            .withHeader("X-License-Key", equalTo("test-license-key")));
    }

    @Test
    @DisplayName("should include mode header")
    void shouldIncludeModeHeader(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
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
            .endpoint("https://api.axonflow.com")
            .licenseKey("test-license")
            .clientId("test-client")
            .clientSecret("test-secret")
            .build();

        assertThat(config.getLicenseKey()).isEqualTo("test-license");
        assertThat(config.getClientId()).isEqualTo("test-client");
        assertThat(config.getClientSecret()).isEqualTo("test-secret");
        assertThat(config.isLocalhost()).isFalse();
    }

    // ========================================================================
    // Execution Replay - List Executions
    // ========================================================================

    @Test
    @DisplayName("listExecutions should return empty list")
    void listExecutionsShouldReturnEmptyList(WireMockRuntimeInfo wmRuntimeInfo) {
        // Create client with orchestrator URL pointing to WireMock
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/executions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"executions\":[],\"total\":0,\"limit\":50,\"offset\":0}")));

        var response = client.listExecutions();

        assertThat(response.getExecutions()).isEmpty();
        assertThat(response.getTotal()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(50);
    }

    @Test
    @DisplayName("listExecutions should return executions with filter")
    void listExecutionsShouldReturnExecutionsWithFilter(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlPathEqualTo("/api/v1/executions"))
            .withQueryParam("status", equalTo("completed"))
            .withQueryParam("limit", equalTo("10"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"executions\":[{\"request_id\":\"exec-123\",\"workflow_name\":\"test\",\"status\":\"completed\",\"total_steps\":1,\"completed_steps\":1,\"started_at\":\"2026-01-03T12:00:00Z\",\"total_tokens\":50,\"total_cost_usd\":0.001}],\"total\":1,\"limit\":10,\"offset\":0}")));

        var options = com.getaxonflow.sdk.types.executionreplay.ExecutionReplayTypes.ListExecutionsOptions.builder()
            .setStatus("completed")
            .setLimit(10);
        var response = client.listExecutions(options);

        assertThat(response.getExecutions()).hasSize(1);
        assertThat(response.getExecutions().get(0).getRequestId()).isEqualTo("exec-123");
        assertThat(response.getExecutions().get(0).getStatus()).isEqualTo("completed");
    }

    // ========================================================================
    // Execution Replay - Get Execution
    // ========================================================================

    @Test
    @DisplayName("getExecution should return execution detail")
    void getExecutionShouldReturnExecutionDetail(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/executions/exec-123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"summary\":{\"request_id\":\"exec-123\",\"workflow_name\":\"test\",\"status\":\"completed\",\"total_steps\":2,\"completed_steps\":2,\"started_at\":\"2026-01-03T12:00:00Z\",\"total_tokens\":100,\"total_cost_usd\":0.005},\"steps\":[{\"request_id\":\"exec-123\",\"step_index\":0,\"step_name\":\"greet\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\",\"tokens_in\":10,\"tokens_out\":20,\"cost_usd\":0.001}]}")));

        var detail = client.getExecution("exec-123");

        assertThat(detail.getSummary().getRequestId()).isEqualTo("exec-123");
        assertThat(detail.getSummary().getStatus()).isEqualTo("completed");
        assertThat(detail.getSteps()).hasSize(1);
        assertThat(detail.getSteps().get(0).getStepName()).isEqualTo("greet");
    }

    // ========================================================================
    // Execution Replay - Get Execution Steps
    // ========================================================================

    @Test
    @DisplayName("getExecutionSteps should return step snapshots")
    void getExecutionStepsShouldReturnSnapshots(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/executions/exec-123/steps"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"request_id\":\"exec-123\",\"step_index\":0,\"step_name\":\"step1\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\",\"tokens_in\":10,\"tokens_out\":15,\"cost_usd\":0.001},{\"request_id\":\"exec-123\",\"step_index\":1,\"step_name\":\"step2\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:01Z\",\"tokens_in\":15,\"tokens_out\":20,\"cost_usd\":0.002}]")));

        var steps = client.getExecutionSteps("exec-123");

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getStepName()).isEqualTo("step1");
        assertThat(steps.get(1).getStepName()).isEqualTo("step2");
    }

    // ========================================================================
    // Execution Replay - Get Execution Timeline
    // ========================================================================

    @Test
    @DisplayName("getExecutionTimeline should return timeline entries")
    void getExecutionTimelineShouldReturnEntries(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/executions/exec-123/timeline"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"step_index\":0,\"step_name\":\"start\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\",\"has_error\":false,\"has_approval\":false},{\"step_index\":1,\"step_name\":\"approve\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:01Z\",\"has_error\":false,\"has_approval\":true}]")));

        var timeline = client.getExecutionTimeline("exec-123");

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).getStepName()).isEqualTo("start");
        assertThat(timeline.get(0).hasApproval()).isFalse();
        assertThat(timeline.get(1).getStepName()).isEqualTo("approve");
        assertThat(timeline.get(1).hasApproval()).isTrue();
    }

    // ========================================================================
    // Execution Replay - Export Execution
    // ========================================================================

    @Test
    @DisplayName("exportExecution should return export data")
    void exportExecutionShouldReturnExportData(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlPathEqualTo("/api/v1/executions/exec-123/export"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"execution_id\":\"exec-123\",\"workflow_name\":\"test\",\"exported_at\":\"2026-01-03T12:00:00Z\"}")));

        var export = client.exportExecution("exec-123");

        assertThat(export.get("execution_id")).isEqualTo("exec-123");
        assertThat(export.get("workflow_name")).isEqualTo("test");
    }

    // ========================================================================
    // Execution Replay - Delete Execution
    // ========================================================================

    @Test
    @DisplayName("deleteExecution should succeed")
    void deleteExecutionShouldSucceed(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(delete(urlEqualTo("/api/v1/executions/exec-123"))
            .willReturn(aResponse()
                .withStatus(204)));

        // Should not throw
        client.deleteExecution("exec-123");

        verify(deleteRequestedFor(urlEqualTo("/api/v1/executions/exec-123")));
    }

    // ========================================================================
    // Cost Controls - Budgets
    // ========================================================================

    @Test
    @DisplayName("createBudget should create a budget")
    void createBudgetShouldCreateBudget(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(post(urlEqualTo("/api/v1/budgets"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

        var request = com.getaxonflow.sdk.types.costcontrols.CostControlTypes.CreateBudgetRequest.builder()
            .id("budget-123")
            .name("Test Budget")
            .scope(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.ORGANIZATION)
            .limitUsd(100.0)
            .period(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.MONTHLY)
            .onExceed(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.WARN)
            .alertThresholds(List.of(50, 80, 100))
            .build();

        var budget = client.createBudget(request);

        assertThat(budget.getId()).isEqualTo("budget-123");
        assertThat(budget.getName()).isEqualTo("Test Budget");
        assertThat(budget.getLimitUsd()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("getBudget should return budget by ID")
    void getBudgetShouldReturnBudget(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/budgets/budget-123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

        var budget = client.getBudget("budget-123");

        assertThat(budget.getId()).isEqualTo("budget-123");
        assertThat(budget.getName()).isEqualTo("Test Budget");
    }

    @Test
    @DisplayName("listBudgets should return list of budgets")
    void listBudgetsShouldReturnList(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlPathEqualTo("/api/v1/budgets"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"budgets\":[{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}],\"total\":1}")));

        var response = client.listBudgets();

        assertThat(response.getBudgets()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteBudget should delete a budget")
    void deleteBudgetShouldDelete(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(delete(urlEqualTo("/api/v1/budgets/budget-123"))
            .willReturn(aResponse()
                .withStatus(204)));

        client.deleteBudget("budget-123");

        verify(deleteRequestedFor(urlEqualTo("/api/v1/budgets/budget-123")));
    }

    @Test
    @DisplayName("getBudgetStatus should return budget status")
    void getBudgetStatusShouldReturnStatus(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/budgets/budget-123/status"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"budget_id\":\"budget-123\",\"used_usd\":45.50,\"remaining_usd\":54.50,\"usage_percent\":45.5,\"period_start\":\"2026-01-01T00:00:00Z\",\"period_end\":\"2026-01-31T23:59:59Z\",\"is_exceeded\":false}")));

        var status = client.getBudgetStatus("budget-123");

        assertThat(status.getUsedUsd()).isEqualTo(45.50);
        assertThat(status.getRemainingUsd()).isEqualTo(54.50);
        assertThat(status.isExceeded()).isFalse();
    }

    @Test
    @DisplayName("getBudgetAlerts should return budget alerts")
    void getBudgetAlertsShouldReturnAlerts(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/budgets/budget-123/alerts"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"alerts\":[],\"count\":0}")));

        var response = client.getBudgetAlerts("budget-123");

        assertThat(response.getAlerts()).isEmpty();
        assertThat(response.getCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("checkBudget should return budget decision")
    void checkBudgetShouldReturnDecision(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(post(urlEqualTo("/api/v1/budgets/check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"allowed\":true,\"budget_id\":\"budget-123\",\"message\":\"Within budget\"}")));

        var request = com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetCheckRequest.builder()
            .orgId("org-123")
            .build();

        var decision = client.checkBudget(request);

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getMessage()).isEqualTo("Within budget");
    }

    // ========================================================================
    // Cost Controls - Usage
    // ========================================================================

    @Test
    @DisplayName("getUsageSummary should return usage summary")
    void getUsageSummaryShouldReturnSummary(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlPathEqualTo("/api/v1/usage"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"total_cost_usd\":125.50,\"total_tokens_in\":50000,\"total_tokens_out\":25000,\"total_requests\":100,\"period\":\"monthly\",\"period_start\":\"2026-01-01T00:00:00Z\",\"period_end\":\"2026-01-31T23:59:59Z\"}")));

        var summary = client.getUsageSummary();

        assertThat(summary.getTotalCostUsd()).isEqualTo(125.50);
        assertThat(summary.getTotalTokensIn()).isEqualTo(50000);
        assertThat(summary.getTotalTokensOut()).isEqualTo(25000);
        assertThat(summary.getTotalRequests()).isEqualTo(100);
    }

    @Test
    @DisplayName("getUsageBreakdown should return usage breakdown")
    void getUsageBreakdownShouldReturnBreakdown(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlPathEqualTo("/api/v1/usage/breakdown"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"items\":[{\"dimension\":\"openai\",\"cost_usd\":80.0,\"input_tokens\":30000,\"output_tokens\":15000,\"requests\":60}],\"group_by\":\"provider\",\"period\":\"monthly\"}")));

        var breakdown = client.getUsageBreakdown("provider", "monthly");

        assertThat(breakdown.getItems()).hasSize(1);
        assertThat(breakdown.getGroupBy()).isEqualTo("provider");
    }

    @Test
    @DisplayName("listUsageRecords should return usage records")
    void listUsageRecordsShouldReturnRecords(WireMockRuntimeInfo wmRuntimeInfo) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlPathEqualTo("/api/v1/usage/records"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"records\":[],\"total\":0}")));

        var response = client.listUsageRecords();

        assertThat(response.getRecords()).isEmpty();
        assertThat(response.getTotal()).isEqualTo(0);
    }

    // ========================================================================
    // Cost Controls - Async Methods
    // ========================================================================

    @Test
    @DisplayName("createBudgetAsync should return future")
    void createBudgetAsyncShouldReturnFuture(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(post(urlEqualTo("/api/v1/budgets"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

        var request = com.getaxonflow.sdk.types.costcontrols.CostControlTypes.CreateBudgetRequest.builder()
            .id("budget-123")
            .name("Test Budget")
            .scope(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.ORGANIZATION)
            .limitUsd(100.0)
            .period(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.MONTHLY)
            .onExceed(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.WARN)
            .alertThresholds(List.of(50, 80, 100))
            .build();

        var future = client.createBudgetAsync(request);
        var budget = future.get();

        assertThat(budget.getId()).isEqualTo("budget-123");
    }

    @Test
    @DisplayName("getBudgetAsync should return future")
    void getBudgetAsyncShouldReturnFuture(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlEqualTo("/api/v1/budgets/budget-123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

        var future = client.getBudgetAsync("budget-123");
        var budget = future.get();

        assertThat(budget.getId()).isEqualTo("budget-123");
    }

    @Test
    @DisplayName("getUsageSummaryAsync should return future")
    void getUsageSummaryAsyncShouldReturnFuture(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .endpoint(wmRuntimeInfo.getHttpBaseUrl())
            .licenseKey("test-license")
            .build());

        stubFor(get(urlPathEqualTo("/api/v1/usage"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"total_cost_usd\":125.50,\"total_tokens_in\":50000,\"total_tokens_out\":25000,\"total_requests\":100,\"period\":\"monthly\",\"period_start\":\"2026-01-01T00:00:00Z\",\"period_end\":\"2026-01-31T23:59:59Z\"}")));

        var future = client.getUsageSummaryAsync("monthly");
        var summary = future.get();

        assertThat(summary.getTotalCostUsd()).isEqualTo(125.50);
    }

    // ========================================
    // COST CONTROLS - ENUM UNIT TESTS
    // ========================================

    @Test
    @DisplayName("BudgetScope fromValue should return correct enum")
    void budgetScopeFromValueShouldWork() {
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("organization"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.ORGANIZATION);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("team"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.TEAM);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("agent"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.AGENT);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("workflow"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.WORKFLOW);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("user"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.USER);
    }

    @Test
    @DisplayName("BudgetScope getValue should return correct string")
    void budgetScopeGetValueShouldWork() {
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.ORGANIZATION.getValue())
            .isEqualTo("organization");
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.TEAM.getValue())
            .isEqualTo("team");
    }

    @Test
    @DisplayName("BudgetPeriod fromValue should return correct enum")
    void budgetPeriodFromValueShouldWork() {
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue("daily"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.DAILY);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue("weekly"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.WEEKLY);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue("monthly"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.MONTHLY);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue("quarterly"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.QUARTERLY);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue("yearly"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.YEARLY);
    }

    @Test
    @DisplayName("BudgetOnExceed fromValue should return correct enum")
    void budgetOnExceedFromValueShouldWork() {
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue("warn"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.WARN);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue("block"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.BLOCK);
        assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue("downgrade"))
            .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.DOWNGRADE);
    }

    @Test
    @DisplayName("BudgetScope fromValue should throw for invalid value")
    void budgetScopeFromValueShouldThrowForInvalid() {
        assertThatThrownBy(() ->
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown budget scope");
    }

    @Test
    @DisplayName("BudgetPeriod fromValue should throw for invalid value")
    void budgetPeriodFromValueShouldThrowForInvalid() {
        assertThatThrownBy(() ->
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown budget period");
    }

    @Test
    @DisplayName("BudgetOnExceed fromValue should throw for invalid value")
    void budgetOnExceedFromValueShouldThrowForInvalid() {
        assertThatThrownBy(() ->
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown budget on exceed action");
    }

    @Test
    @DisplayName("CreateBudgetRequest builder should set all fields")
    void createBudgetRequestBuilderShouldSetAllFields() {
        var request = com.getaxonflow.sdk.types.costcontrols.CostControlTypes.CreateBudgetRequest.builder()
            .id("budget-1")
            .name("My Budget")
            .scope(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.TEAM)
            .scopeId("team-123")
            .limitUsd(500.0)
            .period(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.WEEKLY)
            .onExceed(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.BLOCK)
            .alertThresholds(List.of(25, 50, 75))
            .build();

        assertThat(request.getId()).isEqualTo("budget-1");
        assertThat(request.getName()).isEqualTo("My Budget");
        assertThat(request.getScope()).isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.TEAM);
        assertThat(request.getScopeId()).isEqualTo("team-123");
        assertThat(request.getLimitUsd()).isEqualTo(500.0);
        assertThat(request.getPeriod()).isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.WEEKLY);
        assertThat(request.getOnExceed()).isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.BLOCK);
        assertThat(request.getAlertThresholds()).containsExactly(25, 50, 75);
    }

    @Test
    @DisplayName("UpdateBudgetRequest builder should set all fields")
    void updateBudgetRequestBuilderShouldSetAllFields() {
        var request = com.getaxonflow.sdk.types.costcontrols.CostControlTypes.UpdateBudgetRequest.builder()
            .name("Updated Budget")
            .limitUsd(1000.0)
            .onExceed(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.DOWNGRADE)
            .alertThresholds(List.of(80, 90, 100))
            .build();

        assertThat(request.getName()).isEqualTo("Updated Budget");
        assertThat(request.getLimitUsd()).isEqualTo(1000.0);
        assertThat(request.getOnExceed()).isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.DOWNGRADE);
        assertThat(request.getAlertThresholds()).containsExactly(80, 90, 100);
    }

    @Test
    @DisplayName("BudgetCheckRequest builder should set all fields")
    void budgetCheckRequestBuilderShouldSetAllFields() {
        var request = com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetCheckRequest.builder()
            .orgId("org-1")
            .teamId("team-1")
            .agentId("agent-1")
            .workflowId("workflow-1")
            .userId("user-1")
            .build();

        assertThat(request.getOrgId()).isEqualTo("org-1");
        assertThat(request.getTeamId()).isEqualTo("team-1");
        assertThat(request.getAgentId()).isEqualTo("agent-1");
        assertThat(request.getWorkflowId()).isEqualTo("workflow-1");
        assertThat(request.getUserId()).isEqualTo("user-1");
    }
}
