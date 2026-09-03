// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

/** Thrown when an MCP connector operation fails. */
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
   * @param message the error message
   * @param connectorId the connector that failed
   * @param operation the operation that failed
   */
  public ConnectorException(String message, String connectorId, String operation) {
    super(message, 0, "CONNECTOR_ERROR");
    this.connectorId = connectorId;
    this.operation = operation;
  }

  /**
   * Creates a new ConnectorException with cause.
   *
   * @param message the error message
   * @param connectorId the connector that failed
   * @param operation the operation that failed
   * @param cause the underlying cause
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
