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
 * Response from resuming a paused multi-agent plan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ResumePlanResponse {

    @JsonProperty("plan_id")
    private final String planId;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("approved")
    private final boolean approved;

    @JsonProperty("message")
    private final String message;

    public ResumePlanResponse(
            @JsonProperty("plan_id") String planId,
            @JsonProperty("status") String status,
            @JsonProperty("approved") boolean approved,
            @JsonProperty("message") String message) {
        this.planId = planId;
        this.status = status;
        this.approved = approved;
        this.message = message;
    }

    /**
     * Returns the ID of the resumed plan.
     *
     * @return the plan ID
     */
    public String getPlanId() {
        return planId;
    }

    /**
     * Returns the status after resuming.
     *
     * @return the status (e.g., "in_progress", "rejected")
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns whether the plan was approved to continue.
     *
     * @return true if the plan was approved
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * Returns a human-readable message about the resume action.
     *
     * @return the resume message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResumePlanResponse that = (ResumePlanResponse) o;
        return approved == that.approved &&
               Objects.equals(planId, that.planId) &&
               Objects.equals(status, that.status) &&
               Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, status, approved, message);
    }

    @Override
    public String toString() {
        return "ResumePlanResponse{" +
               "planId='" + planId + '\'' +
               ", status='" + status + '\'' +
               ", approved=" + approved +
               ", message='" + message + '\'' +
               '}';
    }
}
