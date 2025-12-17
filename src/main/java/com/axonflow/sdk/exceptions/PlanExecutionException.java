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
package com.axonflow.sdk.exceptions;

/**
 * Thrown when plan generation or execution fails.
 */
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
     * @param message    the error message
     * @param planId     the plan that failed
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
     * @param message    the error message
     * @param planId     the plan that failed
     * @param failedStep the step that failed
     * @param cause      the underlying cause
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
