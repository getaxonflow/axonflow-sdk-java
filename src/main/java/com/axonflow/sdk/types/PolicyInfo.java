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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Contains information about policies evaluated during a request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyInfo {

    @JsonProperty("policies_evaluated")
    private final List<String> policiesEvaluated;

    @JsonProperty("static_checks")
    private final List<String> staticChecks;

    @JsonProperty("processing_time")
    private final String processingTime;

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("risk_score")
    private final Double riskScore;

    public PolicyInfo(
            @JsonProperty("policies_evaluated") List<String> policiesEvaluated,
            @JsonProperty("static_checks") List<String> staticChecks,
            @JsonProperty("processing_time") String processingTime,
            @JsonProperty("tenant_id") String tenantId,
            @JsonProperty("risk_score") Double riskScore) {
        this.policiesEvaluated = policiesEvaluated != null ? Collections.unmodifiableList(policiesEvaluated) : Collections.emptyList();
        this.staticChecks = staticChecks != null ? Collections.unmodifiableList(staticChecks) : Collections.emptyList();
        this.processingTime = processingTime;
        this.tenantId = tenantId;
        this.riskScore = riskScore;
    }

    /**
     * Returns the list of policies that were evaluated.
     *
     * @return immutable list of policy names
     */
    public List<String> getPoliciesEvaluated() {
        return policiesEvaluated;
    }

    /**
     * Returns the list of static checks that were performed.
     *
     * @return immutable list of static check names
     */
    public List<String> getStaticChecks() {
        return staticChecks;
    }

    /**
     * Returns the raw processing time string (e.g., "17.48ms").
     *
     * @return processing time as a string
     */
    public String getProcessingTime() {
        return processingTime;
    }

    /**
     * Parses and returns the processing time as a Duration.
     *
     * @return the processing time as a Duration, or Duration.ZERO if parsing fails
     */
    public Duration getProcessingDuration() {
        if (processingTime == null || processingTime.isEmpty()) {
            return Duration.ZERO;
        }
        try {
            String normalized = processingTime.trim().toLowerCase();
            if (normalized.endsWith("ms")) {
                double millis = Double.parseDouble(normalized.substring(0, normalized.length() - 2));
                return Duration.ofNanos((long) (millis * 1_000_000));
            } else if (normalized.endsWith("s")) {
                double seconds = Double.parseDouble(normalized.substring(0, normalized.length() - 1));
                return Duration.ofNanos((long) (seconds * 1_000_000_000));
            } else if (normalized.endsWith("us") || normalized.endsWith("µs")) {
                String numPart = normalized.endsWith("µs")
                    ? normalized.substring(0, normalized.length() - 2)
                    : normalized.substring(0, normalized.length() - 2);
                double micros = Double.parseDouble(numPart);
                return Duration.ofNanos((long) (micros * 1_000));
            } else if (normalized.endsWith("ns")) {
                long nanos = Long.parseLong(normalized.substring(0, normalized.length() - 2));
                return Duration.ofNanos(nanos);
            }
            // Try parsing as milliseconds if no unit
            double millis = Double.parseDouble(normalized);
            return Duration.ofNanos((long) (millis * 1_000_000));
        } catch (NumberFormatException e) {
            return Duration.ZERO;
        }
    }

    /**
     * Returns the tenant ID associated with this request.
     *
     * @return the tenant identifier
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Returns the calculated risk score for this request.
     *
     * @return the risk score, or null if not calculated
     */
    public Double getRiskScore() {
        return riskScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyInfo that = (PolicyInfo) o;
        return Objects.equals(policiesEvaluated, that.policiesEvaluated) &&
               Objects.equals(staticChecks, that.staticChecks) &&
               Objects.equals(processingTime, that.processingTime) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(riskScore, that.riskScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policiesEvaluated, staticChecks, processingTime, tenantId, riskScore);
    }

    @Override
    public String toString() {
        return "PolicyInfo{" +
               "policiesEvaluated=" + policiesEvaluated +
               ", staticChecks=" + staticChecks +
               ", processingTime='" + processingTime + '\'' +
               ", tenantId='" + tenantId + '\'' +
               ", riskScore=" + riskScore +
               '}';
    }
}
