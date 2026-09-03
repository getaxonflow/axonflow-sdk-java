// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Information about exfiltration limit checks (Issue #966).
 *
 * <p>Helps prevent large-scale data extraction via MCP queries by enforcing row count and data
 * volume limits on responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ExfiltrationCheckInfo {

  @JsonProperty("rows_returned")
  private final long rowsReturned;

  @JsonProperty("row_limit")
  private final int rowLimit;

  @JsonProperty("bytes_returned")
  private final long bytesReturned;

  @JsonProperty("byte_limit")
  private final long byteLimit;

  @JsonProperty("within_limits")
  private final boolean withinLimits;

  public ExfiltrationCheckInfo(
      @JsonProperty("rows_returned") long rowsReturned,
      @JsonProperty("row_limit") int rowLimit,
      @JsonProperty("bytes_returned") long bytesReturned,
      @JsonProperty("byte_limit") long byteLimit,
      @JsonProperty("within_limits") boolean withinLimits) {
    this.rowsReturned = rowsReturned;
    this.rowLimit = rowLimit;
    this.bytesReturned = bytesReturned;
    this.byteLimit = byteLimit;
    this.withinLimits = withinLimits;
  }

  /** Returns the number of rows in the response. */
  public long getRowsReturned() {
    return rowsReturned;
  }

  /** Returns the configured maximum rows per query. */
  public int getRowLimit() {
    return rowLimit;
  }

  /** Returns the size of the response data in bytes. */
  public long getBytesReturned() {
    return bytesReturned;
  }

  /** Returns the configured maximum bytes per response. */
  public long getByteLimit() {
    return byteLimit;
  }

  /** Returns whether the response is within configured limits. */
  public boolean isWithinLimits() {
    return withinLimits;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExfiltrationCheckInfo that = (ExfiltrationCheckInfo) o;
    return rowsReturned == that.rowsReturned
        && rowLimit == that.rowLimit
        && bytesReturned == that.bytesReturned
        && byteLimit == that.byteLimit
        && withinLimits == that.withinLimits;
  }

  @Override
  public int hashCode() {
    return Objects.hash(rowsReturned, rowLimit, bytesReturned, byteLimit, withinLimits);
  }

  @Override
  public String toString() {
    return "ExfiltrationCheckInfo{"
        + "rowsReturned="
        + rowsReturned
        + ", rowLimit="
        + rowLimit
        + ", bytesReturned="
        + bytesReturned
        + ", byteLimit="
        + byteLimit
        + ", withinLimits="
        + withinLimits
        + '}';
  }
}
