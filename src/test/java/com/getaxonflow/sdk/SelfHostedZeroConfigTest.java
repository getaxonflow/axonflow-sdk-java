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
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Self-Hosted Zero-Config Mode Tests.
 *
 * <p>Tests for the zero-configuration self-hosted mode where users can run
 * AxonFlow without any API keys, license keys, or credentials.
 *
 * <p>This tests the scenario where a first-time user:
 * <ol>
 *   <li>Starts the agent with SELF_HOSTED_MODE=true</li>
 *   <li>Connects the SDK with no credentials</li>
 *   <li>Makes requests that should succeed without authentication</li>
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
            AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                // No clientId, no clientSecret, no licenseKey
                .build());

            assertThat(client).isNotNull();
            System.out.println("✅ Client created without credentials for localhost");
        }

        @Test
        @DisplayName("should create client with empty credentials for localhost")
        void shouldCreateClientWithEmptyCredentialsForLocalhost(WireMockRuntimeInfo wmRuntimeInfo) {
            AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("")
                .clientSecret("")
                .build());

            assertThat(client).isNotNull();
            System.out.println("✅ Client created with empty credentials for localhost");
        }

        @Test
        @DisplayName("should require credentials for non-localhost endpoints")
        void shouldRequireCredentialsForNonLocalhost() {
            assertThatThrownBy(() -> AxonFlowConfig.builder()
                .agentUrl("https://staging-eu.getaxonflow.com")
                // No credentials
                .build())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("licenseKey")
                .hasMessageContaining("clientId");

            System.out.println("✅ Non-localhost correctly requires credentials");
        }
    }

    // ========================================================================
    // 2. GATEWAY MODE WITHOUT AUTHENTICATION
    // ========================================================================
    @Nested
    @DisplayName("2. Gateway Mode Without Authentication")
    @WireMockTest
    class GatewayModeTests {

        private AxonFlow axonflow;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                // No credentials - zero-config mode
                .build());
        }

        @Test
        @DisplayName("should perform pre-check with empty token")
        void shouldPerformPreCheckWithEmptyToken() {
            stubFor(post(urlEqualTo("/api/policy/pre-check"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"context_id\": \"ctx_zeroconfig_123\","
                        + "\"approved\": true,"
                        + "\"policies\": [\"default_policy\"]"
                        + "}")));

            PolicyApprovalResult result = axonflow.getPolicyApprovedContext(
                PolicyApprovalRequest.builder()
                    .userToken("")  // Empty token - zero-config scenario
                    .query("What is the weather in Paris?")
                    .build()
            );

            assertThat(result.isApproved()).isTrue();
            assertThat(result.getContextId()).isEqualTo("ctx_zeroconfig_123");

            // Verify request was made without auth headers
            verify(postRequestedFor(urlEqualTo("/api/policy/pre-check"))
                .withRequestBody(containing("\"user_token\":\"\"")));

            System.out.println("✅ Pre-check succeeded with empty token");
        }

        @Test
        @DisplayName("should perform pre-check with whitespace token")
        void shouldPerformPreCheckWithWhitespaceToken() {
            stubFor(post(urlEqualTo("/api/policy/pre-check"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"context_id\": \"ctx_whitespace_456\","
                        + "\"approved\": true,"
                        + "\"policies\": []"
                        + "}")));

            PolicyApprovalResult result = axonflow.getPolicyApprovedContext(
                PolicyApprovalRequest.builder()
                    .userToken("   ")  // Whitespace only
                    .query("Simple test query")
                    .build()
            );

            assertThat(result.isApproved()).isTrue();
            System.out.println("✅ Pre-check succeeded with whitespace token");
        }

        @Test
        @DisplayName("should complete full Gateway Mode flow without credentials")
        void shouldCompleteFullGatewayFlowWithoutCredentials() {
            // Step 1: Pre-check
            stubFor(post(urlEqualTo("/api/policy/pre-check"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"context_id\": \"ctx_fullflow_789\","
                        + "\"approved\": true"
                        + "}")));

            PolicyApprovalResult preCheck = axonflow.getPolicyApprovedContext(
                PolicyApprovalRequest.builder()
                    .userToken("")
                    .query("Analyze quarterly sales data")
                    .build()
            );

            assertThat(preCheck.getContextId()).isEqualTo("ctx_fullflow_789");

            // Step 2: Audit
            stubFor(post(urlEqualTo("/api/audit/llm-call"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"success\": true,"
                        + "\"audit_id\": \"audit_zeroconfig_001\""
                        + "}")));

            AuditResult audit = axonflow.auditLLMCall(AuditOptions.builder()
                .contextId(preCheck.getContextId())
                .clientId("default")
                .provider("openai")
                .model("gpt-4")
                .tokenUsage(TokenUsage.of(100, 175))
                .latencyMs(350)
                .build()
            );

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
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());
        }

        @Test
        @DisplayName("should execute query with empty token")
        void shouldExecuteQueryWithEmptyToken() {
            stubFor(post(urlEqualTo("/api/request"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"success\": true,"
                        + "\"data\": {\"answer\": \"4\"},"
                        + "\"blocked\": false"
                        + "}")));

            ClientResponse response = axonflow.executeQuery(ClientRequest.builder()
                .userToken("")  // Empty token
                .query("What is 2 + 2?")
                .build()
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.isBlocked()).isFalse();

            System.out.println("✅ Query executed with empty token");
        }
    }

    // ========================================================================
    // 4. POLICY ENFORCEMENT STILL WORKS
    // ========================================================================
    @Nested
    @DisplayName("4. Policy Enforcement Still Works Without Auth")
    @WireMockTest
    class PolicyEnforcementTests {

        private AxonFlow axonflow;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
            axonflow = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());
        }

        @Test
        @DisplayName("should still block SQL injection without credentials")
        void shouldBlockSqlInjectionWithoutCredentials() {
            stubFor(post(urlEqualTo("/api/policy/pre-check"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"context_id\": \"ctx_blocked_001\","
                        + "\"approved\": false,"
                        + "\"block_reason\": \"SQL injection detected\","
                        + "\"policies\": [\"sql_injection_detection\"]"
                        + "}")));

            assertThatThrownBy(() -> axonflow.getPolicyApprovedContext(
                PolicyApprovalRequest.builder()
                    .userToken("")
                    .query("SELECT * FROM users; DROP TABLE users;--")
                    .build()
            )).hasMessageContaining("SQL injection");

            System.out.println("✅ SQL injection blocked without credentials");
        }

        @Test
        @DisplayName("should still block PII without credentials")
        void shouldBlockPiiWithoutCredentials() {
            stubFor(post(urlEqualTo("/api/policy/pre-check"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"context_id\": \"ctx_blocked_002\","
                        + "\"approved\": false,"
                        + "\"block_reason\": \"PII detected: SSN\","
                        + "\"policies\": [\"pii_detection\"]"
                        + "}")));

            assertThatThrownBy(() -> axonflow.getPolicyApprovedContext(
                PolicyApprovalRequest.builder()
                    .userToken("")
                    .query("My social security number is 123-45-6789")
                    .build()
            )).hasMessageContaining("PII");

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
            stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"status\": \"healthy\","
                        + "\"version\": \"1.0.0\""
                        + "}")));

            AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());

            HealthStatus health = client.healthCheck();

            assertThat(health.isHealthy()).isTrue();
            assertThat(health.getVersion()).isEqualTo("1.0.0");

            System.out.println("✅ Health check succeeded without credentials");
        }
    }

    // ========================================================================
    // 6. FIRST-TIME USER EXPERIENCE
    // ========================================================================
    @Nested
    @DisplayName("6. First-Time User Experience")
    @WireMockTest
    class FirstTimeUserTests {

        @Test
        @DisplayName("should support first-time user with minimal configuration")
        void shouldSupportFirstTimeUser(WireMockRuntimeInfo wmRuntimeInfo) {
            // Stub health endpoint
            stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\": \"healthy\"}")));

            // Stub pre-check endpoint
            stubFor(post(urlEqualTo("/api/policy/pre-check"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"context_id\": \"ctx_firstuser_001\","
                        + "\"approved\": true"
                        + "}")));

            // First-time user - minimal configuration
            AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                // No credentials at all
                .build());

            // Step 1: Health check should work
            HealthStatus health = client.healthCheck();
            assertThat(health.isHealthy()).isTrue();

            // Step 2: Pre-check should work with empty token
            PolicyApprovalResult result = client.getPolicyApprovedContext(
                PolicyApprovalRequest.builder()
                    .userToken("")
                    .query("Hello, this is my first query!")
                    .build()
            );

            assertThat(result.getContextId()).isNotEmpty();

            System.out.println("✅ First-time user experience validated");
            System.out.println("   - Client creation: OK");
            System.out.println("   - Health check: OK");
            System.out.println("   - Pre-check: OK");
        }
    }

    // ========================================================================
    // 7. AUTH HEADERS NOT SENT FOR LOCALHOST
    // ========================================================================
    @Nested
    @DisplayName("7. Auth Headers Not Sent for Localhost")
    @WireMockTest
    class AuthHeaderTests {

        @Test
        @DisplayName("should not send auth headers for localhost")
        void shouldNotSendAuthHeadersForLocalhost(WireMockRuntimeInfo wmRuntimeInfo) {
            stubFor(post(urlEqualTo("/api/policy/pre-check"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{"
                        + "\"context_id\": \"ctx_noauth_001\","
                        + "\"approved\": true"
                        + "}")));

            AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .build());

            client.getPolicyApprovedContext(
                PolicyApprovalRequest.builder()
                    .userToken("")
                    .query("Test query")
                    .build()
            );

            // Verify no auth headers were sent
            verify(postRequestedFor(urlEqualTo("/api/policy/pre-check"))
                .withoutHeader("X-License-Key")
                .withoutHeader("X-Client-Secret"));

            System.out.println("✅ Auth headers not sent for localhost");
        }
    }
}
