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

import java.util.Objects;

/**
 * Platform-level media governance status.
 *
 * <p>Indicates whether media governance is available, the default enablement
 * state, and the license tier required.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaGovernanceStatus {

    @JsonProperty("available")
    private boolean available;

    @JsonProperty("enabled_by_default")
    private boolean enabledByDefault;

    @JsonProperty("per_tenant_control")
    private boolean perTenantControl;

    @JsonProperty("tier")
    private String tier;

    public MediaGovernanceStatus() {}

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public boolean isEnabledByDefault() { return enabledByDefault; }
    public void setEnabledByDefault(boolean enabledByDefault) { this.enabledByDefault = enabledByDefault; }

    public boolean isPerTenantControl() { return perTenantControl; }
    public void setPerTenantControl(boolean perTenantControl) { this.perTenantControl = perTenantControl; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaGovernanceStatus that = (MediaGovernanceStatus) o;
        return available == that.available &&
               enabledByDefault == that.enabledByDefault &&
               perTenantControl == that.perTenantControl &&
               Objects.equals(tier, that.tier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(available, enabledByDefault, perTenantControl, tier);
    }

    @Override
    public String toString() {
        return "MediaGovernanceStatus{" +
               "available=" + available +
               ", enabledByDefault=" + enabledByDefault +
               ", perTenantControl=" + perTenantControl +
               ", tier='" + tier + '\'' +
               '}';
    }
}
