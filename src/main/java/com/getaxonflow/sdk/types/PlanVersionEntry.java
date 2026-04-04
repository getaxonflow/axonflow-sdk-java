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

/** Represents a single version entry in a plan's version history. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlanVersionEntry {

  @JsonProperty("version")
  private final int version;

  @JsonProperty("changed_at")
  private final String changedAt;

  @JsonProperty("changed_by")
  private final String changedBy;

  @JsonProperty("change_type")
  private final String changeType;

  @JsonProperty("change_summary")
  private final String changeSummary;

  public PlanVersionEntry(
      @JsonProperty("version") int version,
      @JsonProperty("changed_at") String changedAt,
      @JsonProperty("changed_by") String changedBy,
      @JsonProperty("change_type") String changeType,
      @JsonProperty("change_summary") String changeSummary) {
    this.version = version;
    this.changedAt = changedAt;
    this.changedBy = changedBy;
    this.changeType = changeType;
    this.changeSummary = changeSummary;
  }

  /**
   * Returns the version number.
   *
   * @return the version number
   */
  public int getVersion() {
    return version;
  }

  /**
   * Returns when this version was created.
   *
   * @return ISO 8601 timestamp string
   */
  public String getChangedAt() {
    return changedAt;
  }

  /**
   * Returns who made this change.
   *
   * @return the user or system identifier
   */
  public String getChangedBy() {
    return changedBy;
  }

  /**
   * Returns the type of change (e.g., "created", "updated", "cancelled").
   *
   * @return the change type
   */
  public String getChangeType() {
    return changeType;
  }

  /**
   * Returns a human-readable summary of the change.
   *
   * @return the change summary
   */
  public String getChangeSummary() {
    return changeSummary;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlanVersionEntry that = (PlanVersionEntry) o;
    return version == that.version
        && Objects.equals(changedAt, that.changedAt)
        && Objects.equals(changedBy, that.changedBy)
        && Objects.equals(changeType, that.changeType)
        && Objects.equals(changeSummary, that.changeSummary);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, changedAt, changedBy, changeType, changeSummary);
  }

  @Override
  public String toString() {
    return "PlanVersionEntry{"
        + "version="
        + version
        + ", changedAt='"
        + changedAt
        + '\''
        + ", changedBy='"
        + changedBy
        + '\''
        + ", changeType='"
        + changeType
        + '\''
        + '}';
  }
}
