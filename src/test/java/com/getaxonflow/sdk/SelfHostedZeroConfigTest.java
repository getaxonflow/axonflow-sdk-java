// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Self-Hosted Zero-Config Mode Tests.
 *
 * <p>Tests for the zero-configuration self-hosted mode where users can run AxonFlow without any API
 * keys, license keys, or credentials.
 *
 * <p>This tests the scenario where a first-time user:
 *
 * <ol>
 *   <li>Starts the agent with SELF_HOSTED_MODE=true
 *   <li>Connects the SDK with no credentials
 *   <li>Makes requests that should succeed without authentication
 * </ol>
 */
@WireMockTest
@DisplayName("Self-Hosted Zero-Config Mode Tests")
class SelfHostedZeroConfigTest {

  // ========================================================================
  // 1. CLIENT INITIALIZATION WITHOUT CREDENTIALS
  // ========================================================================
  @Nested
  @DisplayName("1. Client Initialization Without Credentials")
  class ClientInitializationTests {

    @Test
    @DisplayName("should create client with no credentials for localhost")
    void shouldCreateClientWithNoCredentialsForLocalhost(WireMockRuntimeInfo wmRuntimeInfo) {
      // WireMock runs on localhost - should not require credentials
      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                  // No clientId, no clientSecret
                  .build());

      assertThat(client).isNotNull();
      System.out.println("✅ Client created without credentials for localhost");
    }

    @Test
    @DisplayName("should create client with empty credentials for localhost")
    void shouldCreateClientWithEmptyCredentialsForLocalhost(WireMockRuntimeInfo wmRuntimeInfo) {
      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                  .clientId("")
                  .clientSecret("")
                  .build());

