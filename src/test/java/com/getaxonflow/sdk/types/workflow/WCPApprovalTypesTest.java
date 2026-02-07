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
import com.getaxonflow.sdk.types.workflow.WorkflowTypes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WCP Approval types (Feature 5).
 */
@DisplayName("WCP Approval Types")
class WCPApprovalTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ========================================================================
    // ApproveStepResponse
    // ========================================================================

    @Test
    @DisplayName("ApproveStepResponse - should construct with all fields")
    void approveStepResponseShouldConstructWithAllFields() {
        ApproveStepResponse response = new ApproveStepResponse("wf-123", "step-1", "approved");

        assertThat(response.getWorkflowId()).isEqualTo("wf-123");
        assertThat(response.getStepId()).isEqualTo("step-1");
        assertThat(response.getStatus()).isEqualTo("approved");
    }

    @Test
    @DisplayName("ApproveStepResponse - should deserialize from JSON")
    void approveStepResponseShouldDeserialize() throws Exception {
        String json = "{\"workflow_id\":\"wf-456\",\"step_id\":\"step-2\",\"status\":\"approved\"}";

        ApproveStepResponse response = objectMapper.readValue(json, ApproveStepResponse.class);

        assertThat(response.getWorkflowId()).isEqualTo("wf-456");
        assertThat(response.getStepId()).isEqualTo("step-2");
        assertThat(response.getStatus()).isEqualTo("approved");
    }

    @Test
    @DisplayName("ApproveStepResponse - should serialize to JSON")
    void approveStepResponseShouldSerialize() throws Exception {
        ApproveStepResponse response = new ApproveStepResponse("wf-789", "step-3", "approved");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"workflow_id\":\"wf-789\"");
        assertThat(json).contains("\"step_id\":\"step-3\"");
        assertThat(json).contains("\"status\":\"approved\"");
    }

    @Test
    @DisplayName("ApproveStepResponse - equals and hashCode")
    void approveStepResponseEqualsAndHashCode() {
        ApproveStepResponse r1 = new ApproveStepResponse("wf-1", "step-1", "approved");
        ApproveStepResponse r2 = new ApproveStepResponse("wf-1", "step-1", "approved");
        ApproveStepResponse r3 = new ApproveStepResponse("wf-2", "step-1", "approved");

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1).isNotEqualTo(r3);
    }

    @Test
    @DisplayName("ApproveStepResponse - toString contains all fields")
    void approveStepResponseToStringShouldContainAllFields() {
        ApproveStepResponse response = new ApproveStepResponse("wf-1", "step-1", "approved");
        String str = response.toString();

        assertThat(str).contains("wf-1");
        assertThat(str).contains("step-1");
        assertThat(str).contains("approved");
    }

    @Test
    @DisplayName("ApproveStepResponse - should ignore unknown properties")
    void approveStepResponseShouldIgnoreUnknownProperties() throws Exception {
        String json = "{\"workflow_id\":\"wf-1\",\"step_id\":\"s-1\",\"status\":\"approved\",\"extra\":\"field\"}";

        ApproveStepResponse response = objectMapper.readValue(json, ApproveStepResponse.class);

        assertThat(response.getWorkflowId()).isEqualTo("wf-1");
    }

    // ========================================================================
    // RejectStepResponse
    // ========================================================================

    @Test
    @DisplayName("RejectStepResponse - should construct with all fields")
    void rejectStepResponseShouldConstructWithAllFields() {
        RejectStepResponse response = new RejectStepResponse("wf-123", "step-1", "rejected");

        assertThat(response.getWorkflowId()).isEqualTo("wf-123");
        assertThat(response.getStepId()).isEqualTo("step-1");
        assertThat(response.getStatus()).isEqualTo("rejected");
    }

    @Test
    @DisplayName("RejectStepResponse - should deserialize from JSON")
    void rejectStepResponseShouldDeserialize() throws Exception {
        String json = "{\"workflow_id\":\"wf-456\",\"step_id\":\"step-2\",\"status\":\"rejected\"}";

        RejectStepResponse response = objectMapper.readValue(json, RejectStepResponse.class);

        assertThat(response.getWorkflowId()).isEqualTo("wf-456");
        assertThat(response.getStepId()).isEqualTo("step-2");
        assertThat(response.getStatus()).isEqualTo("rejected");
    }

    @Test
    @DisplayName("RejectStepResponse - should serialize to JSON")
    void rejectStepResponseShouldSerialize() throws Exception {
        RejectStepResponse response = new RejectStepResponse("wf-789", "step-3", "rejected");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"workflow_id\":\"wf-789\"");
        assertThat(json).contains("\"step_id\":\"step-3\"");
        assertThat(json).contains("\"status\":\"rejected\"");
    }

    @Test
    @DisplayName("RejectStepResponse - equals and hashCode")
    void rejectStepResponseEqualsAndHashCode() {
        RejectStepResponse r1 = new RejectStepResponse("wf-1", "step-1", "rejected");
        RejectStepResponse r2 = new RejectStepResponse("wf-1", "step-1", "rejected");
        RejectStepResponse r3 = new RejectStepResponse("wf-2", "step-1", "rejected");

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1).isNotEqualTo(r3);
    }

    @Test
    @DisplayName("RejectStepResponse - toString contains all fields")
    void rejectStepResponseToStringShouldContainAllFields() {
        RejectStepResponse response = new RejectStepResponse("wf-1", "step-1", "rejected");
        String str = response.toString();

        assertThat(str).contains("wf-1");
        assertThat(str).contains("step-1");
        assertThat(str).contains("rejected");
    }

    // ========================================================================
    // PendingApproval
    // ========================================================================

    @Test
    @DisplayName("PendingApproval - should construct with all fields")
    void pendingApprovalShouldConstructWithAllFields() {
        PendingApproval approval = new PendingApproval(
            "wf-1", "Code Review", "step-1", "Generate Code", "llm_call", "2026-02-07T10:00:00Z");

        assertThat(approval.getWorkflowId()).isEqualTo("wf-1");
        assertThat(approval.getWorkflowName()).isEqualTo("Code Review");
        assertThat(approval.getStepId()).isEqualTo("step-1");
        assertThat(approval.getStepName()).isEqualTo("Generate Code");
        assertThat(approval.getStepType()).isEqualTo("llm_call");
        assertThat(approval.getCreatedAt()).isEqualTo("2026-02-07T10:00:00Z");
    }

    @Test
    @DisplayName("PendingApproval - should deserialize from JSON")
    void pendingApprovalShouldDeserialize() throws Exception {
        String json = "{"
            + "\"workflow_id\":\"wf-1\","
            + "\"workflow_name\":\"Code Review\","
            + "\"step_id\":\"step-1\","
            + "\"step_name\":\"Generate Code\","
            + "\"step_type\":\"llm_call\","
            + "\"created_at\":\"2026-02-07T10:00:00Z\""
            + "}";

        PendingApproval approval = objectMapper.readValue(json, PendingApproval.class);

        assertThat(approval.getWorkflowId()).isEqualTo("wf-1");
        assertThat(approval.getWorkflowName()).isEqualTo("Code Review");
        assertThat(approval.getStepId()).isEqualTo("step-1");
        assertThat(approval.getStepName()).isEqualTo("Generate Code");
        assertThat(approval.getStepType()).isEqualTo("llm_call");
        assertThat(approval.getCreatedAt()).isEqualTo("2026-02-07T10:00:00Z");
    }

    @Test
    @DisplayName("PendingApproval - should serialize to JSON")
    void pendingApprovalShouldSerialize() throws Exception {
        PendingApproval approval = new PendingApproval(
            "wf-1", "Code Review", "step-1", "Generate Code", "llm_call", "2026-02-07T10:00:00Z");

        String json = objectMapper.writeValueAsString(approval);

        assertThat(json).contains("\"workflow_id\":\"wf-1\"");
        assertThat(json).contains("\"workflow_name\":\"Code Review\"");
        assertThat(json).contains("\"step_id\":\"step-1\"");
        assertThat(json).contains("\"step_name\":\"Generate Code\"");
        assertThat(json).contains("\"step_type\":\"llm_call\"");
    }

    @Test
    @DisplayName("PendingApproval - equals and hashCode")
    void pendingApprovalEqualsAndHashCode() {
        PendingApproval a1 = new PendingApproval("wf-1", "Name", "s-1", "Step", "llm_call", "2026-02-07T10:00:00Z");
        PendingApproval a2 = new PendingApproval("wf-1", "Name", "s-1", "Step", "llm_call", "2026-02-07T10:00:00Z");
        PendingApproval a3 = new PendingApproval("wf-2", "Name", "s-1", "Step", "llm_call", "2026-02-07T10:00:00Z");

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
        assertThat(a1).isNotEqualTo(a3);
    }

    @Test
    @DisplayName("PendingApproval - toString contains all fields")
    void pendingApprovalToStringShouldContainAllFields() {
        PendingApproval approval = new PendingApproval(
            "wf-1", "Code Review", "step-1", "Generate Code", "llm_call", "2026-02-07T10:00:00Z");
        String str = approval.toString();

        assertThat(str).contains("wf-1");
        assertThat(str).contains("Code Review");
        assertThat(str).contains("step-1");
        assertThat(str).contains("Generate Code");
        assertThat(str).contains("llm_call");
    }

    // ========================================================================
    // PendingApprovalsResponse
    // ========================================================================

    @Test
    @DisplayName("PendingApprovalsResponse - should construct with all fields")
    void pendingApprovalsResponseShouldConstructWithAllFields() {
        PendingApproval approval = new PendingApproval(
            "wf-1", "Name", "s-1", "Step", "llm_call", "2026-02-07T10:00:00Z");
        PendingApprovalsResponse response = new PendingApprovalsResponse(
            Collections.singletonList(approval), 1);

        assertThat(response.getApprovals()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getApprovals().get(0).getWorkflowId()).isEqualTo("wf-1");
    }

    @Test
    @DisplayName("PendingApprovalsResponse - should handle null approvals list")
    void pendingApprovalsResponseShouldHandleNullList() {
        PendingApprovalsResponse response = new PendingApprovalsResponse(null, 0);

        assertThat(response.getApprovals()).isEmpty();
        assertThat(response.getTotal()).isEqualTo(0);
    }

    @Test
    @DisplayName("PendingApprovalsResponse - should deserialize from JSON")
    void pendingApprovalsResponseShouldDeserialize() throws Exception {
        String json = "{"
            + "\"approvals\":["
            + "  {\"workflow_id\":\"wf-1\",\"workflow_name\":\"Review\","
            + "   \"step_id\":\"s-1\",\"step_name\":\"Generate\","
            + "   \"step_type\":\"llm_call\",\"created_at\":\"2026-02-07T10:00:00Z\"}"
            + "],"
            + "\"total\":1"
            + "}";

        PendingApprovalsResponse response = objectMapper.readValue(json, PendingApprovalsResponse.class);

        assertThat(response.getApprovals()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getApprovals().get(0).getWorkflowId()).isEqualTo("wf-1");
    }

    @Test
    @DisplayName("PendingApprovalsResponse - approvals list should be immutable")
    void pendingApprovalsResponseListShouldBeImmutable() {
        PendingApproval approval = new PendingApproval(
            "wf-1", "Name", "s-1", "Step", "llm_call", "2026-02-07T10:00:00Z");
        PendingApprovalsResponse response = new PendingApprovalsResponse(
            Arrays.asList(approval), 1);

        assertThatThrownBy(() -> response.getApprovals().add(
            new PendingApproval("wf-2", "N", "s-2", "S", "tool_call", "2026-02-07T11:00:00Z")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("PendingApprovalsResponse - equals and hashCode")
    void pendingApprovalsResponseEqualsAndHashCode() {
        PendingApproval approval = new PendingApproval(
            "wf-1", "Name", "s-1", "Step", "llm_call", "2026-02-07T10:00:00Z");
        PendingApprovalsResponse r1 = new PendingApprovalsResponse(Collections.singletonList(approval), 1);
        PendingApprovalsResponse r2 = new PendingApprovalsResponse(Collections.singletonList(approval), 1);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("PendingApprovalsResponse - toString contains key info")
    void pendingApprovalsResponseToStringShouldContainInfo() {
        PendingApprovalsResponse response = new PendingApprovalsResponse(Collections.emptyList(), 0);
        String str = response.toString();

        assertThat(str).contains("total=0");
        assertThat(str).contains("approvals=");
    }
}
