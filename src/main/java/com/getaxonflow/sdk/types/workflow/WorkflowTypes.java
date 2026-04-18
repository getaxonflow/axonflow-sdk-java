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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Workflow Control Plane types for AxonFlow SDK.
 *
 * <p>The Workflow Control Plane provides governance gates for external orchestrators like
 * LangChain, LangGraph, and CrewAI.
 *
 * <p>"LangChain runs the workflow. AxonFlow decides when it's allowed to move forward."
 */
public final class WorkflowTypes {

  private WorkflowTypes() {
    // Utility class
  }

  /** Workflow status values. */
  public enum WorkflowStatus {
    @JsonProperty("in_progress")
    IN_PROGRESS("in_progress"),
    @JsonProperty("completed")
    COMPLETED("completed"),
    @JsonProperty("aborted")
    ABORTED("aborted"),
    @JsonProperty("failed")
    FAILED("failed");

    private final String value;

    WorkflowStatus(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static WorkflowStatus fromValue(String value) {
      for (WorkflowStatus status : values()) {
        if (status.value.equals(value)) {
          return status;
        }
      }
      throw new IllegalArgumentException("Unknown workflow status: " + value);
    }
  }

  /** Source of the workflow (which orchestrator is running it). */
  public enum WorkflowSource {
    @JsonProperty("langgraph")
    LANGGRAPH("langgraph"),
    @JsonProperty("langchain")
    LANGCHAIN("langchain"),
    @JsonProperty("crewai")
    CREWAI("crewai"),
    @JsonProperty("external")
    EXTERNAL("external");

    private final String value;

    WorkflowSource(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static WorkflowSource fromValue(String value) {
      for (WorkflowSource source : values()) {
        if (source.value.equals(value)) {
          return source;
        }
      }
      throw new IllegalArgumentException("Unknown workflow source: " + value);
    }
  }

  /** Gate decision values returned by step gate checks. */
  public enum GateDecision {
    @JsonProperty("allow")
    ALLOW("allow"),
    @JsonProperty("block")
    BLOCK("block"),
    @JsonProperty("require_approval")
    REQUIRE_APPROVAL("require_approval");

    private final String value;

    GateDecision(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static GateDecision fromValue(String value) {
      for (GateDecision decision : values()) {
        if (decision.value.equals(value)) {
          return decision;
        }
      }
      throw new IllegalArgumentException("Unknown gate decision: " + value);
    }
  }

  /** Approval status for steps requiring human approval. */
  public enum ApprovalStatus {
    @JsonProperty("pending")
    PENDING("pending"),
    @JsonProperty("approved")
    APPROVED("approved"),
    @JsonProperty("rejected")
    REJECTED("rejected");

    private final String value;

    ApprovalStatus(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static ApprovalStatus fromValue(String value) {
      for (ApprovalStatus status : values()) {
        if (status.value.equals(value)) {
          return status;
        }
      }
      throw new IllegalArgumentException("Unknown approval status: " + value);
    }
  }

  /** Step type indicating what kind of operation the step performs. */
  public enum StepType {
    @JsonProperty("llm_call")
    LLM_CALL("llm_call"),
    @JsonProperty("tool_call")
    TOOL_CALL("tool_call"),
    @JsonProperty("connector_call")
    CONNECTOR_CALL("connector_call"),
    @JsonProperty("human_task")
    HUMAN_TASK("human_task");

    private final String value;

    StepType(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static StepType fromValue(String value) {
      for (StepType type : values()) {
        if (type.value.equals(value)) {
          return type;
        }
      }
      throw new IllegalArgumentException("Unknown step type: " + value);
    }
  }

  /** Request to create a new workflow. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class CreateWorkflowRequest {

    @JsonProperty("workflow_name")
    private final String workflowName;

    @JsonProperty("source")
    private final WorkflowSource source;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("trace_id")
    private final String traceId;

    /** Backward-compatible constructor without traceId. */
    public CreateWorkflowRequest(
        String workflowName, WorkflowSource source, Map<String, Object> metadata) {
      this(workflowName, source, metadata, null);
    }

    @JsonCreator
    public CreateWorkflowRequest(
        @JsonProperty("workflow_name") String workflowName,
        @JsonProperty("source") WorkflowSource source,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("trace_id") String traceId) {
      this.workflowName = Objects.requireNonNull(workflowName, "workflowName is required");
      this.source = source != null ? source : WorkflowSource.EXTERNAL;
      this.metadata =
          metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
      this.traceId = traceId;
    }

