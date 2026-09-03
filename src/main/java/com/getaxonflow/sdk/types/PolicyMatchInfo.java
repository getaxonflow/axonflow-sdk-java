// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Information about a policy match during evaluation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyMatchInfo {

  @JsonProperty("policy_id")
  private final String policyId;

  @JsonProperty("policy_name")
  private final String policyName;

  @JsonProperty("category")
  private final String category;

  @JsonProperty("severity")
  private final String severity;

  @JsonProperty("action")
  private final String action;

  public PolicyMatchInfo(
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("policy_name") String policyName,
      @JsonProperty("category") String category,
      @JsonProperty("severity") String severity,
      @JsonProperty("action") String action) {
    this.policyId = policyId;
    this.policyName = policyName;
    this.category = category;
    this.severity = severity;
    this.action = action;
  }

  public String getPolicyId() {
    return policyId;
  }

  public String getPolicyName() {
    return policyName;
  }

  public String getCategory() {
    return category;
  }

  public String getSeverity() {
    return severity;
  }

  public String getAction() {
    return action;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PolicyMatchInfo that = (PolicyMatchInfo) o;
    return Objects.equals(policyId, that.policyId)
        && Objects.equals(policyName, that.policyName)
        && Objects.equals(category, that.category)
        && Objects.equals(severity, that.severity)
        && Objects.equals(action, that.action);
  }

  @Override
  public int hashCode() {
    return Objects.hash(policyId, policyName, category, severity, action);
  }

  @Override
  public String toString() {
    return "PolicyMatchInfo{"
        + "policyId='"
        + policyId
        + '\''
        + ", policyName='"
        + policyName
        + '\''
        + ", category='"
        + category
        + '\''
        + ", severity='"
        + severity
        + '\''
        + ", action='"
        + action
        + '\''
        + '}';
  }
}
