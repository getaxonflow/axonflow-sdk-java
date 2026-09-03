// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
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
