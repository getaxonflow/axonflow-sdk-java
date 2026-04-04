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

import java.util.Objects;

/** Options for listing PRs. */
public final class ListPRsOptions {

  private final Integer limit;
  private final Integer offset;
  private final String state;

  private ListPRsOptions(Integer limit, Integer offset, String state) {
    this.limit = limit;
    this.offset = offset;
    this.state = state;
  }

  public Integer getLimit() {
    return limit;
  }

  public Integer getOffset() {
    return offset;
  }

  public String getState() {
    return state;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Integer limit;
    private Integer offset;
    private String state;

    public Builder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public Builder offset(Integer offset) {
      this.offset = offset;
      return this;
    }

    public Builder state(String state) {
      this.state = state;
      return this;
    }

    public ListPRsOptions build() {
      return new ListPRsOptions(limit, offset, state);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListPRsOptions that = (ListPRsOptions) o;
    return Objects.equals(limit, that.limit)
        && Objects.equals(offset, that.offset)
        && Objects.equals(state, that.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(limit, offset, state);
  }

  @Override
  public String toString() {
    return "ListPRsOptions{"
        + "limit="
        + limit
        + ", offset="
        + offset
        + ", state='"
        + state
        + '\''
        + '}';
  }
}