    public String getWorkflowName() {
      return workflowName;
    }

    public WorkflowSource getSource() {
      return source;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }

    public String getTraceId() {
      return traceId;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      private String workflowName;
      private WorkflowSource source = WorkflowSource.EXTERNAL;
      private Map<String, Object> metadata;
      private String traceId;

      public Builder workflowName(String workflowName) {
        this.workflowName = workflowName;
        return this;
      }

      public Builder source(WorkflowSource source) {
        this.source = source;
        return this;
      }

      public Builder metadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
      }

      public Builder traceId(String traceId) {
        this.traceId = traceId;
        return this;
      }

      public CreateWorkflowRequest build() {
        return new CreateWorkflowRequest(workflowName, source, metadata, traceId);
      }
    }
  }

  /** Response from creating a workflow. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class CreateWorkflowResponse {

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("workflow_name")
    private final String workflowName;

    @JsonProperty("source")
    private final WorkflowSource source;

    @JsonProperty("status")
    private final WorkflowStatus status;

    @JsonProperty("created_at")
    private final Instant createdAt;

    @JsonProperty("trace_id")
    private final String traceId;

    /** Backward-compatible constructor without traceId. */
    public CreateWorkflowResponse(
        String workflowId,
        String workflowName,
        WorkflowSource source,
        WorkflowStatus status,
        Instant createdAt) {
      this(workflowId, workflowName, source, status, createdAt, null);
    }

