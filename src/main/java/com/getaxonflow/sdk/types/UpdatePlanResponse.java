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

/** Response from updating a multi-agent plan. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UpdatePlanResponse {

  @JsonProperty("plan_id")
  private final String planId;

  @JsonProperty("version")
  private final int version;

  @JsonProperty("status")
  private final String status;

  @JsonProperty("success")
  private final boolean success;

  public UpdatePlanResponse(
      @JsonProperty("plan_id") String planId,
      @JsonProperty("version") int version,
      @JsonProperty("status") String status,
      @JsonProperty("success") boolean success) {
    this.planId = planId;
    this.version = version;
    this.status = status;
    this.success = success;
  }

  /**
   * Returns the ID of the updated plan.
   *
   * @return the plan ID
   */
  public String getPlanId() {
    return planId;
  }

  /**
   * Returns the new version number after the update.
   *
   * @return the version number
   */
  public int getVersion() {
    return version;
  }

  /**
   * Returns the status of the plan after the update.
   *
   * @return the status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Returns whether the update was successful.
   *
   * @return true if the update succeeded
   */
  public boolean isSuccess() {
    return success;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UpdatePlanResponse that = (UpdatePlanResponse) o;
    return version == that.version
        && success == that.success
        && Objects.equals(planId, that.planId)
        && Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(planId, version, status, success);
  }

  @Override
  public String toString() {
    return "UpdatePlanResponse{"
        + "planId='"
        + planId
        + '\''
        + ", version="
        + version
        + ", status='"
        + status
        + '\''
        + ", success="
        + success
        + '}';
  }
}
