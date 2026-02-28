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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * Request to validate an MCP input against configured policies without executing it.
 *
 * <p>Used with the {@code POST /api/v1/mcp/check-input} endpoint to pre-validate
 * a statement before sending it to the connector.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MCPCheckInputRequest {

    @JsonProperty("connector_type")
    private final String connectorType;

    @JsonProperty("statement")
    private final String statement;

    @JsonProperty("parameters")
    private final Map<String, Object> parameters;

    @JsonProperty("operation")
    private final String operation;

    /**
     * Creates a request with connector type and statement only.
     * Operation defaults to "query".
     *
     * @param connectorType the MCP connector type (e.g., "postgres")
     * @param statement     the statement to validate
     */
    public MCPCheckInputRequest(String connectorType, String statement) {
        this(connectorType, statement, null, "query");
    }

    /**
     * Creates a request with all fields.
     *
     * @param connectorType the MCP connector type (e.g., "postgres")
     * @param statement     the statement to validate
     * @param parameters    optional query parameters
     * @param operation     the operation type (e.g., "query", "execute")
     */
    public MCPCheckInputRequest(String connectorType, String statement,
                                Map<String, Object> parameters, String operation) {
        this.connectorType = connectorType;
        this.statement = statement;
        this.parameters = parameters;
        this.operation = operation;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public String getStatement() {
        return statement;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getOperation() {
        return operation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPCheckInputRequest that = (MCPCheckInputRequest) o;
        return Objects.equals(connectorType, that.connectorType) &&
               Objects.equals(statement, that.statement) &&
               Objects.equals(parameters, that.parameters) &&
               Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectorType, statement, parameters, operation);
    }

    @Override
    public String toString() {
        return "MCPCheckInputRequest{" +
               "connectorType='" + connectorType + '\'' +
               ", statement='" + statement + '\'' +
               ", operation='" + operation + '\'' +
               '}';
    }
}