    @JsonCreator
    public CreateWorkflowResponse(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("workflow_name") String workflowName,
        @JsonProperty("source") WorkflowSource source,
        @JsonProperty("status") WorkflowStatus status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("trace_id") String traceId) {
      this.workflowId = workflowId;
      this.workflowName = workflowName;
      this.source = source;
      this.status = status;
      this.createdAt = createdAt;
      this.traceId = traceId;
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public String getWorkflowName() {
      return workflowName;
    }

    public WorkflowSource getSource() {
      return source;
    }

    public WorkflowStatus getStatus() {
      return status;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public String getTraceId() {
      return traceId;
    }
  }

  /** Tool-level context for per-tool governance within tool_call steps. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ToolContext {

    @JsonProperty("tool_name")
    private final String toolName;

    @JsonProperty("tool_type")
    private final String toolType;

    @JsonProperty("tool_input")
    private final Map<String, Object> toolInput;

    private ToolContext(Builder builder) {
      this.toolName = builder.toolName;
      this.toolType = builder.toolType;
      this.toolInput =
          builder.toolInput != null
              ? Collections.unmodifiableMap(new HashMap<>(builder.toolInput))
              : null;
    }

    @JsonCreator
    public ToolContext(
        @JsonProperty("tool_name") String toolName,
        @JsonProperty("tool_type") String toolType,
        @JsonProperty("tool_input") Map<String, Object> toolInput) {
      this.toolName = toolName;
      this.toolType = toolType;
      this.toolInput =
          toolInput != null ? Collections.unmodifiableMap(new HashMap<>(toolInput)) : null;
    }

    public String getToolName() {
      return toolName;
    }

    public String getToolType() {
      return toolType;
    }

    public Map<String, Object> getToolInput() {
      return toolInput;
    }

    public static Builder builder(String toolName) {
      return new Builder(toolName);
    }

    public static final class Builder {
      private final String toolName;
      private String toolType;
      private Map<String, Object> toolInput;

      public Builder(String toolName) {
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
      }

      public Builder toolType(String toolType) {
        this.toolType = toolType;
        return this;
      }

      public Builder toolInput(Map<String, Object> toolInput) {
        this.toolInput = toolInput;
        return this;
      }

      public ToolContext build() {
        return new ToolContext(this);
      }
    }
  }

  /** Request to check if a step is allowed to proceed. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class StepGateRequest {

    @JsonProperty("step_name")
    private final String stepName;

    @JsonProperty("step_type")
    private final StepType stepType;

    @JsonProperty("step_input")
    private final Map<String, Object> stepInput;

    @JsonProperty("model")
    private final String model;

    @JsonProperty("provider")
    private final String provider;

    @JsonProperty("tool_context")
    private final ToolContext toolContext;

    @JsonProperty("retry_policy")
    private final String retryPolicy;

    /** Backward-compatible constructor without toolContext or retryPolicy. */
    public StepGateRequest(
        String stepName,
        StepType stepType,
        Map<String, Object> stepInput,
        String model,
        String provider) {
      this(stepName, stepType, stepInput, model, provider, null, null);
    }

    /** Backward-compatible constructor without retryPolicy. */
    public StepGateRequest(
        String stepName,
        StepType stepType,
        Map<String, Object> stepInput,
        String model,
        String provider,
        ToolContext toolContext) {
      this(stepName, stepType, stepInput, model, provider, toolContext, null);
    }

    @JsonCreator
    public StepGateRequest(
        @JsonProperty("step_name") String stepName,
        @JsonProperty("step_type") StepType stepType,
        @JsonProperty("step_input") Map<String, Object> stepInput,
        @JsonProperty("model") String model,
        @JsonProperty("provider") String provider,
        @JsonProperty("tool_context") ToolContext toolContext,
        @JsonProperty("retry_policy") String retryPolicy) {
      this.stepName = stepName;
      this.stepType = Objects.requireNonNull(stepType, "stepType is required");
      this.stepInput =
          stepInput != null ? Collections.unmodifiableMap(stepInput) : Collections.emptyMap();
      this.model = model;
      this.provider = provider;
      this.toolContext = toolContext;
      this.retryPolicy = retryPolicy;
    }

    public String getStepName() {
      return stepName;
    }

    public StepType getStepType() {
      return stepType;
    }

    public Map<String, Object> getStepInput() {
      return stepInput;
    }

    public String getModel() {
      return model;
    }

    public String getProvider() {
      return provider;
    }

    public ToolContext getToolContext() {
      return toolContext;
    }

    /**
     * Returns the retry policy for this step gate request.
     * "idempotent" (default): return cached decision. "reevaluate": force fresh evaluation.
     */
    public String getRetryPolicy() {
      return retryPolicy;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      private String stepName;
      private StepType stepType;
      private Map<String, Object> stepInput;
      private String model;
      private String provider;
      private ToolContext toolContext;
      private String retryPolicy;

      public Builder stepName(String stepName) {
        this.stepName = stepName;
        return this;
      }

      public Builder stepType(StepType stepType) {
        this.stepType = stepType;
        return this;
      }

      public Builder stepInput(Map<String, Object> stepInput) {
        this.stepInput = stepInput;
        return this;
      }

      public Builder model(String model) {
        this.model = model;
        return this;
      }

      public Builder provider(String provider) {
        this.provider = provider;
        return this;
      }

      public Builder toolContext(ToolContext toolContext) {
        this.toolContext = toolContext;
        return this;
      }

      /** Set the retry policy: "idempotent" (default) or "reevaluate". */
      public Builder retryPolicy(String retryPolicy) {
        this.retryPolicy = retryPolicy;
        return this;
      }

      public StepGateRequest build() {
        return new StepGateRequest(
            stepName, stepType, stepInput, model, provider, toolContext, retryPolicy);
      }
    }
  }

