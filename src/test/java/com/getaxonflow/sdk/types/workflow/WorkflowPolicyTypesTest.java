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

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for workflow policy types (Issues #1019, #1020, #1021). */
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
    PolicyMatch match =
        PolicyMatch.builder()
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
    PolicyMatch match = PolicyMatch.builder().policyId("policy-123").action("block").build();

    assertThat(match.isBlocking()).isTrue();
  }

  @Test
  @DisplayName("PolicyMatch - isBlocking returns false for allow action")
  void policyMatchIsBlockingShouldReturnFalseForAllowAction() {
    PolicyMatch match = PolicyMatch.builder().policyId("policy-123").action("allow").build();

    assertThat(match.isBlocking()).isFalse();
  }

  @Test
  @DisplayName("PolicyMatch - requiresApproval returns true for require_approval action")
  void policyMatchRequiresApprovalShouldReturnTrue() {
    PolicyMatch match =
        PolicyMatch.builder().policyId("policy-123").action("require_approval").build();

    assertThat(match.requiresApproval()).isTrue();
  }

  @Test
  @DisplayName("PolicyMatch - should deserialize from JSON")
  void policyMatchShouldDeserialize() throws Exception {
    String json =
        "{"
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
    PolicyMatch match =
        PolicyMatch.builder()
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
    PolicyMatch match1 =
        PolicyMatch.builder().policyId("policy-123").policyName("test").action("allow").build();

    PolicyMatch match2 =
        PolicyMatch.builder().policyId("policy-123").policyName("test").action("allow").build();

    PolicyMatch match3 =
        PolicyMatch.builder().policyId("policy-456").policyName("other").action("block").build();

    assertThat(match1).isEqualTo(match2);
    assertThat(match1.hashCode()).isEqualTo(match2.hashCode());
    assertThat(match1).isNotEqualTo(match3);
  }

  @Test
  @DisplayName("PolicyMatch - toString contains all fields")
  void policyMatchToStringShouldContainAllFields() {
    PolicyMatch match =
        PolicyMatch.builder()
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
    PolicyEvaluationResult result =
        PolicyEvaluationResult.builder()
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
    String json =
        "{"
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
    PolicyEvaluationResult result =
        PolicyEvaluationResult.builder()
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
    PolicyEvaluationResult result1 =
        PolicyEvaluationResult.builder()
            .allowed(true)
            .appliedPolicies(policies)
            .riskScore(0.5)
            .build();

    PolicyEvaluationResult result2 =
        PolicyEvaluationResult.builder()
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
    PolicyEvaluationResult result =
        PolicyEvaluationResult.builder()
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
    String json =
        "{"
            + "\"plan_id\": \"plan-123\","
            + "\"status\": \"completed\","
            + "\"result\": \"Plan executed successfully\","
            + "\"steps_completed\": 3,"
            + "\"total_steps\": 3,"
            + "\"policy_info\": {"
            + "  \"allowed\": true,"
            + "  \"applied_policies\": [\"cost-limit\"],"
            + "  \"risk_score\": 0.2"
            + "}"
            + "}";

    PlanExecutionResponse response = objectMapper.readValue(json, PlanExecutionResponse.class);

    assertThat(response.isCompleted()).isTrue();
    assertThat(response.getPlanId()).isEqualTo("plan-123");
    assertThat(response.getResult()).isEqualTo("Plan executed successfully");
    assertThat(response.getPolicyInfo()).isNotNull();
    assertThat(response.getPolicyInfo().isAllowed()).isTrue();
    assertThat(response.getPolicyInfo().getAppliedPolicies()).containsExactly("cost-limit");
  }

  @Test
  @DisplayName("PlanExecutionResponse - should handle blocked response")
  void planExecutionResponseShouldHandleBlockedResponse() throws Exception {
    String json =
        "{"
            + "\"plan_id\": \"plan-456\","
            + "\"status\": \"blocked\","
            + "\"result\": \"Plan execution blocked by policy\","
            + "\"steps_completed\": 0,"
            + "\"total_steps\": 3,"
            + "\"policy_info\": {"
            + "  \"allowed\": false,"
            + "  \"applied_policies\": [\"high-risk-block\"],"
            + "  \"risk_score\": 0.9"
            + "}"
            + "}";

    PlanExecutionResponse response = objectMapper.readValue(json, PlanExecutionResponse.class);

    assertThat(response.isBlocked()).isTrue();
    assertThat(response.isCompleted()).isFalse();
    assertThat(response.getResult()).isEqualTo("Plan execution blocked by policy");
    assertThat(response.getPolicyInfo().isAllowed()).isFalse();
  }

  @Test
  @DisplayName("PlanExecutionResponse - should construct with all fields")
  void planExecutionResponseShouldConstructWithAllFields() {
    PolicyEvaluationResult policyInfo =
        PolicyEvaluationResult.builder().allowed(true).riskScore(0.1).build();

    PlanExecutionResponse response =
        new PlanExecutionResponse(
            "plan-789", "completed", "done", 3, 3, null, null, null, policyInfo, null);

    assertThat(response.isCompleted()).isTrue();
    assertThat(response.getPlanId()).isEqualTo("plan-789");
    assertThat(response.getResult()).isEqualTo("done");
    assertThat(response.getPolicyInfo()).isEqualTo(policyInfo);
  }

  @Test
  @DisplayName("PlanExecutionResponse - equals and hashCode")
  void planExecutionResponseEqualsAndHashCode() {
    PolicyEvaluationResult policyInfo =
        PolicyEvaluationResult.builder().allowed(true).riskScore(0.5).build();

    PlanExecutionResponse response1 =
        new PlanExecutionResponse(
            "plan-123", "completed", "done", 2, 2, null, null, null, policyInfo, null);

    PlanExecutionResponse response2 =
        new PlanExecutionResponse(
            "plan-123", "completed", "done", 2, 2, null, null, null, policyInfo, null);

    assertThat(response1).isEqualTo(response2);
    assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
  }

  @Test
  @DisplayName("PlanExecutionResponse - toString contains all fields")
  void planExecutionResponseToStringShouldContainAllFields() {
    PlanExecutionResponse response =
        new PlanExecutionResponse(
            "plan-test", "completed", "test-result", 1, 2, null, null, null, null, null);

    String str = response.toString();

    assertThat(str).contains("plan-test");
    assertThat(str).contains("completed");
  }

  @Test
  @DisplayName("PlanExecutionResponse - status helper methods")
  void planExecutionResponseStatusHelperMethods() {
    PlanExecutionResponse completed =
        new PlanExecutionResponse("p1", "completed", null, 3, 3, null, null, null, null, null);
    PlanExecutionResponse failed =
        new PlanExecutionResponse("p2", "failed", null, 1, 3, null, null, null, null, null);
    PlanExecutionResponse blocked =
        new PlanExecutionResponse("p3", "blocked", null, 0, 3, null, null, null, null, null);
    PlanExecutionResponse inProgress =
        new PlanExecutionResponse("p4", "in_progress", null, 1, 3, null, null, null, null, null);

    assertThat(completed.isCompleted()).isTrue();
    assertThat(completed.isFailed()).isFalse();
    assertThat(completed.isBlocked()).isFalse();

    assertThat(failed.isFailed()).isTrue();
    assertThat(failed.isCompleted()).isFalse();

    assertThat(blocked.isBlocked()).isTrue();
    assertThat(blocked.isCompleted()).isFalse();

    assertThat(inProgress.isInProgress()).isTrue();
    assertThat(inProgress.isCompleted()).isFalse();
  }

  @Test
  @DisplayName("PlanExecutionResponse - progress calculation")
  void planExecutionResponseProgressCalculation() {
    PlanExecutionResponse halfDone =
        new PlanExecutionResponse("p1", "in_progress", null, 2, 4, null, null, null, null, null);
    PlanExecutionResponse allDone =
        new PlanExecutionResponse("p2", "completed", null, 3, 3, null, null, null, null, null);
    PlanExecutionResponse notStarted =
        new PlanExecutionResponse("p3", "pending", null, 0, 5, null, null, null, null, null);
    PlanExecutionResponse zeroSteps =
        new PlanExecutionResponse("p4", "pending", null, 0, 0, null, null, null, null, null);

    assertThat(halfDone.getProgress()).isEqualTo(0.5);
    assertThat(allDone.getProgress()).isEqualTo(1.0);
    assertThat(notStarted.getProgress()).isEqualTo(0.0);
    assertThat(zeroSteps.getProgress()).isEqualTo(0.0);
  }
}
