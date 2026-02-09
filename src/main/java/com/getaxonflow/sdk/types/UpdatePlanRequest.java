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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request for updating a multi-agent plan.
 *
 * <p>The version field is used for optimistic concurrency control.
 * If the version does not match the current server version, a
 * {@link com.getaxonflow.sdk.exceptions.VersionConflictException} is thrown.
 *
 * <p>Example usage:
 * <pre>{@code
 * UpdatePlanRequest request = UpdatePlanRequest.builder()
 *     .version(2)
 *     .executionMode(ExecutionMode.PARALLEL)
 *     .domain("finance")
 *     .build();
 *
 * UpdatePlanResponse response = axonflow.updatePlan(planId, request);
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UpdatePlanRequest {

    @JsonProperty("version")
    private final int version;

    @JsonProperty("execution_mode")
    private final ExecutionMode executionMode;

    @JsonProperty("domain")
    private final String domain;

    private UpdatePlanRequest(Builder builder) {
        this.version = builder.version;
        this.executionMode = builder.executionMode;
        this.domain = builder.domain;
    }

    /**
     * Returns the expected version for optimistic concurrency control.
     *
     * @return the version number
     */
    public int getVersion() {
        return version;
    }

    /**
     * Returns the new execution mode for the plan.
     *
     * @return the execution mode, or null if not being changed
     */
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    /**
     * Returns the new domain for the plan.
     *
     * @return the domain, or null if not being changed
     */
    public String getDomain() {
        return domain;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdatePlanRequest that = (UpdatePlanRequest) o;
        return version == that.version &&
               executionMode == that.executionMode &&
               Objects.equals(domain, that.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, executionMode, domain);
    }

    @Override
    public String toString() {
        return "UpdatePlanRequest{" +
               "version=" + version +
               ", executionMode=" + executionMode +
               ", domain='" + domain + '\'' +
               '}';
    }

    /**
     * Builder for UpdatePlanRequest.
     */
    public static final class Builder {
        private int version;
        private ExecutionMode executionMode;
        private String domain;

        private Builder() {}

        /**
         * Sets the expected version for optimistic concurrency control.
         *
         * @param version the current version of the plan
         * @return this builder
         */
        public Builder version(int version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the new execution mode for the plan.
         *
         * @param executionMode the execution mode
         * @return this builder
         */
        public Builder executionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        /**
         * Sets the new domain for the plan.
         *
         * @param domain the domain identifier
         * @return this builder
         */
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Builds the UpdatePlanRequest.
         *
         * @return a new UpdatePlanRequest instance
         */
        public UpdatePlanRequest build() {
            return new UpdatePlanRequest(this);
        }
    }
}