  /** Response from a step gate check. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class StepGateResponse {

    @JsonProperty("decision")
    private final GateDecision decision;

    @JsonProperty("step_id")
    private final String stepId;

    @JsonProperty("reason")
    private final String reason;

    @JsonProperty("policy_ids")
    private final List<String> policyIds;

    @JsonProperty("approval_url")
    private final String approvalUrl;

    @JsonProperty("policies_evaluated")
    private final List<PolicyMatch> policiesEvaluated;

    @JsonProperty("policies_matched")
    private final List<PolicyMatch> policiesMatched;

    @JsonProperty("cached")
    private final boolean cached;

    @JsonProperty("decision_source")
    private final String decisionSource;

    @JsonCreator
    public StepGateResponse(
        @JsonProperty("decision") GateDecision decision,
        @JsonProperty("step_id") String stepId,
        @JsonProperty("reason") String reason,
        @JsonProperty("policy_ids") List<String> policyIds,
        @JsonProperty("approval_url") String approvalUrl,
        @JsonProperty("policies_evaluated") List<PolicyMatch> policiesEvaluated,
        @JsonProperty("policies_matched") List<PolicyMatch> policiesMatched,
        @JsonProperty("cached") boolean cached,
        @JsonProperty("decision_source") String decisionSource) {
      this.decision = decision;
      this.stepId = stepId;
      this.reason = reason;
      this.policyIds =
          policyIds != null ? Collections.unmodifiableList(policyIds) : Collections.emptyList();
      this.approvalUrl = approvalUrl;
      this.policiesEvaluated =
          policiesEvaluated != null
              ? Collections.unmodifiableList(policiesEvaluated)
              : Collections.emptyList();
      this.policiesMatched =
          policiesMatched != null
              ? Collections.unmodifiableList(policiesMatched)
              : Collections.emptyList();
      this.cached = cached;
      this.decisionSource = decisionSource;
    }

    public GateDecision getDecision() {
      return decision;
    }

    public String getStepId() {
      return stepId;
    }

    public String getReason() {
      return reason;
    }

    public List<String> getPolicyIds() {
      return policyIds;
    }

    public String getApprovalUrl() {
      return approvalUrl;
    }

    /**
     * Returns all policies that were evaluated during the gate check.
     *
     * @return immutable list of evaluated policies
     * @since 2.3.0
     */
    public List<PolicyMatch> getPoliciesEvaluated() {
      return policiesEvaluated;
    }

    /**
     * Returns policies that matched and influenced the decision.
     *
     * @return immutable list of matched policies
     * @since 2.3.0
     */
    public List<PolicyMatch> getPoliciesMatched() {
      return policiesMatched;
    }

    /**
     * Returns whether this response was served from a prior decision rather than a fresh evaluation.
     */
    public boolean isCached() {
      return cached;
    }

    /**
     * Returns how the decision was produced: "fresh" or "cached".
     */
    public String getDecisionSource() {
      return decisionSource;
    }

    public boolean isAllowed() {
      return decision == GateDecision.ALLOW;
    }

    public boolean isBlocked() {
      return decision == GateDecision.BLOCK;
    }

    public boolean requiresApproval() {
      return decision == GateDecision.REQUIRE_APPROVAL;
    }
  }

