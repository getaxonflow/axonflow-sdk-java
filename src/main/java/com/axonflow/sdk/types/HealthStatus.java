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
package com.axonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Health status of the AxonFlow Agent.
 */
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

    public HealthStatus(
            @JsonProperty("status") String status,
            @JsonProperty("version") String version,
            @JsonProperty("uptime") String uptime,
            @JsonProperty("components") Map<String, Object> components) {
        this.status = status;
        this.version = version;
        this.uptime = uptime;
        this.components = components != null ? Collections.unmodifiableMap(components) : Collections.emptyMap();
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
     * Checks if the Agent is healthy.
     *
     * @return true if status is "healthy"
     */
    public boolean isHealthy() {
        return "healthy".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealthStatus that = (HealthStatus) o;
        return Objects.equals(status, that.status) &&
               Objects.equals(version, that.version) &&
               Objects.equals(uptime, that.uptime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, version, uptime);
    }

    @Override
    public String toString() {
        return "HealthStatus{" +
               "status='" + status + '\'' +
               ", version='" + version + '\'' +
               ", uptime='" + uptime + '\'' +
               '}';
    }
}
