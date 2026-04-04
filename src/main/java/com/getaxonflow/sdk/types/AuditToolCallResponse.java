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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Response from auditing a tool call. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuditToolCallResponse {

  @JsonProperty("audit_id")
  private final String auditId;

  @JsonProperty("status")
  private final String status;

  @JsonProperty("timestamp")
  private final String timestamp;

  public AuditToolCallResponse(
      @JsonProperty("audit_id") String auditId,
      @JsonProperty("status") String status,
      @JsonProperty("timestamp") String timestamp) {
    this.auditId = auditId;
    this.status = status;
    this.timestamp = timestamp;
  }

  /**
   * Returns the unique identifier for this audit record.
   *
   * @return the audit ID
   */
  public String getAuditId() {
    return auditId;
  }

  /**
   * Returns the status of the audit operation.
   *
   * @return the status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Returns the timestamp when the audit was recorded.
   *
   * @return the timestamp as an ISO 8601 string
   */
  public String getTimestamp() {
    return timestamp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AuditToolCallResponse that = (AuditToolCallResponse) o;
    return Objects.equals(auditId, that.auditId)
        && Objects.equals(status, that.status)
        && Objects.equals(timestamp, that.timestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(auditId, status, timestamp);
  }

  @Override
  public String toString() {
    return "AuditToolCallResponse{"
        + "auditId='"
        + auditId
        + '\''
        + ", status='"
        + status
        + '\''
        + ", timestamp='"
        + timestamp
        + '\''
        + '}';
  }
}
