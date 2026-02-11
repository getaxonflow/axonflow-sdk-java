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

package com.getaxonflow.sdk.types.execution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unified Execution Tracking Types for AxonFlow SDK.
 * <p>
 * These types provide a consistent interface for tracking both Multi-Agent Planning (MAP)
 * and Workflow Control Plane (WCP) executions. The unified schema enables consistent
 * status tracking, progress reporting, and cost tracking across execution types.
 * <p>
 * Issue #1075 - EPIC #1074: Unified Workflow Infrastructure
 */
public final class ExecutionTypes {

    private ExecutionTypes() {
        // Utility class, no instances
    }

    /**
     * Execution type distinguishing between MAP plans and WCP workflows.
     */
    public enum ExecutionType {
        MAP_PLAN("map_plan"),
        WCP_WORKFLOW("wcp_workflow");

        private final String value;

        ExecutionType(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static ExecutionType fromValue(String value) {
            for (ExecutionType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown execution type: " + value);
        }
    }

    /**
     * Unified execution status values.
     */
    public enum ExecutionStatusValue {
        PENDING("pending"),
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed"),
        CANCELLED("cancelled"),
        ABORTED("aborted"),   // WCP-specific: workflow aborted
        EXPIRED("expired");   // MAP-specific: plan expired before execution

        private final String value;

        ExecutionStatusValue(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED ||
                   this == ABORTED || this == EXPIRED;
        }

        @JsonCreator
        public static ExecutionStatusValue fromValue(String value) {
            for (ExecutionStatusValue status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown execution status: " + value);
        }
    }

    /**
     * Step status values.
     */
    public enum StepStatusValue {
        PENDING("pending"),
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed"),
        SKIPPED("skipped"),
        BLOCKED("blocked"),   // WCP: blocked by policy
        APPROVAL("approval"); // WCP: waiting for approval

        private final String value;

        StepStatusValue(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == SKIPPED;
        }

        public boolean isBlocking() {
            return this == BLOCKED || this == APPROVAL;
        }

        @JsonCreator
        public static StepStatusValue fromValue(String value) {
            for (StepStatusValue status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown step status: " + value);
        }
    }

    /**
     * Step type indicating what kind of operation the step performs.
     */
    public enum UnifiedStepType {
        LLM_CALL("llm_call"),
        TOOL_CALL("tool_call"),
        CONNECTOR_CALL("connector_call"),
        HUMAN_TASK("human_task"),
        SYNTHESIS("synthesis"), // MAP: result synthesis step
        ACTION("action"),       // Generic action step
        GATE("gate");           // WCP: policy gate evaluation

        private final String value;

        UnifiedStepType(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static UnifiedStepType fromValue(String value) {
            for (UnifiedStepType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown step type: " + value);
        }
    }

    /**
     * Gate decision values (applicable to both MAP and WCP).
     */
    public enum UnifiedGateDecision {
        ALLOW("allow"),
        BLOCK("block"),
        REQUIRE_APPROVAL("require_approval");

        private final String value;

        UnifiedGateDecision(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static UnifiedGateDecision fromValue(String value) {
            for (UnifiedGateDecision decision : values()) {
                if (decision.value.equals(value)) {
                    return decision;
                }
            }
            throw new IllegalArgumentException("Unknown gate decision: " + value);
        }
    }

    /**
     * Approval status for require_approval decisions.
     */
    public enum UnifiedApprovalStatus {
        PENDING("pending"),
        APPROVED("approved"),
        REJECTED("rejected");

        private final String value;

        UnifiedApprovalStatus(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static UnifiedApprovalStatus fromValue(String value) {
            for (UnifiedApprovalStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown approval status: " + value);
        }
    }

    /**
     * Detailed information about an individual execution step.
     */
    public static final class UnifiedStepStatus {
        private final String stepId;
        private final int stepIndex;
        private final String stepName;
        private final UnifiedStepType stepType;
        private final StepStatusValue status;
        private final Instant startedAt;
        private final Instant endedAt;
        private final String duration;
        private final UnifiedGateDecision decision;
        private final String decisionReason;
        private final List<String> policiesMatched;
        private final UnifiedApprovalStatus approvalStatus;
        private final String approvedBy;
        private final Instant approvedAt;
        private final String model;
        private final String provider;
        private final Double costUsd;
        private final Object input;
        private final Object output;
        private final String resultSummary;
        private final String error;

        @JsonCreator
        public UnifiedStepStatus(
                @JsonProperty("step_id") String stepId,
                @JsonProperty("step_index") int stepIndex,
                @JsonProperty("step_name") String stepName,
                @JsonProperty("step_type") UnifiedStepType stepType,
                @JsonProperty("status") StepStatusValue status,
                @JsonProperty("started_at") Instant startedAt,
                @JsonProperty("ended_at") Instant endedAt,
                @JsonProperty("duration") String duration,
                @JsonProperty("decision") UnifiedGateDecision decision,
                @JsonProperty("decision_reason") String decisionReason,
                @JsonProperty("policies_matched") List<String> policiesMatched,
                @JsonProperty("approval_status") UnifiedApprovalStatus approvalStatus,
                @JsonProperty("approved_by") String approvedBy,
                @JsonProperty("approved_at") Instant approvedAt,
                @JsonProperty("model") String model,
                @JsonProperty("provider") String provider,
                @JsonProperty("cost_usd") Double costUsd,
                @JsonProperty("input") Object input,
                @JsonProperty("output") Object output,
                @JsonProperty("result_summary") String resultSummary,
                @JsonProperty("error") String error) {
            this.stepId = stepId;
            this.stepIndex = stepIndex;
            this.stepName = stepName;
            this.stepType = stepType;
            this.status = status;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.duration = duration;
            this.decision = decision;
            this.decisionReason = decisionReason;
            this.policiesMatched = policiesMatched;
            this.approvalStatus = approvalStatus;
            this.approvedBy = approvedBy;
            this.approvedAt = approvedAt;
            this.model = model;
            this.provider = provider;
            this.costUsd = costUsd;
            this.input = input;
            this.output = output;
            this.resultSummary = resultSummary;
            this.error = error;
        }

        public String getStepId() { return stepId; }
        public int getStepIndex() { return stepIndex; }
        public String getStepName() { return stepName; }
        public UnifiedStepType getStepType() { return stepType; }
        public StepStatusValue getStatus() { return status; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getEndedAt() { return endedAt; }
        public String getDuration() { return duration; }
        public UnifiedGateDecision getDecision() { return decision; }
        public String getDecisionReason() { return decisionReason; }
        public List<String> getPoliciesMatched() { return policiesMatched; }
        public UnifiedApprovalStatus getApprovalStatus() { return approvalStatus; }
        public String getApprovedBy() { return approvedBy; }
        public Instant getApprovedAt() { return approvedAt; }
        public String getModel() { return model; }
        public String getProvider() { return provider; }
        public Double getCostUsd() { return costUsd; }
        public Object getInput() { return input; }
        public Object getOutput() { return output; }
        public String getResultSummary() { return resultSummary; }
        public String getError() { return error; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnifiedStepStatus that = (UnifiedStepStatus) o;
            return stepIndex == that.stepIndex && Objects.equals(stepId, that.stepId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stepId, stepIndex);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String stepId;
            private int stepIndex;
            private String stepName;
            private UnifiedStepType stepType;
            private StepStatusValue status;
            private Instant startedAt;
            private Instant endedAt;
            private String duration;
            private UnifiedGateDecision decision;
            private String decisionReason;
            private List<String> policiesMatched;
            private UnifiedApprovalStatus approvalStatus;
            private String approvedBy;
            private Instant approvedAt;
            private String model;
            private String provider;
            private Double costUsd;
            private Object input;
            private Object output;
            private String resultSummary;
            private String error;

            public Builder stepId(String stepId) { this.stepId = stepId; return this; }
            public Builder stepIndex(int stepIndex) { this.stepIndex = stepIndex; return this; }
            public Builder stepName(String stepName) { this.stepName = stepName; return this; }
            public Builder stepType(UnifiedStepType stepType) { this.stepType = stepType; return this; }
            public Builder status(StepStatusValue status) { this.status = status; return this; }
            public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
            public Builder endedAt(Instant endedAt) { this.endedAt = endedAt; return this; }
            public Builder duration(String duration) { this.duration = duration; return this; }
            public Builder decision(UnifiedGateDecision decision) { this.decision = decision; return this; }
            public Builder decisionReason(String decisionReason) { this.decisionReason = decisionReason; return this; }
            public Builder policiesMatched(List<String> policiesMatched) { this.policiesMatched = policiesMatched; return this; }
            public Builder approvalStatus(UnifiedApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; return this; }
            public Builder approvedBy(String approvedBy) { this.approvedBy = approvedBy; return this; }
            public Builder approvedAt(Instant approvedAt) { this.approvedAt = approvedAt; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public Builder provider(String provider) { this.provider = provider; return this; }
            public Builder costUsd(Double costUsd) { this.costUsd = costUsd; return this; }
            public Builder input(Object input) { this.input = input; return this; }
            public Builder output(Object output) { this.output = output; return this; }
            public Builder resultSummary(String resultSummary) { this.resultSummary = resultSummary; return this; }
            public Builder error(String error) { this.error = error; return this; }

            public UnifiedStepStatus build() {
                return new UnifiedStepStatus(
                    stepId, stepIndex, stepName, stepType, status, startedAt, endedAt,
                    duration, decision, decisionReason, policiesMatched, approvalStatus,
                    approvedBy, approvedAt, model, provider, costUsd, input, output,
                    resultSummary, error
                );
            }
        }
    }

    /**
     * Unified execution status for both MAP plans and WCP workflows.
     */
    public static final class ExecutionStatus {
        private final String executionId;
        private final ExecutionType executionType;
        private final String name;
        private final String source;
        private final ExecutionStatusValue status;
        private final int currentStepIndex;
        private final int totalSteps;
        private final double progressPercent;
        private final Instant startedAt;
        private final Instant completedAt;
        private final String duration;
        private final Double estimatedCostUsd;
        private final Double actualCostUsd;
        private final List<UnifiedStepStatus> steps;
        private final String error;
        private final String tenantId;
        private final String orgId;
        private final String userId;
        private final String clientId;
        private final Map<String, Object> metadata;
        private final Instant createdAt;
        private final Instant updatedAt;

        @JsonCreator
        public ExecutionStatus(
                @JsonProperty("execution_id") String executionId,
                @JsonProperty("execution_type") ExecutionType executionType,
                @JsonProperty("name") String name,
                @JsonProperty("source") String source,
                @JsonProperty("status") ExecutionStatusValue status,
                @JsonProperty("current_step_index") int currentStepIndex,
                @JsonProperty("total_steps") int totalSteps,
                @JsonProperty("progress_percent") double progressPercent,
                @JsonProperty("started_at") Instant startedAt,
                @JsonProperty("completed_at") Instant completedAt,
                @JsonProperty("duration") String duration,
                @JsonProperty("estimated_cost_usd") Double estimatedCostUsd,
                @JsonProperty("actual_cost_usd") Double actualCostUsd,
                @JsonProperty("steps") List<UnifiedStepStatus> steps,
                @JsonProperty("error") String error,
                @JsonProperty("tenant_id") String tenantId,
                @JsonProperty("org_id") String orgId,
                @JsonProperty("user_id") String userId,
                @JsonProperty("client_id") String clientId,
                @JsonProperty("metadata") Map<String, Object> metadata,
                @JsonProperty("created_at") Instant createdAt,
                @JsonProperty("updated_at") Instant updatedAt) {
            this.executionId = executionId;
            this.executionType = executionType;
            this.name = name;
            this.source = source;
            this.status = status;
            this.currentStepIndex = currentStepIndex;
            this.totalSteps = totalSteps;
            this.progressPercent = progressPercent;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
            this.duration = duration;
            this.estimatedCostUsd = estimatedCostUsd;
            this.actualCostUsd = actualCostUsd;
            this.steps = steps;
            this.error = error;
            this.tenantId = tenantId;
            this.orgId = orgId;
            this.userId = userId;
            this.clientId = clientId;
            this.metadata = metadata;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getExecutionId() { return executionId; }
        public ExecutionType getExecutionType() { return executionType; }
        public String getName() { return name; }
        public String getSource() { return source; }
        public ExecutionStatusValue getStatus() { return status; }
        public int getCurrentStepIndex() { return currentStepIndex; }
        public int getTotalSteps() { return totalSteps; }
        public double getProgressPercent() { return progressPercent; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public String getDuration() { return duration; }
        public Double getEstimatedCostUsd() { return estimatedCostUsd; }
        public Double getActualCostUsd() { return actualCostUsd; }
        public List<UnifiedStepStatus> getSteps() { return steps; }
        public String getError() { return error; }
        public String getTenantId() { return tenantId; }
        public String getOrgId() { return orgId; }
        public String getUserId() { return userId; }
        public String getClientId() { return clientId; }
        public Map<String, Object> getMetadata() { return metadata; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }

        /**
         * Check if the execution is in a terminal state.
         */
        public boolean isTerminal() {
            return status.isTerminal();
        }

        /**
         * Get the currently running step, if any.
         */
        public UnifiedStepStatus getCurrentStep() {
            if (steps == null) return null;
            for (UnifiedStepStatus step : steps) {
                if (step.getStatus() == StepStatusValue.RUNNING) {
                    return step;
                }
            }
            return null;
        }

        /**
         * Calculate total cost from all steps.
         */
        public double calculateTotalCost() {
            if (steps == null) return 0.0;
            double total = 0.0;
            for (UnifiedStepStatus step : steps) {
                if (step.getCostUsd() != null) {
                    total += step.getCostUsd();
                }
            }
            return total;
        }

        /**
         * Check if this is a MAP plan execution.
         */
        public boolean isMapPlan() {
            return executionType == ExecutionType.MAP_PLAN;
        }

        /**
         * Check if this is a WCP workflow execution.
         */
        public boolean isWcpWorkflow() {
            return executionType == ExecutionType.WCP_WORKFLOW;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExecutionStatus that = (ExecutionStatus) o;
            return Objects.equals(executionId, that.executionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(executionId);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String executionId;
            private ExecutionType executionType;
            private String name;
            private String source;
            private ExecutionStatusValue status;
            private int currentStepIndex;
            private int totalSteps;
            private double progressPercent;
            private Instant startedAt;
            private Instant completedAt;
            private String duration;
            private Double estimatedCostUsd;
            private Double actualCostUsd;
            private List<UnifiedStepStatus> steps;
            private String error;
            private String tenantId;
            private String orgId;
            private String userId;
            private String clientId;
            private Map<String, Object> metadata;
            private Instant createdAt;
            private Instant updatedAt;

            public Builder executionId(String executionId) { this.executionId = executionId; return this; }
            public Builder executionType(ExecutionType executionType) { this.executionType = executionType; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder source(String source) { this.source = source; return this; }
            public Builder status(ExecutionStatusValue status) { this.status = status; return this; }
            public Builder currentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; return this; }
            public Builder totalSteps(int totalSteps) { this.totalSteps = totalSteps; return this; }
            public Builder progressPercent(double progressPercent) { this.progressPercent = progressPercent; return this; }
            public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
            public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
            public Builder duration(String duration) { this.duration = duration; return this; }
            public Builder estimatedCostUsd(Double estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; return this; }
            public Builder actualCostUsd(Double actualCostUsd) { this.actualCostUsd = actualCostUsd; return this; }
            public Builder steps(List<UnifiedStepStatus> steps) { this.steps = steps; return this; }
            public Builder error(String error) { this.error = error; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder orgId(String orgId) { this.orgId = orgId; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder clientId(String clientId) { this.clientId = clientId; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
            public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

            public ExecutionStatus build() {
                return new ExecutionStatus(
                    executionId, executionType, name, source, status, currentStepIndex,
                    totalSteps, progressPercent, startedAt, completedAt, duration,
                    estimatedCostUsd, actualCostUsd, steps, error, tenantId, orgId,
                    userId, clientId, metadata, createdAt, updatedAt
                );
            }
        }
    }

    /**
     * Request to list executions with optional filters.
     */
    public static final class UnifiedListExecutionsRequest {
        private final ExecutionType executionType;
        private final ExecutionStatusValue status;
        private final String tenantId;
        private final String orgId;
        private final int limit;
        private final int offset;

        public UnifiedListExecutionsRequest(
                ExecutionType executionType, ExecutionStatusValue status,
                String tenantId, String orgId, int limit, int offset) {
            this.executionType = executionType;
            this.status = status;
            this.tenantId = tenantId;
            this.orgId = orgId;
            this.limit = limit;
            this.offset = offset;
        }

        public ExecutionType getExecutionType() { return executionType; }
        public ExecutionStatusValue getStatus() { return status; }
        public String getTenantId() { return tenantId; }
        public String getOrgId() { return orgId; }
        public int getLimit() { return limit; }
        public int getOffset() { return offset; }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private ExecutionType executionType;
            private ExecutionStatusValue status;
            private String tenantId;
            private String orgId;
            private int limit = 50;
            private int offset = 0;

            public Builder executionType(ExecutionType executionType) { this.executionType = executionType; return this; }
            public Builder status(ExecutionStatusValue status) { this.status = status; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder orgId(String orgId) { this.orgId = orgId; return this; }
            public Builder limit(int limit) { this.limit = limit; return this; }
            public Builder offset(int offset) { this.offset = offset; return this; }

            public UnifiedListExecutionsRequest build() {
                return new UnifiedListExecutionsRequest(
                    executionType, status, tenantId, orgId, limit, offset
                );
            }
        }
    }

    /**
     * Paginated response for listing executions.
     */
    public static final class UnifiedListExecutionsResponse {
        private final List<ExecutionStatus> executions;
        private final int total;
        private final int limit;
        private final int offset;
        private final boolean hasMore;

        @JsonCreator
        public UnifiedListExecutionsResponse(
                @JsonProperty("executions") List<ExecutionStatus> executions,
                @JsonProperty("total") int total,
                @JsonProperty("limit") int limit,
                @JsonProperty("offset") int offset,
                @JsonProperty("has_more") boolean hasMore) {
            this.executions = executions;
            this.total = total;
            this.limit = limit;
            this.offset = offset;
            this.hasMore = hasMore;
        }

        public List<ExecutionStatus> getExecutions() { return executions; }
        public int getTotal() { return total; }
        public int getLimit() { return limit; }
        public int getOffset() { return offset; }
        public boolean isHasMore() { return hasMore; }
    }
}