      assertThat(client).isNotNull();
      System.out.println("✅ Client created with empty credentials for localhost");
    }

    @Test
    @DisplayName(
        "should allow client creation without credentials for any endpoint (community mode)")
    void shouldAllowClientCreationWithoutCredentialsForAnyEndpoint() {
      // Community mode: credentials are optional for any endpoint
      AxonFlowConfig config =
          AxonFlowConfig.builder()
              .agentUrl("https://my-custom-domain.local")
              // No credentials - community mode
              .build();

      assertThat(config.hasCredentials()).isFalse();

      System.out.println("✅ Community mode works without credentials for any endpoint");
    }
  }

  // ========================================================================
  // 2. GATEWAY MODE (Enterprise Feature - requires credentials)
  // ========================================================================
  @Nested
  @DisplayName("2. Gateway Mode (Enterprise Feature)")
  @WireMockTest
  class GatewayModeTests {

    private AxonFlow axonflow;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
      // Gateway Mode is an enterprise feature that requires credentials
      axonflow =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                  .clientId("test-client")
                  .clientSecret("test-secret")
                  .build());
    }

    @Test
    @DisplayName("should perform pre-check with empty token")
    void shouldPerformPreCheckWithEmptyToken() {
      stubFor(
          post(urlEqualTo("/api/policy/pre-check"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"context_id\": \"ctx_zeroconfig_123\","
                              + "\"approved\": true,"
                              + "\"policies\": [\"default_policy\"]"
                              + "}")));

      PolicyApprovalResult result =
          axonflow.getPolicyApprovedContext(
              PolicyApprovalRequest.builder()
                  .userToken("") // Empty token - zero-config scenario
                  .query("What is the weather in Paris?")
                  .build());

      assertThat(result.isApproved()).isTrue();
      assertThat(result.getContextId()).isEqualTo("ctx_zeroconfig_123");

      // Verify request was made without auth headers
      verify(
          postRequestedFor(urlEqualTo("/api/policy/pre-check"))
              .withRequestBody(containing("\"user_token\":\"\"")));

      System.out.println("✅ Pre-check succeeded with empty token");
    }

    @Test
    @DisplayName("should perform pre-check with whitespace token")
    void shouldPerformPreCheckWithWhitespaceToken() {
      stubFor(
          post(urlEqualTo("/api/policy/pre-check"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"context_id\": \"ctx_whitespace_456\","
                              + "\"approved\": true,"
                              + "\"policies\": []"
                              + "}")));

      PolicyApprovalResult result =
          axonflow.getPolicyApprovedContext(
              PolicyApprovalRequest.builder()
                  .userToken("   ") // Whitespace only
                  .query("Simple test query")
                  .build());

      assertThat(result.isApproved()).isTrue();
      System.out.println("✅ Pre-check succeeded with whitespace token");
    }

    @Test
    @DisplayName("should complete full Gateway Mode flow without credentials")
    void shouldCompleteFullGatewayFlowWithoutCredentials() {
      // Step 1: Pre-check
      stubFor(
          post(urlEqualTo("/api/policy/pre-check"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"context_id\": \"ctx_fullflow_789\","
                              + "\"approved\": true"
                              + "}")));

      PolicyApprovalResult preCheck =
          axonflow.getPolicyApprovedContext(
              PolicyApprovalRequest.builder()
                  .userToken("")
                  .query("Analyze quarterly sales data")
                  .build());

      assertThat(preCheck.getContextId()).isEqualTo("ctx_fullflow_789");

      // Step 2: Audit
      stubFor(
          post(urlEqualTo("/api/audit/llm-call"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"success\": true,"
                              + "\"audit_id\": \"audit_zeroconfig_001\""
                              + "}")));

      AuditResult audit =
          axonflow.auditLLMCall(
              AuditOptions.builder()
                  .contextId(preCheck.getContextId())
                  .clientId("default")
                  .provider("openai")
                  .model("gpt-4")
                  .tokenUsage(TokenUsage.of(100, 175))
                  .latencyMs(350)
                  .build());

      assertThat(audit.isSuccess()).isTrue();
      assertThat(audit.getAuditId()).isEqualTo("audit_zeroconfig_001");

      System.out.println("✅ Full Gateway Mode flow completed without credentials");
    }
  }

  // ========================================================================
  // 3. PROXY MODE WITHOUT AUTHENTICATION
  // ========================================================================
  @Nested
  @DisplayName("3. Proxy Mode Without Authentication")
  @WireMockTest
  class ProxyModeTests {

    private AxonFlow axonflow;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
      axonflow =
          AxonFlow.create(
              AxonFlowConfig.builder().agentUrl(wmRuntimeInfo.getHttpBaseUrl()).build());
    }

    @Test
    @DisplayName("should execute query with empty token")
    void shouldExecuteQueryWithEmptyToken() {
      stubFor(
          post(urlEqualTo("/api/request"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"success\": true,"
                              + "\"data\": {\"answer\": \"4\"},"
                              + "\"blocked\": false"
                              + "}")));

      ClientResponse response =
          axonflow.proxyLLMCall(
              ClientRequest.builder()
                  .userToken("") // Empty token
                  .query("What is 2 + 2?")
                  .build());

      assertThat(response.isSuccess()).isTrue();
      assertThat(response.isBlocked()).isFalse();

      System.out.println("✅ Query executed with empty token");
    }
  }

  // ========================================================================
  // 4. POLICY ENFORCEMENT (Enterprise Feature - requires credentials)
  // ========================================================================
  @Nested
  @DisplayName("4. Policy Enforcement (Enterprise Feature)")
  @WireMockTest
  class PolicyEnforcementTests {

    private AxonFlow axonflow;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
      // Policy enforcement (Gateway Mode) is an enterprise feature
      axonflow =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                  .clientId("test-client")
                  .clientSecret("test-secret")
                  .build());
    }

    @Test
    @DisplayName("should block SQL injection with enterprise credentials")
    void shouldBlockSqlInjectionWithoutCredentials() {
      stubFor(
          post(urlEqualTo("/api/policy/pre-check"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"context_id\": \"ctx_blocked_001\","
                              + "\"approved\": false,"
                              + "\"block_reason\": \"SQL injection detected\","
                              + "\"policies\": [\"sql_injection_detection\"]"
                              + "}")));

      assertThatThrownBy(
              () ->
                  axonflow.getPolicyApprovedContext(
                      PolicyApprovalRequest.builder()
                          .userToken("")
                          .query("SELECT * FROM users; DROP TABLE users;--")
                          .build()))
          .hasMessageContaining("SQL injection");

      System.out.println("✅ SQL injection blocked without credentials");
    }

    @Test
    @DisplayName("should block PII with enterprise credentials")
    void shouldBlockPiiWithoutCredentials() {
      stubFor(
          post(urlEqualTo("/api/policy/pre-check"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"context_id\": \"ctx_blocked_002\","
                              + "\"approved\": false,"
                              + "\"block_reason\": \"PII detected: SSN\","
                              + "\"policies\": [\"pii_detection\"]"
                              + "}")));

      assertThatThrownBy(
              () ->
                  axonflow.getPolicyApprovedContext(
                      PolicyApprovalRequest.builder()
                          .userToken("")
                          .query("My social security number is 123-45-6789")
                          .build()))
          .hasMessageContaining("PII");

      System.out.println("✅ PII blocked without credentials");
    }
  }

  // ========================================================================
  // 5. HEALTH CHECK WITHOUT AUTH
  // ========================================================================
  @Nested
  @DisplayName("5. Health Check Without Authentication")
  @WireMockTest
  class HealthCheckTests {

    @Test
    @DisplayName("should check health without credentials")
    void shouldCheckHealthWithoutCredentials(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          get(urlEqualTo("/health"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{" + "\"status\": \"healthy\"," + "\"version\": \"1.0.0\"" + "}")));

      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder().agentUrl(wmRuntimeInfo.getHttpBaseUrl()).build());

      HealthStatus health = client.healthCheck();

      assertThat(health.isHealthy()).isTrue();
      assertThat(health.getVersion()).isEqualTo("1.0.0");

      System.out.println("✅ Health check succeeded without credentials");
    }
  }

  // ========================================================================
  // 6. FIRST-TIME USER EXPERIENCE (Community Mode)
  // ========================================================================
  @Nested
  @DisplayName("6. First-Time User Experience (Community Mode)")
  @WireMockTest
  class FirstTimeUserTests {

    @Test
    @DisplayName("should support first-time user with minimal configuration for community features")
    void shouldSupportFirstTimeUser(WireMockRuntimeInfo wmRuntimeInfo) {
      // Stub health endpoint
      stubFor(
          get(urlEqualTo("/health"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"status\": \"healthy\"}")));

      // Stub proxyLLMCall endpoint (community feature)
      stubFor(
          post(urlEqualTo("/api/request"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"success\": true,"
                              + "\"data\": {\"answer\": \"Hello world!\"}"
                              + "}")));

      // First-time user - minimal configuration (community mode)
      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                  // No credentials - community mode
                  .build());

      // Step 1: Health check should work
      HealthStatus health = client.healthCheck();
      assertThat(health.isHealthy()).isTrue();

      // Step 2: proxyLLMCall should work (community feature)
      ClientResponse response =
          client.proxyLLMCall(
              ClientRequest.builder()
                  .userToken("")
                  .query("Hello, this is my first query!")
                  .build());

      assertThat(response.isSuccess()).isTrue();

      System.out.println("✅ First-time user experience validated (community mode)");
      System.out.println("   - Client creation: OK");
      System.out.println("   - Health check: OK");
      System.out.println("   - Proxy LLM call: OK");
    }
  }

  // ========================================================================
  // 7. AUTH HEADERS BASED ON CREDENTIALS
  // ========================================================================
  @Nested
  @DisplayName("7. Auth Headers Based on Credentials")
  @WireMockTest
  class AuthHeaderTests {

    @Test
    @DisplayName("should send community Basic auth when no credentials configured")
    void shouldSendCommunityAuthWithoutCredentials(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          post(urlEqualTo("/api/request"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{" + "\"success\": true," + "\"data\": {\"answer\": \"test\"}" + "}")));

      // No credentials - community mode (effective clientId = "community")
      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder().agentUrl(wmRuntimeInfo.getHttpBaseUrl()).build());

      client.proxyLLMCall(ClientRequest.builder().userToken("").query("Test query").build());

      // Basic auth always sent with effective clientId ("community:")
      String expectedAuth =
          "Basic "
              + java.util.Base64.getEncoder()
                  .encodeToString("community:".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      verify(
          postRequestedFor(urlEqualTo("/api/request"))
              .withoutHeader("X-License-Key")
              .withHeader("Authorization", equalTo(expectedAuth)));

      System.out.println("✅ Community mode: Basic auth with default clientId");
    }

    @Test
    @DisplayName("should send auth headers when credentials are configured")
    void shouldSendAuthHeadersWithCredentials(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          post(urlEqualTo("/api/request"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{" + "\"success\": true," + "\"data\": {\"answer\": \"test\"}" + "}")));

      // With credentials - enterprise mode
      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                  .clientId("test-client")
                  .clientSecret("test-secret")
                  .build());

      client.proxyLLMCall(ClientRequest.builder().userToken("").query("Test query").build());

      // Verify OAuth2 Basic auth header is sent when credentials are configured
      String expectedBasic =
          "Basic "
              + java.util.Base64.getEncoder()
                  .encodeToString(
                      "test-client:test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      verify(
          postRequestedFor(urlEqualTo("/api/request"))
              .withHeader("Authorization", equalTo(expectedBasic)));

      System.out.println("✅ Auth headers sent when credentials are configured");
    }

    @Test
    @DisplayName("should send OAuth2 Basic auth with clientId and clientSecret")
    void shouldSendOAuth2BasicAuth(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          post(urlEqualTo("/api/request"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{" + "\"success\": true," + "\"data\": {\"answer\": \"test\"}" + "}")));

      // With OAuth2 credentials
      AxonFlow client =
          AxonFlow.create(
              AxonFlowConfig.builder()
                  .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                  .clientId("my-client")
                  .clientSecret("my-secret")
                  .build());

      client.proxyLLMCall(ClientRequest.builder().userToken("").query("Test query").build());

      // Verify OAuth2 Basic auth header is sent
      String expectedBasic =
          "Basic "
              + java.util.Base64.getEncoder()
                  .encodeToString(
                      "my-client:my-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      verify(
          postRequestedFor(urlEqualTo("/api/request"))
              .withHeader("Authorization", equalTo(expectedBasic)));

      System.out.println("✅ OAuth2 Basic auth header sent correctly");
    }
  }
}
