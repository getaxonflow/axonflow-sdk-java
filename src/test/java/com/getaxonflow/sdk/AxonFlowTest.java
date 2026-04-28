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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.*;
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@WireMockTest
@DisplayName("AxonFlow Client")
class AxonFlowTest {

  private AxonFlow axonflow;
  private String baseUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    baseUrl = wmRuntimeInfo.getHttpBaseUrl();
    // Add credentials for Gateway Mode tests (enterprise features)
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(baseUrl)
                .clientId("test-client")
                .clientSecret("test-secret")
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
    assertThatThrownBy(() -> AxonFlow.create(null)).isInstanceOf(NullPointerException.class);
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
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse()
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
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse()
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
    stubFor(
        post(urlEqualTo("/api/policy/pre-check"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

    PolicyApprovalRequest request =
        PolicyApprovalRequest.builder().userToken("user-123").query("test").build();

    PolicyApprovalResult result = axonflow.preCheck(request);

    assertThat(result.isApproved()).isTrue();
  }

  @Test
  @DisplayName("getPolicyApprovedContextAsync should return future")
  void getPolicyApprovedContextAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/policy/pre-check"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

    PolicyApprovalRequest request =
        PolicyApprovalRequest.builder().userToken("user-123").query("test").build();

    CompletableFuture<PolicyApprovalResult> future =
        axonflow.getPolicyApprovedContextAsync(request);
    PolicyApprovalResult result = future.get();

    assertThat(result.isApproved()).isTrue();
  }

  @Test
  @DisplayName("getPolicyApprovedContext should auto-populate clientId from config")
  void getPolicyApprovedContextShouldAutoPopulateClientId(WireMockRuntimeInfo wmRuntimeInfo) {
    // Create client with clientId configured
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("my-client-id")
                .clientSecret("my-secret")
                .build());

    stubFor(
        post(urlEqualTo("/api/policy/pre-check"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

    // Request WITHOUT explicit clientId - SDK should auto-populate from config
    PolicyApprovalRequest request =
        PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("What is the capital of France?")
            .build();

    PolicyApprovalResult result = client.getPolicyApprovedContext(request);

    assertThat(result.isApproved()).isTrue();

    // Verify clientId was sent in request body (server requires this)
    verify(
        postRequestedFor(urlEqualTo("/api/policy/pre-check"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("my-client-id"))));
  }

  @Test
  @DisplayName("getPolicyApprovedContext should use explicit clientId if provided")
  void getPolicyApprovedContextShouldUseExplicitClientId(WireMockRuntimeInfo wmRuntimeInfo) {
    // Create client with clientId configured
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("config-client-id")
                .clientSecret("my-secret")
                .build());

    stubFor(
        post(urlEqualTo("/api/policy/pre-check"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"context_id\":\"ctx_123\",\"approved\":true}")));

    // Request WITH explicit clientId - should use this one, not config
    PolicyApprovalRequest request =
        PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("What is the capital of France?")
            .clientId("explicit-client-id")
            .build();

    PolicyApprovalResult result = client.getPolicyApprovedContext(request);

    assertThat(result.isApproved()).isTrue();

    // Verify explicit clientId was sent (not the config one)
    verify(
        postRequestedFor(urlEqualTo("/api/policy/pre-check"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("explicit-client-id"))));
  }

  // ========================================================================
  // Gateway Mode - Audit
  // ========================================================================

  @Test
  @DisplayName("auditLLMCall should require non-null options")
  void auditLLMCallShouldRequireOptions() {
    assertThatThrownBy(() -> axonflow.auditLLMCall(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("auditLLMCallAsync should return future")
  void auditLLMCallAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/audit/llm-call"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"audit_id\":\"audit_123\"}")));

    AuditOptions options =
        AuditOptions.builder().contextId("ctx_123").clientId("test-client").build();

    CompletableFuture<AuditResult> future = axonflow.auditLLMCallAsync(options);
    AuditResult result = future.get();

    assertThat(result.isSuccess()).isTrue();
  }

  // ========================================================================
  // Proxy Mode - proxyLLMCall
  // ========================================================================

  @Test
  @DisplayName("proxyLLMCall should require non-null request")
  void proxyLLMCallShouldRequireRequest() {
    assertThatThrownBy(() -> axonflow.proxyLLMCall(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("proxyLLMCallAsync should return future")
  void proxyLLMCallAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

    ClientRequest request = ClientRequest.builder().query("test").build();

    CompletableFuture<ClientResponse> future = axonflow.proxyLLMCallAsync(request);
    ClientResponse response = future.get();

    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("proxyLLMCall should auto-inject clientId from config when not set in request")
  void proxyLLMCallShouldAutoInjectClientId() {
    // Stub to verify the request contains client_id from config
    stubFor(
        post(urlEqualTo("/api/request"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("test-client")))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

    // Build request WITHOUT clientId
    ClientRequest request =
        ClientRequest.builder()
            .query("test query")
            .userToken("user-123")
            .requestType(RequestType.CHAT)
            .build();

    // The SDK should auto-inject clientId from config
    ClientResponse response = axonflow.proxyLLMCall(request);

    assertThat(response.isSuccess()).isTrue();

    // Verify the request was made with client_id
    verify(
        postRequestedFor(urlEqualTo("/api/request"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("test-client"))));
  }

  @Test
  @DisplayName("proxyLLMCall should preserve clientId when explicitly set in request")
  void proxyLLMCallShouldPreserveExplicitClientId() {
    // Stub to verify the request contains explicit client_id
    stubFor(
        post(urlEqualTo("/api/request"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("explicit-client")))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

    // Build request WITH explicit clientId
    ClientRequest request =
        ClientRequest.builder()
            .query("test query")
            .userToken("user-123")
            .clientId("explicit-client")
            .requestType(RequestType.CHAT)
            .build();

    ClientResponse response = axonflow.proxyLLMCall(request);

    assertThat(response.isSuccess()).isTrue();

    // Verify the request was made with explicit client_id (not overwritten)
    verify(
        postRequestedFor(urlEqualTo("/api/request"))
            .withRequestBody(matchingJsonPath("$.client_id", equalTo("explicit-client"))));
  }

  // ========================================================================
  // Multi-Agent Planning
  // ========================================================================

  @Test
  @DisplayName("generatePlan should require non-null request")
  void generatePlanShouldRequireRequest() {
    assertThatThrownBy(() -> axonflow.generatePlan(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("generatePlanAsync should return future")
  void generatePlanAsyncShouldReturnFuture() throws Exception {
    // Now uses Agent API endpoint with request_type: multi-agent-plan
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"plan_id\":\"plan_123\",\"data\":{\"steps\":[]}}")));

    PlanRequest request = PlanRequest.builder().objective("test").build();

    CompletableFuture<PlanResponse> future = axonflow.generatePlanAsync(request);
    PlanResponse response = future.get();

    assertThat(response.getPlanId()).isEqualTo("plan_123");
  }

  @Test
  @DisplayName("executePlan should require non-null planId")
  void executePlanShouldRequirePlanId() {
    assertThatThrownBy(() -> axonflow.executePlan(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("executePlan should execute plan via Agent API")
  void executePlanShouldExecutePlan() {
    // executePlan now uses /api/request with request_type: "execute-plan" (matches Go SDK)
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"result\":\"Plan executed successfully\"}")));

    PlanResponse response = axonflow.executePlan("plan_123");

    assertThat(response.getPlanId()).isEqualTo("plan_123");
    assertThat(response.getStatus()).isEqualTo("completed");
    assertThat(response.getResult()).isEqualTo("Plan executed successfully");

    // Verify correct request format
    verify(
        postRequestedFor(urlEqualTo("/api/request"))
            .withRequestBody(matchingJsonPath("$.request_type", equalTo("execute-plan")))
            .withRequestBody(matchingJsonPath("$.context.plan_id", equalTo("plan_123"))));
  }

  @Test
  @DisplayName("getPlanStatus should require non-null planId")
  void getPlanStatusShouldRequirePlanId() {
    assertThatThrownBy(() -> axonflow.getPlanStatus(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("getPlanStatus should return plan status")
  void getPlanStatusShouldReturnStatus() {
    stubFor(
        get(urlEqualTo("/api/v1/plan/plan_123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"plan_id\":\"plan_123\",\"status\":\"pending\"}")));

    PlanResponse response = axonflow.getPlanStatus("plan_123");

    assertThat(response.getStatus()).isEqualTo("pending");
  }

  @Test
  @DisplayName("executePlan should throw when nested data.success is false")
  void executePlanShouldThrowOnNestedDataFailure() {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"success\":false,\"error\":\"Step 2 timed out\"}}")));

    assertThatThrownBy(() -> axonflow.executePlan("plan_fail"))
        .isInstanceOf(PlanExecutionException.class)
        .hasMessageContaining("Step 2 timed out");
  }

  @Test
  @DisplayName("executePlan should use metadata.status when data.status is absent")
  void executePlanShouldFallbackToMetadataStatus() {
    // No data.status, but metadata.status is present — should use metadata.status
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"result\":\"done\",\"metadata\":{\"status\":\"awaiting_approval\"}}")));

    PlanResponse response = axonflow.executePlan("plan_meta");

    assertThat(response.getStatus()).isEqualTo("awaiting_approval");
  }

  @Test
  @DisplayName("isApproved should return false when approved field is null")
  void isApprovedShouldReturnFalseWhenNull() {
    // Construct a ResumePlanResponse with null approved field
    ResumePlanResponse response =
        new ResumePlanResponse(
            "plan_123", "wf_456", "in_progress", null, "Pending review", 2, "Step 2", 5);

    // Must return false (not throw NPE)
    assertThat(response.isApproved()).isFalse();
  }

  // ========================================================================
  // Orchestrator Health Check
  // ========================================================================

  @Test
  @DisplayName("orchestratorHealthCheck should return healthy status")
  void orchestratorHealthCheckShouldReturnHealthyStatus(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(503).withBody("{\"status\":\"unhealthy\"}")));

    HealthStatus health = client.orchestratorHealthCheck();

    assertThat(health.isHealthy()).isFalse();
  }

  @Test
  @DisplayName("orchestratorHealthCheckAsync should return future")
  void orchestratorHealthCheckAsyncShouldReturnFuture(WireMockRuntimeInfo wmRuntimeInfo)
      throws Exception {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"healthy\"}")));

    CompletableFuture<HealthStatus> future = client.orchestratorHealthCheckAsync();
    HealthStatus health = future.get();

    assertThat(health.isHealthy()).isTrue();
  }

  // ========================================================================
  // LLM Providers
  // ========================================================================

  @Test
  @DisplayName("listLLMProviders should return providers with health snapshot")
  void listLLMProvidersShouldReturnProvidersWithHealth() {
    stubFor(
        get(urlEqualTo("/api/v1/llm-providers"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"providers\":[" +
                            "{\"name\":\"anthropic\",\"type\":\"anthropic\",\"enabled\":true,\"has_api_key\":true,\"health\":{\"status\":\"healthy\",\"message\":\"provider is operational\",\"last_checked\":\"2026-04-28T08:45:12Z\"}}," +
                            "{\"name\":\"openai\",\"type\":\"openai\",\"enabled\":true,\"has_api_key\":true,\"health\":{\"status\":\"unhealthy\",\"message\":\"billing exceeded\"}}" +
                            "]}")));

    List<LLMProvider> providers = axonflow.listLLMProviders();

    assertThat(providers).hasSize(2);
    assertThat(providers.get(0).getName()).isEqualTo("anthropic");
    assertThat(providers.get(0).getHealth().getStatus()).isEqualTo("healthy");
    assertThat(providers.get(1).getName()).isEqualTo("openai");
    assertThat(providers.get(1).getHealth().getStatus()).isEqualTo("unhealthy");
    assertThat(providers.get(1).getHealth().getMessage()).isEqualTo("billing exceeded");
  }

  @Test
  @DisplayName("listLLMProviders with type filter passes query string")
  void listLLMProvidersWithTypeFilterPassesQueryString() {
    stubFor(
        get(urlEqualTo("/api/v1/llm-providers?type=anthropic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"providers\":[]}")));

    List<LLMProvider> providers = axonflow.listLLMProviders("anthropic", null);
    assertThat(providers).isEmpty();
  }

  @Test
  @DisplayName("listLLMProviders with enabled filter passes query string")
  void listLLMProvidersWithEnabledFilterPassesQueryString() {
    stubFor(
        get(urlEqualTo("/api/v1/llm-providers?enabled=false"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"providers\":[]}")));

    List<LLMProvider> providers = axonflow.listLLMProviders(null, false);
    assertThat(providers).isEmpty();
  }

  @Test
  @DisplayName("listLLMProvidersAsync returns a CompletableFuture")
  void listLLMProvidersAsyncShouldReturnFuture() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/llm-providers"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"providers\":[]}")));

    CompletableFuture<List<LLMProvider>> future = axonflow.listLLMProvidersAsync();
    List<LLMProvider> providers = future.get();
    assertThat(providers).isEmpty();
  }

  // ========================================================================
  // MCP Connectors
  // ========================================================================

  @Test
  @DisplayName("listConnectorsAsync should return future")
  void listConnectorsAsyncShouldReturnFuture() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/connectors"))
            .willReturn(
                aResponse()
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
    stubFor(
        post(urlEqualTo("/api/v1/connectors/salesforce/install"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"salesforce\",\"name\":\"Salesforce\",\"installed\":true}")));

    ConnectorInfo info = axonflow.installConnector("salesforce", Map.of("key", "value"));

    assertThat(info.getId()).isEqualTo("salesforce");
    assertThat(info.isInstalled()).isTrue();
  }

  @Test
  @DisplayName("installConnector should handle null config")
  void installConnectorShouldHandleNullConfig() {
    stubFor(
        post(urlEqualTo("/api/v1/connectors/salesforce/install"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"salesforce\",\"name\":\"Salesforce\",\"installed\":true}")));

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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        delete(urlEqualTo("/api/v1/connectors/salesforce"))
            .willReturn(aResponse().withStatus(204)));

    // Should not throw
    client.uninstallConnector("salesforce");

    verify(deleteRequestedFor(urlEqualTo("/api/v1/connectors/salesforce")));
  }

  @Test
  @DisplayName("uninstallConnector should handle 200 response")
  void uninstallConnectorShouldHandle200(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        delete(urlEqualTo("/api/v1/connectors/postgres"))
            .willReturn(aResponse().withStatus(200).withBody("{\"success\":true}")));

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
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":false,\"error\":\"Connector not found\",\"blocked\":false}")));

    ConnectorQuery query =
        ConnectorQuery.builder().connectorId("unknown").operation("test").build();

    assertThatThrownBy(() -> axonflow.queryConnector(query))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Connector not found");
  }

  @Test
  @DisplayName("queryConnectorAsync should return future")
  void queryConnectorAsyncShouldReturnFuture() throws Exception {
    // MCP connector queries now use /api/request with request_type: "mcp-query"
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"data\":[],\"blocked\":false}")));

    ConnectorQuery query =
        ConnectorQuery.builder().connectorId("salesforce").operation("list").build();

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
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse().withStatus(401).withBody("{\"error\":\"Invalid credentials\"}")));

    assertThatThrownBy(() -> axonflow.healthCheck())
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Invalid credentials");
  }

  @Test
  @DisplayName("should handle 403 Forbidden")
  void shouldHandle403() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(403).withBody("{\"error\":\"Access denied\"}")));

    assertThatThrownBy(() -> axonflow.healthCheck()).isInstanceOf(AuthenticationException.class);
  }

  @Test
  @DisplayName("should handle 403 with policy violation")
  void shouldHandle403PolicyViolation() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(403).withBody("{\"error\":\"blocked by policy\"}")));

    assertThatThrownBy(() -> axonflow.healthCheck()).isInstanceOf(PolicyViolationException.class);
  }

  @Test
  @DisplayName("should handle 429 Rate Limit")
  void shouldHandle429() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse().withStatus(429).withBody("{\"error\":\"Rate limit exceeded\"}")));

    assertThatThrownBy(() -> axonflow.healthCheck()).isInstanceOf(RateLimitException.class);
  }

  @Test
  @DisplayName("should handle 408 Timeout")
  void shouldHandle408() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(408).withBody("{\"error\":\"Request timeout\"}")));

    assertThatThrownBy(() -> axonflow.healthCheck()).isInstanceOf(TimeoutException.class);
  }

  @Test
  @DisplayName("should handle 504 Gateway Timeout")
  void shouldHandle504() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(504).withBody("{\"error\":\"Gateway timeout\"}")));

    assertThatThrownBy(() -> axonflow.healthCheck()).isInstanceOf(TimeoutException.class);
  }

  @Test
  @DisplayName("should handle 500 Internal Server Error")
  void shouldHandle500() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(500).withBody("{\"message\":\"Internal error\"}")));

    assertThatThrownBy(() -> axonflow.healthCheck())
        .isInstanceOf(AxonFlowException.class)
        .hasMessageContaining("Internal error");
  }

  @Test
  @DisplayName("should handle non-JSON error body")
  void shouldHandleNonJsonErrorBody() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(500).withBody("Service unavailable")));

    assertThatThrownBy(() -> axonflow.healthCheck())
        .isInstanceOf(AxonFlowException.class)
        .hasMessageContaining("Service unavailable");
  }

  @Test
  @DisplayName("should handle empty error body")
  void shouldHandleEmptyErrorBody() {
    stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(500).withBody("")));

    assertThatThrownBy(() -> axonflow.healthCheck()).isInstanceOf(AxonFlowException.class);
  }

  @Test
  @DisplayName("should handle block_reason in error body")
  void shouldHandleBlockReason() {
    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse().withStatus(500).withBody("{\"block_reason\":\"PII detected\"}")));

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
    AxonFlow client = AxonFlow.create(AxonFlowConfig.builder().endpoint(baseUrl).build());
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"healthy\"}")));

    client.healthCheck();

    // Verify OAuth2 Basic auth header is sent when credentials are configured
    String expectedBasic =
        "Basic "
            + java.util.Base64.getEncoder()
                .encodeToString(
                    "test-client:test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    verify(
        getRequestedFor(urlEqualTo("/health")).withHeader("Authorization", equalTo(expectedBasic)));
  }

  @Test
  @DisplayName("should include mode header")
  void shouldIncludeModeHeader(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .mode(Mode.SANDBOX)
                .build());

    stubFor(
        get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"healthy\"}")));

    client.healthCheck();

    verify(
        getRequestedFor(urlEqualTo("/health")).withHeader("X-AxonFlow-Mode", equalTo("sandbox")));
  }

  @Test
  @DisplayName("should store credentials in config for non-localhost")
  void shouldStoreCredentialsInConfig() {
    AxonFlowConfig config =
        AxonFlowConfig.builder()
            .endpoint("https://api.axonflow.com")
            .clientId("test-client")
            .clientSecret("test-secret")
            .build();

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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/executions"))
            .willReturn(
                aResponse()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlPathEqualTo("/api/v1/executions"))
            .withQueryParam("status", equalTo("completed"))
            .withQueryParam("limit", equalTo("10"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"executions\":[{\"request_id\":\"exec-123\",\"workflow_name\":\"test\",\"status\":\"completed\",\"total_steps\":1,\"completed_steps\":1,\"started_at\":\"2026-01-03T12:00:00Z\",\"total_tokens\":50,\"total_cost_usd\":0.001}],\"total\":1,\"limit\":10,\"offset\":0}")));

    var options =
        com.getaxonflow.sdk.types.executionreplay.ExecutionReplayTypes.ListExecutionsOptions
            .builder()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/executions/exec-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"summary\":{\"request_id\":\"exec-123\",\"workflow_name\":\"test\",\"status\":\"completed\",\"total_steps\":2,\"completed_steps\":2,\"started_at\":\"2026-01-03T12:00:00Z\",\"total_tokens\":100,\"total_cost_usd\":0.005},\"steps\":[{\"request_id\":\"exec-123\",\"step_index\":0,\"step_name\":\"greet\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\",\"tokens_in\":10,\"tokens_out\":20,\"cost_usd\":0.001}]}")));

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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/executions/exec-123/steps"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"request_id\":\"exec-123\",\"step_index\":0,\"step_name\":\"step1\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\",\"tokens_in\":10,\"tokens_out\":15,\"cost_usd\":0.001},{\"request_id\":\"exec-123\",\"step_index\":1,\"step_name\":\"step2\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:01Z\",\"tokens_in\":15,\"tokens_out\":20,\"cost_usd\":0.002}]")));

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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/executions/exec-123/timeline"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"step_index\":0,\"step_name\":\"start\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\",\"has_error\":false,\"has_approval\":false},{\"step_index\":1,\"step_name\":\"approve\",\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:01Z\",\"has_error\":false,\"has_approval\":true}]")));

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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlPathEqualTo("/api/v1/executions/exec-123/export"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"execution_id\":\"exec-123\",\"workflow_name\":\"test\",\"exported_at\":\"2026-01-03T12:00:00Z\"}")));

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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        delete(urlEqualTo("/api/v1/executions/exec-123")).willReturn(aResponse().withStatus(204)));

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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        post(urlEqualTo("/api/v1/budgets"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

    var request =
        com.getaxonflow.sdk.types.costcontrols.CostControlTypes.CreateBudgetRequest.builder()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/budgets/budget-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

    var budget = client.getBudget("budget-123");

    assertThat(budget.getId()).isEqualTo("budget-123");
    assertThat(budget.getName()).isEqualTo("Test Budget");
  }

  @Test
  @DisplayName("listBudgets should return list of budgets")
  void listBudgetsShouldReturnList(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlPathEqualTo("/api/v1/budgets"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"budgets\":[{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}],\"total\":1}")));

    var response = client.listBudgets();

    assertThat(response.getBudgets()).hasSize(1);
    assertThat(response.getTotal()).isEqualTo(1);
  }

  @Test
  @DisplayName("deleteBudget should delete a budget")
  void deleteBudgetShouldDelete(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        delete(urlEqualTo("/api/v1/budgets/budget-123")).willReturn(aResponse().withStatus(204)));

    client.deleteBudget("budget-123");

    verify(deleteRequestedFor(urlEqualTo("/api/v1/budgets/budget-123")));
  }

  @Test
  @DisplayName("getBudgetStatus should return budget status")
  void getBudgetStatusShouldReturnStatus(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/budgets/budget-123/status"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"budget_id\":\"budget-123\",\"used_usd\":45.50,\"remaining_usd\":54.50,\"usage_percent\":45.5,\"period_start\":\"2026-01-01T00:00:00Z\",\"period_end\":\"2026-01-31T23:59:59Z\",\"is_exceeded\":false}")));

    var status = client.getBudgetStatus("budget-123");

    assertThat(status.getUsedUsd()).isEqualTo(45.50);
    assertThat(status.getRemainingUsd()).isEqualTo(54.50);
    assertThat(status.isExceeded()).isFalse();
  }

  @Test
  @DisplayName("getBudgetAlerts should return budget alerts")
  void getBudgetAlertsShouldReturnAlerts(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/budgets/budget-123/alerts"))
            .willReturn(
                aResponse()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        post(urlEqualTo("/api/v1/budgets/check"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\":true,\"budget_id\":\"budget-123\",\"message\":\"Within budget\"}")));

    var request =
        com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetCheckRequest.builder()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlPathEqualTo("/api/v1/usage"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"total_cost_usd\":125.50,\"total_tokens_in\":50000,\"total_tokens_out\":25000,\"total_requests\":100,\"period\":\"monthly\",\"period_start\":\"2026-01-01T00:00:00Z\",\"period_end\":\"2026-01-31T23:59:59Z\"}")));

    var summary = client.getUsageSummary();

    assertThat(summary.getTotalCostUsd()).isEqualTo(125.50);
    assertThat(summary.getTotalTokensIn()).isEqualTo(50000);
    assertThat(summary.getTotalTokensOut()).isEqualTo(25000);
    assertThat(summary.getTotalRequests()).isEqualTo(100);
  }

  @Test
  @DisplayName("getUsageBreakdown should return usage breakdown")
  void getUsageBreakdownShouldReturnBreakdown(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlPathEqualTo("/api/v1/usage/breakdown"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"items\":[{\"dimension\":\"openai\",\"cost_usd\":80.0,\"input_tokens\":30000,\"output_tokens\":15000,\"requests\":60}],\"group_by\":\"provider\",\"period\":\"monthly\"}")));

    var breakdown = client.getUsageBreakdown("provider", "monthly");

    assertThat(breakdown.getItems()).hasSize(1);
    assertThat(breakdown.getGroupBy()).isEqualTo("provider");
  }

  @Test
  @DisplayName("listUsageRecords should return usage records")
  void listUsageRecordsShouldReturnRecords(WireMockRuntimeInfo wmRuntimeInfo) {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlPathEqualTo("/api/v1/usage/records"))
            .willReturn(
                aResponse()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        post(urlEqualTo("/api/v1/budgets"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

    var request =
        com.getaxonflow.sdk.types.costcontrols.CostControlTypes.CreateBudgetRequest.builder()
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
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlEqualTo("/api/v1/budgets/budget-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"budget-123\",\"name\":\"Test Budget\",\"scope\":\"organization\",\"limit_usd\":100.0,\"period\":\"monthly\",\"on_exceed\":\"warn\",\"alert_thresholds\":[50,80,100],\"created_at\":\"2026-01-03T12:00:00Z\",\"updated_at\":\"2026-01-03T12:00:00Z\"}")));

    var future = client.getBudgetAsync("budget-123");
    var budget = future.get();

    assertThat(budget.getId()).isEqualTo("budget-123");
  }

  @Test
  @DisplayName("getUsageSummaryAsync should return future")
  void getUsageSummaryAsyncShouldReturnFuture(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    stubFor(
        get(urlPathEqualTo("/api/v1/usage"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"total_cost_usd\":125.50,\"total_tokens_in\":50000,\"total_tokens_out\":25000,\"total_requests\":100,\"period\":\"monthly\",\"period_start\":\"2026-01-01T00:00:00Z\",\"period_end\":\"2026-01-31T23:59:59Z\"}")));

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
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue(
                "organization"))
        .isEqualTo(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.ORGANIZATION);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("team"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.TEAM);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("agent"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.AGENT);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue(
                "workflow"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.WORKFLOW);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue("user"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.USER);
  }

  @Test
  @DisplayName("BudgetScope getValue should return correct string")
  void budgetScopeGetValueShouldWork() {
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.ORGANIZATION
                .getValue())
        .isEqualTo("organization");
    assertThat(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.TEAM.getValue())
        .isEqualTo("team");
  }

  @Test
  @DisplayName("BudgetPeriod fromValue should return correct enum")
  void budgetPeriodFromValueShouldWork() {
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue("daily"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.DAILY);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue(
                "weekly"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.WEEKLY);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue(
                "monthly"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.MONTHLY);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue(
                "quarterly"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.QUARTERLY);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue(
                "yearly"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.YEARLY);
  }

  @Test
  @DisplayName("BudgetOnExceed fromValue should return correct enum")
  void budgetOnExceedFromValueShouldWork() {
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue(
                "warn"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.WARN);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue(
                "block"))
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.BLOCK);
    assertThat(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue(
                "downgrade"))
        .isEqualTo(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.DOWNGRADE);
  }

  @Test
  @DisplayName("BudgetScope fromValue should throw for invalid value")
  void budgetScopeFromValueShouldThrowForInvalid() {
    assertThatThrownBy(
            () ->
                com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.fromValue(
                    "invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown budget scope");
  }

  @Test
  @DisplayName("BudgetPeriod fromValue should throw for invalid value")
  void budgetPeriodFromValueShouldThrowForInvalid() {
    assertThatThrownBy(
            () ->
                com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.fromValue(
                    "invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown budget period");
  }

  @Test
  @DisplayName("BudgetOnExceed fromValue should throw for invalid value")
  void budgetOnExceedFromValueShouldThrowForInvalid() {
    assertThatThrownBy(
            () ->
                com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.fromValue(
                    "invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown budget on exceed action");
  }

  @Test
  @DisplayName("CreateBudgetRequest builder should set all fields")
  void createBudgetRequestBuilderShouldSetAllFields() {
    var request =
        com.getaxonflow.sdk.types.costcontrols.CostControlTypes.CreateBudgetRequest.builder()
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
    assertThat(request.getScope())
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetScope.TEAM);
    assertThat(request.getScopeId()).isEqualTo("team-123");
    assertThat(request.getLimitUsd()).isEqualTo(500.0);
    assertThat(request.getPeriod())
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetPeriod.WEEKLY);
    assertThat(request.getOnExceed())
        .isEqualTo(com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.BLOCK);
    assertThat(request.getAlertThresholds()).containsExactly(25, 50, 75);
  }

  @Test
  @DisplayName("UpdateBudgetRequest builder should set all fields")
  void updateBudgetRequestBuilderShouldSetAllFields() {
    var request =
        com.getaxonflow.sdk.types.costcontrols.CostControlTypes.UpdateBudgetRequest.builder()
            .name("Updated Budget")
            .limitUsd(1000.0)
            .onExceed(
                com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.DOWNGRADE)
            .alertThresholds(List.of(80, 90, 100))
            .build();

    assertThat(request.getName()).isEqualTo("Updated Budget");
    assertThat(request.getLimitUsd()).isEqualTo(1000.0);
    assertThat(request.getOnExceed())
        .isEqualTo(
            com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetOnExceed.DOWNGRADE);
    assertThat(request.getAlertThresholds()).containsExactly(80, 90, 100);
  }

  @Test
  @DisplayName("BudgetCheckRequest builder should set all fields")
  void budgetCheckRequestBuilderShouldSetAllFields() {
    var request =
        com.getaxonflow.sdk.types.costcontrols.CostControlTypes.BudgetCheckRequest.builder()
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

  // ========================================================================
  // MCP Query/Execute Tests (Policy Enforcement)
  // ========================================================================

  @Test
  @DisplayName("mcpQuery should return response with policy info")
  void mcpQueryShouldReturnResponseWithPolicyInfo() {
    stubFor(
        post(urlEqualTo("/mcp/resources/query"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\": true, \"data\": [{\"id\": 1, \"name\": \"Test\"}], "
                            + "\"redacted\": false, \"policy_info\": {\"policies_evaluated\": 5, "
                            + "\"blocked\": false, \"redactions_applied\": 0, \"processing_time_ms\": 2}}")));

    ConnectorResponse response = axonflow.mcpQuery("postgres", "SELECT * FROM users");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.isRedacted()).isFalse();
    assertThat(response.getPolicyInfo()).isNotNull();
    assertThat(response.getPolicyInfo().getPoliciesEvaluated()).isEqualTo(5);
  }

  @Test
  @DisplayName("mcpQuery should return redacted response")
  void mcpQueryShouldReturnRedactedResponse() {
    stubFor(
        post(urlEqualTo("/mcp/resources/query"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\": true, \"data\": [{\"id\": 1, \"ssn\": \"***REDACTED***\"}], "
                            + "\"redacted\": true, \"redacted_fields\": [\"data[0].ssn\"], "
                            + "\"policy_info\": {\"policies_evaluated\": 5, \"blocked\": false, "
                            + "\"redactions_applied\": 1, \"processing_time_ms\": 3}}")));

    ConnectorResponse response = axonflow.mcpQuery("postgres", "SELECT * FROM customers");

    assertThat(response.isRedacted()).isTrue();
    assertThat(response.getRedactedFields()).contains("data[0].ssn");
    assertThat(response.getPolicyInfo()).isNotNull();
    assertThat(response.getPolicyInfo().getRedactionsApplied()).isEqualTo(1);
  }

  @Test
  @DisplayName("mcpQuery should throw exception when blocked")
  void mcpQueryShouldThrowExceptionWhenBlocked() {
    stubFor(
        post(urlEqualTo("/mcp/resources/query"))
            .willReturn(
                aResponse()
                    .withStatus(403)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": \"Request blocked: SQLi detected\"}")));

    assertThatThrownBy(
            () -> axonflow.mcpQuery("postgres", "SELECT * FROM users; DROP TABLE users;--"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @DisplayName("mcpExecute should return response with policy info")
  void mcpExecuteShouldReturnResponseWithPolicyInfo() {
    stubFor(
        post(urlEqualTo("/mcp/resources/query"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\": true, \"data\": {\"affected_rows\": 1}, "
                            + "\"policy_info\": {\"policies_evaluated\": 3, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 1}}")));

    ConnectorResponse response =
        axonflow.mcpExecute("postgres", "UPDATE users SET name = $1 WHERE id = $2");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getPolicyInfo()).isNotNull();
    assertThat(response.getPolicyInfo().getPoliciesEvaluated()).isEqualTo(3);
  }

  // ========================================================================
  // MCP Check Input/Output Tests (Policy Pre-validation)
  // ========================================================================

  @Test
  @DisplayName("mcpCheckInput should return allowed response")
  void mcpCheckInputShouldReturnAllowedResponse() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-input"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 3, "
                            + "\"policy_info\": {\"policies_evaluated\": 3, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 1}}")));

    MCPCheckInputResponse response = axonflow.mcpCheckInput("postgres", "SELECT * FROM users");

    assertThat(response.isAllowed()).isTrue();
    assertThat(response.getPoliciesEvaluated()).isEqualTo(3);
    assertThat(response.getBlockReason()).isNull();
    assertThat(response.getPolicyInfo()).isNotNull();
    assertThat(response.getPolicyInfo().getPoliciesEvaluated()).isEqualTo(3);
  }

  @Test
  @DisplayName("mcpCheckInput with options should send operation and parameters")
  void mcpCheckInputWithOptionsShouldSendOperationAndParameters() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-input"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 5, "
                            + "\"policy_info\": {\"policies_evaluated\": 5, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 2}}")));

    Map<String, Object> options =
        Map.of("operation", "execute", "parameters", Map.of("limit", 100));
    MCPCheckInputResponse response =
        axonflow.mcpCheckInput("postgres", "UPDATE users SET name = $1", options);

    assertThat(response.isAllowed()).isTrue();
    assertThat(response.getPoliciesEvaluated()).isEqualTo(5);

    verify(
        postRequestedFor(urlEqualTo("/api/v1/mcp/check-input"))
            .withRequestBody(containing("\"connector_type\":\"postgres\""))
            .withRequestBody(containing("\"statement\":\"UPDATE users SET name = $1\""))
            .withRequestBody(containing("\"operation\":\"execute\"")));
  }

  @Test
  @DisplayName("mcpCheckInput should handle 403 as blocked result")
  void mcpCheckInputShouldHandle403AsBlockedResult() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-input"))
            .willReturn(
                aResponse()
                    .withStatus(403)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": false, \"block_reason\": \"SQL injection detected\", "
                            + "\"policies_evaluated\": 3, "
                            + "\"policy_info\": {\"policies_evaluated\": 3, \"blocked\": true, "
                            + "\"block_reason\": \"SQL injection detected\", "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 1}}")));

    MCPCheckInputResponse response =
        axonflow.mcpCheckInput("postgres", "SELECT * FROM users; DROP TABLE users;--");

    assertThat(response.isAllowed()).isFalse();
    assertThat(response.getBlockReason()).isEqualTo("SQL injection detected");
    assertThat(response.getPolicyInfo()).isNotNull();
    assertThat(response.getPolicyInfo().isBlocked()).isTrue();
  }

  @Test
  @DisplayName("mcpCheckInput should throw on 500 error")
  void mcpCheckInputShouldThrowOn500Error() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-input"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": \"Internal server error\"}")));

    assertThatThrownBy(() -> axonflow.mcpCheckInput("postgres", "SELECT 1"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Internal server error");
  }

  @Test
  @DisplayName("mcpCheckInput should require non-null connectorType")
  void mcpCheckInputShouldRequireConnectorType() {
    assertThatThrownBy(() -> axonflow.mcpCheckInput(null, "SELECT 1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("mcpCheckInput should require non-null statement")
  void mcpCheckInputShouldRequireStatement() {
    assertThatThrownBy(() -> axonflow.mcpCheckInput("postgres", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("mcpCheckInputAsync should return future")
  void mcpCheckInputAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-input"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 2, "
                            + "\"policy_info\": {\"policies_evaluated\": 2, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 1}}")));

    CompletableFuture<MCPCheckInputResponse> future =
        axonflow.mcpCheckInputAsync("postgres", "SELECT 1");
    MCPCheckInputResponse response = future.get();

    assertThat(response.isAllowed()).isTrue();
    assertThat(response.getPoliciesEvaluated()).isEqualTo(2);
  }

  @Test
  @DisplayName("mcpCheckOutput should return allowed response")
  void mcpCheckOutputShouldReturnAllowedResponse() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-output"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 4, "
                            + "\"policy_info\": {\"policies_evaluated\": 4, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 3}}")));

    List<Map<String, Object>> responseData =
        List.of(Map.of("id", 1, "name", "Alice"), Map.of("id", 2, "name", "Bob"));
    MCPCheckOutputResponse response = axonflow.mcpCheckOutput("postgres", responseData);

    assertThat(response.isAllowed()).isTrue();
    assertThat(response.getPoliciesEvaluated()).isEqualTo(4);
    assertThat(response.getBlockReason()).isNull();
    assertThat(response.getPolicyInfo()).isNotNull();
  }

  @Test
  @DisplayName("mcpCheckOutput with options should send message, metadata, and row_count")
  void mcpCheckOutputWithOptionsShouldSendOptions() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-output"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 6, "
                            + "\"policy_info\": {\"policies_evaluated\": 6, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 2}}")));

    List<Map<String, Object>> responseData = List.of(Map.of("id", 1, "name", "Alice"));
    Map<String, Object> options =
        Map.of(
            "message",
            "Query completed",
            "metadata",
            Map.of("source", "analytics"),
            "row_count",
            1);
    MCPCheckOutputResponse response = axonflow.mcpCheckOutput("postgres", responseData, options);

    assertThat(response.isAllowed()).isTrue();
    assertThat(response.getPoliciesEvaluated()).isEqualTo(6);

    verify(
        postRequestedFor(urlEqualTo("/api/v1/mcp/check-output"))
            .withRequestBody(containing("\"connector_type\":\"postgres\""))
            .withRequestBody(containing("\"message\":\"Query completed\""))
            .withRequestBody(containing("\"row_count\":1")));
  }

  @Test
  @DisplayName("mcpCheckOutput should handle 403 as blocked result")
  void mcpCheckOutputShouldHandle403AsBlockedResult() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-output"))
            .willReturn(
                aResponse()
                    .withStatus(403)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": false, \"block_reason\": \"PII detected in output\", "
                            + "\"policies_evaluated\": 4, "
                            + "\"redacted_data\": [{\"id\": 1, \"ssn\": \"***REDACTED***\"}], "
                            + "\"policy_info\": {\"policies_evaluated\": 4, \"blocked\": true, "
                            + "\"block_reason\": \"PII detected in output\", "
                            + "\"redactions_applied\": 1, \"processing_time_ms\": 5}}")));

    List<Map<String, Object>> responseData = List.of(Map.of("id", 1, "ssn", "123-45-6789"));
    MCPCheckOutputResponse response = axonflow.mcpCheckOutput("postgres", responseData);

    assertThat(response.isAllowed()).isFalse();
    assertThat(response.getBlockReason()).isEqualTo("PII detected in output");
    assertThat(response.getRedactedData()).isNotNull();
    assertThat(response.getPolicyInfo()).isNotNull();
    assertThat(response.getPolicyInfo().isBlocked()).isTrue();
  }

  @Test
  @DisplayName("mcpCheckOutput should handle response with exfiltration info")
  void mcpCheckOutputShouldHandleExfiltrationInfo() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-output"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 3, "
                            + "\"exfiltration_info\": {\"rows_returned\": 10, \"row_limit\": 1000, "
                            + "\"bytes_returned\": 2048, \"byte_limit\": 1048576, \"within_limits\": true}, "
                            + "\"policy_info\": {\"policies_evaluated\": 3, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 2}}")));

    List<Map<String, Object>> responseData = List.of(Map.of("id", 1));
    MCPCheckOutputResponse response = axonflow.mcpCheckOutput("postgres", responseData);

    assertThat(response.isAllowed()).isTrue();
    assertThat(response.getExfiltrationInfo()).isNotNull();
    assertThat(response.getExfiltrationInfo().getRowsReturned()).isEqualTo(10);
    assertThat(response.getExfiltrationInfo().isWithinLimits()).isTrue();
  }

  @Test
  @DisplayName("mcpCheckOutput should throw on 500 error")
  void mcpCheckOutputShouldThrowOn500Error() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-output"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": \"Internal server error\"}")));

    assertThatThrownBy(() -> axonflow.mcpCheckOutput("postgres", List.of(Map.of("id", 1))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Internal server error");
  }

  @Test
  @DisplayName("mcpCheckOutput should require non-null connectorType")
  void mcpCheckOutputShouldRequireConnectorType() {
    assertThatThrownBy(() -> axonflow.mcpCheckOutput(null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("mcpCheckOutput should allow null responseData for execute-style requests")
  void mcpCheckOutputShouldAllowNullResponseData() {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-output"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 1, "
                            + "\"policy_info\": {\"policies_evaluated\": 1, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 1}}")));

    Map<String, Object> options = new HashMap<>();
    options.put("message", "3 rows updated");

    MCPCheckOutputResponse resp = axonflow.mcpCheckOutput("postgres", null, options);
    assertThat(resp.isAllowed()).isTrue();
  }

  @Test
  @DisplayName("mcpCheckOutputAsync should return future")
  void mcpCheckOutputAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/mcp/check-output"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\": true, \"policies_evaluated\": 2, "
                            + "\"policy_info\": {\"policies_evaluated\": 2, \"blocked\": false, "
                            + "\"redactions_applied\": 0, \"processing_time_ms\": 1}}")));

    CompletableFuture<MCPCheckOutputResponse> future =
        axonflow.mcpCheckOutputAsync("postgres", List.of(Map.of("id", 1)));
    MCPCheckOutputResponse response = future.get();

    assertThat(response.isAllowed()).isTrue();
    assertThat(response.getPoliciesEvaluated()).isEqualTo(2);
  }

  // ========================================================================
  // Rollback Plan
  // ========================================================================

  @Test
  @DisplayName("rollbackPlan should require non-null planId")
  void rollbackPlanShouldRequirePlanId() {
    assertThatThrownBy(() -> axonflow.rollbackPlan(null, 1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("rollbackPlan should return rollback response")
  void rollbackPlanShouldReturnResponse() {
    stubFor(
        post(urlEqualTo("/api/v1/plan/plan_123/rollback/2"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"plan_id\":\"plan_123\",\"version\":2,\"previous_version\":3,\"status\":\"rolled_back\"}")));

    RollbackPlanResponse response = axonflow.rollbackPlan("plan_123", 2);

    assertThat(response.getPlanId()).isEqualTo("plan_123");
    assertThat(response.getVersion()).isEqualTo(2);
    assertThat(response.getPreviousVersion()).isEqualTo(3);
    assertThat(response.getStatus()).isEqualTo("rolled_back");
  }

  @Test
  @DisplayName("rollbackPlanAsync should return future")
  void rollbackPlanAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/plan/plan_456/rollback/1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"plan_id\":\"plan_456\",\"version\":1,\"previous_version\":3,\"status\":\"rolled_back\"}")));

    CompletableFuture<RollbackPlanResponse> future = axonflow.rollbackPlanAsync("plan_456", 1);
    RollbackPlanResponse response = future.get();

    assertThat(response.getPlanId()).isEqualTo("plan_456");
    assertThat(response.getVersion()).isEqualTo(1);
  }

  // ========================================================================
  // WCP Approval Methods
  // ========================================================================

  @Test
  @DisplayName("approveStep should require non-null workflowId")
  void approveStepShouldRequireWorkflowId() {
    assertThatThrownBy(() -> axonflow.approveStep(null, "step-1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("approveStep should require non-null stepId")
  void approveStepShouldRequireStepId() {
    assertThatThrownBy(() -> axonflow.approveStep("wf-1", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("approveStep should return approval response")
  void approveStepShouldReturnResponse() {
    stubFor(
        post(urlEqualTo("/api/v1/workflows/wf-123/steps/step-1/approve"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"workflow_id\":\"wf-123\",\"step_id\":\"step-1\",\"status\":\"approved\"}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse response =
        axonflow.approveStep("wf-123", "step-1");

    assertThat(response.getWorkflowId()).isEqualTo("wf-123");
    assertThat(response.getStepId()).isEqualTo("step-1");
    assertThat(response.getStatus()).isEqualTo("approved");
  }

  @Test
  @DisplayName("approveStepAsync should return future")
  void approveStepAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/workflows/wf-456/steps/step-2/approve"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"workflow_id\":\"wf-456\",\"step_id\":\"step-2\",\"status\":\"approved\"}")));

    CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse> future =
        axonflow.approveStepAsync("wf-456", "step-2");
    com.getaxonflow.sdk.types.workflow.WorkflowTypes.ApproveStepResponse response = future.get();

    assertThat(response.getWorkflowId()).isEqualTo("wf-456");
    assertThat(response.getStatus()).isEqualTo("approved");
  }

  @Test
  @DisplayName("rejectStep should require non-null workflowId")
  void rejectStepShouldRequireWorkflowId() {
    assertThatThrownBy(() -> axonflow.rejectStep(null, "step-1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("rejectStep should require non-null stepId")
  void rejectStepShouldRequireStepId() {
    assertThatThrownBy(() -> axonflow.rejectStep("wf-1", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("rejectStep should return rejection response")
  void rejectStepShouldReturnResponse() {
    stubFor(
        post(urlEqualTo("/api/v1/workflows/wf-123/steps/step-1/reject"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"workflow_id\":\"wf-123\",\"step_id\":\"step-1\",\"status\":\"rejected\"}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse response =
        axonflow.rejectStep("wf-123", "step-1");

    assertThat(response.getWorkflowId()).isEqualTo("wf-123");
    assertThat(response.getStepId()).isEqualTo("step-1");
    assertThat(response.getStatus()).isEqualTo("rejected");
  }

  @Test
  @DisplayName("rejectStepAsync should return future")
  void rejectStepAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/workflows/wf-789/steps/step-3/reject"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"workflow_id\":\"wf-789\",\"step_id\":\"step-3\",\"status\":\"rejected\"}")));

    CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse> future =
        axonflow.rejectStepAsync("wf-789", "step-3");
    com.getaxonflow.sdk.types.workflow.WorkflowTypes.RejectStepResponse response = future.get();

    assertThat(response.getWorkflowId()).isEqualTo("wf-789");
    assertThat(response.getStatus()).isEqualTo("rejected");
  }

  @Test
  @DisplayName("getPendingApprovals should return pending approvals")
  void getPendingApprovalsShouldReturnApprovals() {
    stubFor(
        get(urlEqualTo("/api/v1/workflows/approvals/pending"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"pending_approvals\":[{\"workflow_id\":\"wf-1\",\"workflow_name\":\"Review\","
                            + "\"step_id\":\"s-1\",\"step_index\":0,\"step_name\":\"Generate\","
                            + "\"step_type\":\"llm_call\",\"decision\":\"require_approval\","
                            + "\"created_at\":\"2026-02-07T10:00:00Z\"}],\"count\":1}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        axonflow.getPendingApprovals();

    assertThat(response.getCount()).isEqualTo(1);
    assertThat(response.getPendingApprovals()).hasSize(1);
    assertThat(response.getPendingApprovals().get(0).getWorkflowId()).isEqualTo("wf-1");
    assertThat(response.getPendingApprovals().get(0).getStepName()).isEqualTo("Generate");
    // WCP entries must NOT carry plan_id
    assertThat(response.getPendingApprovals().get(0).getPlanId()).isNull();
  }

  @Test
  @DisplayName("getPendingApprovals with limit should add query parameter")
  void getPendingApprovalsWithLimitShouldAddQueryParam() {
    stubFor(
        get(urlEqualTo("/api/v1/workflows/approvals/pending?limit=10"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pending_approvals\":[],\"count\":0}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        axonflow.getPendingApprovals(10);

    assertThat(response.getCount()).isEqualTo(0);
    assertThat(response.getPendingApprovals()).isEmpty();
  }

  @Test
  @DisplayName("getPendingApprovalsAsync should return future")
  void getPendingApprovalsAsyncShouldReturnFuture() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/workflows/approvals/pending?limit=5"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pending_approvals\":[],\"count\":0}")));

    CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse>
        future = axonflow.getPendingApprovalsAsync(5);
    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        future.get();

    assertThat(response.getCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("getPendingPlanApprovals should return MAP-plane approvals with plan_id")
  void getPendingPlanApprovalsShouldReturnMapApprovals() {
    stubFor(
        get(urlEqualTo("/api/v1/plans/approvals/pending"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"pending_approvals\":[{\"workflow_id\":\"wf_map_abc\","
                            + "\"workflow_name\":\"map-confirm-plan-abc\","
                            + "\"plan_id\":\"plan-abc\","
                            + "\"step_id\":\"step_0_analyze\",\"step_index\":0,"
                            + "\"step_name\":\"Analyze transaction\",\"step_type\":\"tool_call\","
                            + "\"decision\":\"require_approval\","
                            + "\"created_at\":\"2026-04-22T10:00:00Z\"}],\"count\":1}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        axonflow.getPendingPlanApprovals();

    assertThat(response.getCount()).isEqualTo(1);
    assertThat(response.getPendingApprovals()).hasSize(1);
    assertThat(response.getPendingApprovals().get(0).getPlanId()).isEqualTo("plan-abc");
    assertThat(response.getPendingApprovals().get(0).getStepName()).isEqualTo("Analyze transaction");
  }

  @Test
  @DisplayName("getPendingPlanApprovals with plan_id filter should propagate to query string")
  void getPendingPlanApprovalsWithPlanIdFilterShouldEncodeQuery() {
    stubFor(
        get(urlEqualTo("/api/v1/plans/approvals/pending?plan_id=plan-abc"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pending_approvals\":[],\"count\":0}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        axonflow.getPendingPlanApprovals(0, "plan-abc");

    assertThat(response.getCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("getPendingPlanApprovals with limit and plan_id should encode both")
  void getPendingPlanApprovalsWithLimitAndPlanIdShouldEncodeBoth() {
    stubFor(
        get(urlEqualTo("/api/v1/plans/approvals/pending?limit=3&plan_id=plan-x"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pending_approvals\":[],\"count\":0}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        axonflow.getPendingPlanApprovals(3, "plan-x");

    assertThat(response.getCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("getPendingPlanApprovals with limit only omits plan_id")
  void getPendingPlanApprovalsWithLimitOnlyOmitsPlanId() {
    stubFor(
        get(urlEqualTo("/api/v1/plans/approvals/pending?limit=5"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pending_approvals\":[],\"count\":0}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        axonflow.getPendingPlanApprovals(5);

    assertThat(response.getCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("getPendingPlanApprovals URL-encodes plan_id with special characters")
  void getPendingPlanApprovalsUrlEncodesPlanId() {
    // A plan_id containing characters that would break a raw concatenation
    // must be URL-encoded on the wire. URLEncoder encodes ' ' -> '+' and
    // '&' -> '%26'; the stub asserts the encoded form lands on the server.
    stubFor(
        get(urlEqualTo("/api/v1/plans/approvals/pending?plan_id=plan+a%26b"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pending_approvals\":[],\"count\":0}")));

    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        axonflow.getPendingPlanApprovals(0, "plan a&b");

    assertThat(response.getCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("getPendingPlanApprovalsAsync should return future")
  void getPendingPlanApprovalsAsyncShouldReturnFuture() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/plans/approvals/pending"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pending_approvals\":[],\"count\":0}")));

    CompletableFuture<com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse>
        future = axonflow.getPendingPlanApprovalsAsync(0, null);
    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApprovalsResponse response =
        future.get();

    assertThat(response.getCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("PendingApproval back-compat constructor preserves legacy field set")
  void pendingApprovalBackCompatConstructor() {
    com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApproval approval =
        new com.getaxonflow.sdk.types.workflow.WorkflowTypes.PendingApproval(
            "wf-1", "Review", "s-1", "Generate", "llm_call", "2026-02-07T10:00:00Z");
    assertThat(approval.getWorkflowId()).isEqualTo("wf-1");
    assertThat(approval.getStepName()).isEqualTo("Generate");
    // New fields default to null / 0 for the legacy constructor
    assertThat(approval.getPlanId()).isNull();
    assertThat(approval.getStepIndex()).isEqualTo(0);
    assertThat(approval.getDecision()).isNull();
  }

  // ========================================================================
  // Webhook CRUD Methods
  // ========================================================================

  @Test
  @DisplayName("createWebhook should require non-null request")
  void createWebhookShouldRequireRequest() {
    assertThatThrownBy(() -> axonflow.createWebhook(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("createWebhook should return created subscription")
  void createWebhookShouldReturnSubscription() {
    stubFor(
        post(urlEqualTo("/api/v1/webhooks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"wh-123\",\"url\":\"https://example.com/hook\","
                            + "\"events\":[\"step.blocked\"],\"active\":true,"
                            + "\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:00:00Z\"}")));

    com.getaxonflow.sdk.types.webhook.WebhookTypes.CreateWebhookRequest request =
        com.getaxonflow.sdk.types.webhook.WebhookTypes.CreateWebhookRequest.builder()
            .url("https://example.com/hook")
            .events(List.of("step.blocked"))
            .secret("my-secret")
            .active(true)
            .build();

    com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription subscription =
        axonflow.createWebhook(request);

    assertThat(subscription.getId()).isEqualTo("wh-123");
    assertThat(subscription.getUrl()).isEqualTo("https://example.com/hook");
    assertThat(subscription.getEvents()).containsExactly("step.blocked");
    assertThat(subscription.isActive()).isTrue();
  }

  @Test
  @DisplayName("createWebhookAsync should return future")
  void createWebhookAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/webhooks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"wh-456\",\"url\":\"https://example.com\","
                            + "\"events\":[],\"active\":true}")));

    com.getaxonflow.sdk.types.webhook.WebhookTypes.CreateWebhookRequest request =
        com.getaxonflow.sdk.types.webhook.WebhookTypes.CreateWebhookRequest.builder()
            .url("https://example.com")
            .build();

    CompletableFuture<com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription> future =
        axonflow.createWebhookAsync(request);
    com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription subscription = future.get();

    assertThat(subscription.getId()).isEqualTo("wh-456");
  }

  @Test
  @DisplayName("getWebhook should require non-null webhookId")
  void getWebhookShouldRequireWebhookId() {
    assertThatThrownBy(() -> axonflow.getWebhook(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("getWebhook should return subscription")
  void getWebhookShouldReturnSubscription() {
    stubFor(
        get(urlEqualTo("/api/v1/webhooks/wh-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"wh-123\",\"url\":\"https://example.com/hook\","
                            + "\"events\":[\"workflow.completed\"],\"active\":true,"
                            + "\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T11:00:00Z\"}")));

    com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription subscription =
        axonflow.getWebhook("wh-123");

    assertThat(subscription.getId()).isEqualTo("wh-123");
    assertThat(subscription.getUrl()).isEqualTo("https://example.com/hook");
    assertThat(subscription.getEvents()).containsExactly("workflow.completed");
  }

  @Test
  @DisplayName("getWebhookAsync should return future")
  void getWebhookAsyncShouldReturnFuture() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/webhooks/wh-789"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"wh-789\",\"url\":\"https://example.com\","
                            + "\"events\":[],\"active\":true}")));

    CompletableFuture<com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription> future =
        axonflow.getWebhookAsync("wh-789");
    com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription subscription = future.get();

    assertThat(subscription.getId()).isEqualTo("wh-789");
  }

  @Test
  @DisplayName("updateWebhook should require non-null webhookId")
  void updateWebhookShouldRequireWebhookId() {
    assertThatThrownBy(
            () ->
                axonflow.updateWebhook(
                    null,
                    com.getaxonflow.sdk.types.webhook.WebhookTypes.UpdateWebhookRequest.builder()
                        .build()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("updateWebhook should require non-null request")
  void updateWebhookShouldRequireRequest() {
    assertThatThrownBy(() -> axonflow.updateWebhook("wh-1", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("updateWebhook should return updated subscription")
  void updateWebhookShouldReturnUpdatedSubscription() {
    stubFor(
        put(urlEqualTo("/api/v1/webhooks/wh-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"wh-123\",\"url\":\"https://new-url.com/hook\","
                            + "\"events\":[\"step.approved\"],\"active\":false,"
                            + "\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T12:00:00Z\"}")));

    com.getaxonflow.sdk.types.webhook.WebhookTypes.UpdateWebhookRequest request =
        com.getaxonflow.sdk.types.webhook.WebhookTypes.UpdateWebhookRequest.builder()
            .url("https://new-url.com/hook")
            .events(List.of("step.approved"))
            .active(false)
            .build();

    com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription subscription =
        axonflow.updateWebhook("wh-123", request);

    assertThat(subscription.getId()).isEqualTo("wh-123");
    assertThat(subscription.getUrl()).isEqualTo("https://new-url.com/hook");
    assertThat(subscription.isActive()).isFalse();
  }

  @Test
  @DisplayName("updateWebhookAsync should return future")
  void updateWebhookAsyncShouldReturnFuture() throws Exception {
    stubFor(
        put(urlEqualTo("/api/v1/webhooks/wh-456"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"wh-456\",\"url\":\"https://example.com\","
                            + "\"events\":[],\"active\":true}")));

    com.getaxonflow.sdk.types.webhook.WebhookTypes.UpdateWebhookRequest request =
        com.getaxonflow.sdk.types.webhook.WebhookTypes.UpdateWebhookRequest.builder()
            .active(true)
            .build();

    CompletableFuture<com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription> future =
        axonflow.updateWebhookAsync("wh-456", request);
    com.getaxonflow.sdk.types.webhook.WebhookTypes.WebhookSubscription subscription = future.get();

    assertThat(subscription.getId()).isEqualTo("wh-456");
  }

  @Test
  @DisplayName("deleteWebhook should require non-null webhookId")
  void deleteWebhookShouldRequireWebhookId() {
    assertThatThrownBy(() -> axonflow.deleteWebhook(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("deleteWebhook should call delete endpoint")
  void deleteWebhookShouldCallDeleteEndpoint() {
    stubFor(delete(urlEqualTo("/api/v1/webhooks/wh-123")).willReturn(aResponse().withStatus(204)));

    axonflow.deleteWebhook("wh-123");

    verify(deleteRequestedFor(urlEqualTo("/api/v1/webhooks/wh-123")));
  }

  @Test
  @DisplayName("deleteWebhookAsync should return future")
  void deleteWebhookAsyncShouldReturnFuture() throws Exception {
    stubFor(delete(urlEqualTo("/api/v1/webhooks/wh-456")).willReturn(aResponse().withStatus(204)));

    CompletableFuture<Void> future = axonflow.deleteWebhookAsync("wh-456");
    future.get();

    verify(deleteRequestedFor(urlEqualTo("/api/v1/webhooks/wh-456")));
  }

  @Test
  @DisplayName("listWebhooks should return list of subscriptions")
  void listWebhooksShouldReturnList() {
    stubFor(
        get(urlEqualTo("/api/v1/webhooks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"webhooks\":[{\"id\":\"wh-1\",\"url\":\"https://example.com\","
                            + "\"events\":[\"step.blocked\"],\"active\":true},"
                            + "{\"id\":\"wh-2\",\"url\":\"https://other.com\","
                            + "\"events\":[\"workflow.completed\"],\"active\":false}],\"total\":2}")));

    com.getaxonflow.sdk.types.webhook.WebhookTypes.ListWebhooksResponse response =
        axonflow.listWebhooks();

    assertThat(response.getTotal()).isEqualTo(2);
    assertThat(response.getWebhooks()).hasSize(2);
    assertThat(response.getWebhooks().get(0).getId()).isEqualTo("wh-1");
    assertThat(response.getWebhooks().get(1).getId()).isEqualTo("wh-2");
  }

  @Test
  @DisplayName("listWebhooksAsync should return future")
  void listWebhooksAsyncShouldReturnFuture() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/webhooks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"webhooks\":[],\"total\":0}")));

    CompletableFuture<com.getaxonflow.sdk.types.webhook.WebhookTypes.ListWebhooksResponse> future =
        axonflow.listWebhooksAsync();
    com.getaxonflow.sdk.types.webhook.WebhookTypes.ListWebhooksResponse response = future.get();

    assertThat(response.getTotal()).isEqualTo(0);
    assertThat(response.getWebhooks()).isEmpty();
  }

  @Test
  @DisplayName("listWebhooks should return empty list when no webhooks exist")
  void listWebhooksShouldReturnEmptyList() {
    stubFor(
        get(urlEqualTo("/api/v1/webhooks"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"webhooks\":[],\"total\":0}")));

    com.getaxonflow.sdk.types.webhook.WebhookTypes.ListWebhooksResponse response =
        axonflow.listWebhooks();

    assertThat(response.getTotal()).isEqualTo(0);
    assertThat(response.getWebhooks()).isEmpty();
  }

  // ========================================================================
  // Unified Execution Streaming (SSE)
  // ========================================================================

  @Test
  @DisplayName("streamExecutionStatus should throw NullPointerException for null executionId")
  void streamExecutionStatusShouldRejectNullId() {
    assertThatThrownBy(() -> axonflow.streamExecutionStatus(null, status -> {}))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("streamExecutionStatus should throw NullPointerException for null callback")
  void streamExecutionStatusShouldRejectNullCallback() {
    assertThatThrownBy(() -> axonflow.streamExecutionStatus("exec_123", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("streamExecutionStatus should invoke callback for each SSE event")
  void streamExecutionStatusShouldInvokeCallback() {
    String runningEvent =
        "data: {\"execution_id\":\"exec_123\",\"execution_type\":\"map_plan\","
            + "\"name\":\"Test\",\"status\":\"running\",\"current_step_index\":0,"
            + "\"total_steps\":3,\"progress_percent\":33.0,\"started_at\":\"2026-02-07T10:00:00Z\","
            + "\"steps\":[],\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:00:00Z\"}\n\n";
    String completedEvent =
        "data: {\"execution_id\":\"exec_123\",\"execution_type\":\"map_plan\","
            + "\"name\":\"Test\",\"status\":\"completed\",\"current_step_index\":2,"
            + "\"total_steps\":3,\"progress_percent\":100.0,\"started_at\":\"2026-02-07T10:00:00Z\","
            + "\"completed_at\":\"2026-02-07T10:01:00Z\",\"steps\":[],"
            + "\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:01:00Z\"}\n\n";

    stubFor(
        get(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(runningEvent + completedEvent)));

    java.util.List<com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus> updates =
        new java.util.ArrayList<>();
    axonflow.streamExecutionStatus("exec_123", updates::add);

    assertThat(updates).hasSize(2);
    assertThat(updates.get(0).getStatus().getValue()).isEqualTo("running");
    assertThat(updates.get(0).getProgressPercent()).isEqualTo(33.0);
    assertThat(updates.get(1).getStatus().getValue()).isEqualTo("completed");
    assertThat(updates.get(1).getProgressPercent()).isEqualTo(100.0);
  }

  @Test
  @DisplayName("streamExecutionStatus should stop on failed terminal status")
  void streamExecutionStatusShouldStopOnFailed() {
    String failedEvent =
        "data: {\"execution_id\":\"exec_123\",\"execution_type\":\"wcp_workflow\","
            + "\"name\":\"Test\",\"status\":\"failed\",\"current_step_index\":1,"
            + "\"total_steps\":3,\"progress_percent\":33.0,\"started_at\":\"2026-02-07T10:00:00Z\","
            + "\"error\":\"Step 2 timed out\",\"steps\":[],"
            + "\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:00:30Z\"}\n\n";

    // Add extra data after failed - should not be consumed
    String extraEvent =
        "data: {\"execution_id\":\"exec_123\",\"execution_type\":\"wcp_workflow\","
            + "\"name\":\"Test\",\"status\":\"running\",\"current_step_index\":2,"
            + "\"total_steps\":3,\"progress_percent\":66.0,\"started_at\":\"2026-02-07T10:00:00Z\","
            + "\"steps\":[],\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:00:45Z\"}\n\n";

    stubFor(
        get(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(failedEvent + extraEvent)));

    java.util.List<com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus> updates =
        new java.util.ArrayList<>();
    axonflow.streamExecutionStatus("exec_123", updates::add);

    assertThat(updates).hasSize(1);
    assertThat(updates.get(0).getStatus().getValue()).isEqualTo("failed");
    assertThat(updates.get(0).getError()).isEqualTo("Step 2 timed out");
  }

  @Test
  @DisplayName("streamExecutionStatus should skip [DONE] sentinel")
  void streamExecutionStatusShouldSkipDone() {
    String completedEvent =
        "data: {\"execution_id\":\"exec_123\",\"execution_type\":\"map_plan\","
            + "\"name\":\"Test\",\"status\":\"completed\",\"current_step_index\":2,"
            + "\"total_steps\":2,\"progress_percent\":100.0,\"started_at\":\"2026-02-07T10:00:00Z\","
            + "\"steps\":[],\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:01:00Z\"}\n\n";
    String doneEvent = "data: [DONE]\n\n";

    stubFor(
        get(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(completedEvent + doneEvent)));

    java.util.List<com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus> updates =
        new java.util.ArrayList<>();
    axonflow.streamExecutionStatus("exec_123", updates::add);

    assertThat(updates).hasSize(1);
    assertThat(updates.get(0).getStatus().getValue()).isEqualTo("completed");
  }

  @Test
  @DisplayName("streamExecutionStatus should throw on 401")
  void streamExecutionStatusShouldThrowOn401() {
    stubFor(
        get(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

    assertThatThrownBy(() -> axonflow.streamExecutionStatus("exec_123", status -> {}))
        .isInstanceOf(AuthenticationException.class);
  }

  @Test
  @DisplayName("streamExecutionStatus should throw on 404")
  void streamExecutionStatusShouldThrowOn404() {
    stubFor(
        get(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .willReturn(
                aResponse().withStatus(404).withBody("{\"error\":\"Execution not found\"}")));

    assertThatThrownBy(() -> axonflow.streamExecutionStatus("exec_123", status -> {}))
        .isInstanceOf(AxonFlowException.class);
  }

  @Test
  @DisplayName("streamExecutionStatus should handle malformed JSON gracefully")
  void streamExecutionStatusShouldHandleMalformedJson() {
    String malformedEvent = "data: {invalid json}\n\n";
    String completedEvent =
        "data: {\"execution_id\":\"exec_123\",\"execution_type\":\"map_plan\","
            + "\"name\":\"Test\",\"status\":\"completed\",\"current_step_index\":1,"
            + "\"total_steps\":1,\"progress_percent\":100.0,\"started_at\":\"2026-02-07T10:00:00Z\","
            + "\"steps\":[],\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:01:00Z\"}\n\n";

    stubFor(
        get(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(malformedEvent + completedEvent)));

    java.util.List<com.getaxonflow.sdk.types.execution.ExecutionTypes.ExecutionStatus> updates =
        new java.util.ArrayList<>();
    axonflow.streamExecutionStatus("exec_123", updates::add);

    // Should skip malformed event and get the completed one
    assertThat(updates).hasSize(1);
    assertThat(updates.get(0).getStatus().getValue()).isEqualTo("completed");
  }

  @Test
  @DisplayName("streamExecutionStatus should send correct request headers")
  void streamExecutionStatusShouldSendCorrectHeaders() {
    String completedEvent =
        "data: {\"execution_id\":\"exec_123\",\"execution_type\":\"map_plan\","
            + "\"name\":\"Test\",\"status\":\"completed\",\"current_step_index\":0,"
            + "\"total_steps\":1,\"progress_percent\":100.0,\"started_at\":\"2026-02-07T10:00:00Z\","
            + "\"steps\":[],\"created_at\":\"2026-02-07T10:00:00Z\",\"updated_at\":\"2026-02-07T10:01:00Z\"}\n\n";

    stubFor(
        get(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(completedEvent)));

    axonflow.streamExecutionStatus("exec_123", status -> {});

    verify(
        getRequestedFor(urlEqualTo("/api/v1/unified/executions/exec_123/stream"))
            .withHeader("Accept", equalTo("text/event-stream")));
  }

  // ========================================================================
  // Media Cache Skip
  // ========================================================================

  @Test
  @DisplayName("proxyLLMCall should skip cache with media")
  void proxyLLMCallShouldSkipCacheWithMedia() {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

    MediaContent mediaItem =
        MediaContent.builder()
            .source("base64")
            .mimeType("image/png")
            .base64Data("dGVzdC1pbWFnZQ==")
            .build();

    ClientRequest request =
        ClientRequest.builder()
            .query("describe image")
            .userToken("user-123")
            .requestType(RequestType.CHAT)
            .media(List.of(mediaItem))
            .build();

    // First call
    axonflow.proxyLLMCall(request);
    // Second call — should NOT use cache
    axonflow.proxyLLMCall(request);

    // Both calls should hit the server (no caching for media)
    verify(exactly(2), postRequestedFor(urlEqualTo("/api/request")));
  }

  @Test
  @DisplayName("proxyLLMCall should use cache without media")
  void proxyLLMCallShouldUseCacheWithoutMedia() {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"blocked\":false}")));

    ClientRequest request =
        ClientRequest.builder()
            .query("hello")
            .userToken("user-123")
            .requestType(RequestType.CHAT)
            .build();

    // First call
    axonflow.proxyLLMCall(request);
    // Second call — should use cache
    axonflow.proxyLLMCall(request);

    // Only one call should hit the server (second cached)
    verify(exactly(1), postRequestedFor(urlEqualTo("/api/request")));
  }
}
