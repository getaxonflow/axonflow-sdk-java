// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

/**
 * Thrown when authentication with the AxonFlow API fails.
 *
 * <p>This typically occurs when:
 *
 * <ul>
 *   <li>The license key is invalid or expired
 *   <li>The client ID/secret combination is incorrect
 *   <li>The API key has been revoked
 * </ul>
 */
public class AuthenticationException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new AuthenticationException.
   *
   * @param message the error message
   */
  public AuthenticationException(String message) {
    super(message, 401, "AUTHENTICATION_FAILED");
  }

  /**
   * Creates a new AuthenticationException with a cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public AuthenticationException(String message, Throwable cause) {
    super(message, 401, "AUTHENTICATION_FAILED", cause);
  }

  /**
   * Creates a new AuthenticationException with a custom status code.
   *
   * @param message the error message
   * @param statusCode the HTTP status code
   */
  public AuthenticationException(String message, int statusCode) {
    super(message, statusCode, "AUTHENTICATION_FAILED");
  }
}
