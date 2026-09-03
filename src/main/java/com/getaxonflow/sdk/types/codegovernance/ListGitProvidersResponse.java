// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Response listing configured Git providers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ListGitProvidersResponse {

  @JsonProperty("providers")
  private final List<GitProviderInfo> providers;

  @JsonProperty("count")
  private final int count;

  public ListGitProvidersResponse(
      @JsonProperty("providers") List<GitProviderInfo> providers,
      @JsonProperty("count") int count) {
    this.providers =
        providers != null ? Collections.unmodifiableList(providers) : Collections.emptyList();
    this.count = count;
  }

  public List<GitProviderInfo> getProviders() {
    return providers;
  }

  public int getCount() {
    return count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListGitProvidersResponse that = (ListGitProvidersResponse) o;
    return count == that.count && Objects.equals(providers, that.providers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(providers, count);
  }

  @Override
  public String toString() {
    return "ListGitProvidersResponse{" + "providers=" + providers + ", count=" + count + '}';
  }
}
