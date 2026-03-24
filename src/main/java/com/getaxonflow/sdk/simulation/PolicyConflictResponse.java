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
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from the policy conflict detection endpoint.
 */
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

    public List<PolicyConflict> getConflicts() { return conflicts; }
    public int getTotalPolicies() { return totalPolicies; }
    public int getConflictCount() { return conflictCount; }
    public String getCheckedAt() { return checkedAt; }
    public String getTier() { return tier; }
}
