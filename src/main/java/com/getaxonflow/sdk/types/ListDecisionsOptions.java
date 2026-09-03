// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import java.time.Instant;

/**
 * Optional filters for {@code AxonFlow.listDecisions}.
 *
 * <p>Every field is optional; null values are omitted from the URL so the platform applies its
 * tier-default page. {@code decision}, when set, must be one of the canonical audit verdicts {@code
 * "allowed"}, {@code "blocked"}, {@code "redacted"}, {@code "needs_approval"}, or {@code "error"}
 * (platform 9.0.0+); the pre-9.0.0 values {@code "allow"} / {@code "deny"} / {@code
 * "require_approval"} are rejected with HTTP 400 by 9.0.0 (see
 * https://docs.getaxonflow.com/docs/deployment/v8-to-v9-migration/). {@code limit} is server-capped
 * per tier; over-cap requests yield a 429 with the V1 upgrade envelope (surfaced as {@link
 * com.getaxonflow.sdk.exceptions.RateLimitException} carrying upgrade info).
 *
 * <p>Use the builder for ergonomic optional construction:
 *
 * <pre>{@code
 * ListDecisionsOptions opts = ListDecisionsOptions.builder()
 *     .decision("blocked")
 *     .limit(10)
 *     .build();
 * List<DecisionSummary> decisions = client.listDecisions(opts);
 * }</pre>
 */
public final class ListDecisionsOptions {

  private final Instant since;
  private final String decision;
  private final String policyId;
  private final String toolSignature;
  private final Integer limit;

  private ListDecisionsOptions(Builder b) {
    this.since = b.since;
    this.decision = b.decision;
    this.policyId = b.policyId;
    this.toolSignature = b.toolSignature;
    this.limit = b.limit;
  }

  public Instant getSince() {
    return since;
  }

  public String getDecision() {
    return decision;
  }

  public String getPolicyId() {
    return policyId;
  }

  public String getToolSignature() {
    return toolSignature;
  }

  /** May be null — meaning "use tier default". */
  public Integer getLimit() {
    return limit;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Empty options — all filters unset, server applies tier defaults. */
  public static ListDecisionsOptions empty() {
    return builder().build();
  }

  public static final class Builder {
    private Instant since;
    private String decision;
    private String policyId;
    private String toolSignature;
    private Integer limit;

    public Builder since(Instant since) {
      this.since = since;
      return this;
    }

    public Builder decision(String decision) {
      this.decision = decision;
      return this;
    }

    public Builder policyId(String policyId) {
      this.policyId = policyId;
      return this;
    }

    public Builder toolSignature(String toolSignature) {
      this.toolSignature = toolSignature;
      return this;
    }

    public Builder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public ListDecisionsOptions build() {
      return new ListDecisionsOptions(this);
    }
  }
}
