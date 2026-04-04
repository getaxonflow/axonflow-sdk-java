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
package com.getaxonflow.sdk.types.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of a policy evaluation during workflow execution.
 *
 * <p>Contains detailed information about whether a step or plan execution was allowed based on
 * policy checks, including risk assessment and any required actions.
 *
 * @since 2.3.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyEvaluationResult {

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

  @JsonProperty("database_accessed")
  private final Boolean databaseAccessed;

  @JsonCreator
  public PolicyEvaluationResult(
      @JsonProperty("allowed") boolean allowed,
      @JsonProperty("applied_policies") List<String> appliedPolicies,
      @JsonProperty("risk_score") double riskScore,
      @JsonProperty("required_actions") List<String> requiredActions,
      @JsonProperty("processing_time_ms") long processingTimeMs,
      @JsonProperty("database_accessed") Boolean databaseAccessed) {
    this.allowed = allowed;
    this.appliedPolicies =
        appliedPolicies != null
            ? Collections.unmodifiableList(appliedPolicies)
            : Collections.emptyList();
    this.riskScore = riskScore;
    this.requiredActions =
        requiredActions != null
            ? Collections.unmodifiableList(requiredActions)
            : Collections.emptyList();
    this.processingTimeMs = processingTimeMs;
    this.databaseAccessed = databaseAccessed;
  }

  /**
   * Returns whether the operation was allowed by policy evaluation.
   *
   * @return true if the operation is allowed, false if blocked
   */
  public boolean isAllowed() {
    return allowed;
  }

  /**
   * Returns the list of policies that were applied during evaluation.
   *
   * @return immutable list of applied policy identifiers
   */
  public List<String> getAppliedPolicies() {
    return appliedPolicies;
  }

  /**
   * Returns the calculated risk score for this operation.
   *
   * <p>Risk scores typically range from 0.0 (no risk) to 1.0 (high risk).
   *
   * @return the risk score
   */
  public double getRiskScore() {
    return riskScore;
  }

  /**
   * Returns the list of actions required before the operation can proceed.
   *
   * <p>Examples include "approval_required", "audit_required", "rate_limit_exceeded".
   *
   * @return immutable list of required action identifiers
   */
  public List<String> getRequiredActions() {
    return requiredActions;
  }

  /**
   * Returns the time taken to evaluate policies in milliseconds.
   *
   * @return processing time in milliseconds
   */
  public long getProcessingTimeMs() {
    return processingTimeMs;
  }

  /**
   * Returns whether a database was accessed during policy evaluation.
   *
   * <p>This is useful for tracking whether dynamic policy lookups were performed.
   *
   * @return true if database was accessed, false otherwise, null if unknown
   */
  public Boolean getDatabaseAccessed() {
    return databaseAccessed;
  }

  /**
   * Convenience method to check if database was accessed.
   *
   * @return true if database was definitely accessed, false otherwise
   */
  public boolean wasDatabaseAccessed() {
    return Boolean.TRUE.equals(databaseAccessed);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PolicyEvaluationResult that = (PolicyEvaluationResult) o;
    return allowed == that.allowed
        && Double.compare(that.riskScore, riskScore) == 0
        && processingTimeMs == that.processingTimeMs
        && Objects.equals(appliedPolicies, that.appliedPolicies)
        && Objects.equals(requiredActions, that.requiredActions)
        && Objects.equals(databaseAccessed, that.databaseAccessed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        allowed, appliedPolicies, riskScore, requiredActions, processingTimeMs, databaseAccessed);
  }

  @Override
  public String toString() {
    return "PolicyEvaluationResult{"
        + "allowed="
        + allowed
        + ", appliedPolicies="
        + appliedPolicies
        + ", riskScore="
        + riskScore
        + ", requiredActions="
        + requiredActions
        + ", processingTimeMs="
        + processingTimeMs
        + ", databaseAccessed="
        + databaseAccessed
        + '}';
  }

  /**
   * Creates a new builder for PolicyEvaluationResult.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for PolicyEvaluationResult. */
  public static final class Builder {
    private boolean allowed;
    private List<String> appliedPolicies;
    private double riskScore;
    private List<String> requiredActions;
    private long processingTimeMs;
    private Boolean databaseAccessed;

    public Builder allowed(boolean allowed) {
      this.allowed = allowed;
      return this;
    }

    public Builder appliedPolicies(List<String> appliedPolicies) {
      this.appliedPolicies = appliedPolicies;
      return this;
    }

    public Builder riskScore(double riskScore) {
      this.riskScore = riskScore;
      return this;
    }

    public Builder requiredActions(List<String> requiredActions) {
      this.requiredActions = requiredActions;
      return this;
    }

    public Builder processingTimeMs(long processingTimeMs) {
      this.processingTimeMs = processingTimeMs;
      return this;
    }

    public Builder databaseAccessed(Boolean databaseAccessed) {
      this.databaseAccessed = databaseAccessed;
      return this;
    }

    public PolicyEvaluationResult build() {
      return new PolicyEvaluationResult(
          allowed, appliedPolicies, riskScore, requiredActions, processingTimeMs, databaseAccessed);
    }
  }
}
