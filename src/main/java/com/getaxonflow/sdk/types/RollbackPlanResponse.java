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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Response from rolling back a multi-agent plan to a previous version. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RollbackPlanResponse {

  @JsonProperty("plan_id")
  private final String planId;

  @JsonProperty("version")
  private final int version;

  @JsonProperty("previous_version")
  private final int previousVersion;

  @JsonProperty("status")
  private final String status;

  public RollbackPlanResponse(
      @JsonProperty("plan_id") String planId,
      @JsonProperty("version") int version,
      @JsonProperty("previous_version") int previousVersion,
      @JsonProperty("status") String status) {
    this.planId = planId;
    this.version = version;
    this.previousVersion = previousVersion;
    this.status = status;
  }

  /**
   * Returns the ID of the rolled-back plan.
   *
   * @return the plan ID
   */
  public String getPlanId() {
    return planId;
  }

  /**
   * Returns the new version number after rollback.
   *
   * @return the version number
   */
  public int getVersion() {
    return version;
  }

  /**
   * Returns the version that was rolled back from.
   *
   * @return the previous version number
   */
  public int getPreviousVersion() {
    return previousVersion;
  }

  /**
   * Returns the status of the plan after rollback.
   *
   * @return the status (e.g., "rolled_back")
   */
  public String getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RollbackPlanResponse that = (RollbackPlanResponse) o;
    return version == that.version
        && previousVersion == that.previousVersion
        && Objects.equals(planId, that.planId)
        && Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(planId, version, previousVersion, status);
  }

  @Override
  public String toString() {
    return "RollbackPlanResponse{"
        + "planId='"
        + planId
        + '\''
        + ", version="
        + version
        + ", previousVersion="
        + previousVersion
        + ", status='"
        + status
        + '\''
        + '}';
  }
}
