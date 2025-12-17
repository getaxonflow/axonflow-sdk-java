/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.axonflow.sdk.exceptions;

/**
 * Thrown when the SDK is misconfigured.
 *
 * <p>This typically occurs when:
 * <ul>
 *   <li>Required configuration parameters are missing</li>
 *   <li>Invalid values are provided for configuration</li>
 *   <li>Incompatible configuration options are used together</li>
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
     * @param message   the error message
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
     * @param cause   the underlying cause
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
