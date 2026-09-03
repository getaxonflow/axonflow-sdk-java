// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.authzen;

/**
 * The policy question about a refusal code, kept out of the generated types.
 *
 * <p>{@link AuthZENErrorCode} is generated from the contract artifact and says only what the values
 * ARE. Whether one is worth retrying is a judgement about this SDK's behaviour, not a fact the
 * contract states, so it lives here — where a reviewer can find it, and where changing it does not
 * mean editing a generated file.
 */
public final class AuthZENRefusals {

  private AuthZENRefusals() {}

  /**
   * Whether the caller could get a different answer by sending the same request again.
   *
   * <p>Only a dependency failure is. Every other code names something about the request itself,
   * which will not change on a retry — so a client that retries on any refusal burns its budget on
   * requests that cannot succeed.
   *
   * <p>A code this build does not know is NOT retryable. Guessing the other way would turn every
   * future code into a retry loop against a server that has already given its final answer.
   *
   * @param code the refusal code; a null or unrecognised code is not retryable
   * @return true only for {@code evaluation_unavailable}
   */
  public static boolean isRetryable(AuthZENErrorCode code) {
    return AuthZENErrorCode.EVALUATION_UNAVAILABLE.equals(code);
  }
}
