// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
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
