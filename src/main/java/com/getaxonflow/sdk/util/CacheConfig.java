// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.util;

import java.time.Duration;
import java.util.Objects;

/** Configuration for caching behavior. */
public final class CacheConfig {

  /** Default TTL for cached entries. */
  public static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

  /** Default maximum cache size. */
  public static final int DEFAULT_MAX_SIZE = 1000;

  private final boolean enabled;
  private final Duration ttl;
  private final int maxSize;

  private CacheConfig(Builder builder) {
    this.enabled = builder.enabled;
    this.ttl = builder.ttl;
    this.maxSize = builder.maxSize;
  }

  /**
   * Returns default cache configuration.
   *
   * @return default configuration with caching enabled
   */
  public static CacheConfig defaults() {
    return builder().build();
  }

  /**
   * Returns configuration with caching disabled.
   *
   * @return configuration with caching disabled
   */
  public static CacheConfig disabled() {
    return builder().enabled(false).build();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Duration getTtl() {
    return ttl;
  }

  public int getMaxSize() {
    return maxSize;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CacheConfig that = (CacheConfig) o;
    return enabled == that.enabled && maxSize == that.maxSize && Objects.equals(ttl, that.ttl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, ttl, maxSize);
  }

  @Override
  public String toString() {
    return "CacheConfig{" + "enabled=" + enabled + ", ttl=" + ttl + ", maxSize=" + maxSize + '}';
  }

  /** Builder for CacheConfig. */
  public static final class Builder {
    private boolean enabled = true;
    private Duration ttl = DEFAULT_TTL;
    private int maxSize = DEFAULT_MAX_SIZE;

    private Builder() {}

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder ttl(Duration ttl) {
      if (ttl == null || ttl.isNegative()) {
        throw new IllegalArgumentException("ttl must be non-negative");
      }
      this.ttl = ttl;
      return this;
    }

    public Builder maxSize(int maxSize) {
      if (maxSize < 1) {
        throw new IllegalArgumentException("maxSize must be at least 1");
      }
      this.maxSize = maxSize;
      return this;
    }

    public CacheConfig build() {
      return new CacheConfig(this);
    }
  }
}
