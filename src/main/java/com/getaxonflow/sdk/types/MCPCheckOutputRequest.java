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
   * Creates a request with connector type and response data only.
   *
   * @param connectorType the MCP connector type (e.g., "postgres")
   * @param responseData the response data rows to validate
   */
  public MCPCheckOutputRequest(String connectorType, List<Map<String, Object>> responseData) {
    this(connectorType, responseData, null, null, 0);
  }

  /**
   * Creates a request with all fields.
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
    this.connectorType = connectorType;
    this.responseData = responseData;
    this.message = message;
    this.metadata = metadata;
    this.rowCount = rowCount;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MCPCheckOutputRequest that = (MCPCheckOutputRequest) o;
    return rowCount == that.rowCount
        && Objects.equals(connectorType, that.connectorType)
        && Objects.equals(responseData, that.responseData)
        && Objects.equals(message, that.message)
        && Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectorType, responseData, message, metadata, rowCount);
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
