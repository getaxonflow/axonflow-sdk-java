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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Response from an audit search operation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuditSearchResponse {

  @JsonProperty("entries")
  private final List<AuditLogEntry> entries;

  @JsonProperty("total")
  private final int total;

  @JsonProperty("limit")
  private final int limit;

  @JsonProperty("offset")
  private final int offset;

  public AuditSearchResponse(
      @JsonProperty("entries") List<AuditLogEntry> entries,
      @JsonProperty("total") Integer total,
      @JsonProperty("limit") Integer limit,
      @JsonProperty("offset") Integer offset) {
    this.entries = entries != null ? entries : Collections.emptyList();
    this.total = total != null ? total : this.entries.size();
    this.limit = limit != null ? limit : 100;
    this.offset = offset != null ? offset : 0;
  }

  /** Creates a response with the given entries and metadata. */
  public static AuditSearchResponse of(
      List<AuditLogEntry> entries, int total, int limit, int offset) {
    return new AuditSearchResponse(entries, total, limit, offset);
  }

  /** Creates a response from an array (direct API response format). */
  public static AuditSearchResponse fromArray(List<AuditLogEntry> entries, int limit, int offset) {
    return new AuditSearchResponse(entries, entries.size(), limit, offset);
  }

  /** Returns the audit log entries matching the search. */
  public List<AuditLogEntry> getEntries() {
    return entries;
  }

  /** Returns the total number of matching entries (for pagination). */
  public int getTotal() {
    return total;
  }

  /** Returns the limit that was applied. */
  public int getLimit() {
    return limit;
  }

  /** Returns the offset that was applied. */
  public int getOffset() {
    return offset;
  }

  /** Returns true if there are more results available. */
  public boolean hasMore() {
    return offset + entries.size() < total;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AuditSearchResponse that = (AuditSearchResponse) o;
    return total == that.total
        && limit == that.limit
        && offset == that.offset
        && Objects.equals(entries, that.entries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entries, total, limit, offset);
  }

  @Override
  public String toString() {
    return "AuditSearchResponse{"
        + "entriesCount="
        + entries.size()
        + ", total="
        + total
        + ", limit="
        + limit
        + ", offset="
        + offset
        + '}';
  }
}
