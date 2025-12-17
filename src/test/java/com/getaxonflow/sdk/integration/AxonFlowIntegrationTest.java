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
package com.getaxonflow.sdk.integration;

import com.getaxonflow.sdk.*;
import com.getaxonflow.sdk.exceptions.*;
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
@DisplayName("AxonFlow Integration Tests")
class AxonFlowIntegrationTest {

    private AxonFlow axonflow;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        axonflow = AxonFlow.create(AxonFlowConfig.builder()
            .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
            .build());
    }

    @Test
    @DisplayName("health check should return healthy status")
    void healthCheckShouldReturnHealthy() {
        stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"status\": \"healthy\","
                    + "\"version\": \"1.0.0\","
                    + "\"uptime\": \"24h\""
                    + "}")));

        HealthStatus health = axonflow.healthCheck();

        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getVersion()).isEqualTo("1.0.0");
        assertThat(health.getUptime()).isEqualTo("24h");
    }

    @Test
    @DisplayName("getPolicyApprovedContext should return approval")
    void getPolicyApprovedContextShouldReturnApproval() {
        stubFor(post(urlEqualTo("/api/v1/gateway/pre-check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"context_id\": \"ctx_abc123\","
                    + "\"approved\": true,"
                    + "\"policies\": [\"policy1\", \"policy2\"],"
                    + "\"processing_time\": \"5.23ms\""
                    + "}")));

        PolicyApprovalResult result = axonflow.getPolicyApprovedContext(
            PolicyApprovalRequest.builder()
                .userToken("user-123")
                .query("What is the weather?")
                .build()
        );

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getContextId()).isEqualTo("ctx_abc123");
        assertThat(result.getPolicies()).containsExactly("policy1", "policy2");

        verify(postRequestedFor(urlEqualTo("/api/v1/gateway/pre-check"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(containing("\"user_token\":\"user-123\""))
            .withRequestBody(containing("\"query\":\"What is the weather?\"")));
    }

    @Test
    @DisplayName("getPolicyApprovedContext should throw on block")
    void getPolicyApprovedContextShouldThrowOnBlock() {
        stubFor(post(urlEqualTo("/api/v1/gateway/pre-check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"context_id\": \"ctx_abc123\","
                    + "\"approved\": false,"
                    + "\"block_reason\": \"Request blocked by policy: pii_detection\","
                    + "\"policies\": [\"pii_detection\"]"
                    + "}")));

        assertThatThrownBy(() -> axonflow.getPolicyApprovedContext(
            PolicyApprovalRequest.builder()
                .userToken("user-123")
                .query("My SSN is 123-45-6789")
                .build()
        ))
            .isInstanceOf(PolicyViolationException.class)
            .extracting("policyName")
            .isEqualTo("pii_detection");
    }

    @Test
    @DisplayName("auditLLMCall should record audit")
    void auditLLMCallShouldRecordAudit() {
        stubFor(post(urlEqualTo("/api/v1/gateway/audit"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"success\": true,"
                    + "\"audit_id\": \"audit_xyz789\""
                    + "}")));

        AuditResult result = axonflow.auditLLMCall(AuditOptions.builder()
            .contextId("ctx_abc123")
            .provider("openai")
            .model("gpt-4")
            .tokenUsage(TokenUsage.of(100, 150))
            .latencyMs(1234)
            .build()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAuditId()).isEqualTo("audit_xyz789");

        verify(postRequestedFor(urlEqualTo("/api/v1/gateway/audit"))
            .withRequestBody(containing("\"context_id\":\"ctx_abc123\""))
            .withRequestBody(containing("\"provider\":\"openai\"")));
    }

    @Test
    @DisplayName("executeQuery should return response")
    void executeQueryShouldReturnResponse() {
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"success\": true,"
                    + "\"data\": {\"message\": \"The weather is sunny\"},"
                    + "\"blocked\": false,"
                    + "\"policy_info\": {"
                    + "\"policies_evaluated\": [\"rate_limit\"],"
                    + "\"processing_time\": \"12.5ms\""
                    + "}"
                    + "}")));

        ClientResponse response = axonflow.executeQuery(ClientRequest.builder()
            .query("What is the weather?")
            .userToken("user-123")
            .build()
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isBlocked()).isFalse();
        assertThat(response.getData()).isNotNull();
    }

    @Test
    @DisplayName("executeQuery should throw on policy block")
    void executeQueryShouldThrowOnPolicyBlock() {
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"success\": false,"
                    + "\"blocked\": true,"
                    + "\"block_reason\": \"Request blocked by policy: sql_injection_detection\","
                    + "\"policy_info\": {"
                    + "\"policies_evaluated\": [\"sql_injection_detection\"],"
                    + "\"static_checks\": [\"sql_injection\"]"
                    + "}"
                    + "}")));

        assertThatThrownBy(() -> axonflow.executeQuery(ClientRequest.builder()
            .query("SELECT * FROM users; DROP TABLE users;")
            .userToken("user-123")
            .build()
        ))
            .isInstanceOf(PolicyViolationException.class)
            .extracting("policyName")
            .isEqualTo("sql_injection_detection");
    }

    @Test
    @DisplayName("should handle authentication error")
    void shouldHandleAuthenticationError() {
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"error\": \"Invalid credentials\""
                    + "}")));

        assertThatThrownBy(() -> axonflow.executeQuery(ClientRequest.builder()
            .query("test")
            .build()
        ))
            .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("should handle rate limit error")
    void shouldHandleRateLimitError() {
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"error\": \"Rate limit exceeded\""
                    + "}")));

        assertThatThrownBy(() -> axonflow.executeQuery(ClientRequest.builder()
            .query("test")
            .build()
        ))
            .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("generatePlan should return plan")
    void generatePlanShouldReturnPlan() {
        stubFor(post(urlEqualTo("/api/v1/orchestrator/plan"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"plan_id\": \"plan_123\","
                    + "\"steps\": ["
                    + "{"
                    + "\"id\": \"step_001\","
                    + "\"name\": \"research\","
                    + "\"type\": \"llm-call\""
                    + "}"
                    + "],"
                    + "\"domain\": \"generic\","
                    + "\"complexity\": 2,"
                    + "\"status\": \"pending\""
                    + "}")));

        PlanResponse plan = axonflow.generatePlan(PlanRequest.builder()
            .objective("Research AI governance")
            .domain("generic")
            .build()
        );

        assertThat(plan.getPlanId()).isEqualTo("plan_123");
        assertThat(plan.getSteps()).hasSize(1);
        assertThat(plan.getDomain()).isEqualTo("generic");
    }

    @Test
    @DisplayName("listConnectors should return connectors")
    void listConnectorsShouldReturnConnectors() {
        stubFor(get(urlEqualTo("/api/v1/connectors"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("["
                    + "{"
                    + "\"id\": \"salesforce\","
                    + "\"name\": \"Salesforce\","
                    + "\"type\": \"crm\","
                    + "\"installed\": true"
                    + "},"
                    + "{"
                    + "\"id\": \"hubspot\","
                    + "\"name\": \"HubSpot\","
                    + "\"type\": \"crm\","
                    + "\"installed\": false"
                    + "}"
                    + "]")));

        List<ConnectorInfo> connectors = axonflow.listConnectors();

        assertThat(connectors).hasSize(2);
        assertThat(connectors.get(0).getId()).isEqualTo("salesforce");
        assertThat(connectors.get(0).isInstalled()).isTrue();
    }

    @Test
    @DisplayName("queryConnector should return response")
    void queryConnectorShouldReturnResponse() {
        stubFor(post(urlEqualTo("/api/v1/connectors/query"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"success\": true,"
                    + "\"data\": [{\"name\": \"Acme Corp\"}],"
                    + "\"connector_id\": \"salesforce\","
                    + "\"operation\": \"getAccounts\""
                    + "}")));

        ConnectorResponse response = axonflow.queryConnector(ConnectorQuery.builder()
            .connectorId("salesforce")
            .operation("getAccounts")
            .build()
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getConnectorId()).isEqualTo("salesforce");
    }

    @Test
    @DisplayName("should cache successful responses")
    void shouldCacheSuccessfulResponses() {
        stubFor(post(urlEqualTo("/api/request"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{"
                    + "\"success\": true,"
                    + "\"data\": \"cached response\","
                    + "\"blocked\": false"
                    + "}")));

        ClientRequest request = ClientRequest.builder()
            .query("test query")
            .userToken("user-123")
            .build();

        // First call - should hit the server
        axonflow.executeQuery(request);

        // Second call with same parameters - should use cache
        axonflow.executeQuery(request);

        // Verify only one request was made
        verify(1, postRequestedFor(urlEqualTo("/api/request")));
    }
}
