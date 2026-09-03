// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Response containing the version history of a multi-agent plan. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlanVersionsResponse {

  @JsonProperty("plan_id")
  private final String planId;

  @JsonProperty("versions")
  private final List<PlanVersionEntry> versions;

  public PlanVersionsResponse(
      @JsonProperty("plan_id") String planId,
      @JsonProperty("versions") List<PlanVersionEntry> versions) {
    this.planId = planId;
    this.versions =
        versions != null ? Collections.unmodifiableList(versions) : Collections.emptyList();
  }

  /**
   * Returns the plan ID.
   *
   * @return the plan ID
   */
  public String getPlanId() {
    return planId;
  }

  /**
   * Returns the version history entries.
   *
   * @return immutable list of version entries
   */
  public List<PlanVersionEntry> getVersions() {
    return versions;
  }

  /**
   * Returns the number of versions.
   *
   * @return the version count
   */
  public int getVersionCount() {
    return versions.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlanVersionsResponse that = (PlanVersionsResponse) o;
    return Objects.equals(planId, that.planId) && Objects.equals(versions, that.versions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(planId, versions);
  }

  @Override
  public String toString() {
    return "PlanVersionsResponse{"
        + "planId='"
        + planId
        + '\''
        + ", versionCount="
        + versions.size()
        + '}';
  }
}
