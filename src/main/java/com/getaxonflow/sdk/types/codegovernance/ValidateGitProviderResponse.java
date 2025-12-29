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
 * Response from Git provider validation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ValidateGitProviderResponse {

    @JsonProperty("valid")
    private final boolean valid;

    @JsonProperty("message")
    private final String message;

    public ValidateGitProviderResponse(
            @JsonProperty("valid") boolean valid,
            @JsonProperty("message") String message) {
        this.valid = valid;
        this.message = message != null ? message : "";
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidateGitProviderResponse that = (ValidateGitProviderResponse) o;
        return valid == that.valid && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, message);
    }

    @Override
    public String toString() {
        return "ValidateGitProviderResponse{" +
               "valid=" + valid +
               ", message='" + message + '\'' +
               '}';
    }
}
