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
 * Thrown when an MCP connector operation fails.
 */
public class ConnectorException extends AxonFlowException {

    private static final long serialVersionUID = 1L;

    private final String connectorId;
    private final String operation;

    /**
     * Creates a new ConnectorException.
     *
     * @param message the error message
     */
    public ConnectorException(String message) {
        super(message, 0, "CONNECTOR_ERROR");
        this.connectorId = null;
        this.operation = null;
    }

    /**
     * Creates a new ConnectorException with connector details.
     *
     * @param message     the error message
     * @param connectorId the connector that failed
     * @param operation   the operation that failed
     */
    public ConnectorException(String message, String connectorId, String operation) {
        super(message, 0, "CONNECTOR_ERROR");
        this.connectorId = connectorId;
        this.operation = operation;
    }

    /**
     * Creates a new ConnectorException with cause.
     *
     * @param message     the error message
     * @param connectorId the connector that failed
     * @param operation   the operation that failed
     * @param cause       the underlying cause
     */
    public ConnectorException(String message, String connectorId, String operation, Throwable cause) {
        super(message, 0, "CONNECTOR_ERROR", cause);
        this.connectorId = connectorId;
        this.operation = operation;
    }

    /**
     * Returns the connector ID that failed.
     *
     * @return the connector ID
     */
    public String getConnectorId() {
        return connectorId;
    }

    /**
     * Returns the operation that failed.
     *
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }
}
