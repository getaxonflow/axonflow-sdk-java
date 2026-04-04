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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response from executing a plan in Multi-Agent Planning (MAP).
 *
 * <p>Contains the execution result, policy evaluation information, and metadata about the plan
 * execution.
 *
 * @since 2.3.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlanExecutionResponse {

  @JsonProperty("plan_id")
  private final String planId;

  @JsonProperty("status")
  private final String status;

  @JsonProperty("result")
  private final String result;

  @JsonProperty("steps_completed")
  private final int stepsCompleted;

  @JsonProperty("total_steps")
  private final int totalSteps;

  @JsonProperty("started_at")
  private final Instant startedAt;

  @JsonProperty("completed_at")
  private final Instant completedAt;

  @JsonProperty("step_results")
  private final List<StepResult> stepResults;

  @JsonProperty("policy_info")
  private final PolicyEvaluationResult policyInfo;

  @JsonProperty("metadata")
  private final Map<String, Object> metadata;

  @JsonCreator
  public PlanExecutionResponse(
      @JsonProperty("plan_id") String planId,
      @JsonProperty("status") String status,
      @JsonProperty("result") String result,
      @JsonProperty("steps_completed") int stepsCompleted,
      @JsonProperty("total_steps") int totalSteps,
      @JsonProperty("started_at") Instant startedAt,
      @JsonProperty("completed_at") Instant completedAt,
      @JsonProperty("step_results") List<StepResult> stepResults,
      @JsonProperty("policy_info") PolicyEvaluationResult policyInfo,
      @JsonProperty("metadata") Map<String, Object> metadata) {
    this.planId = planId;
    this.status = status;
    this.result = result;
    this.stepsCompleted = stepsCompleted;
    this.totalSteps = totalSteps;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.stepResults =
        stepResults != null ? Collections.unmodifiableList(stepResults) : Collections.emptyList();
    this.policyInfo = policyInfo;
    this.metadata =
        metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
  }

  /**
   * Returns the unique identifier of the executed plan.
   *
   * @return the plan ID
   */
  public String getPlanId() {
    return planId;
  }

  /**
   * Returns the current execution status.
   *
   * <p>Possible values: "pending", "in_progress", "completed", "failed", "blocked".
   *
   * @return the execution status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Returns the execution result or error message.
   *
   * @return the result string, or null if not yet completed
   */
  public String getResult() {
    return result;
  }

  /**
   * Returns the number of steps that have been completed.
   *
   * @return the count of completed steps
   */
  public int getStepsCompleted() {
    return stepsCompleted;
  }

  /**
   * Returns the total number of steps in the plan.
   *
   * @return the total step count
   */
  public int getTotalSteps() {
    return totalSteps;
  }

  /**
   * Returns when the plan execution started.
   *
   * @return the start timestamp, or null if not yet started
   */
  public Instant getStartedAt() {
    return startedAt;
  }

  /**
   * Returns when the plan execution completed.
   *
   * @return the completion timestamp, or null if not yet completed
   */
  public Instant getCompletedAt() {
    return completedAt;
  }

  /**
   * Returns the results of individual steps.
   *
   * @return immutable list of step results
   */
  public List<StepResult> getStepResults() {
    return stepResults;
  }

  /**
   * Returns the policy evaluation information for this execution.
   *
   * <p>Contains details about which policies were applied, the risk score, and any required
   * actions.
   *
   * @return the policy evaluation result, or null if no policy evaluation was performed
   * @since 2.3.0
   */
  public PolicyEvaluationResult getPolicyInfo() {
    return policyInfo;
  }

  /**
   * Returns additional metadata about the execution.
   *
   * @return immutable map of metadata
   */
  public Map<String, Object> getMetadata() {
    return metadata;
  }

  /**
   * Checks if the plan execution completed successfully.
   *
   * @return true if status is "completed"
   */
  public boolean isCompleted() {
    return "completed".equalsIgnoreCase(status);
  }

  /**
   * Checks if the plan execution failed.
   *
   * @return true if status is "failed"
   */
  public boolean isFailed() {
    return "failed".equalsIgnoreCase(status);
  }

  /**
   * Checks if the plan execution was blocked by policy.
   *
   * @return true if status is "blocked"
   */
  public boolean isBlocked() {
    return "blocked".equalsIgnoreCase(status);
  }

  /**
   * Checks if the plan execution is still in progress.
   *
   * @return true if status is "in_progress" or "pending"
   */
  public boolean isInProgress() {
    return "in_progress".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status);
  }

  /**
   * Calculates the progress percentage.
   *
   * @return progress as a value between 0.0 and 1.0
   */
  public double getProgress() {
    if (totalSteps == 0) {
      return 0.0;
    }
    return (double) stepsCompleted / totalSteps;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlanExecutionResponse that = (PlanExecutionResponse) o;
    return stepsCompleted == that.stepsCompleted
        && totalSteps == that.totalSteps
        && Objects.equals(planId, that.planId)
        && Objects.equals(status, that.status)
        && Objects.equals(result, that.result)
        && Objects.equals(startedAt, that.startedAt)
        && Objects.equals(completedAt, that.completedAt)
        && Objects.equals(stepResults, that.stepResults)
        && Objects.equals(policyInfo, that.policyInfo)
        && Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        planId,
        status,
        result,
        stepsCompleted,
        totalSteps,
        startedAt,
        completedAt,
        stepResults,
        policyInfo,
        metadata);
  }

  @Override
  public String toString() {
    return "PlanExecutionResponse{"
        + "planId='"
        + planId
        + '\''
        + ", status='"
        + status
        + '\''
        + ", stepsCompleted="
        + stepsCompleted
        + ", totalSteps="
        + totalSteps
        + ", policyInfo="
        + policyInfo
        + '}';
  }

  /** Result of an individual step execution. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class StepResult {

    @JsonProperty("step_index")
    private final int stepIndex;

    @JsonProperty("step_name")
    private final String stepName;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("output")
    private final String output;

    @JsonProperty("error")
    private final String error;

    @JsonProperty("duration_ms")
    private final long durationMs;

    @JsonCreator
    public StepResult(
        @JsonProperty("step_index") int stepIndex,
        @JsonProperty("step_name") String stepName,
        @JsonProperty("status") String status,
        @JsonProperty("output") String output,
        @JsonProperty("error") String error,
        @JsonProperty("duration_ms") long durationMs) {
      this.stepIndex = stepIndex;
      this.stepName = stepName;
      this.status = status;
      this.output = output;
      this.error = error;
      this.durationMs = durationMs;
    }

    public int getStepIndex() {
      return stepIndex;
    }

    public String getStepName() {
      return stepName;
    }

    public String getStatus() {
      return status;
    }

    public String getOutput() {
      return output;
    }

    public String getError() {
      return error;
    }

    public long getDurationMs() {
      return durationMs;
    }

    public boolean isSuccess() {
      return "completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StepResult that = (StepResult) o;
      return stepIndex == that.stepIndex
          && durationMs == that.durationMs
          && Objects.equals(stepName, that.stepName)
          && Objects.equals(status, that.status)
          && Objects.equals(output, that.output)
          && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      return Objects.hash(stepIndex, stepName, status, output, error, durationMs);
    }

    @Override
    public String toString() {
      return "StepResult{"
          + "stepIndex="
          + stepIndex
          + ", stepName='"
          + stepName
          + '\''
          + ", status='"
          + status
          + '\''
          + '}';
    }
  }
}
