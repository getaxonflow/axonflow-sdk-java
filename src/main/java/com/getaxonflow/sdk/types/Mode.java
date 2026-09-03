// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

/**
 * Operating mode for the AxonFlow client.
 *
 * <p>The mode determines the behavior of certain operations:
 *
 * <ul>
 *   <li>{@link #PRODUCTION} - Standard production mode with full governance
 *   <li>{@link #SANDBOX} - Testing mode with relaxed policies for development
 * </ul>
 */
public enum Mode {
  /** Production mode with full policy enforcement. */
  PRODUCTION("production"),

  /** Sandbox mode for testing and development. Policies may be relaxed or simulated. */
  SANDBOX("sandbox");

  private final String value;

  Mode(String value) {
    this.value = value;
  }

  /**
   * Returns the string value used in API requests.
   *
   * @return the mode value as a string
   */
  public String getValue() {
    return value;
  }

  /**
   * Parses a string value to a Mode enum.
   *
   * @param value the string value to parse
   * @return the corresponding Mode, or PRODUCTION if not recognized
   */
  public static Mode fromValue(String value) {
    if (value == null) {
      return PRODUCTION;
    }
    for (Mode mode : values()) {
      if (mode.value.equalsIgnoreCase(value)) {
        return mode;
      }
    }
    return PRODUCTION;
  }
}
