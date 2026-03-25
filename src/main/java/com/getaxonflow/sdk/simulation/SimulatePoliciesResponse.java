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
 * Response from policy simulation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SimulatePoliciesResponse {

    @JsonProperty("allowed")
    private final boolean allowed;

    @JsonProperty("applied_policies")
    private final List<String> appliedPolicies;

    @JsonProperty("risk_score")
    private final double riskScore;

    @JsonProperty("required_actions")
    private final List<String> requiredActions;

    @JsonProperty("processing_time_ms")
    private final long processingTimeMs;

    @JsonProperty("total_policies")
    private final int totalPolicies;

    @JsonProperty("dry_run")
    private final boolean dryRun;

    @JsonProperty("simulated_at")
    private final String simulatedAt;

    @JsonProperty("tier")
    private final String tier;

    @JsonProperty("daily_usage")
    private final SimulationDailyUsage dailyUsage;

    public SimulatePoliciesResponse(
            @JsonProperty("allowed") boolean allowed,
            @JsonProperty("applied_policies") List<String> appliedPolicies,
            @JsonProperty("risk_score") double riskScore,
            @JsonProperty("required_actions") List<String> requiredActions,
            @JsonProperty("processing_time_ms") long processingTimeMs,
            @JsonProperty("total_policies") int totalPolicies,
            @JsonProperty("dry_run") boolean dryRun,
            @JsonProperty("simulated_at") String simulatedAt,
            @JsonProperty("tier") String tier,
            @JsonProperty("daily_usage") SimulationDailyUsage dailyUsage) {
        this.allowed = allowed;
        this.appliedPolicies = appliedPolicies != null ? List.copyOf(appliedPolicies) : List.of();
        this.riskScore = riskScore;
        this.requiredActions = requiredActions != null ? List.copyOf(requiredActions) : List.of();
        this.processingTimeMs = processingTimeMs;
        this.totalPolicies = totalPolicies;
        this.dryRun = dryRun;
        this.simulatedAt = simulatedAt;
        this.tier = tier;
        this.dailyUsage = dailyUsage;
    }

    public boolean isAllowed() { return allowed; }
    public List<String> getAppliedPolicies() { return appliedPolicies; }
    public double getRiskScore() { return riskScore; }
    public List<String> getRequiredActions() { return requiredActions; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public int getTotalPolicies() { return totalPolicies; }
    public boolean isDryRun() { return dryRun; }
    public String getSimulatedAt() { return simulatedAt; }
    public String getTier() { return tier; }
    public SimulationDailyUsage getDailyUsage() { return dailyUsage; }
}
