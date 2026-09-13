// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

/**
 * Thrown when the platform refuses a write to its static- or dynamic-policy routes.
 *
 * <p>From v11.0.0 those writes answer {@code 409 LEGACY_POLICY_WRITE_FROZEN}, on the agent and the
 * orchestrator alike: policy is authored through the typed route ({@code /api/v1/typed-policies}),
 * which the message names. Reads on the legacy routes still work and are deprecated; see {@link
 * com.getaxonflow.sdk.AxonFlowConfig.Builder#onRouteDeprecation}.
 */
public class LegacyPolicyWriteFrozenException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  /** The error code a v11.0.0 platform answers a legacy policy write with. */
  public static final String CODE = "LEGACY_POLICY_WRITE_FROZEN";

  /**
   * Creates the exception.
   *
   * @param message the platform's message, which names the typed policy route
   */
  public LegacyPolicyWriteFrozenException(String message) {
    super(message, 409, CODE);
  }
}
