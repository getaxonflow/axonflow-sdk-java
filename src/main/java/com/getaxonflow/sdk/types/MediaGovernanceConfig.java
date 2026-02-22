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

import java.util.List;
import java.util.Objects;

/**
 * Per-tenant media governance configuration.
 *
 * <p>Controls whether media analysis is enabled for a tenant and which
 * analyzers are allowed. Returned by the media governance config API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaGovernanceConfig {

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("allowed_analyzers")
    private List<String> allowedAnalyzers;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("updated_by")
    private String updatedBy;

    public MediaGovernanceConfig() {}

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getAllowedAnalyzers() { return allowedAnalyzers; }
    public void setAllowedAnalyzers(List<String> allowedAnalyzers) { this.allowedAnalyzers = allowedAnalyzers; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaGovernanceConfig that = (MediaGovernanceConfig) o;
        return enabled == that.enabled &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(allowedAnalyzers, that.allowedAnalyzers) &&
               Objects.equals(updatedAt, that.updatedAt) &&
               Objects.equals(updatedBy, that.updatedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, enabled, allowedAnalyzers, updatedAt, updatedBy);
    }

    @Override
    public String toString() {
        return "MediaGovernanceConfig{" +
               "tenantId='" + tenantId + '\'' +
               ", enabled=" + enabled +
               ", allowedAnalyzers=" + allowedAnalyzers +
               ", updatedAt='" + updatedAt + '\'' +
               ", updatedBy='" + updatedBy + '\'' +
               '}';
    }
}
