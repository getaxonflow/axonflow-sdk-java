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
 * Response from Customer Portal login.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PortalLoginResponse {

    @JsonProperty("session_id")
    private final String sessionId;

    @JsonProperty("org_id")
    private final String orgId;

    @JsonProperty("email")
    private final String email;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("expires_at")
    private final String expiresAt;

    public PortalLoginResponse(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("org_id") String orgId,
            @JsonProperty("email") String email,
            @JsonProperty("name") String name,
            @JsonProperty("expires_at") String expiresAt) {
        this.sessionId = sessionId;
        this.orgId = orgId;
        this.email = email;
        this.name = name;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PortalLoginResponse that = (PortalLoginResponse) o;
        return Objects.equals(sessionId, that.sessionId) &&
               Objects.equals(orgId, that.orgId) &&
               Objects.equals(email, that.email) &&
               Objects.equals(name, that.name) &&
               Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, orgId, email, name, expiresAt);
    }

    @Override
    public String toString() {
        return "PortalLoginResponse{" +
               "sessionId='" + sessionId + '\'' +
               ", orgId='" + orgId + '\'' +
               ", email='" + email + '\'' +
               ", name='" + name + '\'' +
               ", expiresAt='" + expiresAt + '\'' +
               '}';
    }
}
