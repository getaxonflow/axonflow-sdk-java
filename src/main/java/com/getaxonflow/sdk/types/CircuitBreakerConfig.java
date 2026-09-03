// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Circuit breaker configuration for a tenant. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CircuitBreakerConfig {

  @JsonProperty("source")
  private final String source;

  @JsonProperty("error_threshold")
  private final int errorThreshold;

  @JsonProperty("violation_threshold")
  private final int violationThreshold;

  @JsonProperty("window_seconds")
  private final int windowSeconds;

  @JsonProperty("default_timeout_seconds")
  private final int defaultTimeoutSeconds;

  @JsonProperty("max_timeout_seconds")
  private final int maxTimeoutSeconds;

  @JsonProperty("enable_auto_recovery")
  private final boolean enableAutoRecovery;

  @JsonProperty("tenant_id")
  private final String tenantId;

  @JsonProperty("overrides")
  private final Map<String, Object> overrides;

  public CircuitBreakerConfig(
      @JsonProperty("source") String source,
      @JsonProperty("error_threshold") int errorThreshold,
      @JsonProperty("violation_threshold") int violationThreshold,
      @JsonProperty("window_seconds") int windowSeconds,
      @JsonProperty("default_timeout_seconds") int defaultTimeoutSeconds,
      @JsonProperty("max_timeout_seconds") int maxTimeoutSeconds,
      @JsonProperty("enable_auto_recovery") boolean enableAutoRecovery,
      @JsonProperty("tenant_id") String tenantId,
      @JsonProperty("overrides") Map<String, Object> overrides) {
    this.source = source;
    this.errorThreshold = errorThreshold;
    this.violationThreshold = violationThreshold;
    this.windowSeconds = windowSeconds;
    this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    this.maxTimeoutSeconds = maxTimeoutSeconds;
    this.enableAutoRecovery = enableAutoRecovery;
    this.tenantId = tenantId;
    this.overrides = overrides != null ? Map.copyOf(overrides) : null;
  }

  public String getSource() {
    return source;
  }

  public int getErrorThreshold() {
    return errorThreshold;
  }

  public int getViolationThreshold() {
    return violationThreshold;
  }

  public int getWindowSeconds() {
    return windowSeconds;
  }

  public int getDefaultTimeoutSeconds() {
    return defaultTimeoutSeconds;
  }

  public int getMaxTimeoutSeconds() {
    return maxTimeoutSeconds;
  }

  public boolean isEnableAutoRecovery() {
    return enableAutoRecovery;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Map<String, Object> getOverrides() {
    return overrides;
  }
}
