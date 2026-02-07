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
 * Response from cancelling a multi-agent plan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CancelPlanResponse {

    @JsonProperty("plan_id")
    private final String planId;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("message")
    private final String message;

    public CancelPlanResponse(
            @JsonProperty("plan_id") String planId,
            @JsonProperty("status") String status,
            @JsonProperty("message") String message) {
        this.planId = planId;
        this.status = status;
        this.message = message;
    }

    /**
     * Returns the ID of the cancelled plan.
     *
     * @return the plan ID
     */
    public String getPlanId() {
        return planId;
    }

    /**
     * Returns the status after cancellation.
     *
     * @return the status (e.g., "cancelled")
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns a human-readable message about the cancellation.
     *
     * @return the cancellation message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CancelPlanResponse that = (CancelPlanResponse) o;
        return Objects.equals(planId, that.planId) &&
               Objects.equals(status, that.status) &&
               Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, status, message);
    }

    @Override
    public String toString() {
        return "CancelPlanResponse{" +
               "planId='" + planId + '\'' +
               ", status='" + status + '\'' +
               ", message='" + message + '\'' +
               '}';
    }
}
