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
 * A registered LLM provider returned by {@code GET /api/v1/llm-providers}.
 *
 * <p>Mirrors {@code LLMProvider} in the Python and Go SDKs and the {@code LLMProvider}
 * TypeScript interface.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LLMProvider {

  private final String name;
  private final String type;
  private final boolean enabled;
  private final int priority;
  private final int weight;
  private final boolean hasApiKey;
  private final LLMProviderHealth health;

  public LLMProvider(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("enabled") boolean enabled,
      @JsonProperty("priority") int priority,
      @JsonProperty("weight") int weight,
      @JsonProperty("has_api_key") boolean hasApiKey,
      @JsonProperty("health") LLMProviderHealth health) {
    this.name = name;
    this.type = type;
    this.enabled = enabled;
    this.priority = priority;
    this.weight = weight;
    this.hasApiKey = hasApiKey;
    this.health = health;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getPriority() {
    return priority;
  }

  public int getWeight() {
    return weight;
  }

  @JsonProperty("has_api_key")
  public boolean hasApiKey() {
    return hasApiKey;
  }

  /** Health snapshot; may be null if the platform did not return a health probe. */
  public LLMProviderHealth getHealth() {
    return health;
  }
}
