// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Health status of the AxonFlow Agent. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HealthStatus {

  @JsonProperty("status")
  private final String status;

  @JsonProperty("version")
  private final String version;

  @JsonProperty("uptime")
  private final String uptime;

  @JsonProperty("components")
  private final Map<String, Object> components;

  @JsonProperty("capabilities")
  private final List<PlatformCapability> capabilities;

  @JsonProperty("sdk_compatibility")
  private final SDKCompatibility sdkCompatibility;

  /** Backward-compatible constructor without capabilities and sdkCompatibility. */
  public HealthStatus(
      String status, String version, String uptime, Map<String, Object> components) {
    this(status, version, uptime, components, null, null);
  }

  public HealthStatus(
      @JsonProperty("status") String status,
      @JsonProperty("version") String version,
      @JsonProperty("uptime") String uptime,
      @JsonProperty("components") Map<String, Object> components,
      @JsonProperty("capabilities") List<PlatformCapability> capabilities,
      @JsonProperty("sdk_compatibility") SDKCompatibility sdkCompatibility) {
    this.status = status;
    this.version = version;
    this.uptime = uptime;
    this.components =
        components != null ? Collections.unmodifiableMap(components) : Collections.emptyMap();
    this.capabilities =
        capabilities != null ? Collections.unmodifiableList(capabilities) : Collections.emptyList();
    this.sdkCompatibility = sdkCompatibility;
  }

  /**
   * Returns the overall health status.
   *
   * @return the status (e.g., "healthy", "degraded", "unhealthy")
   */
  public String getStatus() {
    return status;
  }

  /**
   * Returns the AxonFlow Agent version.
   *
   * @return the version string
   */
  public String getVersion() {
    return version;
  }

  /**
   * Returns how long the Agent has been running.
   *
   * @return the uptime string
   */
  public String getUptime() {
    return uptime;
  }

  /**
   * Returns the health status of individual components.
   *
   * @return immutable map of component statuses
   */
  public Map<String, Object> getComponents() {
    return components;
  }

  /**
   * Returns the list of capabilities advertised by the platform.
   *
   * @return immutable list of capabilities (never null)
   */
  public List<PlatformCapability> getCapabilities() {
    return capabilities;
  }

  /**
   * Returns SDK compatibility information from the platform.
   *
   * @return the SDK compatibility info, or null if not provided
   */
  public SDKCompatibility getSdkCompatibility() {
    return sdkCompatibility;
  }

  /**
   * Checks if the Agent is healthy.
   *
   * @return true if status is "healthy"
   */
  public boolean isHealthy() {
    return "healthy".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status);
  }

  /**
   * Checks if the platform advertises a given capability by name.
   *
   * @param name the capability name to check
   * @return true if the capability is present
   */
  public boolean hasCapability(String name) {
    if (name == null) return false;
    return capabilities.stream().anyMatch(c -> name.equals(c.getName()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HealthStatus that = (HealthStatus) o;
    return Objects.equals(status, that.status)
        && Objects.equals(version, that.version)
        && Objects.equals(uptime, that.uptime)
        && Objects.equals(capabilities, that.capabilities)
        && Objects.equals(sdkCompatibility, that.sdkCompatibility);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, version, uptime, capabilities, sdkCompatibility);
  }

  @Override
  public String toString() {
    return "HealthStatus{"
        + "status='"
        + status
        + '\''
        + ", version='"
        + version
        + '\''
        + ", uptime='"
        + uptime
        + '\''
        + ", capabilities="
        + capabilities
        + ", sdkCompatibility="
        + sdkCompatibility
        + '}';
  }
}
