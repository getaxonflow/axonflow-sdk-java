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

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types of requests that can be processed by AxonFlow.
 */
public enum RequestType {
    /**
     * Standard chat/conversation request.
     */
    CHAT("chat"),

    /**
     * SQL query request.
     */
    SQL("sql"),

    /**
     * MCP (Model Context Protocol) connector query.
     */
    MCP_QUERY("mcp-query"),

    /**
     * Multi-agent planning request.
     */
    MULTI_AGENT_PLAN("multi-agent-plan");

    private final String value;

    RequestType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Parses a string value to a RequestType enum.
     *
     * @param value the string value to parse
     * @return the corresponding RequestType
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static RequestType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Request type cannot be null");
        }
        for (RequestType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown request type: " + value);
    }
}
