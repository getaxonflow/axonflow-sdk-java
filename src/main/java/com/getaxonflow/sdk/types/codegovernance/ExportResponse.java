// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Response from exporting code governance data. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ExportResponse {

  @JsonProperty("records")
  private final List<PRRecord> records;

  @JsonProperty("count")
  private final int count;

  @JsonProperty("exported_at")
  private final String exportedAt;

  public ExportResponse(
      @JsonProperty("records") List<PRRecord> records,
      @JsonProperty("count") int count,
      @JsonProperty("exported_at") String exportedAt) {
    this.records =
        records != null ? Collections.unmodifiableList(records) : Collections.emptyList();
    this.count = count;
    this.exportedAt = exportedAt;
  }

  public List<PRRecord> getRecords() {
    return records;
  }

  public int getCount() {
    return count;
  }

  public String getExportedAt() {
    return exportedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExportResponse that = (ExportResponse) o;
    return count == that.count && Objects.equals(exportedAt, that.exportedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(count, exportedAt);
  }

  @Override
  public String toString() {
    return "ExportResponse{" + "count=" + count + ", exportedAt='" + exportedAt + '\'' + '}';
  }
}