  /** A governance-aware resume boundary at a step-gate evaluation. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Checkpoint {

    @JsonProperty("id")
    private final long id;

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("step_id")
    private final String stepId;

    @JsonProperty("step_index")
    private final int stepIndex;

    @JsonProperty("step_type")
    private final String stepType;

    @JsonProperty("checkpoint_type")
    private final String checkpointType;

    @JsonProperty("gate_decision")
    private final String gateDecision;

    @JsonProperty("gate_reason")
    private final String gateReason;

    @JsonProperty("is_resumable")
    private final boolean resumable;

    @JsonProperty("resume_count")
    private final int resumeCount;

    @JsonProperty("created_at")
    private final String createdAt;

    @JsonCreator
    public Checkpoint(
        @JsonProperty("id") long id,
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("step_id") String stepId,
        @JsonProperty("step_index") int stepIndex,
        @JsonProperty("step_type") String stepType,
        @JsonProperty("checkpoint_type") String checkpointType,
        @JsonProperty("gate_decision") String gateDecision,
        @JsonProperty("gate_reason") String gateReason,
        @JsonProperty("is_resumable") boolean resumable,
        @JsonProperty("resume_count") int resumeCount,
        @JsonProperty("created_at") String createdAt) {
      this.id = id;
      this.workflowId = workflowId;
      this.stepId = stepId;
      this.stepIndex = stepIndex;
      this.stepType = stepType;
      this.checkpointType = checkpointType;
      this.gateDecision = gateDecision;
      this.gateReason = gateReason;
      this.resumable = resumable;
      this.resumeCount = resumeCount;
      this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public String getStepId() { return stepId; }
    public int getStepIndex() { return stepIndex; }
    public String getStepType() { return stepType; }
    public String getCheckpointType() { return checkpointType; }
    public String getGateDecision() { return gateDecision; }
    public String getGateReason() { return gateReason; }
    public boolean isResumable() { return resumable; }
    public int getResumeCount() { return resumeCount; }
    public String getCreatedAt() { return createdAt; }
  }

  /** Response from listing checkpoints. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class CheckpointListResponse {

    @JsonProperty("checkpoints")
    private final List<Checkpoint> checkpoints;

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonCreator
    public CheckpointListResponse(
        @JsonProperty("checkpoints") List<Checkpoint> checkpoints,
        @JsonProperty("workflow_id") String workflowId) {
      this.checkpoints = checkpoints != null
          ? Collections.unmodifiableList(checkpoints)
          : Collections.emptyList();
      this.workflowId = workflowId;
    }

    public List<Checkpoint> getCheckpoints() { return checkpoints; }
    public String getWorkflowId() { return workflowId; }
  }

  /** Response after resuming from a checkpoint. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ResumeFromCheckpointResponse {

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("resumed_from_checkpoint")
    private final String resumedFromCheckpoint;

    @JsonProperty("resumed_from_index")
    private final int resumedFromIndex;

    @JsonProperty("new_decision")
    private final String newDecision;

    @JsonProperty("decision_source")
    private final String decisionSource;

    @JsonProperty("resume_count")
    private final int resumeCount;

    @JsonProperty("message")
    private final String message;

    @JsonCreator
    public ResumeFromCheckpointResponse(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("resumed_from_checkpoint") String resumedFromCheckpoint,
        @JsonProperty("resumed_from_index") int resumedFromIndex,
        @JsonProperty("new_decision") String newDecision,
        @JsonProperty("decision_source") String decisionSource,
        @JsonProperty("resume_count") int resumeCount,
        @JsonProperty("message") String message) {
      this.workflowId = workflowId;
      this.resumedFromCheckpoint = resumedFromCheckpoint;
      this.resumedFromIndex = resumedFromIndex;
      this.newDecision = newDecision;
      this.decisionSource = decisionSource;
      this.resumeCount = resumeCount;
      this.message = message;
    }

    public String getWorkflowId() { return workflowId; }
    public String getResumedFromCheckpoint() { return resumedFromCheckpoint; }
    public int getResumedFromIndex() { return resumedFromIndex; }
    public String getNewDecision() { return newDecision; }
    public String getDecisionSource() { return decisionSource; }
    public int getResumeCount() { return resumeCount; }
    public String getMessage() { return message; }
  }

  /** Information about a workflow step. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class WorkflowStepInfo {

    @JsonProperty("step_id")
    private final String stepId;

    @JsonProperty("step_index")
    private final int stepIndex;

    @JsonProperty("step_name")
    private final String stepName;

    @JsonProperty("step_type")
    private final StepType stepType;

    @JsonProperty("decision")
    private final GateDecision decision;

    @JsonProperty("decision_reason")
    private final String decisionReason;

    @JsonProperty("approval_status")
    private final ApprovalStatus approvalStatus;

    @JsonProperty("approved_by")
    private final String approvedBy;

    @JsonProperty("gate_checked_at")
    private final Instant gateCheckedAt;

    @JsonProperty("completed_at")
    private final Instant completedAt;

    @JsonCreator
    public WorkflowStepInfo(
        @JsonProperty("step_id") String stepId,
        @JsonProperty("step_index") int stepIndex,
        @JsonProperty("step_name") String stepName,
        @JsonProperty("step_type") StepType stepType,
        @JsonProperty("decision") GateDecision decision,
        @JsonProperty("decision_reason") String decisionReason,
        @JsonProperty("approval_status") ApprovalStatus approvalStatus,
        @JsonProperty("approved_by") String approvedBy,
        @JsonProperty("gate_checked_at") Instant gateCheckedAt,
        @JsonProperty("completed_at") Instant completedAt) {
      this.stepId = stepId;
      this.stepIndex = stepIndex;
      this.stepName = stepName;
      this.stepType = stepType;
      this.decision = decision;
      this.decisionReason = decisionReason;
      this.approvalStatus = approvalStatus;
      this.approvedBy = approvedBy;
      this.gateCheckedAt = gateCheckedAt;
      this.completedAt = completedAt;
    }

    public String getStepId() {
      return stepId;
    }

    public int getStepIndex() {
      return stepIndex;
    }

    public String getStepName() {
      return stepName;
    }

    public StepType getStepType() {
      return stepType;
    }

    public GateDecision getDecision() {
      return decision;
    }

    public String getDecisionReason() {
      return decisionReason;
    }

    public ApprovalStatus getApprovalStatus() {
      return approvalStatus;
    }

    public String getApprovedBy() {
      return approvedBy;
    }

    public Instant getGateCheckedAt() {
      return gateCheckedAt;
    }

    public Instant getCompletedAt() {
      return completedAt;
    }
  }

  /** Response containing workflow status. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class WorkflowStatusResponse {

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("workflow_name")
    private final String workflowName;

    @JsonProperty("source")
    private final WorkflowSource source;

    @JsonProperty("status")
    private final WorkflowStatus status;

    @JsonProperty("current_step_index")
    private final int currentStepIndex;

    @JsonProperty("total_steps")
    private final Integer totalSteps;

    @JsonProperty("started_at")
    private final Instant startedAt;

    @JsonProperty("completed_at")
    private final Instant completedAt;

    @JsonProperty("steps")
    private final List<WorkflowStepInfo> steps;

    @JsonProperty("trace_id")
    private final String traceId;

    /** Backward-compatible constructor without traceId. */
    public WorkflowStatusResponse(
        String workflowId,
        String workflowName,
        WorkflowSource source,
        WorkflowStatus status,
        int currentStepIndex,
        Integer totalSteps,
        Instant startedAt,
        Instant completedAt,
        List<WorkflowStepInfo> steps) {
      this(
          workflowId,
          workflowName,
          source,
          status,
          currentStepIndex,
          totalSteps,
          startedAt,
          completedAt,
          steps,
          null);
    }

