// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Response listing PRs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ListPRsResponse {

  @JsonProperty("prs")
  private final List<PRRecord> prs;

  @JsonProperty("count")
  private final int count;

  public ListPRsResponse(
      @JsonProperty("prs") List<PRRecord> prs, @JsonProperty("count") int count) {
    this.prs = prs != null ? Collections.unmodifiableList(prs) : Collections.emptyList();
    this.count = count;
  }

  public List<PRRecord> getPrs() {
    return prs;
  }

  public int getCount() {
    return count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListPRsResponse that = (ListPRsResponse) o;
    return count == that.count && Objects.equals(prs, that.prs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prs, count);
  }

  @Override
  public String toString() {
    return "ListPRsResponse{" + "prs=" + prs + ", count=" + count + '}';
  }
}
