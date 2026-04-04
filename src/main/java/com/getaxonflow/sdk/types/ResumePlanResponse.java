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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Response from resuming a paused multi-agent plan. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ResumePlanResponse {

  @JsonProperty("plan_id")
  private final String planId;

  @JsonProperty("workflow_id")
  private final String workflowId;

  @JsonProperty("status")
  private final String status;

  @JsonProperty("approved")
  private final Boolean approved;

  @JsonProperty("message")
  private final String message;

  @JsonProperty("next_step")
  private final Integer nextStep;

  @JsonProperty("next_step_name")
  private final String nextStepName;

  @JsonProperty("total_steps")
  private final Integer totalSteps;

  public ResumePlanResponse(
      @JsonProperty("plan_id") String planId,
      @JsonProperty("workflow_id") String workflowId,
      @JsonProperty("status") String status,
      @JsonProperty("approved") Boolean approved,
      @JsonProperty("message") String message,
      @JsonProperty("next_step") Integer nextStep,
      @JsonProperty("next_step_name") String nextStepName,
      @JsonProperty("total_steps") Integer totalSteps) {
    this.planId = planId;
    this.workflowId = workflowId;
    this.status = status;
    this.approved = approved;
    this.message = message;
    this.nextStep = nextStep;
    this.nextStepName = nextStepName;
    this.totalSteps = totalSteps;
  }

  /**
   * Returns the ID of the resumed plan.
   *
   * @return the plan ID
   */
  public String getPlanId() {
    return planId;
  }

  /**
   * Returns the status after resuming.
   *
   * @return the status (e.g., "in_progress", "rejected")
   */
  public String getStatus() {
    return status;
  }

  /**
   * Returns the WCP workflow ID.
   *
   * @return the workflow ID, or null
   */
  public String getWorkflowId() {
    return workflowId;
  }

  /**
   * Returns whether the plan was approved to continue.
   *
   * @return true if the plan was approved, false if not approved or not applicable
   */
  public boolean isApproved() {
    return Boolean.TRUE.equals(approved);
  }

  /**
   * Returns a human-readable message about the resume action.
   *
   * @return the resume message, or null
   */
  public String getMessage() {
    return message;
  }

  /**
   * Returns the next step index to be executed.
   *
   * @return the next step index, or null if completed
   */
  public Integer getNextStep() {
    return nextStep;
  }

  /**
   * Returns the name of the next step.
   *
   * @return the next step name, or null if completed
   */
  public String getNextStepName() {
    return nextStepName;
  }

  /**
   * Returns the total number of steps.
   *
   * @return total steps, or null
   */
  public Integer getTotalSteps() {
    return totalSteps;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResumePlanResponse that = (ResumePlanResponse) o;
    return Objects.equals(approved, that.approved)
        && Objects.equals(planId, that.planId)
        && Objects.equals(workflowId, that.workflowId)
        && Objects.equals(status, that.status)
        && Objects.equals(message, that.message)
        && Objects.equals(nextStep, that.nextStep)
        && Objects.equals(nextStepName, that.nextStepName)
        && Objects.equals(totalSteps, that.totalSteps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        planId, workflowId, status, approved, message, nextStep, nextStepName, totalSteps);
  }

  @Override
  public String toString() {
    return "ResumePlanResponse{"
        + "planId='"
        + planId
        + '\''
        + ", workflowId='"
        + workflowId
        + '\''
        + ", status='"
        + status
        + '\''
        + ", approved="
        + approved
        + ", message='"
        + message
        + '\''
        + ", nextStep="
        + nextStep
        + '}';
  }
}
