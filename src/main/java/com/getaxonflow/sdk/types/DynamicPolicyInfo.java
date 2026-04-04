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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Information about dynamic policy evaluation (Issue #968).
 *
 * <p>Dynamic policies are evaluated by the Orchestrator and can include rate limiting, budget
 * controls, time-based access, and role-based access.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DynamicPolicyInfo {

  @JsonProperty("policies_evaluated")
  private final int policiesEvaluated;

  @JsonProperty("matched_policies")
  private final List<DynamicPolicyMatch> matchedPolicies;

  @JsonProperty("orchestrator_reachable")
  private final boolean orchestratorReachable;

  @JsonProperty("processing_time_ms")
  private final long processingTimeMs;

  public DynamicPolicyInfo(
      @JsonProperty("policies_evaluated") int policiesEvaluated,
      @JsonProperty("matched_policies") List<DynamicPolicyMatch> matchedPolicies,
      @JsonProperty("orchestrator_reachable") boolean orchestratorReachable,
      @JsonProperty("processing_time_ms") long processingTimeMs) {
    this.policiesEvaluated = policiesEvaluated;
    this.matchedPolicies = matchedPolicies != null ? matchedPolicies : Collections.emptyList();
    this.orchestratorReachable = orchestratorReachable;
    this.processingTimeMs = processingTimeMs;
  }

  /** Returns the number of dynamic policies checked. */
  public int getPoliciesEvaluated() {
    return policiesEvaluated;
  }

  /** Returns details about policies that matched. */
  public List<DynamicPolicyMatch> getMatchedPolicies() {
    return matchedPolicies;
  }

  /** Returns whether the Orchestrator was reachable. */
  public boolean isOrchestratorReachable() {
    return orchestratorReachable;
  }

  /** Returns the time taken for dynamic policy evaluation in milliseconds. */
  public long getProcessingTimeMs() {
    return processingTimeMs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DynamicPolicyInfo that = (DynamicPolicyInfo) o;
    return policiesEvaluated == that.policiesEvaluated
        && orchestratorReachable == that.orchestratorReachable
        && processingTimeMs == that.processingTimeMs
        && Objects.equals(matchedPolicies, that.matchedPolicies);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        policiesEvaluated, matchedPolicies, orchestratorReachable, processingTimeMs);
  }

  @Override
  public String toString() {
    return "DynamicPolicyInfo{"
        + "policiesEvaluated="
        + policiesEvaluated
        + ", matchedPolicies="
        + matchedPolicies
        + ", orchestratorReachable="
        + orchestratorReachable
        + ", processingTimeMs="
        + processingTimeMs
        + '}';
  }
}
