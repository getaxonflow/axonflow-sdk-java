// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

/** Thrown when plan generation or execution fails. */
public class PlanExecutionException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final String planId;
  private final String failedStep;

  /**
   * Creates a new PlanExecutionException.
   *
   * @param message the error message
   */
  public PlanExecutionException(String message) {
    super(message, 0, "PLAN_EXECUTION_FAILED");
    this.planId = null;
    this.failedStep = null;
  }

  /**
   * Creates a new PlanExecutionException with plan details.
   *
   * @param message the error message
   * @param planId the plan that failed
   * @param failedStep the step that failed
   */
  public PlanExecutionException(String message, String planId, String failedStep) {
    super(message, 0, "PLAN_EXECUTION_FAILED");
    this.planId = planId;
    this.failedStep = failedStep;
  }

  /**
   * Creates a new PlanExecutionException with cause.
   *
   * @param message the error message
   * @param planId the plan that failed
   * @param failedStep the step that failed
   * @param cause the underlying cause
   */
  public PlanExecutionException(String message, String planId, String failedStep, Throwable cause) {
    super(message, 0, "PLAN_EXECUTION_FAILED", cause);
    this.planId = planId;
    this.failedStep = failedStep;
  }

  /**
   * Returns the plan ID that failed.
   *
   * @return the plan ID
   */
  public String getPlanId() {
    return planId;
  }

  /**
   * Returns the step that failed.
   *
   * @return the failed step ID
   */
  public String getFailedStep() {
    return failedStep;
  }
}
