// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Details about a matched dynamic policy.
 *
 * <p>Provides information about which dynamic policy matched during Orchestrator evaluation,
 * including the policy type and action taken.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DynamicPolicyMatch {

  @JsonProperty("policy_id")
  private final String policyId;

  @JsonProperty("policy_name")
  private final String policyName;

  @JsonProperty("policy_type")
  private final String policyType;

  @JsonProperty("action")
  private final String action;

  @JsonProperty("reason")
  private final String reason;

  public DynamicPolicyMatch(
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("policy_name") String policyName,
      @JsonProperty("policy_type") String policyType,
      @JsonProperty("action") String action,
      @JsonProperty("reason") String reason) {
    this.policyId = policyId;
    this.policyName = policyName;
    this.policyType = policyType;
    this.action = action;
    this.reason = reason;
  }

  /** Returns the unique identifier of the policy. */
  public String getPolicyId() {
    return policyId;
  }

  /** Returns the human-readable name of the policy. */
  public String getPolicyName() {
    return policyName;
  }

  /** Returns the type of policy (rate-limit, budget, time-access, role-access, mcp, connector). */
  public String getPolicyType() {
    return policyType;
  }

  /** Returns the action taken (allow, block, log, etc.). */
  public String getAction() {
    return action;
  }

  /** Returns the context for the policy match. */
  public String getReason() {
    return reason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DynamicPolicyMatch that = (DynamicPolicyMatch) o;
    return Objects.equals(policyId, that.policyId)
        && Objects.equals(policyName, that.policyName)
        && Objects.equals(policyType, that.policyType)
        && Objects.equals(action, that.action)
        && Objects.equals(reason, that.reason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(policyId, policyName, policyType, action, reason);
  }

  @Override
  public String toString() {
    return "DynamicPolicyMatch{"
        + "policyId='"
        + policyId
        + '\''
        + ", policyName='"
        + policyName
        + '\''
        + ", policyType='"
        + policyType
        + '\''
        + ", action='"
        + action
        + '\''
        + ", reason='"
        + reason
        + '\''
        + '}';
  }
}
