// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

import java.time.Duration;
import java.time.Instant;

/**
 * Thrown when the rate limit has been exceeded.
 *
 * <p>For V1 tier-cap 429s (e.g. {@code listDecisions} page-size cap, daily-quota cap), {@link
 * #getLimitType()}, {@link #getTier()}, and {@link #getUpgrade()} are populated from the
 * platform-side feedback_429_no_upgrade_hint_is_conversion_gap.md envelope. Legacy 429s leave them
 * null for backwards compatibility.
 */
public class RateLimitException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final int limit;
  private final int remaining;
  private final Instant resetAt;
  private final String limitType;
  private final String tier;
  private final UpgradeInfo upgrade;

  /**
   * Creates a new RateLimitException.
   *
   * @param message the error message
   */
  public RateLimitException(String message) {
    super(message, 429, "RATE_LIMIT_EXCEEDED");
    this.limit = 0;
    this.remaining = 0;
    this.resetAt = null;
    this.limitType = null;
    this.tier = null;
    this.upgrade = null;
  }

  /**
   * Creates a new RateLimitException with rate limit details.
   *
   * @param message the error message
   * @param limit the maximum requests allowed
   * @param remaining the remaining requests in the current window
   * @param resetAt when the rate limit resets
   */
  public RateLimitException(String message, int limit, int remaining, Instant resetAt) {
    super(message, 429, "RATE_LIMIT_EXCEEDED");
    this.limit = limit;
    this.remaining = remaining;
    this.resetAt = resetAt;
    this.limitType = null;
    this.tier = null;
    this.upgrade = null;
  }

  /**
   * Creates a new RateLimitException carrying the V1 upgrade envelope. Used by {@code
   * AxonFlow.listDecisions} (and other tier-capped endpoints) so callers can branch on the upgrade
   * fields without re-parsing the body.
   *
   * @param message the error message
   * @param limit the maximum requests allowed
   * @param remaining the remaining requests in the current window
   * @param resetAt when the rate limit resets (may be null)
   * @param limitType the platform-side limit identifier (e.g. {@code decision_list_size})
   * @param tier the caller's current tier (e.g. {@code Community})
   * @param upgrade the upgrade context (tier / wording / compareUrl / buyUrl)
   */
  public RateLimitException(
      String message,
      int limit,
      int remaining,
      Instant resetAt,
      String limitType,
      String tier,
      UpgradeInfo upgrade) {
    super(message, 429, "RATE_LIMIT_EXCEEDED");
    this.limit = limit;
    this.remaining = remaining;
    this.resetAt = resetAt;
    this.limitType = limitType;
    this.tier = tier;
    this.upgrade = upgrade;
  }

  /**
   * Returns the maximum number of requests allowed.
   *
   * @return the rate limit
   */
  public int getLimit() {
    return limit;
  }

  /**
   * Returns the remaining requests in the current window.
   *
   * @return the remaining count
   */
  public int getRemaining() {
    return remaining;
  }

  /**
   * Returns when the rate limit resets.
   *
   * @return the reset time
   */
  public Instant getResetAt() {
    return resetAt;
  }

  /**
   * Returns the duration until the rate limit resets.
   *
   * @return the duration until reset, or Duration.ZERO if already reset
   */
  public Duration getRetryAfter() {
    if (resetAt == null) {
      return Duration.ZERO;
    }
    Duration duration = Duration.between(Instant.now(), resetAt);
    return duration.isNegative() ? Duration.ZERO : duration;
  }

  /**
   * Returns the platform-side limit identifier (e.g. {@code decision_list_size}, {@code
   * daily_request_count}). Null for legacy 429s without a V1 envelope.
   *
   * @return the limit_type or null
   */
  public String getLimitType() {
    return limitType;
  }

  /**
   * Returns the caller's current pricing tier (e.g. {@code Community}, {@code Free}). Null for
   * legacy 429s without a V1 envelope.
   *
   * @return the tier or null
   */
  public String getTier() {
    return tier;
  }

  /**
   * Returns the upgrade context (tier, wording, compareUrl, buyUrl) so callers can surface a
   * tier-upgrade affordance to the user. Null when the 429 didn't carry a V1 envelope.
   *
   * @return the upgrade info or null
   */
  public UpgradeInfo getUpgrade() {
    return upgrade;
  }

  /**
   * Pricing-tier upgrade context emitted in a V1 429 envelope.
   *
   * <p>Cross-SDK parity:
   *
   * <ul>
   *   <li>Go: axonflow-sdk-go/decisions.go (UpgradeInfo)
   *   <li>Python: axonflow-sdk-python/axonflow/exceptions.py (UpgradeInfo)
   *   <li>TS: axonflow-sdk-typescript/src/errors.ts (UpgradeInfo)
   *   <li>Rust: axonflow-sdk-rust/src/types/decisions.rs (UpgradeInfo)
   * </ul>
   */
  public static final class UpgradeInfo {
    private final String tier;
    private final String wording;
    private final String compareUrl;
    private final String buyUrl;

    public UpgradeInfo(String tier, String wording, String compareUrl, String buyUrl) {
      this.tier = tier;
      this.wording = wording;
      this.compareUrl = compareUrl;
      this.buyUrl = buyUrl;
    }

    public String getTier() {
      return tier;
    }

    public String getWording() {
      return wording;
    }

    public String getCompareUrl() {
      return compareUrl;
    }

    public String getBuyUrl() {
      return buyUrl;
    }
  }
}
