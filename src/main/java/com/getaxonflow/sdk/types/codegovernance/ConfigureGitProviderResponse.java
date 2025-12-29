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

import java.util.Objects;

/**
 * Response from Git provider configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConfigureGitProviderResponse {

    @JsonProperty("message")
    private final String message;

    @JsonProperty("type")
    private final String type;

    public ConfigureGitProviderResponse(
            @JsonProperty("message") String message,
            @JsonProperty("type") String type) {
        this.message = message != null ? message : "";
        this.type = type != null ? type : "";
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigureGitProviderResponse that = (ConfigureGitProviderResponse) o;
        return Objects.equals(message, that.message) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, type);
    }

    @Override
    public String toString() {
        return "ConfigureGitProviderResponse{" +
               "message='" + message + '\'' +
               ", type='" + type + '\'' +
               '}';
    }
}
