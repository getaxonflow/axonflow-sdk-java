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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * A registered LLM provider returned by {@code GET /api/v1/llm-providers}.
 *
 * <p>Mirrors the platform's {@code LLMProviderResource} schema. Optional fields are populated when
 * the provider config has them set; {@code settings} is a free-form provider-specific map.
 *
 * <p><b>Source-compatibility note.</b> Pre-PR-#148 callers wrote {@code new LLMProvider( name,
 * type, true, 0, 0, true, health)} (7 args, primitive booleans/ints) and called {@code int p =
 * provider.getPriority()} / {@code int w = provider.getWeight()}. The 7-arg primitive constructor
 * and the primitive-returning {@code getPriority()} / {@code getWeight()} accessors are preserved
 * as a compatibility shim. The 13-arg boxed constructor is the Jackson entry point; new optional
 * fields default to null via the legacy constructor.
 *
 * <p>Internal storage is boxed ({@link Boolean} / {@link Integer}) so the SDK can faithfully
 * represent fields that were omitted by an older platform. New methods exposing the boxed values
 * directly are suffixed with {@code Boxed} (e.g. {@link #getPriorityBoxed()}) for callers that need
 * to distinguish "explicitly 0" from "field not present".
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

  /**
   * Full constructor used by Jackson — accepts boxed types so a missing field in the JSON response
   * stays null instead of silently becoming {@code false} / {@code 0}.
   */
  @JsonCreator
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

  /**
   * Pre-PR-#148 constructor signature — 7 args, primitive {@code boolean} / {@code int}. Preserved
   * as a compatibility shim so callers that constructed {@code LLMProvider} directly continue to
   * compile. Delegates to the full 13-arg constructor with null for the post-PR-#148 optional
   * fields.
   *
   * @deprecated Prefer the 13-arg constructor when constructing programmatically; this overload
   *     exists only to preserve compile-time source compatibility for pre-existing call sites.
   */
  @Deprecated
  public LLMProvider(
      String name,
      String type,
      boolean enabled,
      int priority,
      int weight,
      boolean hasApiKey,
      LLMProviderHealth health) {
    this(
        name,
        type,
        Boolean.valueOf(enabled),
        Integer.valueOf(priority),
        Integer.valueOf(weight),
        Boolean.valueOf(hasApiKey),
        health,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  /**
   * Convenience: returns true if the {@code enabled} field was explicitly set to true; false
   * otherwise (including when the field was omitted by the platform). Mirrors the pre-PR-#148
   * primitive-returning accessor.
   */
  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  /**
   * Returns the raw boxed {@code enabled} value. May be null if the platform omitted the field —
   * use this when you need to distinguish "explicitly false" from "not set".
   */
  public Boolean getEnabledBoxed() {
    return enabled;
  }

  /**
   * Convenience: returns the {@code priority} field as a primitive {@code int}; returns 0 when the
   * field was omitted. Mirrors the pre-PR-#148 primitive- returning accessor.
   */
  public int getPriority() {
    return priority != null ? priority : 0;
  }

  /**
   * Returns the raw boxed {@code priority}; null when the platform omitted the field — use this
   * when you need to distinguish "explicitly 0" from "not set".
   */
  public Integer getPriorityBoxed() {
    return priority;
  }

  /**
   * Convenience: returns the {@code weight} field as a primitive {@code int}; returns 0 when the
   * field was omitted. Mirrors the pre-PR-#148 primitive- returning accessor.
   */
  public int getWeight() {
    return weight != null ? weight : 0;
  }

  /**
   * Returns the raw boxed {@code weight}; null when the platform omitted the field — use this when
   * you need to distinguish "explicitly 0" from "not set".
   */
  public Integer getWeightBoxed() {
    return weight;
  }

  /**
   * Convenience: returns true if {@code has_api_key} was explicitly set to true; false otherwise
   * (including when the field was omitted). Mirrors the pre- PR-#148 primitive-returning accessor.
   */
  public boolean hasApiKey() {
    return Boolean.TRUE.equals(hasApiKey);
  }

  /**
   * Returns the raw boxed {@code has_api_key}; null when the platform omitted the field — use this
   * when you need to distinguish "explicitly false" from "not set".
   */
  public Boolean getHasApiKeyBoxed() {
    return hasApiKey;
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
