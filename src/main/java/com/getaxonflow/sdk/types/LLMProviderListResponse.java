/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/** Paginated wrapper returned by {@code GET /api/v1/llm-providers}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LLMProviderListResponse {

  private final List<LLMProvider> providers;
  private final PaginationMeta pagination;

  public LLMProviderListResponse(
      @JsonProperty("providers") List<LLMProvider> providers,
      @JsonProperty("pagination") PaginationMeta pagination) {
    this.providers = providers != null ? providers : Collections.emptyList();
    this.pagination = pagination;
  }

  public List<LLMProvider> getProviders() {
    return providers;
  }

  /** May be null if the server didn't return a pagination block. */
  public PaginationMeta getPagination() {
    return pagination;
  }
}
