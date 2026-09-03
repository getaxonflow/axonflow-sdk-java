// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A single entry in circuit breaker history. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CircuitBreakerHistoryEntry {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("org_id")
  private final String orgId;

  @JsonProperty("scope")
  private final String scope;

  @JsonProperty("scope_id")
  private final String scopeId;

  @JsonProperty("state")
  private final String state;

  @JsonProperty("trip_reason")
  private final String tripReason;

  @JsonProperty("tripped_by")
  private final String trippedBy;

  @JsonProperty("tripped_at")
  private final String trippedAt;

  @JsonProperty("expires_at")
  private final String expiresAt;

  @JsonProperty("reset_by")
  private final String resetBy;

  @JsonProperty("reset_at")
  private final String resetAt;

  @JsonProperty("error_count")
  private final int errorCount;

  @JsonProperty("violation_count")
  private final int violationCount;

  public CircuitBreakerHistoryEntry(
      @JsonProperty("id") String id,
      @JsonProperty("org_id") String orgId,
      @JsonProperty("scope") String scope,
      @JsonProperty("scope_id") String scopeId,
      @JsonProperty("state") String state,
      @JsonProperty("trip_reason") String tripReason,
      @JsonProperty("tripped_by") String trippedBy,
      @JsonProperty("tripped_at") String trippedAt,
      @JsonProperty("expires_at") String expiresAt,
      @JsonProperty("reset_by") String resetBy,
      @JsonProperty("reset_at") String resetAt,
      @JsonProperty("error_count") int errorCount,
      @JsonProperty("violation_count") int violationCount) {
    this.id = id;
    this.orgId = orgId;
    this.scope = scope;
    this.scopeId = scopeId;
    this.state = state;
    this.tripReason = tripReason;
    this.trippedBy = trippedBy;
    this.trippedAt = trippedAt;
    this.expiresAt = expiresAt;
    this.resetBy = resetBy;
    this.resetAt = resetAt;
    this.errorCount = errorCount;
    this.violationCount = violationCount;
  }

  public String getId() {
    return id;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getScope() {
    return scope;
  }

  public String getScopeId() {
    return scopeId;
  }

  public String getState() {
    return state;
  }

  public String getTripReason() {
    return tripReason;
  }

  public String getTrippedBy() {
    return trippedBy;
  }

  public String getTrippedAt() {
    return trippedAt;
  }

  public String getExpiresAt() {
    return expiresAt;
  }

  public String getResetBy() {
    return resetBy;
  }

  public String getResetAt() {
    return resetAt;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public int getViolationCount() {
    return violationCount;
  }
}
