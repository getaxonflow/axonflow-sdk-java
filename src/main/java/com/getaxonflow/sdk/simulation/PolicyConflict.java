/*
 * Copyright 2026 AxonFlow
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
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A detected conflict between policies.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyConflict {

    @JsonProperty("type")
    private final String type;

    @JsonProperty("severity")
    private final String severity;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("policies")
    private final List<PolicyConflictRef> policies;

    public PolicyConflict(
            @JsonProperty("type") String type,
            @JsonProperty("severity") String severity,
            @JsonProperty("description") String description,
            @JsonProperty("policies") List<PolicyConflictRef> policies) {
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.policies = policies != null ? List.copyOf(policies) : List.of();
    }

    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getDescription() { return description; }
    public List<PolicyConflictRef> getPolicies() { return policies; }
}
