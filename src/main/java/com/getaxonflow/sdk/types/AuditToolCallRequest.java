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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request to audit a non-LLM tool call.
 *
 * <p>Records tool invocations (function calls, MCP operations, API calls)
 * for compliance and observability.
 *
 * <p>Example usage:
 * <pre>{@code
 * AuditToolCallRequest request = AuditToolCallRequest.builder()
 *     .toolName("web_search")
 *     .toolType("function")
 *     .input(Map.of("query", "latest news"))
 *     .output(Map.of("results", 5))
 *     .workflowId("wf_123")
 *     .durationMs(450L)
 *     .success(true)
 *     .build();
 *
 * AuditToolCallResponse result = axonflow.auditToolCall(request);
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AuditToolCallRequest {

    @JsonProperty("tool_name")
    private final String toolName;

    @JsonProperty("tool_type")
    private final String toolType;

    @JsonProperty("input")
    private final Map<String, Object> input;

    @JsonProperty("output")
    private final Map<String, Object> output;

    @JsonProperty("workflow_id")
    private final String workflowId;

    @JsonProperty("step_id")
    private final String stepId;

    @JsonProperty("user_id")
    private final String userId;

    @JsonProperty("duration_ms")
    private final Long durationMs;

    @JsonProperty("policies_applied")
    private final List<String> policiesApplied;

    @JsonProperty("success")
    private final Boolean success;

    @JsonProperty("error_message")
    private final String errorMessage;

    private AuditToolCallRequest(Builder builder) {
        this.toolName = Objects.requireNonNull(builder.toolName, "toolName cannot be null");
        if (builder.toolName.isEmpty()) {
            throw new IllegalArgumentException("toolName cannot be empty");
        }
        this.toolType = builder.toolType;
        this.input = builder.input != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.input))
            : null;
        this.output = builder.output != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.output))
            : null;
        this.workflowId = builder.workflowId;
        this.stepId = builder.stepId;
        this.userId = builder.userId;
        this.durationMs = builder.durationMs;
        this.policiesApplied = builder.policiesApplied != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.policiesApplied))
            : null;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolType() {
        return toolType;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getStepId() {
        return stepId;
    }

    public String getUserId() {
        return userId;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public List<String> getPoliciesApplied() {
        return policiesApplied;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditToolCallRequest that = (AuditToolCallRequest) o;
        return Objects.equals(toolName, that.toolName) &&
               Objects.equals(toolType, that.toolType) &&
               Objects.equals(input, that.input) &&
               Objects.equals(output, that.output) &&
               Objects.equals(workflowId, that.workflowId) &&
               Objects.equals(stepId, that.stepId) &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(durationMs, that.durationMs) &&
               Objects.equals(policiesApplied, that.policiesApplied) &&
               Objects.equals(success, that.success) &&
               Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolName, toolType, input, output, workflowId, stepId, userId,
                           durationMs, policiesApplied, success, errorMessage);
    }

    @Override
    public String toString() {
        return "AuditToolCallRequest{" +
               "toolName='" + toolName + '\'' +
               ", toolType='" + toolType + '\'' +
               ", workflowId='" + workflowId + '\'' +
               ", stepId='" + stepId + '\'' +
               ", userId='" + userId + '\'' +
               ", durationMs=" + durationMs +
               ", success=" + success +
               '}';
    }

    /**
     * Builder for AuditToolCallRequest.
     */
    public static final class Builder {
        private String toolName;
        private String toolType;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private String workflowId;
        private String stepId;
        private String userId;
        private Long durationMs;
        private List<String> policiesApplied;
        private Boolean success;
        private String errorMessage;

        private Builder() {}

        /**
         * Sets the name of the tool that was called (required).
         *
         * @param toolName the tool name
         * @return this builder
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Sets the type of tool call.
         *
         * @param toolType the tool type (e.g., "function", "mcp", "api")
         * @return this builder
         */
        public Builder toolType(String toolType) {
            this.toolType = toolType;
            return this;
        }

        /**
         * Sets the input parameters passed to the tool.
         *
         * @param input the input map
         * @return this builder
         */
        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the output returned by the tool.
         *
         * @param output the output map
         * @return this builder
         */
        public Builder output(Map<String, Object> output) {
            this.output = output;
            return this;
        }

        /**
         * Sets the workflow ID this tool call belongs to.
         *
         * @param workflowId the workflow identifier
         * @return this builder
         */
        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        /**
         * Sets the step ID within the workflow.
         *
         * @param stepId the step identifier
         * @return this builder
         */
        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        /**
         * Sets the user who initiated the tool call.
         *
         * @param userId the user identifier
         * @return this builder
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Sets the duration of the tool call in milliseconds.
         *
         * @param durationMs the duration in milliseconds
         * @return this builder
         */
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        /**
         * Sets the list of policies that were applied to this tool call.
         *
         * @param policiesApplied the policy names
         * @return this builder
         */
        public Builder policiesApplied(List<String> policiesApplied) {
            this.policiesApplied = policiesApplied;
            return this;
        }

        /**
         * Sets whether the tool call was successful.
         *
         * @param success true if successful, false if failed
         * @return this builder
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * Sets the error message if the tool call failed.
         *
         * @param errorMessage the error message
         * @return this builder
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Builds the AuditToolCallRequest.
         *
         * @return a new AuditToolCallRequest instance
         * @throws NullPointerException if toolName is null
         * @throws IllegalArgumentException if toolName is empty
         */
        public AuditToolCallRequest build() {
            return new AuditToolCallRequest(this);
        }
    }
}
