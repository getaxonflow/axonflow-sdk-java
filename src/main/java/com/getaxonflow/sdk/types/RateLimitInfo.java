// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/** Contains rate limiting information from AxonFlow responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RateLimitInfo {

  @JsonProperty("limit")
  private final int limit;

  @JsonProperty("remaining")
  private final int remaining;

  @JsonProperty("reset_at")
  private final Instant resetAt;

  public RateLimitInfo(
      @JsonProperty("limit") int limit,
      @JsonProperty("remaining") int remaining,
      @JsonProperty("reset_at") Instant resetAt) {
    this.limit = limit;
    this.remaining = remaining;
    this.resetAt = resetAt;
  }

  /**
   * Returns the maximum number of requests allowed in the current window.
   *
   * @return the rate limit
   */
  public int getLimit() {
    return limit;
  }

  /**
   * Returns the number of requests remaining in the current window.
   *
   * @return remaining requests
   */
  public int getRemaining() {
    return remaining;
  }

  /**
   * Returns when the rate limit window resets.
   *
   * @return the reset timestamp
   */
  public Instant getResetAt() {
    return resetAt;
  }

  /**
   * Checks if the rate limit has been exceeded.
   *
   * @return true if no requests remain
   */
  public boolean isExceeded() {
    return remaining <= 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RateLimitInfo that = (RateLimitInfo) o;
    return limit == that.limit
        && remaining == that.remaining
        && Objects.equals(resetAt, that.resetAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(limit, remaining, resetAt);
  }

  @Override
  public String toString() {
    return "RateLimitInfo{"
        + "limit="
        + limit
        + ", remaining="
        + remaining
        + ", resetAt="
        + resetAt
        + '}';
  }
}
