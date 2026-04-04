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
