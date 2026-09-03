// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

import java.time.Duration;

/** Thrown when a request times out. */
public class TimeoutException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final Duration timeout;

  /**
   * Creates a new TimeoutException.
   *
   * @param message the error message
   */
  public TimeoutException(String message) {
    super(message, 0, "TIMEOUT");
    this.timeout = null;
  }

  /**
   * Creates a new TimeoutException with timeout duration.
   *
   * @param message the error message
   * @param timeout the configured timeout duration
   */
  public TimeoutException(String message, Duration timeout) {
    super(message, 0, "TIMEOUT");
    this.timeout = timeout;
  }

  /**
   * Creates a new TimeoutException with cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public TimeoutException(String message, Throwable cause) {
    super(message, 0, "TIMEOUT", cause);
    this.timeout = null;
  }

  /**
   * Creates a new TimeoutException with timeout and cause.
   *
   * @param message the error message
   * @param timeout the configured timeout duration
   * @param cause the underlying cause
   */
  public TimeoutException(String message, Duration timeout, Throwable cause) {
    super(message, 0, "TIMEOUT", cause);
    this.timeout = timeout;
  }

  /**
   * Returns the configured timeout duration.
   *
   * @return the timeout duration, or null if not specified
   */
  public Duration getTimeout() {
    return timeout;
  }
}
