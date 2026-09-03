// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Rule-level detail inside a decision explanation (ADR-043). */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ExplainRule {

  private final String policyId;
  private final String ruleId;
  private final String ruleText;
  private final String matchedOn;

  @JsonCreator
  public ExplainRule(
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("rule_id") String ruleId,
      @JsonProperty("rule_text") String ruleText,
      @JsonProperty("matched_on") String matchedOn) {
    this.policyId = policyId;
    this.ruleId = ruleId;
    this.ruleText = ruleText;
    this.matchedOn = matchedOn;
  }

  public String getPolicyId() {
    return policyId;
  }

  public String getRuleId() {
    return ruleId;
  }

  public String getRuleText() {
    return ruleText;
  }

  public String getMatchedOn() {
    return matchedOn;
  }
}
