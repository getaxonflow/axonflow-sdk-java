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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Request to update per-tenant media governance configuration.
 *
 * <p>Fields set to {@code null} are omitted from the JSON payload,
 * allowing partial updates.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateMediaGovernanceConfigRequest {

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("allowed_analyzers")
    private List<String> allowedAnalyzers;

    public UpdateMediaGovernanceConfigRequest() {}

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public List<String> getAllowedAnalyzers() { return allowedAnalyzers; }
    public void setAllowedAnalyzers(List<String> allowedAnalyzers) { this.allowedAnalyzers = allowedAnalyzers; }

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateMediaGovernanceConfigRequest that = (UpdateMediaGovernanceConfigRequest) o;
        return Objects.equals(enabled, that.enabled) &&
               Objects.equals(allowedAnalyzers, that.allowedAnalyzers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, allowedAnalyzers);
    }

    @Override
    public String toString() {
        return "UpdateMediaGovernanceConfigRequest{" +
               "enabled=" + enabled +
               ", allowedAnalyzers=" + allowedAnalyzers +
               '}';
    }

    public static final class Builder {
        private Boolean enabled;
        private List<String> allowedAnalyzers;

        private Builder() {}

        public Builder enabled(Boolean enabled) { this.enabled = enabled; return this; }
        public Builder allowedAnalyzers(List<String> allowedAnalyzers) { this.allowedAnalyzers = allowedAnalyzers; return this; }

        public UpdateMediaGovernanceConfigRequest build() {
            UpdateMediaGovernanceConfigRequest request = new UpdateMediaGovernanceConfigRequest();
            request.enabled = this.enabled;
            request.allowedAnalyzers = this.allowedAnalyzers;
            return request;
        }
    }
}
