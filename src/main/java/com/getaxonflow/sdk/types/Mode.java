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
package com.getaxonflow.sdk.types;

/**
 * Operating mode for the AxonFlow client.
 *
 * <p>The mode determines the behavior of certain operations:
 * <ul>
 *   <li>{@link #PRODUCTION} - Standard production mode with full governance</li>
 *   <li>{@link #SANDBOX} - Testing mode with relaxed policies for development</li>
 * </ul>
 */
public enum Mode {
    /**
     * Production mode with full policy enforcement.
     */
    PRODUCTION("production"),

    /**
     * Sandbox mode for testing and development.
     * Policies may be relaxed or simulated.
     */
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
