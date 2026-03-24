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

    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("type")
    private final String type;

    public PolicyConflictRef(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
}
