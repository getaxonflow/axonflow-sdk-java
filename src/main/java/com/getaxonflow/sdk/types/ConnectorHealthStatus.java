// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Health status of an installed MCP connector. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConnectorHealthStatus {

  @JsonProperty("healthy")
  private final Boolean healthy;

  @JsonProperty("latency")
  private final Long latency;

  @JsonProperty("details")
  private final Map<String, String> details;

  @JsonProperty("timestamp")
  private final String timestamp;

  @JsonProperty("error")
  private final String error;

  public ConnectorHealthStatus(
      @JsonProperty("healthy") Boolean healthy,
      @JsonProperty("latency") Long latency,
      @JsonProperty("details") Map<String, String> details,
      @JsonProperty("timestamp") String timestamp,
      @JsonProperty("error") String error) {
    this.healthy = healthy != null ? healthy : false;
    this.latency = latency != null ? latency : 0L;
    this.details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
    this.timestamp = timestamp != null ? timestamp : "";
    this.error = error;
  }

  /**
   * Returns whether the connector is healthy.
   *
   * @return true if healthy
   */
  public Boolean isHealthy() {
    return healthy;
  }

  /**
   * Returns the connection latency in nanoseconds.
   *
   * @return latency in nanoseconds
   */
  public Long getLatency() {
    return latency;
  }

  /**
   * Returns additional health check details.
   *
   * @return immutable map of health details
   */
  public Map<String, String> getDetails() {
    return details;
  }

  /**
   * Returns the timestamp of the health check.
   *
   * @return ISO 8601 timestamp string
   */
  public String getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the error message if unhealthy.
   *
   * @return error message or null if healthy
   */
  public String getError() {
    return error;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConnectorHealthStatus that = (ConnectorHealthStatus) o;
    return Objects.equals(healthy, that.healthy)
        && Objects.equals(latency, that.latency)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(healthy, latency, timestamp, error);
  }

  @Override
  public String toString() {
    return "ConnectorHealthStatus{"
        + "healthy="
        + healthy
        + ", latency="
        + latency
        + ", timestamp='"
        + timestamp
        + '\''
        + ", error='"
        + error
        + '\''
        + '}';
  }
}
