// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

/**
 * Thrown when the SDK is misconfigured.
 *
 * <p>This typically occurs when:
 *
 * <ul>
 *   <li>Required configuration parameters are missing
 *   <li>Invalid values are provided for configuration
 *   <li>Incompatible configuration options are used together
 * </ul>
 */
public class ConfigurationException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final String configKey;

  /**
   * Creates a new ConfigurationException.
   *
   * @param message the error message
   */
  public ConfigurationException(String message) {
    super(message, 0, "CONFIGURATION_ERROR");
    this.configKey = null;
  }

  /**
   * Creates a new ConfigurationException for a specific configuration key.
   *
   * @param message the error message
   * @param configKey the configuration key that is invalid
   */
  public ConfigurationException(String message, String configKey) {
    super(message, 0, "CONFIGURATION_ERROR");
    this.configKey = configKey;
  }

  /**
   * Creates a new ConfigurationException with cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public ConfigurationException(String message, Throwable cause) {
    super(message, 0, "CONFIGURATION_ERROR", cause);
    this.configKey = null;
  }

  /**
   * Returns the configuration key that caused the error.
   *
   * @return the config key, or null if not specific to a key
   */
  public String getConfigKey() {
    return configKey;
  }
}
