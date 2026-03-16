/*
 * Copyright 2026 AxonFlow
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
package com.getaxonflow.sdk.adapters;

import java.util.function.Function;

/**
 * Options for {@link LangGraphAdapter#mcpToolInterceptor}.
 *
 * <p>Controls how MCP tool requests are mapped to connector types and what
 * operation type is used for policy checks.
 */
public final class MCPInterceptorOptions {

    private final Function<MCPToolRequest, String> connectorTypeFn;
    private final String operation;

    private MCPInterceptorOptions(Builder builder) {
        this.connectorTypeFn = builder.connectorTypeFn;
        this.operation = builder.operation;
    }

    /**
     * Returns the function that maps an MCP request to a connector type string.
     * May be null, in which case the default "{serverName}.{toolName}" is used.
     *
     * @return the connector type function, or null
     */
    public Function<MCPToolRequest, String> getConnectorTypeFn() {
        return connectorTypeFn;
    }

    /**
     * Returns the operation type passed to {@code mcpCheckInput}.
     * Defaults to "execute".
     *
     * @return the operation type
     */
    public String getOperation() {
        return operation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Function<MCPToolRequest, String> connectorTypeFn;
        private String operation = "execute";

        private Builder() {
        }

        /**
         * Sets a custom function to derive the connector type from an MCP request.
         *
         * @param connectorTypeFn mapping function
         * @return this builder
         */
        public Builder connectorTypeFn(Function<MCPToolRequest, String> connectorTypeFn) {
            this.connectorTypeFn = connectorTypeFn;
            return this;
        }

        /**
         * Sets the operation type. Defaults to "execute".
         * Use "query" for known read-only tool calls.
         *
         * @param operation the operation type
         * @return this builder
         */
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public MCPInterceptorOptions build() {
            return new MCPInterceptorOptions(this);
        }
    }
}
