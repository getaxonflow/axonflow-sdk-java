// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen;

/**
 * The request never got an answer this surface could read: connection, timeout, credentials, or a
 * non-refusal error status.
 *
 * <p>An authentication failure arrives here rather than as a denial. Rendering a {@code 401} as
 * {@code decision: false} would make it indistinguishable from a policy denial in every caller
 * branch and every dashboard, and the operator looking for "why is everything being blocked" would
 * be reading the policy engine instead of the credentials.
 */
public final class AuthZENTransportException extends AuthZENEvaluationException {

  private static final long serialVersionUID = 1L;

  /**
   * @param message what went wrong
   * @param statusCode the HTTP status, or 0 when there was no response at all
   * @param cause the underlying failure, if any
   */
  public AuthZENTransportException(String message, int statusCode, Throwable cause) {
    super(message, statusCode, null, cause);
  }

  /**
   * Whether a retry could produce a different answer.
   *
   * <p>A {@code 5xx} or a {@code 429} may clear; a {@code 4xx} naming the caller's credentials will
   * not, and retrying it is a way of turning a configuration mistake into a rate-limit incident. A
   * failure with no status at all is a connection or a timeout, both of which are worth one more
   * try.
   *
   * @return true when a retry could change the outcome
   */
  @Override
  public boolean isRetryable() {
    int status = getStatusCode();
    if (status == 0) {
      return true;
    }
    return status >= 500 || status == 429;
  }
}
