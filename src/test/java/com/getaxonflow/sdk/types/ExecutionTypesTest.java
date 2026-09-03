// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT

package com.getaxonflow.sdk.types;

import static org.junit.jupiter.api.Assertions.*;

import com.getaxonflow.sdk.types.execution.ExecutionTypes.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for unified execution types. */
class ExecutionTypesTest {

  @Test
  @DisplayName("ExecutionType.fromValue should return correct enum")
  void testExecutionTypeFromValue() {
    assertEquals(ExecutionType.MAP_PLAN, ExecutionType.fromValue("map_plan"));
    assertEquals(ExecutionType.WCP_WORKFLOW, ExecutionType.fromValue("wcp_workflow"));
  }

  @Test
  @DisplayName("ExecutionType.fromValue should throw for unknown value")
  void testExecutionTypeFromValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> ExecutionType.fromValue("unknown"));
  }

  @Test
  @DisplayName("ExecutionType.getValue should return correct string")
  void testExecutionTypeGetValue() {
    assertEquals("map_plan", ExecutionType.MAP_PLAN.getValue());
    assertEquals("wcp_workflow", ExecutionType.WCP_WORKFLOW.getValue());
  }

  @ParameterizedTest
  @EnumSource(ExecutionStatusValue.class)
  @DisplayName("ExecutionStatusValue should have correct terminal status")
  void testExecutionStatusValueIsTerminal(ExecutionStatusValue status) {
    boolean expected =
        status == ExecutionStatusValue.COMPLETED
            || status == ExecutionStatusValue.FAILED
            || status == ExecutionStatusValue.CANCELLED
            || status == ExecutionStatusValue.ABORTED
            || status == ExecutionStatusValue.EXPIRED;
    assertEquals(expected, status.isTerminal());
  }

  @Test
  @DisplayName("ExecutionStatusValue.fromValue should return correct enum")
  void testExecutionStatusValueFromValue() {
    assertEquals(ExecutionStatusValue.PENDING, ExecutionStatusValue.fromValue("pending"));
    assertEquals(ExecutionStatusValue.RUNNING, ExecutionStatusValue.fromValue("running"));
    assertEquals(ExecutionStatusValue.COMPLETED, ExecutionStatusValue.fromValue("completed"));
    assertEquals(ExecutionStatusValue.FAILED, ExecutionStatusValue.fromValue("failed"));
    assertEquals(ExecutionStatusValue.CANCELLED, ExecutionStatusValue.fromValue("cancelled"));
    assertEquals(ExecutionStatusValue.ABORTED, ExecutionStatusValue.fromValue("aborted"));
    assertEquals(ExecutionStatusValue.EXPIRED, ExecutionStatusValue.fromValue("expired"));
  }

  @Test
  @DisplayName("ExecutionStatusValue.fromValue should throw for unknown value")
  void testExecutionStatusValueFromValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> ExecutionStatusValue.fromValue("unknown"));
  }

  @ParameterizedTest
  @EnumSource(StepStatusValue.class)
  @DisplayName("StepStatusValue should have correct terminal status")
  void testStepStatusValueIsTerminal(StepStatusValue status) {
    boolean expected =
        status == StepStatusValue.COMPLETED
            || status == StepStatusValue.FAILED
            || status == StepStatusValue.SKIPPED;
    assertEquals(expected, status.isTerminal());
  }

  @ParameterizedTest
  @EnumSource(StepStatusValue.class)
  @DisplayName("StepStatusValue should have correct blocking status")
  void testStepStatusValueIsBlocking(StepStatusValue status) {
    boolean expected = status == StepStatusValue.BLOCKED || status == StepStatusValue.APPROVAL;
    assertEquals(expected, status.isBlocking());
  }

  @Test
  @DisplayName("StepStatusValue.fromValue should return correct enum")
  void testStepStatusValueFromValue() {
    assertEquals(StepStatusValue.PENDING, StepStatusValue.fromValue("pending"));
    assertEquals(StepStatusValue.RUNNING, StepStatusValue.fromValue("running"));
    assertEquals(StepStatusValue.COMPLETED, StepStatusValue.fromValue("completed"));
    assertEquals(StepStatusValue.FAILED, StepStatusValue.fromValue("failed"));
    assertEquals(StepStatusValue.SKIPPED, StepStatusValue.fromValue("skipped"));
    assertEquals(StepStatusValue.BLOCKED, StepStatusValue.fromValue("blocked"));
    assertEquals(StepStatusValue.APPROVAL, StepStatusValue.fromValue("approval"));
  }

  @Test
  @DisplayName("StepStatusValue.fromValue should throw for unknown value")
  void testStepStatusValueFromValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> StepStatusValue.fromValue("unknown"));
  }

  @Test
  @DisplayName("UnifiedStepType.fromValue should return correct enum")
  void testUnifiedStepTypeFromValue() {
    assertEquals(UnifiedStepType.LLM_CALL, UnifiedStepType.fromValue("llm_call"));
    assertEquals(UnifiedStepType.TOOL_CALL, UnifiedStepType.fromValue("tool_call"));
    assertEquals(UnifiedStepType.CONNECTOR_CALL, UnifiedStepType.fromValue("connector_call"));
    assertEquals(UnifiedStepType.HUMAN_TASK, UnifiedStepType.fromValue("human_task"));
    assertEquals(UnifiedStepType.SYNTHESIS, UnifiedStepType.fromValue("synthesis"));
    assertEquals(UnifiedStepType.ACTION, UnifiedStepType.fromValue("action"));
    assertEquals(UnifiedStepType.GATE, UnifiedStepType.fromValue("gate"));
  }

  @Test
  @DisplayName("UnifiedStepType.fromValue should throw for unknown value")
  void testUnifiedStepTypeFromValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> UnifiedStepType.fromValue("unknown"));
  }

  @Test
  @DisplayName("UnifiedGateDecision.fromValue should return correct enum")
  void testUnifiedGateDecisionFromValue() {
    assertEquals(UnifiedGateDecision.ALLOW, UnifiedGateDecision.fromValue("allow"));
    assertEquals(UnifiedGateDecision.BLOCK, UnifiedGateDecision.fromValue("block"));
    assertEquals(
        UnifiedGateDecision.REQUIRE_APPROVAL, UnifiedGateDecision.fromValue("require_approval"));
  }

  @Test
  @DisplayName("UnifiedGateDecision.fromValue should throw for unknown value")
  void testUnifiedGateDecisionFromValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> UnifiedGateDecision.fromValue("unknown"));
  }

  @Test
  @DisplayName("UnifiedApprovalStatus.fromValue should return correct enum")
  void testUnifiedApprovalStatusFromValue() {
    assertEquals(UnifiedApprovalStatus.PENDING, UnifiedApprovalStatus.fromValue("pending"));
    assertEquals(UnifiedApprovalStatus.APPROVED, UnifiedApprovalStatus.fromValue("approved"));
    assertEquals(UnifiedApprovalStatus.REJECTED, UnifiedApprovalStatus.fromValue("rejected"));
  }

  @Test
  @DisplayName("UnifiedApprovalStatus.fromValue should throw for unknown value")
  void testUnifiedApprovalStatusFromValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> UnifiedApprovalStatus.fromValue("unknown"));
  }

  @Test
  @DisplayName("UnifiedStepStatus builder should create valid object")
  void testUnifiedStepStatusBuilder() {
    Instant now = Instant.now();
    UnifiedStepStatus step =
        UnifiedStepStatus.builder()
            .stepId("step-1")
            .stepIndex(0)
            .stepName("Test Step")
            .stepType(UnifiedStepType.LLM_CALL)
            .status(StepStatusValue.COMPLETED)
            .startedAt(now)
            .endedAt(now.plusSeconds(5))
            .duration("5s")
            .decision(UnifiedGateDecision.ALLOW)
            .decisionReason("Policy passed")
            .policiesMatched(Arrays.asList("policy-1", "policy-2"))
            .model("gpt-4")
            .provider("openai")
            .costUsd(0.05)
            .resultSummary("Step completed successfully")
            .build();

    assertEquals("step-1", step.getStepId());
    assertEquals(0, step.getStepIndex());
    assertEquals("Test Step", step.getStepName());
    assertEquals(UnifiedStepType.LLM_CALL, step.getStepType());
    assertEquals(StepStatusValue.COMPLETED, step.getStatus());
    assertEquals(now, step.getStartedAt());
    assertEquals("5s", step.getDuration());
    assertEquals(UnifiedGateDecision.ALLOW, step.getDecision());
    assertEquals("Policy passed", step.getDecisionReason());
    assertEquals(2, step.getPoliciesMatched().size());
    assertEquals("gpt-4", step.getModel());
    assertEquals("openai", step.getProvider());
    assertEquals(0.05, step.getCostUsd());
    assertEquals("Step completed successfully", step.getResultSummary());
  }

  @Test
  @DisplayName("UnifiedStepStatus equals and hashCode")
  void testUnifiedStepStatusEqualsHashCode() {
    UnifiedStepStatus step1 = UnifiedStepStatus.builder().stepId("step-1").stepIndex(0).build();
    UnifiedStepStatus step2 = UnifiedStepStatus.builder().stepId("step-1").stepIndex(0).build();
    UnifiedStepStatus step3 = UnifiedStepStatus.builder().stepId("step-2").stepIndex(1).build();

    assertEquals(step1, step2);
    assertEquals(step1.hashCode(), step2.hashCode());
    assertNotEquals(step1, step3);
  }

  @Test
  @DisplayName("ExecutionStatus builder should create valid object")
  void testExecutionStatusBuilder() {
    Instant now = Instant.now();
    List<UnifiedStepStatus> steps =
        Arrays.asList(
            UnifiedStepStatus.builder()
                .stepId("step-1")
                .stepIndex(0)
                .status(StepStatusValue.COMPLETED)
                .costUsd(0.05)
                .build(),
            UnifiedStepStatus.builder()
                .stepId("step-2")
                .stepIndex(1)
                .status(StepStatusValue.RUNNING)
                .costUsd(0.10)
                .build());

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("key", "value");

    ExecutionStatus status =
        ExecutionStatus.builder()
            .executionId("exec-1")
            .executionType(ExecutionType.MAP_PLAN)
            .name("Test Execution")
            .source("langchain")
            .status(ExecutionStatusValue.RUNNING)
            .currentStepIndex(1)
            .totalSteps(2)
            .progressPercent(50.0)
            .startedAt(now)
            .duration("30s")
            .estimatedCostUsd(0.20)
            .steps(steps)
            .tenantId("tenant-1")
            .orgId("org-1")
            .userId("user-1")
            .clientId("client-1")
            .metadata(metadata)
            .createdAt(now)
            .updatedAt(now)
            .build();

    assertEquals("exec-1", status.getExecutionId());
    assertEquals(ExecutionType.MAP_PLAN, status.getExecutionType());
    assertEquals("Test Execution", status.getName());
    assertEquals("langchain", status.getSource());
    assertEquals(ExecutionStatusValue.RUNNING, status.getStatus());
    assertEquals(1, status.getCurrentStepIndex());
    assertEquals(2, status.getTotalSteps());
    assertEquals(50.0, status.getProgressPercent());
    assertEquals(now, status.getStartedAt());
    assertEquals("30s", status.getDuration());
    assertEquals(0.20, status.getEstimatedCostUsd());
    assertEquals(2, status.getSteps().size());
    assertEquals("tenant-1", status.getTenantId());
    assertEquals("org-1", status.getOrgId());
    assertEquals("user-1", status.getUserId());
    assertEquals("client-1", status.getClientId());
    assertEquals("value", status.getMetadata().get("key"));
  }

  @Test
  @DisplayName("ExecutionStatus.isTerminal should delegate to status")
  void testExecutionStatusIsTerminal() {
    ExecutionStatus running =
        ExecutionStatus.builder()
            .executionId("exec-1")
            .status(ExecutionStatusValue.RUNNING)
            .build();
    ExecutionStatus completed =
        ExecutionStatus.builder()
            .executionId("exec-2")
            .status(ExecutionStatusValue.COMPLETED)
            .build();

    assertFalse(running.isTerminal());
    assertTrue(completed.isTerminal());
  }

  @Test
  @DisplayName("ExecutionStatus.getCurrentStep should return running step")
  void testExecutionStatusGetCurrentStep() {
    List<UnifiedStepStatus> steps =
        Arrays.asList(
            UnifiedStepStatus.builder()
                .stepId("step-1")
                .stepIndex(0)
                .status(StepStatusValue.COMPLETED)
                .build(),
            UnifiedStepStatus.builder()
                .stepId("step-2")
                .stepIndex(1)
                .status(StepStatusValue.RUNNING)
                .build(),
            UnifiedStepStatus.builder()
                .stepId("step-3")
                .stepIndex(2)
                .status(StepStatusValue.PENDING)
                .build());

    ExecutionStatus status = ExecutionStatus.builder().executionId("exec-1").steps(steps).build();

    UnifiedStepStatus current = status.getCurrentStep();
    assertNotNull(current);
    assertEquals("step-2", current.getStepId());
  }

  @Test
  @DisplayName("ExecutionStatus.getCurrentStep should return null when no running step")
  void testExecutionStatusGetCurrentStepNull() {
    List<UnifiedStepStatus> steps =
        Arrays.asList(
            UnifiedStepStatus.builder().stepId("step-1").status(StepStatusValue.COMPLETED).build());

    ExecutionStatus status = ExecutionStatus.builder().executionId("exec-1").steps(steps).build();

    assertNull(status.getCurrentStep());
  }

  @Test
  @DisplayName("ExecutionStatus.getCurrentStep should return null when steps is null")
  void testExecutionStatusGetCurrentStepNullSteps() {
    ExecutionStatus status = ExecutionStatus.builder().executionId("exec-1").steps(null).build();

    assertNull(status.getCurrentStep());
  }

  @Test
  @DisplayName("ExecutionStatus.calculateTotalCost should sum step costs")
  void testExecutionStatusCalculateTotalCost() {
    List<UnifiedStepStatus> steps =
        Arrays.asList(
            UnifiedStepStatus.builder().stepId("step-1").costUsd(0.05).build(),
            UnifiedStepStatus.builder().stepId("step-2").costUsd(0.10).build(),
            UnifiedStepStatus.builder().stepId("step-3").costUsd(null).build());

    ExecutionStatus status = ExecutionStatus.builder().executionId("exec-1").steps(steps).build();

    assertEquals(0.15, status.calculateTotalCost(), 0.001);
  }

  @Test
  @DisplayName("ExecutionStatus.calculateTotalCost should return 0 for null steps")
  void testExecutionStatusCalculateTotalCostNullSteps() {
    ExecutionStatus status = ExecutionStatus.builder().executionId("exec-1").steps(null).build();

    assertEquals(0.0, status.calculateTotalCost());
  }

  @Test
  @DisplayName("ExecutionStatus.isMapPlan should return true for MAP_PLAN")
  void testExecutionStatusIsMapPlan() {
    ExecutionStatus map =
        ExecutionStatus.builder()
            .executionId("exec-1")
            .executionType(ExecutionType.MAP_PLAN)
            .build();
    ExecutionStatus wcp =
        ExecutionStatus.builder()
            .executionId("exec-2")
            .executionType(ExecutionType.WCP_WORKFLOW)
            .build();

    assertTrue(map.isMapPlan());
    assertFalse(wcp.isMapPlan());
  }

  @Test
  @DisplayName("ExecutionStatus.isWcpWorkflow should return true for WCP_WORKFLOW")
  void testExecutionStatusIsWcpWorkflow() {
    ExecutionStatus map =
        ExecutionStatus.builder()
            .executionId("exec-1")
            .executionType(ExecutionType.MAP_PLAN)
            .build();
    ExecutionStatus wcp =
        ExecutionStatus.builder()
            .executionId("exec-2")
            .executionType(ExecutionType.WCP_WORKFLOW)
            .build();

    assertFalse(map.isWcpWorkflow());
    assertTrue(wcp.isWcpWorkflow());
  }

  @Test
  @DisplayName("ExecutionStatus equals and hashCode")
  void testExecutionStatusEqualsHashCode() {
    ExecutionStatus status1 = ExecutionStatus.builder().executionId("exec-1").build();
    ExecutionStatus status2 = ExecutionStatus.builder().executionId("exec-1").build();
    ExecutionStatus status3 = ExecutionStatus.builder().executionId("exec-2").build();

    assertEquals(status1, status2);
    assertEquals(status1.hashCode(), status2.hashCode());
    assertNotEquals(status1, status3);
  }

  @Test
  @DisplayName("UnifiedListExecutionsRequest builder should create valid object")
  void testUnifiedListExecutionsRequestBuilder() {
    UnifiedListExecutionsRequest request =
        UnifiedListExecutionsRequest.builder()
            .executionType(ExecutionType.MAP_PLAN)
            .status(ExecutionStatusValue.RUNNING)
            .tenantId("tenant-1")
            .orgId("org-1")
            .limit(25)
            .offset(10)
            .build();

    assertEquals(ExecutionType.MAP_PLAN, request.getExecutionType());
    assertEquals(ExecutionStatusValue.RUNNING, request.getStatus());
    assertEquals("tenant-1", request.getTenantId());
    assertEquals("org-1", request.getOrgId());
    assertEquals(25, request.getLimit());
    assertEquals(10, request.getOffset());
  }

  @Test
  @DisplayName("UnifiedListExecutionsRequest builder should have defaults")
  void testUnifiedListExecutionsRequestBuilderDefaults() {
    UnifiedListExecutionsRequest request = UnifiedListExecutionsRequest.builder().build();

    assertNull(request.getExecutionType());
    assertNull(request.getStatus());
    assertEquals(50, request.getLimit());
    assertEquals(0, request.getOffset());
  }

  @Test
  @DisplayName("UnifiedListExecutionsResponse should store values correctly")
  void testUnifiedListExecutionsResponse() {
    List<ExecutionStatus> executions =
        Arrays.asList(
            ExecutionStatus.builder().executionId("exec-1").build(),
            ExecutionStatus.builder().executionId("exec-2").build());

    UnifiedListExecutionsResponse response =
        new UnifiedListExecutionsResponse(executions, 100, 50, 0, true);

    assertEquals(2, response.getExecutions().size());
    assertEquals(100, response.getTotal());
    assertEquals(50, response.getLimit());
    assertEquals(0, response.getOffset());
    assertTrue(response.isHasMore());
  }
}
