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
import java.util.Map;

/**
 * A registered LLM provider returned by {@code GET /api/v1/llm-providers}.
 *
 * <p>Mirrors the platform's {@code LLMProviderResource} schema. Optional fields are
 * populated when the provider config has them set; {@code settings} is a free-form
 * provider-specific map.
 *
 * <p>{@code enabled} and {@code hasApiKey} are typed as {@link Boolean} (boxed) so a
 * missing or {@code null} value in the JSON response is distinguishable from the
 * explicit boolean values — primitive {@code boolean} would silently default to
 * {@code false} and mask whether the field was actually emitted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LLMProvider {

  private final String name;
  private final String type;
  private final Boolean enabled;
  private final Integer priority;
  private final Integer weight;
  private final Boolean hasApiKey;
  private final LLMProviderHealth health;
  private final String endpoint;
  private final String model;
  private final String region;
  private final Integer rateLimit;
  private final Integer timeoutSeconds;
  private final Map<String, Object> settings;

  public LLMProvider(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("enabled") Boolean enabled,
      @JsonProperty("priority") Integer priority,
      @JsonProperty("weight") Integer weight,
      @JsonProperty("has_api_key") Boolean hasApiKey,
      @JsonProperty("health") LLMProviderHealth health,
      @JsonProperty("endpoint") String endpoint,
      @JsonProperty("model") String model,
      @JsonProperty("region") String region,
      @JsonProperty("rate_limit") Integer rateLimit,
      @JsonProperty("timeout_seconds") Integer timeoutSeconds,
      @JsonProperty("settings") Map<String, Object> settings) {
    this.name = name;
    this.type = type;
    this.enabled = enabled;
    this.priority = priority;
    this.weight = weight;
    this.hasApiKey = hasApiKey;
    this.health = health;
    this.endpoint = endpoint;
    this.model = model;
    this.region = region;
    this.rateLimit = rateLimit;
    this.timeoutSeconds = timeoutSeconds;
    this.settings = settings;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  /** May be null if the platform omitted the field. */
  public Boolean getEnabled() {
    return enabled;
  }

  /** Convenience: true if explicitly enabled, false otherwise (including null). */
  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  /** May be null if the platform omitted the field. */
  public Integer getPriority() {
    return priority;
  }

  /** May be null if the platform omitted the field. */
  public Integer getWeight() {
    return weight;
  }

  /** May be null if the platform omitted the field. */
  public Boolean getHasApiKey() {
    return hasApiKey;
  }

  /** Convenience: true if has_api_key is explicitly true, false otherwise (including null). */
  public boolean hasApiKey() {
    return Boolean.TRUE.equals(hasApiKey);
  }

  /** Health snapshot; may be null if the platform did not return a health probe. */
  public LLMProviderHealth getHealth() {
    return health;
  }

  /** API endpoint URL; null if unset on this provider. */
  public String getEndpoint() {
    return endpoint;
  }

  /** Default model name; null if unset on this provider. */
  public String getModel() {
    return model;
  }

  /** AWS region (Bedrock); null if unset on this provider. */
  public String getRegion() {
    return region;
  }

  /** Max requests per second; null if unset on this provider. */
  public Integer getRateLimit() {
    return rateLimit;
  }

  /** Request timeout in seconds; null if unset on this provider. */
  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  /** Free-form provider-specific settings; null if unset. */
  public Map<String, Object> getSettings() {
    return settings;
  }
}
