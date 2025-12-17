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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Request for generating a multi-agent plan (MAP).
 *
 * <p>Multi-Agent Planning allows you to describe a complex task and have
 * AxonFlow generate an execution plan with multiple steps that can be
 * executed by different agents.
 *
 * <p>Example usage:
 * <pre>{@code
 * PlanRequest request = PlanRequest.builder()
 *     .objective("Research and summarize the latest AI governance regulations")
 *     .domain("generic")
 *     .userToken("user-123")
 *     .build();
 *
 * PlanResponse plan = axonflow.generatePlan(request);
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PlanRequest {

    @JsonProperty("objective")
    private final String objective;

    @JsonProperty("domain")
    private final String domain;

    @JsonProperty("user_token")
    private final String userToken;

    @JsonProperty("context")
    private final Map<String, Object> context;

    @JsonProperty("constraints")
    private final Map<String, Object> constraints;

    @JsonProperty("max_steps")
    private final Integer maxSteps;

    @JsonProperty("parallel")
    private final Boolean parallel;

    private PlanRequest(Builder builder) {
        this.objective = Objects.requireNonNull(builder.objective, "objective cannot be null");
        this.domain = builder.domain != null ? builder.domain : "generic";
        this.userToken = builder.userToken;
        this.context = builder.context != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.context))
            : null;
        this.constraints = builder.constraints != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.constraints))
            : null;
        this.maxSteps = builder.maxSteps;
        this.parallel = builder.parallel;
    }

    public String getObjective() {
        return objective;
    }

    public String getDomain() {
        return domain;
    }

    public String getUserToken() {
        return userToken;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public Map<String, Object> getConstraints() {
        return constraints;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public Boolean getParallel() {
        return parallel;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanRequest that = (PlanRequest) o;
        return Objects.equals(objective, that.objective) &&
               Objects.equals(domain, that.domain) &&
               Objects.equals(userToken, that.userToken) &&
               Objects.equals(context, that.context) &&
               Objects.equals(constraints, that.constraints) &&
               Objects.equals(maxSteps, that.maxSteps) &&
               Objects.equals(parallel, that.parallel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objective, domain, userToken, context, constraints, maxSteps, parallel);
    }

    @Override
    public String toString() {
        return "PlanRequest{" +
               "objective='" + objective + '\'' +
               ", domain='" + domain + '\'' +
               ", userToken='" + userToken + '\'' +
               ", maxSteps=" + maxSteps +
               ", parallel=" + parallel +
               '}';
    }

    /**
     * Builder for PlanRequest.
     */
    public static final class Builder {
        private String objective;
        private String domain = "generic";
        private String userToken;
        private Map<String, Object> context;
        private Map<String, Object> constraints;
        private Integer maxSteps;
        private Boolean parallel;

        private Builder() {}

        /**
         * Sets the objective or task description for the plan.
         *
         * @param objective a description of what the plan should accomplish
         * @return this builder
         */
        public Builder objective(String objective) {
            this.objective = objective;
            return this;
        }

        /**
         * Sets the domain for specialized planning.
         *
         * <p>Common domains include:
         * <ul>
         *   <li>generic - General purpose planning</li>
         *   <li>travel - Travel and booking workflows</li>
         *   <li>healthcare - Healthcare data processing</li>
         *   <li>finance - Financial analysis workflows</li>
         * </ul>
         *
         * @param domain the domain identifier
         * @return this builder
         */
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Sets the user token for identifying the requesting user.
         *
         * @param userToken the user identifier
         * @return this builder
         */
        public Builder userToken(String userToken) {
            this.userToken = userToken;
            return this;
        }

        /**
         * Sets additional context for plan generation.
         *
         * @param context key-value pairs of contextual information
         * @return this builder
         */
        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        /**
         * Adds a single context entry.
         *
         * @param key   the context key
         * @param value the context value
         * @return this builder
         */
        public Builder addContext(String key, Object value) {
            if (this.context == null) {
                this.context = new HashMap<>();
            }
            this.context.put(key, value);
            return this;
        }

        /**
         * Sets constraints for plan generation.
         *
         * @param constraints key-value pairs of constraints
         * @return this builder
         */
        public Builder constraints(Map<String, Object> constraints) {
            this.constraints = constraints;
            return this;
        }

        /**
         * Sets the maximum number of steps in the plan.
         *
         * @param maxSteps the maximum step count
         * @return this builder
         */
        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        /**
         * Sets whether parallel execution is allowed.
         *
         * @param parallel true to allow parallel step execution
         * @return this builder
         */
        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        /**
         * Builds the PlanRequest.
         *
         * @return a new PlanRequest instance
         * @throws NullPointerException if objective is null
         */
        public PlanRequest build() {
            return new PlanRequest(this);
        }
    }
}
