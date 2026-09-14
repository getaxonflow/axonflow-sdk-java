// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.AuthoringFinding;
import java.util.Collections;
import java.util.List;

/**
 * A typed policy authoring request the platform refused (platform v11.0.0).
 *
 * <p>{@link #getStatus()} is the HTTP status and {@link #getReason()} the platform's reason, for
 * example {@code publication_refused} (422), {@code activation_refused} (409), {@code tier_limit}
 * (402, with {@link #getCode()} naming the limit and {@link #getPolicy()} the policy that crossed
 * it) or {@code artifact_cap} (429). {@link #getFindings()} holds the declared findings a refused
 * publication or document carries, and {@link #getRetryAfter()} the seconds from {@code
 * Retry-After} when the refusal is retryable. The message is the platform's own explanation. A 401
 * is an {@link AuthenticationException}, as on every other route.
 */
public class TypedPolicyRefusalException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final String reason;
  private final String code;
  private final String policy;
  private final transient List<AuthoringFinding> findings;
  private final Integer retryAfter;

  /**
   * Creates a refusal that names no policy.
   *
   * @param message the platform's explanation
   * @param status the HTTP status
   * @param reason the platform's reason, or null
   * @param code the platform's code, or null
   * @param findings the declared findings, or null for none
   * @param retryAfter the seconds from {@code Retry-After}, or null when there is none
   */
  public TypedPolicyRefusalException(
      String message,
      int status,
      String reason,
      String code,
      List<AuthoringFinding> findings,
      Integer retryAfter) {
    this(message, status, reason, code, null, findings, retryAfter);
  }

  /**
   * Creates a refusal.
   *
   * @param message the platform's explanation
   * @param status the HTTP status
   * @param reason the platform's reason, or null
   * @param code the platform's code, or null
   * @param policy the policy a tier refusal names, or null
   * @param findings the declared findings, or null for none
   * @param retryAfter the seconds from {@code Retry-After}, or null when there is none
   */
  public TypedPolicyRefusalException(
      String message,
      int status,
      String reason,
      String code,
      String policy,
      List<AuthoringFinding> findings,
      Integer retryAfter) {
    super(message, status, code);
    this.reason = reason;
    this.code = code;
    this.policy = policy;
    this.findings =
        findings == null ? Collections.emptyList() : Collections.unmodifiableList(findings);
    this.retryAfter = retryAfter;
  }

  /** Returns the HTTP status. */
  public int getStatus() {
    return getStatusCode();
  }

  /** Returns the platform's reason, such as {@code publication_refused}, or null. */
  public String getReason() {
    return reason;
  }

  /** Returns the platform's code, such as the tier limit a 402 names, or null. */
  public String getCode() {
    return code;
  }

  /**
   * Returns the policy a tier refusal names, the one that crossed the ceiling, or null; an outage
   * refusal (with {@code Retry-After}) names none.
   */
  public String getPolicy() {
    return policy;
  }

  /** Returns the declared findings the refusal carries; empty when there are none. */
  public List<AuthoringFinding> getFindings() {
    return findings;
  }

  /** Returns the seconds from {@code Retry-After} when the refusal is retryable, or null. */
  public Integer getRetryAfter() {
    return retryAfter;
  }
}
