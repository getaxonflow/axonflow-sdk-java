// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents a policy that was matched during workflow step gate evaluation.
 *
 * <p>Contains information about which policy matched, the action taken, and the reason for the
 * match.
 *
 * @since 2.3.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyMatch {

  @JsonProperty("policy_id")
  private final String policyId;

  @JsonProperty("policy_name")
  private final String policyName;

  @JsonProperty("action")
  private final String action;

  @JsonProperty("reason")
  private final String reason;

  @JsonCreator
  public PolicyMatch(
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("policy_name") String policyName,
      @JsonProperty("action") String action,
      @JsonProperty("reason") String reason) {
    this.policyId = policyId;
    this.policyName = policyName;
    this.action = action;
    this.reason = reason;
  }

  /**
   * Returns the unique identifier of the matched policy.
   *
   * @return the policy ID
   */
  public String getPolicyId() {
    return policyId;
  }

  /**
   * Returns the human-readable name of the matched policy.
   *
   * @return the policy name
   */
  public String getPolicyName() {
    return policyName;
  }

  /**
   * Returns the action taken as a result of this policy match.
   *
   * <p>Common actions include "allow", "block", "require_approval", "redact".
   *
   * @return the action taken
   */
  public String getAction() {
    return action;
  }

  /**
   * Returns the reason why this policy was matched.
   *
   * <p>Provides context about what triggered the policy match, useful for debugging and audit
   * purposes.
   *
   * @return the reason for the match
   */
  public String getReason() {
    return reason;
  }

  /**
   * Checks if this policy match resulted in a blocking action.
   *
   * @return true if the action is "block"
   */
  public boolean isBlocking() {
    return "block".equalsIgnoreCase(action);
  }

  /**
   * Checks if this policy match requires approval.
   *
   * @return true if the action is "require_approval"
   */
  public boolean requiresApproval() {
    return "require_approval".equalsIgnoreCase(action);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PolicyMatch that = (PolicyMatch) o;
    return Objects.equals(policyId, that.policyId)
        && Objects.equals(policyName, that.policyName)
        && Objects.equals(action, that.action)
        && Objects.equals(reason, that.reason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(policyId, policyName, action, reason);
  }

  @Override
  public String toString() {
    return "PolicyMatch{"
        + "policyId='"
        + policyId
        + '\''
        + ", policyName='"
        + policyName
        + '\''
        + ", action='"
        + action
        + '\''
        + ", reason='"
        + reason
        + '\''
        + '}';
  }

  /**
   * Creates a new builder for PolicyMatch.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for PolicyMatch. */
  public static final class Builder {
    private String policyId;
    private String policyName;
    private String action;
    private String reason;

    public Builder policyId(String policyId) {
      this.policyId = policyId;
      return this;
    }

    public Builder policyName(String policyName) {
      this.policyName = policyName;
      return this;
    }

    public Builder action(String action) {
      this.action = action;
      return this;
    }

    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    public PolicyMatch build() {
      return new PolicyMatch(policyId, policyName, action, reason);
    }
  }
}
