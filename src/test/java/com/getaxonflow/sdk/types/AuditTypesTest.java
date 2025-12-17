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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Audit Types")
class AuditTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("TokenUsage - should create with factory method")
    void tokenUsageShouldCreateWithFactory() {
        TokenUsage usage = TokenUsage.of(100, 150);

        assertThat(usage.getPromptTokens()).isEqualTo(100);
        assertThat(usage.getCompletionTokens()).isEqualTo(150);
        assertThat(usage.getTotalTokens()).isEqualTo(250);
    }

    @Test
    @DisplayName("TokenUsage - should deserialize from JSON")
    void tokenUsageShouldDeserialize() throws Exception {
        String json = "{"
            + "\"prompt_tokens\": 200,"
            + "\"completion_tokens\": 300,"
            + "\"total_tokens\": 500"
            + "}";

        TokenUsage usage = objectMapper.readValue(json, TokenUsage.class);

        assertThat(usage.getPromptTokens()).isEqualTo(200);
        assertThat(usage.getCompletionTokens()).isEqualTo(300);
        assertThat(usage.getTotalTokens()).isEqualTo(500);
    }

    @Test
    @DisplayName("TokenUsage - should serialize to JSON")
    void tokenUsageShouldSerialize() throws Exception {
        TokenUsage usage = TokenUsage.of(100, 200);
        String json = objectMapper.writeValueAsString(usage);

        assertThat(json).contains("\"prompt_tokens\":100");
        assertThat(json).contains("\"completion_tokens\":200");
        assertThat(json).contains("\"total_tokens\":300");
    }

    @Test
    @DisplayName("AuditOptions - should build with required fields")
    void auditOptionsShouldBuildWithRequired() {
        AuditOptions options = AuditOptions.builder()
            .contextId("ctx_123").clientId("test-client")
            .build();

        assertThat(options.getContextId()).isEqualTo("ctx_123");
        assertThat(options.getSuccess()).isTrue(); // Default
    }

    @Test
    @DisplayName("AuditOptions - should build with all fields")
    void auditOptionsShouldBuildWithAllFields() {
        TokenUsage tokenUsage = TokenUsage.of(100, 150);

        AuditOptions options = AuditOptions.builder()
            .contextId("ctx_123").clientId("test-client")
            .responseSummary("Weather information provided")
            .provider("openai")
            .model("gpt-4")
            .tokenUsage(tokenUsage)
            .latencyMs(1234)
            .metadata(Map.of("source", "api"))
            .success(true)
            .build();

        assertThat(options.getContextId()).isEqualTo("ctx_123");
        assertThat(options.getResponseSummary()).isEqualTo("Weather information provided");
        assertThat(options.getProvider()).isEqualTo("openai");
        assertThat(options.getModel()).isEqualTo("gpt-4");
        assertThat(options.getTokenUsage()).isEqualTo(tokenUsage);
        assertThat(options.getLatencyMs()).isEqualTo(1234L);
        assertThat(options.getMetadata()).containsEntry("source", "api");
        assertThat(options.getSuccess()).isTrue();
    }

    @Test
    @DisplayName("AuditOptions - should throw on null context ID")
    void auditOptionsShouldThrowOnNullContextId() {
        assertThatThrownBy(() -> AuditOptions.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("contextId");
    }

    @Test
    @DisplayName("AuditOptions - should add metadata entries")
    void auditOptionsShouldAddMetadata() {
        AuditOptions options = AuditOptions.builder()
            .contextId("ctx_123").clientId("test-client")
            .addMetadata("key1", "value1")
            .addMetadata("key2", 42)
            .build();

        assertThat(options.getMetadata())
            .containsEntry("key1", "value1")
            .containsEntry("key2", 42);
    }

    @Test
    @DisplayName("AuditOptions - should support error case")
    void auditOptionsShouldSupportError() {
        AuditOptions options = AuditOptions.builder()
            .contextId("ctx_123").clientId("test-client")
            .success(false)
            .errorMessage("LLM call failed: timeout")
            .build();

        assertThat(options.getSuccess()).isFalse();
        assertThat(options.getErrorMessage()).isEqualTo("LLM call failed: timeout");
    }

    @Test
    @DisplayName("AuditResult - should deserialize success")
    void auditResultShouldDeserializeSuccess() throws Exception {
        String json = "{"
            + "\"success\": true,"
            + "\"audit_id\": \"audit_abc123\","
            + "\"message\": \"Audit recorded\""
            + "}";

        AuditResult result = objectMapper.readValue(json, AuditResult.class);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAuditId()).isEqualTo("audit_abc123");
        assertThat(result.getMessage()).isEqualTo("Audit recorded");
        assertThat(result.getError()).isNull();
    }

    @Test
    @DisplayName("AuditResult - should deserialize failure")
    void auditResultShouldDeserializeFailure() throws Exception {
        String json = "{"
            + "\"success\": false,"
            + "\"error\": \"Context ID expired\""
            + "}";

        AuditResult result = objectMapper.readValue(json, AuditResult.class);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Context ID expired");
    }
}
