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
 * <p>Used with the {@code POST /api/v1/mcp/check-input} endpoint to pre-validate a statement before
 * sending it to the connector.
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
   * Selects the request-redaction detector (ADR-056 / #2563 addendum). Null defaults to {@code
   * text/plain} server-side. A content type with no registered detector is rejected (415) so a PEP
   * fulfilling a {@code redact_pii} obligation fails closed rather than forwarding content the
   * engine cannot govern. Source of truth: {@code platform/agent/mcp_handler.go
   * MCPCheckInputRequest}.
   */
  @JsonProperty("content_type")
  private final String contentType;

  /**
   * Creates a request with connector type and statement only. Operation defaults to "execute".
   *
   * @param connectorType the MCP connector type (e.g., "postgres")
   * @param statement the statement to validate
   */
  public MCPCheckInputRequest(String connectorType, String statement) {
    this(connectorType, statement, null, "execute");
  }

  /**
   * Creates a request with connector type, statement, parameters, and operation. Content type is
   * left null (server defaults to {@code text/plain}).
   *
   * @param connectorType the MCP connector type (e.g., "postgres")
   * @param statement the statement to validate
   * @param parameters optional query parameters
   * @param operation the operation type (e.g., "query", "execute")
   */
  public MCPCheckInputRequest(
      String connectorType, String statement, Map<String, Object> parameters, String operation) {
    this(connectorType, statement, parameters, operation, null);
  }

  /**
   * Creates a request with all fields, including the redaction content type.
   *
   * @param connectorType the MCP connector type (e.g., "postgres")
   * @param statement the statement to validate
   * @param parameters optional query parameters
   * @param operation the operation type (e.g., "query", "execute")
   * @param contentType the redaction content type (e.g., {@code text/plain}); null defaults
   *     server-side
   */
  public MCPCheckInputRequest(
      String connectorType,
      String statement,
      Map<String, Object> parameters,
      String operation,
      String contentType) {
    this.connectorType = connectorType;
    this.statement = statement;
    this.parameters = parameters;
    this.operation = operation;
    this.contentType = contentType;
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

  /** Returns the redaction content type (e.g., {@code text/plain}), or null when server-default. */
  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MCPCheckInputRequest that = (MCPCheckInputRequest) o;
    return Objects.equals(connectorType, that.connectorType)
        && Objects.equals(statement, that.statement)
        && Objects.equals(parameters, that.parameters)
        && Objects.equals(operation, that.operation)
        && Objects.equals(contentType, that.contentType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectorType, statement, parameters, operation, contentType);
  }

  @Override
  public String toString() {
    return "MCPCheckInputRequest{"
        + "connectorType='"
        + connectorType
        + '\''
        + ", statement='"
        + statement
        + '\''
        + ", operation='"
        + operation
        + '\''
        + '}';
  }
}
