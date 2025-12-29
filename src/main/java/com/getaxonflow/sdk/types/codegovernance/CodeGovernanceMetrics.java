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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregated code governance metrics for a tenant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CodeGovernanceMetrics {

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("total_prs")
    private final int totalPrs;

    @JsonProperty("open_prs")
    private final int openPrs;

    @JsonProperty("merged_prs")
    private final int mergedPrs;

    @JsonProperty("closed_prs")
    private final int closedPrs;

    @JsonProperty("total_files")
    private final int totalFiles;

    @JsonProperty("total_secrets_detected")
    private final int totalSecretsDetected;

    @JsonProperty("total_unsafe_patterns")
    private final int totalUnsafePatterns;

    @JsonProperty("first_pr_at")
    private final Instant firstPrAt;

    @JsonProperty("last_pr_at")
    private final Instant lastPrAt;

    public CodeGovernanceMetrics(
            @JsonProperty("tenant_id") String tenantId,
            @JsonProperty("total_prs") int totalPrs,
            @JsonProperty("open_prs") int openPrs,
            @JsonProperty("merged_prs") int mergedPrs,
            @JsonProperty("closed_prs") int closedPrs,
            @JsonProperty("total_files") int totalFiles,
            @JsonProperty("total_secrets_detected") int totalSecretsDetected,
            @JsonProperty("total_unsafe_patterns") int totalUnsafePatterns,
            @JsonProperty("first_pr_at") Instant firstPrAt,
            @JsonProperty("last_pr_at") Instant lastPrAt) {
        this.tenantId = tenantId;
        this.totalPrs = totalPrs;
        this.openPrs = openPrs;
        this.mergedPrs = mergedPrs;
        this.closedPrs = closedPrs;
        this.totalFiles = totalFiles;
        this.totalSecretsDetected = totalSecretsDetected;
        this.totalUnsafePatterns = totalUnsafePatterns;
        this.firstPrAt = firstPrAt;
        this.lastPrAt = lastPrAt;
    }

    public String getTenantId() { return tenantId; }
    public int getTotalPrs() { return totalPrs; }
    public int getOpenPrs() { return openPrs; }
    public int getMergedPrs() { return mergedPrs; }
    public int getClosedPrs() { return closedPrs; }
    public int getTotalFiles() { return totalFiles; }
    public int getTotalSecretsDetected() { return totalSecretsDetected; }
    public int getTotalUnsafePatterns() { return totalUnsafePatterns; }
    public Instant getFirstPrAt() { return firstPrAt; }
    public Instant getLastPrAt() { return lastPrAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeGovernanceMetrics that = (CodeGovernanceMetrics) o;
        return totalPrs == that.totalPrs &&
               Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, totalPrs);
    }

    @Override
    public String toString() {
        return "CodeGovernanceMetrics{" +
               "tenantId='" + tenantId + '\'' +
               ", totalPrs=" + totalPrs +
               ", openPrs=" + openPrs +
               ", mergedPrs=" + mergedPrs +
               ", closedPrs=" + closedPrs +
               ", totalFiles=" + totalFiles +
               ", totalSecretsDetected=" + totalSecretsDetected +
               ", totalUnsafePatterns=" + totalUnsafePatterns +
               '}';
    }
}
