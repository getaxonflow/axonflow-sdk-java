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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Plan Types")
class PlanTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("PlanRequest - should build with required fields")
    void planRequestShouldBuildWithRequired() {
        PlanRequest request = PlanRequest.builder()
            .objective("Research AI governance")
            .build();

        assertThat(request.getObjective()).isEqualTo("Research AI governance");
        assertThat(request.getDomain()).isEqualTo("generic");
    }

    @Test
    @DisplayName("PlanRequest - should build with all fields")
    void planRequestShouldBuildWithAllFields() {
        PlanRequest request = PlanRequest.builder()
            .objective("Book a flight to Paris")
            .domain("travel")
            .userToken("user-123")
            .context(Map.of("budget", 1000))
            .constraints(Map.of("maxStops", 1))
            .maxSteps(5)
            .parallel(true)
            .build();

        assertThat(request.getObjective()).isEqualTo("Book a flight to Paris");
        assertThat(request.getDomain()).isEqualTo("travel");
        assertThat(request.getUserToken()).isEqualTo("user-123");
        assertThat(request.getContext()).containsEntry("budget", 1000);
        assertThat(request.getConstraints()).containsEntry("maxStops", 1);
        assertThat(request.getMaxSteps()).isEqualTo(5);
        assertThat(request.getParallel()).isTrue();
    }

    @Test
    @DisplayName("PlanRequest - should throw on null objective")
    void planRequestShouldThrowOnNullObjective() {
        assertThatThrownBy(() -> PlanRequest.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("objective");
    }

    @Test
    @DisplayName("PlanStep - should deserialize from JSON")
    void planStepShouldDeserialize() throws Exception {
        String json = "{"
            + "\"id\": \"step_001\","
            + "\"name\": \"research-benefits\","
            + "\"type\": \"llm-call\","
            + "\"description\": \"Research the benefits of AI governance\","
            + "\"depends_on\": [],"
            + "\"agent\": \"researcher\","
            + "\"parameters\": {\"topic\": \"governance\"},"
            + "\"estimated_time\": \"2s\""
            + "}";

        PlanStep step = objectMapper.readValue(json, PlanStep.class);

        assertThat(step.getId()).isEqualTo("step_001");
        assertThat(step.getName()).isEqualTo("research-benefits");
        assertThat(step.getType()).isEqualTo("llm-call");
        assertThat(step.getDescription()).isEqualTo("Research the benefits of AI governance");
        assertThat(step.getDependsOn()).isEmpty();
        assertThat(step.getAgent()).isEqualTo("researcher");
        assertThat(step.getParameters()).containsEntry("topic", "governance");
        assertThat(step.getEstimatedTime()).isEqualTo("2s");
    }

    @Test
    @DisplayName("PlanStep - should handle dependencies")
    void planStepShouldHandleDependencies() throws Exception {
        String json = "{"
            + "\"id\": \"step_002\","
            + "\"name\": \"summarize\","
            + "\"type\": \"llm-call\","
            + "\"depends_on\": [\"step_001\"]"
            + "}";

        PlanStep step = objectMapper.readValue(json, PlanStep.class);

        assertThat(step.getDependsOn()).containsExactly("step_001");
    }

    @Test
    @DisplayName("PlanResponse - should deserialize complete plan")
    void planResponseShouldDeserialize() throws Exception {
        String json = "{"
            + "\"plan_id\": \"plan_abc123\","
            + "\"steps\": ["
            + "{"
            + "\"id\": \"step_001\","
            + "\"name\": \"research\","
            + "\"type\": \"llm-call\""
            + "},"
            + "{"
            + "\"id\": \"step_002\","
            + "\"name\": \"summarize\","
            + "\"type\": \"llm-call\","
            + "\"depends_on\": [\"step_001\"]"
            + "}"
            + "],"
            + "\"domain\": \"generic\","
            + "\"complexity\": 3,"
            + "\"parallel\": true,"
            + "\"estimated_duration\": \"10s\","
            + "\"status\": \"completed\","
            + "\"result\": \"Plan executed successfully\""
            + "}";

        PlanResponse response = objectMapper.readValue(json, PlanResponse.class);

        assertThat(response.getPlanId()).isEqualTo("plan_abc123");
        assertThat(response.getSteps()).hasSize(2);
        assertThat(response.getStepCount()).isEqualTo(2);
        assertThat(response.getDomain()).isEqualTo("generic");
        assertThat(response.getComplexity()).isEqualTo(3);
        assertThat(response.isParallel()).isTrue();
        assertThat(response.getEstimatedDuration()).isEqualTo("10s");
        assertThat(response.getStatus()).isEqualTo("completed");
        assertThat(response.getResult()).isEqualTo("Plan executed successfully");
        assertThat(response.isCompleted()).isTrue();
        assertThat(response.isFailed()).isFalse();
    }

    @Test
    @DisplayName("PlanResponse - should detect failed status")
    void planResponseShouldDetectFailed() throws Exception {
        String json = "{"
            + "\"plan_id\": \"plan_abc123\","
            + "\"steps\": [],"
            + "\"status\": \"failed\""
            + "}";

        PlanResponse response = objectMapper.readValue(json, PlanResponse.class);

        assertThat(response.isFailed()).isTrue();
        assertThat(response.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("PlanResponse - should handle empty steps")
    void planResponseShouldHandleEmptySteps() throws Exception {
        String json = "{"
            + "\"plan_id\": \"plan_abc123\","
            + "\"status\": \"pending\""
            + "}";

        PlanResponse response = objectMapper.readValue(json, PlanResponse.class);

        assertThat(response.getSteps()).isEmpty();
        assertThat(response.getStepCount()).isEqualTo(0);
    }
}