    @JsonCreator
    public WorkflowStatusResponse(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("workflow_name") String workflowName,
        @JsonProperty("source") WorkflowSource source,
        @JsonProperty("status") WorkflowStatus status,
        @JsonProperty("current_step_index") int currentStepIndex,
        @JsonProperty("total_steps") Integer totalSteps,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt,
        @JsonProperty("steps") List<WorkflowStepInfo> steps,
        @JsonProperty("trace_id") String traceId) {
      this.workflowId = workflowId;
      this.workflowName = workflowName;
      this.source = source;
      this.status = status;
      this.currentStepIndex = currentStepIndex;
      this.totalSteps = totalSteps;
      this.startedAt = startedAt;
      this.completedAt = completedAt;
      this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
      this.traceId = traceId;
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public String getWorkflowName() {
      return workflowName;
    }

    public WorkflowSource getSource() {
      return source;
    }

    public WorkflowStatus getStatus() {
      return status;
    }

    public int getCurrentStepIndex() {
      return currentStepIndex;
    }

    public Integer getTotalSteps() {
      return totalSteps;
    }

    public Instant getStartedAt() {
      return startedAt;
    }

    public Instant getCompletedAt() {
      return completedAt;
    }

    public List<WorkflowStepInfo> getSteps() {
      return steps;
    }

    public String getTraceId() {
      return traceId;
    }

    public boolean isTerminal() {
      return status == WorkflowStatus.COMPLETED
          || status == WorkflowStatus.ABORTED
          || status == WorkflowStatus.FAILED;
    }
  }

  /** Options for listing workflows. */
  public static final class ListWorkflowsOptions {

    private final WorkflowStatus status;
    private final WorkflowSource source;
    private final int limit;
    private final int offset;
    private final String traceId;

    /** Backward-compatible constructor without traceId. */
    public ListWorkflowsOptions(
        WorkflowStatus status, WorkflowSource source, int limit, int offset) {
      this(status, source, limit, offset, null);
    }

    public ListWorkflowsOptions(
        WorkflowStatus status, WorkflowSource source, int limit, int offset, String traceId) {
      this.status = status;
      this.source = source;
      this.limit = limit > 0 ? limit : 50;
      this.offset = Math.max(offset, 0);
      this.traceId = traceId;
    }

    public WorkflowStatus getStatus() {
      return status;
    }

    public WorkflowSource getSource() {
      return source;
    }

    public int getLimit() {
      return limit;
    }

    public int getOffset() {
      return offset;
    }

    public String getTraceId() {
      return traceId;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      private WorkflowStatus status;
      private WorkflowSource source;
      private int limit = 50;
      private int offset = 0;
      private String traceId;

      public Builder status(WorkflowStatus status) {
        this.status = status;
        return this;
      }

      public Builder source(WorkflowSource source) {
        this.source = source;
        return this;
      }

      public Builder limit(int limit) {
        this.limit = limit;
        return this;
      }

      public Builder offset(int offset) {
        this.offset = offset;
        return this;
      }

      public Builder traceId(String traceId) {
        this.traceId = traceId;
        return this;
      }

      public ListWorkflowsOptions build() {
        return new ListWorkflowsOptions(status, source, limit, offset, traceId);
      }
    }
  }

