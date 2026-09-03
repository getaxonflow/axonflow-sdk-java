// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.adapters;

import java.util.Collections;
import java.util.List;

/**
 * Raised when a workflow step is blocked by policy.
 *
 * <p>This exception is thrown by {@link LangGraphAdapter#checkGate} when {@code autoBlock} is
 * {@code true} and the gate decision is {@code BLOCK}.
 */
public class WorkflowBlockedError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String stepId;
  private final String reason;
  private final List<String> policyIds;

  /**
   * Creates a new WorkflowBlockedError.
   *
   * @param message the error message
   * @param stepId the step ID that was blocked
   * @param reason the reason the step was blocked
   * @param policyIds the policy IDs that caused the block
   */
  public WorkflowBlockedError(
      String message, String stepId, String reason, List<String> policyIds) {
    super(message);
    this.stepId = stepId;
    this.reason = reason;
    this.policyIds =
        policyIds != null ? Collections.unmodifiableList(policyIds) : Collections.emptyList();
  }

  /**
   * Returns the step ID that was blocked.
   *
   * @return the step ID
   */
  public String getStepId() {
    return stepId;
  }

  /**
   * Returns the reason the step was blocked.
   *
   * @return the block reason
   */
  public String getReason() {
    return reason;
  }

  /**
   * Returns the policy IDs that caused the block.
   *
   * @return immutable list of policy IDs
   */
  public List<String> getPolicyIds() {
    return policyIds;
  }
}
