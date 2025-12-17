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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response containing a generated multi-agent plan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlanResponse {

    @JsonProperty("plan_id")
    private final String planId;

    @JsonProperty("steps")
    private final List<PlanStep> steps;

    @JsonProperty("domain")
    private final String domain;

    @JsonProperty("complexity")
    private final Integer complexity;

    @JsonProperty("parallel")
    private final Boolean parallel;

    @JsonProperty("estimated_duration")
    private final String estimatedDuration;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("result")
    private final String result;

    public PlanResponse(
            @JsonProperty("plan_id") String planId,
            @JsonProperty("steps") List<PlanStep> steps,
            @JsonProperty("domain") String domain,
            @JsonProperty("complexity") Integer complexity,
            @JsonProperty("parallel") Boolean parallel,
            @JsonProperty("estimated_duration") String estimatedDuration,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("status") String status,
            @JsonProperty("result") String result) {
        this.planId = planId;
        this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
        this.domain = domain;
        this.complexity = complexity;
        this.parallel = parallel;
        this.estimatedDuration = estimatedDuration;
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
        this.status = status;
        this.result = result;
    }

    /**
     * Returns the unique identifier for this plan.
     *
     * @return the plan ID
     */
    public String getPlanId() {
        return planId;
    }

    /**
     * Returns the steps in this plan.
     *
     * @return immutable list of plan steps
     */
    public List<PlanStep> getSteps() {
        return steps;
    }

    /**
     * Returns the number of steps in this plan.
     *
     * @return the step count
     */
    public int getStepCount() {
        return steps.size();
    }

    /**
     * Returns the domain this plan was generated for.
     *
     * @return the domain identifier
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Returns the complexity score of this plan (1-10).
     *
     * @return the complexity score
     */
    public Integer getComplexity() {
        return complexity;
    }

    /**
     * Returns whether this plan supports parallel execution.
     *
     * @return true if parallel execution is supported
     */
    public Boolean isParallel() {
        return parallel;
    }

    /**
     * Returns the estimated total duration for plan execution.
     *
     * @return the estimated duration string
     */
    public String getEstimatedDuration() {
        return estimatedDuration;
    }

    /**
     * Returns additional metadata about the plan.
     *
     * @return immutable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Returns the execution status of the plan.
     *
     * @return the status (e.g., "pending", "in_progress", "completed", "failed")
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns the result of plan execution.
     *
     * @return the execution result, or null if not yet executed
     */
    public String getResult() {
        return result;
    }

    /**
     * Checks if the plan execution is complete.
     *
     * @return true if status is "completed"
     */
    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }

    /**
     * Checks if the plan execution failed.
     *
     * @return true if status is "failed"
     */
    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanResponse that = (PlanResponse) o;
        return Objects.equals(planId, that.planId) &&
               Objects.equals(steps, that.steps) &&
               Objects.equals(domain, that.domain) &&
               Objects.equals(complexity, that.complexity) &&
               Objects.equals(parallel, that.parallel) &&
               Objects.equals(estimatedDuration, that.estimatedDuration) &&
               Objects.equals(metadata, that.metadata) &&
               Objects.equals(status, that.status) &&
               Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, steps, domain, complexity, parallel,
                           estimatedDuration, metadata, status, result);
    }

    @Override
    public String toString() {
        return "PlanResponse{" +
               "planId='" + planId + '\'' +
               ", stepCount=" + steps.size() +
               ", domain='" + domain + '\'' +
               ", complexity=" + complexity +
               ", parallel=" + parallel +
               ", status='" + status + '\'' +
               '}';
    }
}
