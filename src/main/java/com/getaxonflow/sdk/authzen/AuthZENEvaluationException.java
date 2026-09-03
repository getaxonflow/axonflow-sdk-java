// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen;

import com.getaxonflow.sdk.exceptions.AxonFlowException;

/**
 * Everything that can come back from an evaluation instead of a decision.
 *
 * <p>The five subclasses are separated by what a caller should DO, not by where the failure
 * happened:
 *
 * <ul>
 *   <li>{@link AuthZENRefusedException} — the request was refused rather than evaluated. Fix the
 *       request; the refusal names the member.
 *   <li>{@link AuthZENUnresolvedException} — the request cannot be SENT as built, because it
 *       carries an attribute nobody could resolve. Re-resolve it and build a NEW request; resending
 *       this one cannot succeed, which is why it is not folded into the refusal above.
 *   <li>{@link AuthZENUnreadableProfileException} — the server answered in a profile this build
 *       cannot interpret. Upgrade the SDK.
 *   <li>{@link AuthZENUnusableResponseException} — a {@code 200} this build will not act on. A
 *       server contract violation to report.
 *   <li>{@link AuthZENTransportException} — no answer: connection, timeout, credentials, or a
 *       non-refusal error status.
 * </ul>
 *
 * <p>Collapsing them into one opaque exception would leave a caller with a message string to match
 * on, and the first thing such a caller does is treat an auth failure as a denial.
 *
 * <p>None of them is a denial. A denial is a {@link AuthZENDecision} whose {@code allowed()} is
 * false: the request WAS evaluated and the answer was no. That distinction is a type, not a
 * convention, so no branch can lose it.
 */
public abstract class AuthZENEvaluationException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  AuthZENEvaluationException(String message) {
    super(message);
  }

  AuthZENEvaluationException(String message, int statusCode, String errorCode, Throwable cause) {
    super(message, statusCode, errorCode, cause);
  }

  /**
   * Whether sending the same request again could produce a different answer.
   *
   * <p>This is the whole retryable set, in one place, so a caller never has to assemble it from
   * status codes:
   *
   * <ul>
   *   <li>a refusal — only when its code is {@code evaluation_unavailable};
   *   <li>an unresolved attribute — NEVER, because the refusal is frozen inside the request;
   *   <li>a transport failure — timeout, connect, {@code 5xx}, {@code 429};
   *   <li>an unreadable profile — never;
   *   <li>an unusable response — never.
   * </ul>
   *
   * @return true when a retry could change the outcome
   */
  public abstract boolean isRetryable();
}
