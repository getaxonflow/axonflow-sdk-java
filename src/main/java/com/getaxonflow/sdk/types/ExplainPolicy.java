// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A policy reference inside a decision explanation (ADR-043). */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ExplainPolicy {

  private final String policyId;
  private final String policyName;
  private final String action;
  private final String riskLevel; // low | medium | high | critical
  private final boolean allowOverride;
  private final String policyDescription;

  @JsonCreator
  public ExplainPolicy(
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("policy_name") String policyName,
      @JsonProperty("action") String action,
      @JsonProperty("risk_level") String riskLevel,
      @JsonProperty("allow_override") boolean allowOverride,
      @JsonProperty("policy_description") String policyDescription) {
    this.policyId = policyId;
    this.policyName = policyName;
    this.action = action;
    this.riskLevel = riskLevel;
    this.allowOverride = allowOverride;
    this.policyDescription = policyDescription;
  }

  public String getPolicyId() {
    return policyId;
  }

  public String getPolicyName() {
    return policyName;
  }

  public String getAction() {
    return action;
  }

  public String getRiskLevel() {
    return riskLevel;
  }

  public boolean isAllowOverride() {
    return allowOverride;
  }

  public String getPolicyDescription() {
    return policyDescription;
  }
}
