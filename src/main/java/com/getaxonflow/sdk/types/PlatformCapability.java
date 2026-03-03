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
 * Represents a capability advertised by the AxonFlow platform.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlatformCapability {

    @JsonProperty("name")
    private final String name;

    @JsonProperty("since")
    private final String since;

    @JsonProperty("description")
    private final String description;

    public PlatformCapability(
            @JsonProperty("name") String name,
            @JsonProperty("since") String since,
            @JsonProperty("description") String description) {
        this.name = name;
        this.since = since;
        this.description = description;
    }

    public String getName() { return name; }
    public String getSince() { return since; }
    public String getDescription() { return description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformCapability that = (PlatformCapability) o;
        return Objects.equals(name, that.name) && Objects.equals(since, that.since) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, since, description);
    }

    @Override
    public String toString() {
        return "PlatformCapability{name='" + name + "', since='" + since + "', description='" + description + "'}";
    }
}
