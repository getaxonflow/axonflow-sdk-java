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

import java.util.Objects;

/**
 * Result of an audit call in Gateway Mode.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuditResult {

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("audit_id")
    private final String auditId;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("error")
    private final String error;

    public AuditResult(
            @JsonProperty("success") boolean success,
            @JsonProperty("audit_id") String auditId,
            @JsonProperty("message") String message,
            @JsonProperty("error") String error) {
        this.success = success;
        this.auditId = auditId;
        this.message = message;
        this.error = error;
    }

    /**
     * Returns whether the audit was recorded successfully.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the unique identifier for this audit record.
     *
     * @return the audit ID
     */
    public String getAuditId() {
        return auditId;
    }

    /**
     * Returns any message from the audit operation.
     *
     * @return the message, may be null
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the error message if the audit failed.
     *
     * @return the error message, or null if successful
     */
    public String getError() {
        return error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditResult that = (AuditResult) o;
        return success == that.success &&
               Objects.equals(auditId, that.auditId) &&
               Objects.equals(message, that.message) &&
               Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, auditId, message, error);
    }

    @Override
    public String toString() {
        return "AuditResult{" +
               "success=" + success +
               ", auditId='" + auditId + '\'' +
               ", message='" + message + '\'' +
               ", error='" + error + '\'' +
               '}';
    }
}
