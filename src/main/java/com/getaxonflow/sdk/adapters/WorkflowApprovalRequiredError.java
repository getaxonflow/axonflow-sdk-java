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
package com.getaxonflow.sdk.adapters;

/**
 * Raised when a workflow step requires human approval before proceeding.
 *
 * <p>This exception is thrown by {@link LangGraphAdapter#checkGate} when the
 * gate decision is {@code REQUIRE_APPROVAL}. The caller should use
 * {@link LangGraphAdapter#waitForApproval} to poll for approval.
 */
public class WorkflowApprovalRequiredError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String stepId;
    private final String approvalUrl;
    private final String reason;

    /**
     * Creates a new WorkflowApprovalRequiredError.
     *
     * @param message     the error message
     * @param stepId      the step ID that requires approval
     * @param approvalUrl the URL where approval can be granted
     * @param reason      the reason approval is required
     */
    public WorkflowApprovalRequiredError(String message, String stepId, String approvalUrl, String reason) {
        super(message);
        this.stepId = stepId;
        this.approvalUrl = approvalUrl;
        this.reason = reason;
    }

    /**
     * Returns the step ID that requires approval.
     *
     * @return the step ID
     */
    public String getStepId() {
        return stepId;
    }

    /**
     * Returns the URL where approval can be granted.
     *
     * @return the approval URL
     */
    public String getApprovalUrl() {
        return approvalUrl;
    }

    /**
     * Returns the reason approval is required.
     *
     * @return the reason
     */
    public String getReason() {
        return reason;
    }
}
