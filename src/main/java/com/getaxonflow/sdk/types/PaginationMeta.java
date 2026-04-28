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

/** Pagination metadata returned alongside paginated list responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PaginationMeta {

  private final int page;
  private final int pageSize;
  private final int totalItems;
  private final int totalPages;

  public PaginationMeta(
      @JsonProperty("page") int page,
      @JsonProperty("page_size") int pageSize,
      @JsonProperty("total_items") int totalItems,
      @JsonProperty("total_pages") int totalPages) {
    this.page = page;
    this.pageSize = pageSize;
    this.totalItems = totalItems;
    this.totalPages = totalPages;
  }

  public int getPage() {
    return page;
  }

  public int getPageSize() {
    return pageSize;
  }

  public int getTotalItems() {
    return totalItems;
  }

  public int getTotalPages() {
    return totalPages;
  }
}
