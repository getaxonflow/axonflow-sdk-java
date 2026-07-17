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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request to validate MCP response data against configured policies.
 *
 * <p>Used with the {@code POST /api/v1/mcp/check-output} endpoint to check response data for PII,
 * exfiltration limits, and other policy violations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MCPCheckOutputRequest {

  @JsonProperty("connector_type")
  private final String connectorType;

  @JsonProperty("response_data")
  private final List<Map<String, Object>> responseData;

  @JsonProperty("message")
  private final String message;

  @JsonProperty("metadata")
  private final Map<String, Object> metadata;

  @JsonProperty("row_count")
  private final int rowCount;

  /**
   * The specific tool/action name being invoked on the MCP server (e.g., "query", "search_docs").
   * Distinct from {@code connectorType}, which identifies the MCP server/connector itself.
   * Optional; null when the caller doesn't distinguish per-tool identity from the connector.
   *
   * <p>NOTE: unlike the input plane, the platform's check-output schema has NO {@code tool} field
   * on any released version yet — #2904 added it input-side only, and check-output support is
   * tracked by #2955 (targeted for v9.11.0). Sending it here is forward-compatible and harmless
   * (the agent ignores unknown keys), but it is not consumed server-side on any platform today.
   * Source of truth: {@code platform/agent} {@code MCPCheckInputRequest.Tool} (epic #2905 / #2904).
   */
  @JsonProperty("tool")
  private final String tool;

  /**
   * Creates a request with connector type and response data only.
   *
   * @param connectorType the MCP connector type (e.g., "postgres")
   * @param responseData the response data rows to validate
   */
  public MCPCheckOutputRequest(String connectorType, List<Map<String, Object>> responseData) {
    this(connectorType, responseData, null, null, 0);
  }

  /**
   * Creates a request with all fields. Tool is left null.
   *
   * @param connectorType the MCP connector type (e.g., "postgres")
   * @param responseData the response data rows to validate
   * @param message optional message context
   * @param metadata optional metadata
   * @param rowCount the number of rows in the response
   */
  public MCPCheckOutputRequest(
      String connectorType,
      List<Map<String, Object>> responseData,
      String message,
      Map<String, Object> metadata,
      int rowCount) {
    this(connectorType, responseData, message, metadata, rowCount, null);
  }

  /**
   * Creates a request with all fields, including the specific tool name.
   *
   * @param connectorType the MCP connector type/server (e.g., "postgres")
   * @param responseData the response data rows to validate
   * @param message optional message context
   * @param metadata optional metadata
   * @param rowCount the number of rows in the response
   * @param tool the specific tool/action name (e.g., "query"); null when not distinguished from
   *     {@code connectorType}
   */
  public MCPCheckOutputRequest(
      String connectorType,
      List<Map<String, Object>> responseData,
      String message,
      Map<String, Object> metadata,
      int rowCount,
      String tool) {
    this.connectorType = connectorType;
    this.responseData = responseData;
    this.message = message;
    this.metadata = metadata;
    this.rowCount = rowCount;
    this.tool = tool;
  }

  public String getConnectorType() {
    return connectorType;
  }

  public List<Map<String, Object>> getResponseData() {
    return responseData;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public int getRowCount() {
    return rowCount;
  }

  /**
   * Returns the specific tool/action name being invoked, or null when not distinguished from
   * {@code connectorType}.
   */
  public String getTool() {
    return tool;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MCPCheckOutputRequest that = (MCPCheckOutputRequest) o;
    return rowCount == that.rowCount
        && Objects.equals(connectorType, that.connectorType)
        && Objects.equals(responseData, that.responseData)
        && Objects.equals(message, that.message)
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(tool, that.tool);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectorType, responseData, message, metadata, rowCount, tool);
  }

  @Override
  public String toString() {
    return "MCPCheckOutputRequest{"
        + "connectorType='"
        + connectorType
        + '\''
        + ", rowCount="
        + rowCount
        + ", message='"
        + message
        + '\''
        + '}';
  }
}
