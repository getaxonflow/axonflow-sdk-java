// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response from the policy impact report endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ImpactReportResponse {

  @JsonProperty("policy_id")
  private final String policyId;

  @JsonProperty("policy_name")
  private final String policyName;

  @JsonProperty("total_inputs")
  private final int totalInputs;

  @JsonProperty("matched")
  private final int matched;

  @JsonProperty("blocked")
  private final int blocked;

  @JsonProperty("match_rate")
  private final double matchRate;

  @JsonProperty("block_rate")
  private final double blockRate;

  @JsonProperty("results")
  private final List<ImpactReportResult> results;

  @JsonProperty("processing_time_ms")
  private final long processingTimeMs;

  @JsonProperty("generated_at")
  private final String generatedAt;

  @JsonProperty("tier")
  private final String tier;

  public ImpactReportResponse(
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("policy_name") String policyName,
      @JsonProperty("total_inputs") int totalInputs,
      @JsonProperty("matched") int matched,
      @JsonProperty("blocked") int blocked,
      @JsonProperty("match_rate") double matchRate,
      @JsonProperty("block_rate") double blockRate,
      @JsonProperty("results") List<ImpactReportResult> results,
      @JsonProperty("processing_time_ms") long processingTimeMs,
      @JsonProperty("generated_at") String generatedAt,
      @JsonProperty("tier") String tier) {
    this.policyId = policyId;
    this.policyName = policyName;
    this.totalInputs = totalInputs;
    this.matched = matched;
    this.blocked = blocked;
    this.matchRate = matchRate;
    this.blockRate = blockRate;
    this.results = results != null ? List.copyOf(results) : List.of();
    this.processingTimeMs = processingTimeMs;
    this.generatedAt = generatedAt;
    this.tier = tier;
  }

  public String getPolicyId() {
    return policyId;
  }

  public String getPolicyName() {
    return policyName;
  }

  public int getTotalInputs() {
    return totalInputs;
  }

  public int getMatched() {
    return matched;
  }

  public int getBlocked() {
    return blocked;
  }

  public double getMatchRate() {
    return matchRate;
  }

  public double getBlockRate() {
    return blockRate;
  }

  public List<ImpactReportResult> getResults() {
    return results;
  }

  public long getProcessingTimeMs() {
    return processingTimeMs;
  }

  public String getGeneratedAt() {
    return generatedAt;
  }

  public String getTier() {
    return tier;
  }
}