  /** Response from listing workflows. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ListWorkflowsResponse {

    @JsonProperty("workflows")
    private final List<WorkflowStatusResponse> workflows;

    @JsonProperty("total")
    private final int total;

    @JsonCreator
    public ListWorkflowsResponse(
        @JsonProperty("workflows") List<WorkflowStatusResponse> workflows,
        @JsonProperty("total") int total) {
      this.workflows =
          workflows != null ? Collections.unmodifiableList(workflows) : Collections.emptyList();
      this.total = total;
    }

    public List<WorkflowStatusResponse> getWorkflows() {
      return workflows;
    }

    public int getTotal() {
      return total;
    }
  }

  /** Request to mark a step as completed. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static final class MarkStepCompletedRequest {

    @JsonProperty("output")
    private final Map<String, Object> output;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("tokens_in")
    private final Integer tokensIn;

    @JsonProperty("tokens_out")
    private final Integer tokensOut;

    @JsonProperty("cost_usd")
    private final Double costUsd;

    @JsonCreator
    public MarkStepCompletedRequest(
        @JsonProperty("output") Map<String, Object> output,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("tokens_in") Integer tokensIn,
        @JsonProperty("tokens_out") Integer tokensOut,
        @JsonProperty("cost_usd") Double costUsd) {
      this.output = output != null ? Collections.unmodifiableMap(output) : Collections.emptyMap();
      this.metadata =
          metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
      this.tokensIn = tokensIn;
      this.tokensOut = tokensOut;
      this.costUsd = costUsd;
    }

    public Map<String, Object> getOutput() {
      return output;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }

    /**
     * Returns the number of input tokens consumed by the step.
     *
     * @return input token count, or null if not provided
     * @since 3.6.0
     */
    public Integer getTokensIn() {
      return tokensIn;
    }

    /**
     * Returns the number of output tokens produced by the step.
     *
     * @return output token count, or null if not provided
     * @since 3.6.0
     */
    public Integer getTokensOut() {
      return tokensOut;
    }

    /**
     * Returns the cost in USD incurred by the step.
     *
     * @return cost in USD, or null if not provided
     * @since 3.6.0
     */
    public Double getCostUsd() {
      return costUsd;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      private Map<String, Object> output;
      private Map<String, Object> metadata;
      private Integer tokensIn;
      private Integer tokensOut;
      private Double costUsd;

      public Builder output(Map<String, Object> output) {
        this.output = output;
        return this;
      }

      public Builder metadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
      }

      public Builder tokensIn(Integer tokensIn) {
        this.tokensIn = tokensIn;
        return this;
      }

      public Builder tokensOut(Integer tokensOut) {
        this.tokensOut = tokensOut;
        return this;
      }

      public Builder costUsd(Double costUsd) {
        this.costUsd = costUsd;
        return this;
      }

      public MarkStepCompletedRequest build() {
        return new MarkStepCompletedRequest(output, metadata, tokensIn, tokensOut, costUsd);
      }
    }
  }

