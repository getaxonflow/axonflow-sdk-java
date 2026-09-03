// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health snapshot for a registered LLM provider, returned inside an {@link LLMProvider} record from
 * {@code GET /api/v1/llm-providers}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LLMProviderHealth {

  private final String status;
  private final String message;
  private final String lastChecked;

  public LLMProviderHealth(
      @JsonProperty("status") String status,
      @JsonProperty("message") String message,
      @JsonProperty("last_checked") String lastChecked) {
    this.status = status;
    this.message = message;
    this.lastChecked = lastChecked;
  }

  /** Coarse health label: {@code "healthy"}, {@code "unhealthy"}, or {@code "unknown"}. */
  public String getStatus() {
    return status;
  }

  /** Optional human-readable detail (e.g. {@code "billing exceeded"}); may be null. */
  public String getMessage() {
    return message;
  }

  /** ISO 8601 timestamp of the last health probe; may be null. */
  public String getLastChecked() {
    return lastChecked;
  }
}
