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
 * Represents a single step in a multi-agent plan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlanStep {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("type")
    private final String type;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("depends_on")
    private final List<String> dependsOn;

    @JsonProperty("agent")
    private final String agent;

    @JsonProperty("parameters")
    private final Map<String, Object> parameters;

    @JsonProperty("estimated_time")
    private final String estimatedTime;

    public PlanStep(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("depends_on") List<String> dependsOn,
            @JsonProperty("agent") String agent,
            @JsonProperty("parameters") Map<String, Object> parameters,
            @JsonProperty("estimated_time") String estimatedTime) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.dependsOn = dependsOn != null ? Collections.unmodifiableList(dependsOn) : Collections.emptyList();
        this.agent = agent;
        this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : Collections.emptyMap();
        this.estimatedTime = estimatedTime;
    }

    /**
     * Returns the unique identifier for this step.
     *
     * @return the step ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable name of this step.
     *
     * @return the step name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the type of this step.
     *
     * <p>Common types include:
     * <ul>
     *   <li>llm-call - LLM inference</li>
     *   <li>api-call - External API call</li>
     *   <li>connector-call - MCP connector query</li>
     *   <li>conditional - Conditional logic</li>
     *   <li>function-call - Custom function execution</li>
     * </ul>
     *
     * @return the step type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns a description of what this step does.
     *
     * @return the step description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the IDs of steps that must complete before this step.
     *
     * @return immutable list of dependency step IDs
     */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /**
     * Returns the agent responsible for executing this step.
     *
     * @return the agent identifier
     */
    public String getAgent() {
        return agent;
    }

    /**
     * Returns the parameters for this step.
     *
     * @return immutable map of parameters
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Returns the estimated execution time for this step.
     *
     * @return the estimated time string (e.g., "2s", "500ms")
     */
    public String getEstimatedTime() {
        return estimatedTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanStep planStep = (PlanStep) o;
        return Objects.equals(id, planStep.id) &&
               Objects.equals(name, planStep.name) &&
               Objects.equals(type, planStep.type) &&
               Objects.equals(description, planStep.description) &&
               Objects.equals(dependsOn, planStep.dependsOn) &&
               Objects.equals(agent, planStep.agent) &&
               Objects.equals(parameters, planStep.parameters) &&
               Objects.equals(estimatedTime, planStep.estimatedTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, description, dependsOn, agent, parameters, estimatedTime);
    }

    @Override
    public String toString() {
        return "PlanStep{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", type='" + type + '\'' +
               ", dependsOn=" + dependsOn +
               ", agent='" + agent + '\'' +
               '}';
    }
}
