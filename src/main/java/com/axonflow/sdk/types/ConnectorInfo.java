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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Information about an available MCP (Model Context Protocol) connector.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConnectorInfo {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("type")
    private final String type;

    @JsonProperty("version")
    private final String version;

    @JsonProperty("capabilities")
    private final List<String> capabilities;

    @JsonProperty("config_schema")
    private final Map<String, Object> configSchema;

    @JsonProperty("installed")
    private final Boolean installed;

    @JsonProperty("enabled")
    private final Boolean enabled;

    public ConnectorInfo(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("type") String type,
            @JsonProperty("version") String version,
            @JsonProperty("capabilities") List<String> capabilities,
            @JsonProperty("config_schema") Map<String, Object> configSchema,
            @JsonProperty("installed") Boolean installed,
            @JsonProperty("enabled") Boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.version = version;
        this.capabilities = capabilities != null ? Collections.unmodifiableList(capabilities) : Collections.emptyList();
        this.configSchema = configSchema != null ? Collections.unmodifiableMap(configSchema) : Collections.emptyMap();
        this.installed = installed;
        this.enabled = enabled;
    }

    /**
     * Returns the unique identifier for this connector.
     *
     * @return the connector ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the display name of this connector.
     *
     * @return the connector name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns a description of what this connector does.
     *
     * @return the connector description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the type of this connector.
     *
     * @return the connector type (e.g., "database", "api", "file")
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the version of this connector.
     *
     * @return the version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the capabilities this connector provides.
     *
     * @return immutable list of capability identifiers
     */
    public List<String> getCapabilities() {
        return capabilities;
    }

    /**
     * Returns the configuration schema for this connector.
     *
     * @return immutable map of configuration options
     */
    public Map<String, Object> getConfigSchema() {
        return configSchema;
    }

    /**
     * Returns whether this connector is installed.
     *
     * @return true if installed
     */
    public Boolean isInstalled() {
        return installed;
    }

    /**
     * Returns whether this connector is enabled.
     *
     * @return true if enabled
     */
    public Boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorInfo that = (ConnectorInfo) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(type, that.type) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, version);
    }

    @Override
    public String toString() {
        return "ConnectorInfo{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", type='" + type + '\'' +
               ", version='" + version + '\'' +
               ", installed=" + installed +
               ", enabled=" + enabled +
               '}';
    }
}
