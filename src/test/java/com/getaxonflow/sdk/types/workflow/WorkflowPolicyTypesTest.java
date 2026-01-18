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
package com.getaxonflow.sdk.types.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for workflow policy types (Issues #1019, #1020, #1021).
 */
@DisplayName("Workflow Policy Types")
class WorkflowPolicyTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // PolicyMatch tests

    @Test
    @DisplayName("PolicyMatch - should build with all fields")
    void policyMatchShouldBuildWithAllFields() {
        PolicyMatch match = PolicyMatch.builder()
            .policyId("policy-123")
            .policyName("block-gpt4")
            .action("block")
            .reason("GPT-4 not allowed in production")
            .build();

        assertThat(match.getPolicyId()).isEqualTo("policy-123");
        assertThat(match.getPolicyName()).isEqualTo("block-gpt4");
        assertThat(match.getAction()).isEqualTo("block");
        assertThat(match.getReason()).isEqualTo("GPT-4 not allowed in production");
    }

    @Test
    @DisplayName("PolicyMatch - isBlocking returns true for block action")
    void policyMatchIsBlockingShouldReturnTrueForBlockAction() {
        PolicyMatch match = PolicyMatch.builder()
            .policyId("policy-123")
            .action("block")
            .build();

        assertThat(match.isBlocking()).isTrue();
    }

    @Test
    @DisplayName("PolicyMatch - isBlocking returns false for allow action")
    void policyMatchIsBlockingShouldReturnFalseForAllowAction() {
        PolicyMatch match = PolicyMatch.builder()
            .policyId("policy-123")
            .action("allow")
            .build();

        assertThat(match.isBlocking()).isFalse();
    }

    @Test
    @DisplayName("PolicyMatch - requiresApproval returns true for require_approval action")
    void policyMatchRequiresApprovalShouldReturnTrue() {
        PolicyMatch match = PolicyMatch.builder()
            .policyId("policy-123")
            .action("require_approval")
            .build();

        assertThat(match.requiresApproval()).isTrue();
    }

    @Test
    @DisplayName("PolicyMatch - should deserialize from JSON")
    void policyMatchShouldDeserialize() throws Exception {
        String json = "{"
            + "\"policy_id\": \"policy-456\","
            + "\"policy_name\": \"pii-detection\","
            + "\"action\": \"redact\","
            + "\"reason\": \"PII detected in input\""
            + "}";

        PolicyMatch match = objectMapper.readValue(json, PolicyMatch.class);

        assertThat(match.getPolicyId()).isEqualTo("policy-456");
        assertThat(match.getPolicyName()).isEqualTo("pii-detection");
        assertThat(match.getAction()).isEqualTo("redact");
        assertThat(match.getReason()).isEqualTo("PII detected in input");
    }

    @Test
    @DisplayName("PolicyMatch - should serialize to JSON")
    void policyMatchShouldSerialize() throws Exception {
        PolicyMatch match = PolicyMatch.builder()
            .policyId("policy-789")
            .policyName("cost-limit")
            .action("allow")
            .reason("Within budget")
            .build();

        String json = objectMapper.writeValueAsString(match);

        assertThat(json).contains("\"policy_id\":\"policy-789\"");
        assertThat(json).contains("\"policy_name\":\"cost-limit\"");
        assertThat(json).contains("\"action\":\"allow\"");
    }

    @Test
    @DisplayName("PolicyMatch - equals and hashCode")
    void policyMatchEqualsAndHashCode() {
        PolicyMatch match1 = PolicyMatch.builder()
            .policyId("policy-123")
            .policyName("test")
            .action("allow")
            .build();

        PolicyMatch match2 = PolicyMatch.builder()
            .policyId("policy-123")
            .policyName("test")
            .action("allow")
            .build();

        PolicyMatch match3 = PolicyMatch.builder()
            .policyId("policy-456")
            .policyName("other")
            .action("block")
            .build();

        assertThat(match1).isEqualTo(match2);
        assertThat(match1.hashCode()).isEqualTo(match2.hashCode());
        assertThat(match1).isNotEqualTo(match3);
    }

    @Test
    @DisplayName("PolicyMatch - toString contains all fields")
    void policyMatchToStringShouldContainAllFields() {
        PolicyMatch match = PolicyMatch.builder()
            .policyId("policy-123")
            .policyName("test-policy")
            .action("block")
            .reason("test reason")
            .build();

        String str = match.toString();

        assertThat(str).contains("policy-123");
        assertThat(str).contains("test-policy");
        assertThat(str).contains("block");
        assertThat(str).contains("test reason");
    }

    // PolicyEvaluationResult tests

    @Test
    @DisplayName("PolicyEvaluationResult - should build with all fields")
    void policyEvaluationResultShouldBuildWithAllFields() {
        List<String> policies = Arrays.asList("cost-limit", "model-restriction");
        PolicyEvaluationResult result = PolicyEvaluationResult.builder()
            .allowed(true)
            .appliedPolicies(policies)
            .riskScore(0.2)
            .build();

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getAppliedPolicies()).containsExactly("cost-limit", "model-restriction");
        assertThat(result.getRiskScore()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("PolicyEvaluationResult - should deserialize from JSON")
    void policyEvaluationResultShouldDeserialize() throws Exception {
        String json = "{"
            + "\"allowed\": false,"
            + "\"applied_policies\": [\"high-risk-block\"],"
            + "\"risk_score\": 0.85"
            + "}";

        PolicyEvaluationResult result = objectMapper.readValue(json, PolicyEvaluationResult.class);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getAppliedPolicies()).containsExactly("high-risk-block");
        assertThat(result.getRiskScore()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("PolicyEvaluationResult - should serialize to JSON")
    void policyEvaluationResultShouldSerialize() throws Exception {
        PolicyEvaluationResult result = PolicyEvaluationResult.builder()
            .allowed(true)
            .appliedPolicies(Arrays.asList("policy-1", "policy-2"))
            .riskScore(0.1)
            .build();

        String json = objectMapper.writeValueAsString(result);

        assertThat(json).contains("\"allowed\":true");
        assertThat(json).contains("\"applied_policies\"");
        assertThat(json).contains("\"risk_score\":0.1");
    }

    @Test
    @DisplayName("PolicyEvaluationResult - equals and hashCode")
    void policyEvaluationResultEqualsAndHashCode() {
        List<String> policies = Arrays.asList("policy-1");
        PolicyEvaluationResult result1 = PolicyEvaluationResult.builder()
            .allowed(true)
            .appliedPolicies(policies)
            .riskScore(0.5)
            .build();

        PolicyEvaluationResult result2 = PolicyEvaluationResult.builder()
            .allowed(true)
            .appliedPolicies(policies)
            .riskScore(0.5)
            .build();

        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("PolicyEvaluationResult - toString contains all fields")
    void policyEvaluationResultToStringShouldContainAllFields() {
        PolicyEvaluationResult result = PolicyEvaluationResult.builder()
            .allowed(false)
            .appliedPolicies(Arrays.asList("test-policy"))
            .riskScore(0.75)
            .build();

        String str = result.toString();

        assertThat(str).contains("allowed=false");
        assertThat(str).contains("test-policy");
        assertThat(str).contains("0.75");
    }

    // PlanExecutionResponse tests

    @Test
    @DisplayName("PlanExecutionResponse - should deserialize from JSON")
    void planExecutionResponseShouldDeserialize() throws Exception {
        String json = "{"
            + "\"success\": true,"
            + "\"result\": \"Plan executed successfully\","
            + "\"policy_info\": {"
            + "  \"allowed\": true,"
            + "  \"applied_policies\": [\"cost-limit\"],"
            + "  \"risk_score\": 0.2"
            + "}"
            + "}";

        PlanExecutionResponse response = objectMapper.readValue(json, PlanExecutionResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isEqualTo("Plan executed successfully");
        assertThat(response.getPolicyInfo()).isNotNull();
        assertThat(response.getPolicyInfo().isAllowed()).isTrue();
        assertThat(response.getPolicyInfo().getAppliedPolicies()).containsExactly("cost-limit");
    }

    @Test
    @DisplayName("PlanExecutionResponse - should handle blocked response")
    void planExecutionResponseShouldHandleBlockedResponse() throws Exception {
        String json = "{"
            + "\"success\": false,"
            + "\"error\": \"Plan execution blocked by policy\","
            + "\"policy_info\": {"
            + "  \"allowed\": false,"
            + "  \"applied_policies\": [\"high-risk-block\"],"
            + "  \"risk_score\": 0.9"
            + "}"
            + "}";

        PlanExecutionResponse response = objectMapper.readValue(json, PlanExecutionResponse.class);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Plan execution blocked by policy");
        assertThat(response.getPolicyInfo().isAllowed()).isFalse();
    }

    @Test
    @DisplayName("PlanExecutionResponse - should build with all fields")
    void planExecutionResponseShouldBuildWithAllFields() {
        PolicyEvaluationResult policyInfo = PolicyEvaluationResult.builder()
            .allowed(true)
            .riskScore(0.1)
            .build();

        PlanExecutionResponse response = PlanExecutionResponse.builder()
            .success(true)
            .result("completed")
            .policyInfo(policyInfo)
            .build();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isEqualTo("completed");
        assertThat(response.getPolicyInfo()).isEqualTo(policyInfo);
    }

    @Test
    @DisplayName("PlanExecutionResponse - equals and hashCode")
    void planExecutionResponseEqualsAndHashCode() {
        PolicyEvaluationResult policyInfo = PolicyEvaluationResult.builder()
            .allowed(true)
            .riskScore(0.5)
            .build();

        PlanExecutionResponse response1 = PlanExecutionResponse.builder()
            .success(true)
            .result("done")
            .policyInfo(policyInfo)
            .build();

        PlanExecutionResponse response2 = PlanExecutionResponse.builder()
            .success(true)
            .result("done")
            .policyInfo(policyInfo)
            .build();

        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("PlanExecutionResponse - toString contains all fields")
    void planExecutionResponseToStringShouldContainAllFields() {
        PlanExecutionResponse response = PlanExecutionResponse.builder()
            .success(true)
            .result("test-result")
            .build();

        String str = response.toString();

        assertThat(str).contains("success=true");
        assertThat(str).contains("test-result");
    }
}
