// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

/**
 * Thrown when an {@code idempotency_key} on a step gate or complete call conflicts with the key
 * recorded on an earlier gate call for the same {@code (workflow_id, step_id)} (HTTP 409).
 *
 * <p>Maps to HTTP 409 with {@code error.code == "IDEMPOTENCY_KEY_MISMATCH"}.
 *
 * <p>{@link #getExpectedIdempotencyKey()} is the empty string {@code ""} when the gate call had no
 * key but complete did; conversely {@link #getReceivedIdempotencyKey()} is {@code ""} when complete
 * omitted a key that gate had set.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try {
 *     axonflow.markStepCompleted(workflowId, stepId,
 *         MarkStepCompletedRequest.builder().idempotencyKey("k2").build());
 * } catch (IdempotencyKeyMismatchException e) {
 *     System.out.println("expected=" + e.getExpectedIdempotencyKey()
 *         + " received=" + e.getReceivedIdempotencyKey());
 * }
 * }</pre>
 */
public class IdempotencyKeyMismatchException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final String workflowId;
  private final String stepId;
  private final String expectedIdempotencyKey;
  private final String receivedIdempotencyKey;

  /**
   * Creates a new IdempotencyKeyMismatchException.
   *
   * @param message the error message from the platform
   * @param workflowId the workflow ID where the mismatch occurred
   * @param stepId the step ID where the mismatch occurred
   * @param expectedIdempotencyKey the key recorded on the first gate call, or "" if none was set
   * @param receivedIdempotencyKey the key supplied on the current request, or "" if none was set
   */
  public IdempotencyKeyMismatchException(
      String message,
      String workflowId,
      String stepId,
      String expectedIdempotencyKey,
      String receivedIdempotencyKey) {
    super(message, 409, "IDEMPOTENCY_KEY_MISMATCH");
    this.workflowId = workflowId;
    this.stepId = stepId;
    this.expectedIdempotencyKey = expectedIdempotencyKey;
    this.receivedIdempotencyKey = receivedIdempotencyKey;
  }

  /** Returns the workflow ID where the mismatch occurred. */
  public String getWorkflowId() {
    return workflowId;
  }

  /** Returns the step ID where the mismatch occurred. */
  public String getStepId() {
    return stepId;
  }

  /**
   * Returns the key recorded on the first gate call for this (workflow, step), or the empty string
   * if gate was called without a key.
   */
  public String getExpectedIdempotencyKey() {
    return expectedIdempotencyKey;
  }

  /**
   * Returns the key supplied on the current request that triggered the mismatch, or the empty
   * string if the current request omitted a key.
   */
  public String getReceivedIdempotencyKey() {
    return receivedIdempotencyKey;
  }
}
