/*
 * Copyright 2025 AxonFlow
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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health snapshot for a registered LLM provider, returned inside an {@link
 * LLMProvider} record from {@code GET /api/v1/llm-providers}.
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
