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

/**
 * Reference to a policy involved in a conflict.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyConflictRef {

    @JsonProperty("policy_id")
    private final String policyId;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("action")
    private final String action;

    public PolicyConflictRef(
            @JsonProperty("policy_id") String policyId,
            @JsonProperty("name") String name,
            @JsonProperty("action") String action) {
        this.policyId = policyId;
        this.name = name;
        this.action = action;
    }

    public String getPolicyId() { return policyId; }
    public String getName() { return name; }
    public String getAction() { return action; }
}
