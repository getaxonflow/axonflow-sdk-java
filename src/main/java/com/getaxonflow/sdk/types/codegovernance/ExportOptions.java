// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types.codegovernance;

import java.time.Instant;

/** Options for exporting code governance data. */
public class ExportOptions {
  private String format = "json";
  private Instant startDate;
  private Instant endDate;
  private String state;

  public ExportOptions() {}

  public String getFormat() {
    return format;
  }

  public ExportOptions setFormat(String format) {
    this.format = format;
    return this;
  }

  public Instant getStartDate() {
    return startDate;
  }

  public ExportOptions setStartDate(Instant startDate) {
    this.startDate = startDate;
    return this;
  }

  public Instant getEndDate() {
    return endDate;
  }

  public ExportOptions setEndDate(Instant endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getState() {
    return state;
  }

  public ExportOptions setState(String state) {
    this.state = state;
    return this;
  }
}
