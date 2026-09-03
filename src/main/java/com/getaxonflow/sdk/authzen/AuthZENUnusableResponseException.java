// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen;

/**
 * The server answered {@code 200} with a body this build will not act on.
 *
 * <p>A decision that cannot be read completely is not a decision. Acting on the half that parsed is
 * how an allow carrying a mandatory obligation reaches an enforcement point that never saw it.
 *
 * <p>Never retryable: a server that produced an unreadable body once will produce the same one
 * again, and the fix is on its side.
 */
public final class AuthZENUnusableResponseException extends AuthZENEvaluationException {

  private static final long serialVersionUID = 1L;

  /**
   * @param detail what about the body could not be trusted
   */
  public AuthZENUnusableResponseException(String detail) {
    super("the server's decision cannot be acted on: " + detail);
  }

  @Override
  public boolean isRetryable() {
    return false;
  }
}
