// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/** Response from PR creation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CreatePRResponse {

  @JsonProperty("pr_id")
  private final String prId;

  @JsonProperty("pr_number")
  private final int prNumber;

  @JsonProperty("pr_url")
  private final String prUrl;

  @JsonProperty("state")
  private final String state;

  @JsonProperty("head_branch")
  private final String headBranch;

  @JsonProperty("created_at")
  private final Instant createdAt;

  public CreatePRResponse(
      @JsonProperty("pr_id") String prId,
      @JsonProperty("pr_number") int prNumber,
      @JsonProperty("pr_url") String prUrl,
      @JsonProperty("state") String state,
      @JsonProperty("head_branch") String headBranch,
      @JsonProperty("created_at") Instant createdAt) {
    this.prId = prId;
    this.prNumber = prNumber;
    this.prUrl = prUrl;
    this.state = state;
    this.headBranch = headBranch;
    this.createdAt = createdAt;
  }

  public String getPrId() {
    return prId;
  }

  public int getPrNumber() {
    return prNumber;
  }

  public String getPrUrl() {
    return prUrl;
  }

  public String getState() {
    return state;
  }

  public String getHeadBranch() {
    return headBranch;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CreatePRResponse that = (CreatePRResponse) o;
    return prNumber == that.prNumber
        && Objects.equals(prId, that.prId)
        && Objects.equals(prUrl, that.prUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prId, prNumber, prUrl);
  }

  @Override
  public String toString() {
    return "CreatePRResponse{"
        + "prId='"
        + prId
        + '\''
        + ", prNumber="
        + prNumber
        + ", prUrl='"
        + prUrl
        + '\''
        + ", state='"
        + state
        + '\''
        + '}';
  }
}