  /** Request to abort a workflow. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class AbortWorkflowRequest {

    @JsonProperty("reason")
    private final String reason;

    @JsonCreator
    public AbortWorkflowRequest(@JsonProperty("reason") String reason) {
      this.reason = reason;
    }

    public String getReason() {
      return reason;
    }

    public static AbortWorkflowRequest withReason(String reason) {
      return new AbortWorkflowRequest(reason);
    }
  }

  // ========================================================================
  // WCP Approval Types
  // ========================================================================

  /** Response from approving a workflow step. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ApproveStepResponse {

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("step_id")
    private final String stepId;

    @JsonProperty("status")
    private final String status;

    @JsonCreator
    public ApproveStepResponse(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("step_id") String stepId,
        @JsonProperty("status") String status) {
      this.workflowId = workflowId;
      this.stepId = stepId;
      this.status = status;
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public String getStepId() {
      return stepId;
    }

    public String getStatus() {
      return status;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ApproveStepResponse that = (ApproveStepResponse) o;
      return Objects.equals(workflowId, that.workflowId)
          && Objects.equals(stepId, that.stepId)
          && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
      return Objects.hash(workflowId, stepId, status);
    }

    @Override
    public String toString() {
      return "ApproveStepResponse{"
          + "workflowId='"
          + workflowId
          + '\''
          + ", stepId='"
          + stepId
          + '\''
          + ", status='"
          + status
          + '\''
          + '}';
    }
  }

  /** Response from rejecting a workflow step. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class RejectStepResponse {

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("step_id")
    private final String stepId;

    @JsonProperty("status")
    private final String status;

    @JsonCreator
    public RejectStepResponse(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("step_id") String stepId,
        @JsonProperty("status") String status) {
      this.workflowId = workflowId;
      this.stepId = stepId;
      this.status = status;
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public String getStepId() {
      return stepId;
    }

    public String getStatus() {
      return status;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RejectStepResponse that = (RejectStepResponse) o;
      return Objects.equals(workflowId, that.workflowId)
          && Objects.equals(stepId, that.stepId)
          && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
      return Objects.hash(workflowId, stepId, status);
    }

    @Override
    public String toString() {
      return "RejectStepResponse{"
          + "workflowId='"
          + workflowId
          + '\''
          + ", stepId='"
          + stepId
          + '\''
          + ", status='"
          + status
          + '\''
          + '}';
    }
  }

  /** A pending approval for a workflow step. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PendingApproval {

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("workflow_name")
    private final String workflowName;

    @JsonProperty("step_id")
    private final String stepId;

    @JsonProperty("step_name")
    private final String stepName;

    @JsonProperty("step_type")
    private final String stepType;

    @JsonProperty("created_at")
    private final String createdAt;

    @JsonCreator
    public PendingApproval(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("workflow_name") String workflowName,
        @JsonProperty("step_id") String stepId,
        @JsonProperty("step_name") String stepName,
        @JsonProperty("step_type") String stepType,
        @JsonProperty("created_at") String createdAt) {
      this.workflowId = workflowId;
      this.workflowName = workflowName;
      this.stepId = stepId;
      this.stepName = stepName;
      this.stepType = stepType;
      this.createdAt = createdAt;
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public String getWorkflowName() {
      return workflowName;
    }

    public String getStepId() {
      return stepId;
    }

    public String getStepName() {
      return stepName;
    }

    public String getStepType() {
      return stepType;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PendingApproval that = (PendingApproval) o;
      return Objects.equals(workflowId, that.workflowId)
          && Objects.equals(workflowName, that.workflowName)
          && Objects.equals(stepId, that.stepId)
          && Objects.equals(stepName, that.stepName)
          && Objects.equals(stepType, that.stepType)
          && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
      return Objects.hash(workflowId, workflowName, stepId, stepName, stepType, createdAt);
    }

    @Override
    public String toString() {
      return "PendingApproval{"
          + "workflowId='"
          + workflowId
          + '\''
          + ", workflowName='"
          + workflowName
          + '\''
          + ", stepId='"
          + stepId
          + '\''
          + ", stepName='"
          + stepName
          + '\''
          + ", stepType='"
          + stepType
          + '\''
          + ", createdAt='"
          + createdAt
          + '\''
          + '}';
    }
  }

  /** Response containing a list of pending approvals. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PendingApprovalsResponse {

    @JsonProperty("approvals")
    private final List<PendingApproval> approvals;

    @JsonProperty("total")
    private final int total;

    @JsonCreator
    public PendingApprovalsResponse(
        @JsonProperty("approvals") List<PendingApproval> approvals,
        @JsonProperty("total") int total) {
      this.approvals =
          approvals != null ? Collections.unmodifiableList(approvals) : Collections.emptyList();
      this.total = total;
    }

    public List<PendingApproval> getApprovals() {
      return approvals;
    }

    public int getTotal() {
      return total;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PendingApprovalsResponse that = (PendingApprovalsResponse) o;
      return total == that.total && Objects.equals(approvals, that.approvals);
    }

    @Override
    public int hashCode() {
      return Objects.hash(approvals, total);
    }

    @Override
    public String toString() {
      return "PendingApprovalsResponse{" + "approvals=" + approvals + ", total=" + total + '}';
    }
  }
}
