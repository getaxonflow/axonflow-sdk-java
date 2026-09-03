// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response from the policy conflict detection endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyConflictResponse {

  @JsonProperty("conflicts")
  private final List<PolicyConflict> conflicts;

  @JsonProperty("total_policies")
  private final int totalPolicies;

  @JsonProperty("conflict_count")
  private final int conflictCount;

  @JsonProperty("checked_at")
  private final String checkedAt;

  @JsonProperty("tier")
  private final String tier;

  public PolicyConflictResponse(
      @JsonProperty("conflicts") List<PolicyConflict> conflicts,
      @JsonProperty("total_policies") int totalPolicies,
      @JsonProperty("conflict_count") int conflictCount,
      @JsonProperty("checked_at") String checkedAt,
      @JsonProperty("tier") String tier) {
    this.conflicts = conflicts != null ? List.copyOf(conflicts) : List.of();
    this.totalPolicies = totalPolicies;
    this.conflictCount = conflictCount;
    this.checkedAt = checkedAt;
    this.tier = tier;
  }

  public List<PolicyConflict> getConflicts() {
    return conflicts;
  }

  public int getTotalPolicies() {
    return totalPolicies;
  }

  public int getConflictCount() {
    return conflictCount;
  }

  public String getCheckedAt() {
    return checkedAt;
  }

  public String getTier() {
    return tier;
  }
}
