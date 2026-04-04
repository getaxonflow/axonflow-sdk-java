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

/** A detected conflict between policies. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyConflict {

  @JsonProperty("policy_a")
  private final PolicyConflictRef policyA;

  @JsonProperty("policy_b")
  private final PolicyConflictRef policyB;

  @JsonProperty("conflict_type")
  private final String conflictType;

  @JsonProperty("description")
  private final String description;

  @JsonProperty("severity")
  private final String severity;

  @JsonProperty("overlapping_field")
  private final String overlappingField;

  public PolicyConflict(
      @JsonProperty("policy_a") PolicyConflictRef policyA,
      @JsonProperty("policy_b") PolicyConflictRef policyB,
      @JsonProperty("conflict_type") String conflictType,
      @JsonProperty("description") String description,
      @JsonProperty("severity") String severity,
      @JsonProperty("overlapping_field") String overlappingField) {
    this.policyA = policyA;
    this.policyB = policyB;
    this.conflictType = conflictType;
    this.description = description;
    this.severity = severity;
    this.overlappingField = overlappingField;
  }

  public PolicyConflictRef getPolicyA() {
    return policyA;
  }

  public PolicyConflictRef getPolicyB() {
    return policyB;
  }

  public String getConflictType() {
    return conflictType;
  }

  public String getDescription() {
    return description;
  }

  public String getSeverity() {
    return severity;
  }

  public String getOverlappingField() {
    return overlappingField;
  }
}
