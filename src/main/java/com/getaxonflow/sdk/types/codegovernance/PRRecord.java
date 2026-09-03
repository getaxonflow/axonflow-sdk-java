// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/** A PR record in the system. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PRRecord {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("pr_number")
  private final int prNumber;

  @JsonProperty("pr_url")
  private final String prUrl;

  @JsonProperty("title")
  private final String title;

  @JsonProperty("state")
  private final String state;

  @JsonProperty("owner")
  private final String owner;

  @JsonProperty("repo")
  private final String repo;

  @JsonProperty("head_branch")
  private final String headBranch;

  @JsonProperty("base_branch")
  private final String baseBranch;

  @JsonProperty("files_count")
  private final int filesCount;

  @JsonProperty("secrets_detected")
  private final int secretsDetected;

  @JsonProperty("unsafe_patterns")
  private final int unsafePatterns;

  @JsonProperty("created_at")
  private final Instant createdAt;

  @JsonProperty("closed_at")
  private final Instant closedAt;

  @JsonProperty("created_by")
  private final String createdBy;

  @JsonProperty("provider_type")
  private final String providerType;

  public PRRecord(
      @JsonProperty("id") String id,
      @JsonProperty("pr_number") int prNumber,
      @JsonProperty("pr_url") String prUrl,
      @JsonProperty("title") String title,
      @JsonProperty("state") String state,
      @JsonProperty("owner") String owner,
      @JsonProperty("repo") String repo,
      @JsonProperty("head_branch") String headBranch,
      @JsonProperty("base_branch") String baseBranch,
      @JsonProperty("files_count") int filesCount,
      @JsonProperty("secrets_detected") int secretsDetected,
      @JsonProperty("unsafe_patterns") int unsafePatterns,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("closed_at") Instant closedAt,
      @JsonProperty("created_by") String createdBy,
      @JsonProperty("provider_type") String providerType) {
    this.id = id;
    this.prNumber = prNumber;
    this.prUrl = prUrl;
    this.title = title;
    this.state = state;
    this.owner = owner;
    this.repo = repo;
    this.headBranch = headBranch;
    this.baseBranch = baseBranch;
    this.filesCount = filesCount;
    this.secretsDetected = secretsDetected;
    this.unsafePatterns = unsafePatterns;
    this.createdAt = createdAt;
    this.closedAt = closedAt;
    this.createdBy = createdBy;
    this.providerType = providerType;
  }

  public String getId() {
    return id;
  }

  public int getPrNumber() {
    return prNumber;
  }

  public String getPrUrl() {
    return prUrl;
  }

  public String getTitle() {
    return title;
  }

  public String getState() {
    return state;
  }

  public String getOwner() {
    return owner;
  }

  public String getRepo() {
    return repo;
  }

  public String getHeadBranch() {
    return headBranch;
  }

  public String getBaseBranch() {
    return baseBranch;
  }

  public int getFilesCount() {
    return filesCount;
  }

  public int getSecretsDetected() {
    return secretsDetected;
  }

  public int getUnsafePatterns() {
    return unsafePatterns;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getProviderType() {
    return providerType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PRRecord prRecord = (PRRecord) o;
    return prNumber == prRecord.prNumber
        && Objects.equals(id, prRecord.id)
        && Objects.equals(owner, prRecord.owner)
        && Objects.equals(repo, prRecord.repo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, prNumber, owner, repo);
  }

  @Override
  public String toString() {
    return "PRRecord{"
        + "id='"
        + id
        + '\''
        + ", prNumber="
        + prNumber
        + ", title='"
        + title
        + '\''
        + ", state='"
        + state
        + '\''
        + ", owner='"
        + owner
        + '\''
        + ", repo='"
        + repo
        + '\''
        + '}';
  }
}
