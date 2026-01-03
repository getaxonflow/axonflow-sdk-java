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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.types.executionreplay.*;
import com.getaxonflow.sdk.types.executionreplay.ExecutionReplayTypes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Execution Replay Types")
class ExecutionReplayTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ========================================================================
    // ExecutionSummary Tests
    // ========================================================================

    @Test
    @DisplayName("ExecutionSummary should deserialize from JSON")
    void executionSummaryShouldDeserialize() throws Exception {
        String json = "{\"request_id\":\"exec-123\",\"workflow_name\":\"test-workflow\"," +
            "\"status\":\"completed\",\"total_steps\":3,\"completed_steps\":3," +
            "\"started_at\":\"2026-01-03T12:00:00Z\",\"completed_at\":\"2026-01-03T12:00:05Z\"," +
            "\"duration_ms\":5000,\"total_tokens\":150,\"total_cost_usd\":0.01}";

        ExecutionSummary summary = objectMapper.readValue(json, ExecutionSummary.class);

        assertThat(summary.getRequestId()).isEqualTo("exec-123");
        assertThat(summary.getWorkflowName()).isEqualTo("test-workflow");
        assertThat(summary.getStatus()).isEqualTo("completed");
        assertThat(summary.getTotalSteps()).isEqualTo(3);
        assertThat(summary.getCompletedSteps()).isEqualTo(3);
        assertThat(summary.getStartedAt()).isEqualTo("2026-01-03T12:00:00Z");
        assertThat(summary.getCompletedAt()).isEqualTo("2026-01-03T12:00:05Z");
        assertThat(summary.getDurationMs()).isEqualTo(5000);
        assertThat(summary.getTotalTokens()).isEqualTo(150);
        assertThat(summary.getTotalCostUsd()).isEqualTo(0.01);
    }

    @Test
    @DisplayName("ExecutionSummary setters should work")
    void executionSummarySettersShouldWork() {
        ExecutionSummary summary = new ExecutionSummary();
        summary.setRequestId("exec-456");
        summary.setWorkflowName("my-workflow");
        summary.setStatus("running");
        summary.setTotalSteps(5);
        summary.setCompletedSteps(2);
        summary.setStartedAt("2026-01-03T10:00:00Z");
        summary.setTotalTokens(100);
        summary.setTotalCostUsd(0.005);

        assertThat(summary.getRequestId()).isEqualTo("exec-456");
        assertThat(summary.getWorkflowName()).isEqualTo("my-workflow");
        assertThat(summary.getStatus()).isEqualTo("running");
        assertThat(summary.getTotalSteps()).isEqualTo(5);
        assertThat(summary.getCompletedSteps()).isEqualTo(2);
    }

    // ========================================================================
    // ExecutionSnapshot Tests
    // ========================================================================

    @Test
    @DisplayName("ExecutionSnapshot should deserialize from JSON")
    void executionSnapshotShouldDeserialize() throws Exception {
        String json = "{\"request_id\":\"exec-123\",\"step_index\":0,\"step_name\":\"greet\"," +
            "\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\"," +
            "\"completed_at\":\"2026-01-03T12:00:02Z\",\"duration_ms\":2000," +
            "\"provider\":\"anthropic\",\"model\":\"claude-sonnet-4\"," +
            "\"tokens_in\":20,\"tokens_out\":30,\"cost_usd\":0.002}";

        ExecutionSnapshot snapshot = objectMapper.readValue(json, ExecutionSnapshot.class);

        assertThat(snapshot.getRequestId()).isEqualTo("exec-123");
        assertThat(snapshot.getStepIndex()).isEqualTo(0);
        assertThat(snapshot.getStepName()).isEqualTo("greet");
        assertThat(snapshot.getStatus()).isEqualTo("completed");
        assertThat(snapshot.getProvider()).isEqualTo("anthropic");
        assertThat(snapshot.getModel()).isEqualTo("claude-sonnet-4");
        assertThat(snapshot.getTokensIn()).isEqualTo(20);
        assertThat(snapshot.getTokensOut()).isEqualTo(30);
        assertThat(snapshot.getCostUsd()).isEqualTo(0.002);
    }

    @Test
    @DisplayName("ExecutionSnapshot setters should work")
    void executionSnapshotSettersShouldWork() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot();
        snapshot.setRequestId("exec-789");
        snapshot.setStepIndex(1);
        snapshot.setStepName("process");
        snapshot.setStatus("running");
        snapshot.setProvider("openai");
        snapshot.setModel("gpt-4");

        assertThat(snapshot.getRequestId()).isEqualTo("exec-789");
        assertThat(snapshot.getStepIndex()).isEqualTo(1);
        assertThat(snapshot.getStepName()).isEqualTo("process");
    }

    // ========================================================================
    // TimelineEntry Tests
    // ========================================================================

    @Test
    @DisplayName("TimelineEntry should deserialize from JSON")
    void timelineEntryShouldDeserialize() throws Exception {
        String json = "{\"step_index\":0,\"step_name\":\"start\",\"status\":\"completed\"," +
            "\"started_at\":\"2026-01-03T12:00:00Z\",\"completed_at\":\"2026-01-03T12:00:01Z\"," +
            "\"duration_ms\":1000,\"has_error\":false,\"has_approval\":true}";

        TimelineEntry entry = objectMapper.readValue(json, TimelineEntry.class);

        assertThat(entry.getStepIndex()).isEqualTo(0);
        assertThat(entry.getStepName()).isEqualTo("start");
        assertThat(entry.getStatus()).isEqualTo("completed");
        assertThat(entry.getDurationMs()).isEqualTo(1000);
        assertThat(entry.hasError()).isFalse();
        assertThat(entry.hasApproval()).isTrue();
    }

    @Test
    @DisplayName("TimelineEntry with error should deserialize")
    void timelineEntryWithErrorShouldDeserialize() throws Exception {
        String json = "{\"step_index\":2,\"step_name\":\"failed-step\",\"status\":\"failed\"," +
            "\"started_at\":\"2026-01-03T12:00:10Z\",\"has_error\":true,\"has_approval\":false}";

        TimelineEntry entry = objectMapper.readValue(json, TimelineEntry.class);

        assertThat(entry.getStepName()).isEqualTo("failed-step");
        assertThat(entry.getStatus()).isEqualTo("failed");
        assertThat(entry.hasError()).isTrue();
        assertThat(entry.hasApproval()).isFalse();
    }

    // ========================================================================
    // ExecutionDetail Tests
    // ========================================================================

    @Test
    @DisplayName("ExecutionDetail should deserialize from JSON")
    void executionDetailShouldDeserialize() throws Exception {
        String json = "{\"summary\":{\"request_id\":\"exec-123\",\"workflow_name\":\"test-workflow\"," +
            "\"status\":\"completed\",\"total_steps\":2,\"completed_steps\":2," +
            "\"started_at\":\"2026-01-03T12:00:00Z\",\"completed_at\":\"2026-01-03T12:00:05Z\"," +
            "\"total_tokens\":100,\"total_cost_usd\":0.005}," +
            "\"steps\":[{\"request_id\":\"exec-123\",\"step_index\":0,\"step_name\":\"greet\"," +
            "\"status\":\"completed\",\"started_at\":\"2026-01-03T12:00:00Z\"," +
            "\"tokens_in\":10,\"tokens_out\":20}]}";

        ExecutionDetail detail = objectMapper.readValue(json, ExecutionDetail.class);

        assertThat(detail.getSummary()).isNotNull();
        assertThat(detail.getSummary().getRequestId()).isEqualTo("exec-123");
        assertThat(detail.getSummary().getStatus()).isEqualTo("completed");
        assertThat(detail.getSteps()).hasSize(1);
        assertThat(detail.getSteps().get(0).getStepName()).isEqualTo("greet");
    }

    @Test
    @DisplayName("ExecutionDetail setters should work")
    void executionDetailSettersShouldWork() {
        ExecutionSummary summary = new ExecutionSummary();
        summary.setRequestId("exec-999");

        ExecutionSnapshot step = new ExecutionSnapshot();
        step.setStepName("test-step");

        ExecutionDetail detail = new ExecutionDetail();
        detail.setSummary(summary);
        detail.setSteps(Arrays.asList(step));

        assertThat(detail.getSummary().getRequestId()).isEqualTo("exec-999");
        assertThat(detail.getSteps()).hasSize(1);
    }

    // ========================================================================
    // ListExecutionsResponse Tests
    // ========================================================================

    @Test
    @DisplayName("ListExecutionsResponse should deserialize from JSON")
    void listExecutionsResponseShouldDeserialize() throws Exception {
        String json = "{\"executions\":[" +
            "{\"request_id\":\"exec-1\",\"workflow_name\":\"workflow-1\",\"status\":\"completed\"," +
            "\"total_steps\":1,\"completed_steps\":1,\"started_at\":\"2026-01-03T12:00:00Z\"," +
            "\"total_tokens\":50,\"total_cost_usd\":0.001}," +
            "{\"request_id\":\"exec-2\",\"workflow_name\":\"workflow-2\",\"status\":\"running\"," +
            "\"total_steps\":3,\"completed_steps\":1,\"started_at\":\"2026-01-03T12:00:10Z\"," +
            "\"total_tokens\":25,\"total_cost_usd\":0.0005}]," +
            "\"total\":2,\"limit\":50,\"offset\":0}";

        ListExecutionsResponse response = objectMapper.readValue(json, ListExecutionsResponse.class);

        assertThat(response.getExecutions()).hasSize(2);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getLimit()).isEqualTo(50);
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getExecutions().get(0).getRequestId()).isEqualTo("exec-1");
        assertThat(response.getExecutions().get(1).getStatus()).isEqualTo("running");
    }

    // ========================================================================
    // ListExecutionsOptions Tests
    // ========================================================================

    @Test
    @DisplayName("ListExecutionsOptions fluent setters should work")
    void listExecutionsOptionsFluentSettersShouldWork() {
        ListExecutionsOptions options = ListExecutionsOptions.builder()
            .setLimit(10)
            .setOffset(20)
            .setStatus("completed")
            .setWorkflowId("test-workflow");

        assertThat(options.getLimit()).isEqualTo(10);
        assertThat(options.getOffset()).isEqualTo(20);
        assertThat(options.getStatus()).isEqualTo("completed");
        assertThat(options.getWorkflowId()).isEqualTo("test-workflow");
    }

    // ========================================================================
    // ExecutionExportOptions Tests
    // ========================================================================

    @Test
    @DisplayName("ExecutionExportOptions fluent setters should work")
    void executionExportOptionsFluentSettersShouldWork() {
        ExecutionExportOptions options = ExecutionExportOptions.builder()
            .setFormat("json")
            .setIncludeInput(true)
            .setIncludeOutput(true)
            .setIncludePolicies(false);

        assertThat(options.getFormat()).isEqualTo("json");
        assertThat(options.isIncludeInput()).isTrue();
        assertThat(options.isIncludeOutput()).isTrue();
        assertThat(options.isIncludePolicies()).isFalse();
    }
}
