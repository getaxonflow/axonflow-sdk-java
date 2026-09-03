// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.executionreplay;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Execution Replay API types for debugging and compliance.
 *
 * <p>The Execution Replay API captures every step of workflow execution for debugging, auditing,
 * and compliance purposes.
 */
public final class ExecutionReplayTypes {

  private ExecutionReplayTypes() {}

  /** Execution summary representing a workflow execution. */
  public static final class ExecutionSummary {
    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("total_steps")
    private int totalSteps;

    @JsonProperty("completed_steps")
    private int completedSteps;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("completed_at")
    private String completedAt;

    @JsonProperty("duration_ms")
    private Integer durationMs;

    @JsonProperty("total_tokens")
    private int totalTokens;

    @JsonProperty("total_cost_usd")
    private double totalCostUsd;

    @JsonProperty("org_id")
    private String orgId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("input_summary")
    private Object inputSummary;

    @JsonProperty("output_summary")
    private Object outputSummary;

    public ExecutionSummary() {}

    public String getRequestId() {
      return requestId;
    }

    public void setRequestId(String requestId) {
      this.requestId = requestId;
    }

    public String getWorkflowName() {
      return workflowName;
    }

    public void setWorkflowName(String workflowName) {
      this.workflowName = workflowName;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public int getTotalSteps() {
      return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
      this.totalSteps = totalSteps;
    }

    public int getCompletedSteps() {
      return completedSteps;
    }

    public void setCompletedSteps(int completedSteps) {
      this.completedSteps = completedSteps;
    }

    public String getStartedAt() {
      return startedAt;
    }

    public void setStartedAt(String startedAt) {
      this.startedAt = startedAt;
    }

    public String getCompletedAt() {
      return completedAt;
    }

    public void setCompletedAt(String completedAt) {
      this.completedAt = completedAt;
    }

    public Integer getDurationMs() {
      return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
      this.durationMs = durationMs;
    }

    public int getTotalTokens() {
      return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
      this.totalTokens = totalTokens;
    }

    public double getTotalCostUsd() {
      return totalCostUsd;
    }

    public void setTotalCostUsd(double totalCostUsd) {
      this.totalCostUsd = totalCostUsd;
    }

    public String getOrgId() {
      return orgId;
    }

    public void setOrgId(String orgId) {
      this.orgId = orgId;
    }

    public String getTenantId() {
      return tenantId;
    }

    public void setTenantId(String tenantId) {
      this.tenantId = tenantId;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public Object getInputSummary() {
      return inputSummary;
    }

    public void setInputSummary(Object inputSummary) {
      this.inputSummary = inputSummary;
    }

    public Object getOutputSummary() {
      return outputSummary;
    }

    public void setOutputSummary(Object outputSummary) {
      this.outputSummary = outputSummary;
    }
  }

  /** Execution snapshot representing a step in a workflow execution. */
  public static final class ExecutionSnapshot {
    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("step_index")
    private int stepIndex;

    @JsonProperty("step_name")
    private String stepName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("completed_at")
    private String completedAt;

    @JsonProperty("duration_ms")
    private Integer durationMs;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("model")
    private String model;

    @JsonProperty("tokens_in")
    private int tokensIn;

    @JsonProperty("tokens_out")
    private int tokensOut;

    @JsonProperty("cost_usd")
    private double costUsd;

    @JsonProperty("input")
    private Object input;

    @JsonProperty("output")
    private Object output;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("policies_checked")
    private List<String> policiesChecked;

    @JsonProperty("policies_triggered")
    private List<String> policiesTriggered;

    @JsonProperty("approval_required")
    private boolean approvalRequired;

    @JsonProperty("approved_by")
    private String approvedBy;

    @JsonProperty("approved_at")
    private String approvedAt;

    public ExecutionSnapshot() {}

    public String getRequestId() {
      return requestId;
    }

    public void setRequestId(String requestId) {
      this.requestId = requestId;
    }

    public int getStepIndex() {
      return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
      this.stepIndex = stepIndex;
    }

    public String getStepName() {
      return stepName;
    }

    public void setStepName(String stepName) {
      this.stepName = stepName;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getStartedAt() {
      return startedAt;
    }

    public void setStartedAt(String startedAt) {
      this.startedAt = startedAt;
    }

    public String getCompletedAt() {
      return completedAt;
    }

    public void setCompletedAt(String completedAt) {
      this.completedAt = completedAt;
    }

    public Integer getDurationMs() {
      return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
      this.durationMs = durationMs;
    }

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public int getTokensIn() {
      return tokensIn;
    }

    public void setTokensIn(int tokensIn) {
      this.tokensIn = tokensIn;
    }

    public int getTokensOut() {
      return tokensOut;
    }

    public void setTokensOut(int tokensOut) {
      this.tokensOut = tokensOut;
    }

    public double getCostUsd() {
      return costUsd;
    }

    public void setCostUsd(double costUsd) {
      this.costUsd = costUsd;
    }

    public Object getInput() {
      return input;
    }

    public void setInput(Object input) {
      this.input = input;
    }

    public Object getOutput() {
      return output;
    }

    public void setOutput(Object output) {
      this.output = output;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public List<String> getPoliciesChecked() {
      return policiesChecked;
    }

    public void setPoliciesChecked(List<String> policiesChecked) {
      this.policiesChecked = policiesChecked;
    }

    public List<String> getPoliciesTriggered() {
      return policiesTriggered;
    }

    public void setPoliciesTriggered(List<String> policiesTriggered) {
      this.policiesTriggered = policiesTriggered;
    }

    public boolean isApprovalRequired() {
      return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
      this.approvalRequired = approvalRequired;
    }

    public String getApprovedBy() {
      return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
      this.approvedBy = approvedBy;
    }

    public String getApprovedAt() {
      return approvedAt;
    }

    public void setApprovedAt(String approvedAt) {
      this.approvedAt = approvedAt;
    }
  }

  /** Timeline entry for execution visualization. */
  public static final class TimelineEntry {
    @JsonProperty("step_index")
    private int stepIndex;

    @JsonProperty("step_name")
    private String stepName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("completed_at")
    private String completedAt;

    @JsonProperty("duration_ms")
    private Integer durationMs;

    @JsonProperty("has_error")
    private boolean hasError;

    @JsonProperty("has_approval")
    private boolean hasApproval;

    public TimelineEntry() {}

    public int getStepIndex() {
      return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
      this.stepIndex = stepIndex;
    }

    public String getStepName() {
      return stepName;
    }

    public void setStepName(String stepName) {
      this.stepName = stepName;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getStartedAt() {
      return startedAt;
    }

    public void setStartedAt(String startedAt) {
      this.startedAt = startedAt;
    }

    public String getCompletedAt() {
      return completedAt;
    }

    public void setCompletedAt(String completedAt) {
      this.completedAt = completedAt;
    }

    public Integer getDurationMs() {
      return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
      this.durationMs = durationMs;
    }

    public boolean hasError() {
      return hasError;
    }

    public void setHasError(boolean hasError) {
      this.hasError = hasError;
    }

    public boolean hasApproval() {
      return hasApproval;
    }

    public void setHasApproval(boolean hasApproval) {
      this.hasApproval = hasApproval;
    }
  }

  /** Response from list executions API. */
  public static final class ListExecutionsResponse {
    @JsonProperty("executions")
    private List<ExecutionSummary> executions;

    @JsonProperty("total")
    private int total;

    @JsonProperty("limit")
    private int limit;

    @JsonProperty("offset")
    private int offset;

    public ListExecutionsResponse() {}

    public List<ExecutionSummary> getExecutions() {
      return executions;
    }

    public void setExecutions(List<ExecutionSummary> executions) {
      this.executions = executions;
    }

    public int getTotal() {
      return total;
    }

    public void setTotal(int total) {
      this.total = total;
    }

    public int getLimit() {
      return limit;
    }

    public void setLimit(int limit) {
      this.limit = limit;
    }

    public int getOffset() {
      return offset;
    }

    public void setOffset(int offset) {
      this.offset = offset;
    }
  }

  /** Full execution with summary and steps. */
  public static final class ExecutionDetail {
    @JsonProperty("summary")
    private ExecutionSummary summary;

    @JsonProperty("steps")
    private List<ExecutionSnapshot> steps;

    public ExecutionDetail() {}

    public ExecutionSummary getSummary() {
      return summary;
    }

    public void setSummary(ExecutionSummary summary) {
      this.summary = summary;
    }

    public List<ExecutionSnapshot> getSteps() {
      return steps;
    }

    public void setSteps(List<ExecutionSnapshot> steps) {
      this.steps = steps;
    }
  }

  /** Options for listing executions. */
  public static final class ListExecutionsOptions {
    private Integer limit;
    private Integer offset;
    private String status;
    private String workflowId;
    private String startTime;
    private String endTime;

    public ListExecutionsOptions() {}

    public Integer getLimit() {
      return limit;
    }

    public ListExecutionsOptions setLimit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public Integer getOffset() {
      return offset;
    }

    public ListExecutionsOptions setOffset(Integer offset) {
      this.offset = offset;
      return this;
    }

    public String getStatus() {
      return status;
    }

    public ListExecutionsOptions setStatus(String status) {
      this.status = status;
      return this;
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public ListExecutionsOptions setWorkflowId(String workflowId) {
      this.workflowId = workflowId;
      return this;
    }

    public String getStartTime() {
      return startTime;
    }

    public ListExecutionsOptions setStartTime(String startTime) {
      this.startTime = startTime;
      return this;
    }

    public String getEndTime() {
      return endTime;
    }

    public ListExecutionsOptions setEndTime(String endTime) {
      this.endTime = endTime;
      return this;
    }

    public static ListExecutionsOptions builder() {
      return new ListExecutionsOptions();
    }
  }

  /** Options for exporting an execution. */
  public static final class ExecutionExportOptions {
    private String format = "json";
    private boolean includeInput = true;
    private boolean includeOutput = true;
    private boolean includePolicies = true;

    public ExecutionExportOptions() {}

    public String getFormat() {
      return format;
    }

    public ExecutionExportOptions setFormat(String format) {
      this.format = format;
      return this;
    }

    public boolean isIncludeInput() {
      return includeInput;
    }

    public ExecutionExportOptions setIncludeInput(boolean includeInput) {
      this.includeInput = includeInput;
      return this;
    }

    public boolean isIncludeOutput() {
      return includeOutput;
    }

    public ExecutionExportOptions setIncludeOutput(boolean includeOutput) {
      this.includeOutput = includeOutput;
      return this;
    }

    public boolean isIncludePolicies() {
      return includePolicies;
    }

    public ExecutionExportOptions setIncludePolicies(boolean includePolicies) {
      this.includePolicies = includePolicies;
      return this;
    }

    public static ExecutionExportOptions builder() {
      return new ExecutionExportOptions();
    }
  }
}
