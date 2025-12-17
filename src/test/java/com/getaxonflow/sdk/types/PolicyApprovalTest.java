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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PolicyApproval Types")
class PolicyApprovalTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    @DisplayName("PolicyApprovalRequest - should build with required fields")
    void requestShouldBuildWithRequiredFields() {
        PolicyApprovalRequest request = PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("What is the weather?")
            .build();

        assertThat(request.getUserToken()).isEqualTo("user-123");
        assertThat(request.getQuery()).isEqualTo("What is the weather?");
        assertThat(request.getDataSources()).isEmpty();
        assertThat(request.getContext()).isEmpty();
    }

    @Test
    @DisplayName("PolicyApprovalRequest - should build with all fields")
    void requestShouldBuildWithAllFields() {
        PolicyApprovalRequest request = PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("Analyze customer data")
            .dataSources(List.of("crm", "analytics"))
            .context(Map.of("department", "sales"))
            .clientId("client-456")
            .build();

        assertThat(request.getUserToken()).isEqualTo("user-123");
        assertThat(request.getQuery()).isEqualTo("Analyze customer data");
        assertThat(request.getDataSources()).containsExactly("crm", "analytics");
        assertThat(request.getContext()).containsEntry("department", "sales");
        assertThat(request.getClientId()).isEqualTo("client-456");
    }

    @Test
    @DisplayName("PolicyApprovalRequest - should throw on null user token")
    void requestShouldThrowOnNullUserToken() {
        assertThatThrownBy(() -> PolicyApprovalRequest.builder()
            .query("test")
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("userToken");
    }

    @Test
    @DisplayName("PolicyApprovalRequest - should throw on null query")
    void requestShouldThrowOnNullQuery() {
        assertThatThrownBy(() -> PolicyApprovalRequest.builder()
            .userToken("user-123")
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("query");
    }

    @Test
    @DisplayName("PolicyApprovalRequest - should serialize to JSON")
    void requestShouldSerializeToJson() throws Exception {
        PolicyApprovalRequest request = PolicyApprovalRequest.builder()
            .userToken("user-123")
            .query("test query")
            .dataSources(List.of("source1"))
            .build();

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"user_token\":\"user-123\"");
        assertThat(json).contains("\"query\":\"test query\"");
        assertThat(json).contains("\"data_sources\":[\"source1\"]");
    }

    @Test
    @DisplayName("PolicyApprovalResult - should deserialize approved response")
    void resultShouldDeserializeApproved() throws Exception {
        String json = "{"
            + "\"context_id\": \"ctx_abc123\","
            + "\"approved\": true,"
            + "\"approved_data\": {\"filtered\": \"data\"},"
            + "\"policies\": [\"policy1\", \"policy2\"],"
            + "\"expires_at\": \"2025-12-31T23:59:59Z\","
            + "\"processing_time\": \"3.14ms\""
            + "}";

        PolicyApprovalResult result = objectMapper.readValue(json, PolicyApprovalResult.class);

        assertThat(result.getContextId()).isEqualTo("ctx_abc123");
        assertThat(result.isApproved()).isTrue();
        assertThat(result.getApprovedData()).containsEntry("filtered", "data");
        assertThat(result.getPolicies()).containsExactly("policy1", "policy2");
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getProcessingTime()).isEqualTo("3.14ms");
        assertThat(result.getBlockReason()).isNull();
    }

    @Test
    @DisplayName("PolicyApprovalResult - should deserialize blocked response")
    void resultShouldDeserializeBlocked() throws Exception {
        String json = "{"
            + "\"context_id\": \"ctx_abc123\","
            + "\"approved\": false,"
            + "\"block_reason\": \"Request blocked by policy: pii_detection\","
            + "\"policies\": [\"pii_detection\"]"
            + "}";

        PolicyApprovalResult result = objectMapper.readValue(json, PolicyApprovalResult.class);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getBlockReason()).isEqualTo("Request blocked by policy: pii_detection");
        assertThat(result.getBlockingPolicyName()).isEqualTo("pii_detection");
    }

    @Test
    @DisplayName("PolicyApprovalResult - should detect expired approval")
    void resultShouldDetectExpired() throws Exception {
        String json = "{"
            + "\"context_id\": \"ctx_abc123\","
            + "\"approved\": true,"
            + "\"expires_at\": \"2020-01-01T00:00:00Z\""
            + "}";

        PolicyApprovalResult result = objectMapper.readValue(json, PolicyApprovalResult.class);

        assertThat(result.isExpired()).isTrue();
    }

    @Test
    @DisplayName("PolicyApprovalResult - should detect non-expired approval")
    void resultShouldDetectNonExpired() throws Exception {
        Instant futureTime = Instant.now().plusSeconds(300);
        String json = String.format("{"
            + "\"context_id\": \"ctx_abc123\","
            + "\"approved\": true,"
            + "\"expires_at\": \"%s\""
            + "}", futureTime.toString());

        PolicyApprovalResult result = objectMapper.readValue(json, PolicyApprovalResult.class);

        assertThat(result.isExpired()).isFalse();
    }

    @Test
    @DisplayName("PolicyApprovalResult - should handle rate limit info")
    void resultShouldHandleRateLimitInfo() throws Exception {
        String json = "{"
            + "\"context_id\": \"ctx_abc123\","
            + "\"approved\": true,"
            + "\"rate_limit_info\": {"
            + "\"limit\": 100,"
            + "\"remaining\": 95,"
            + "\"reset_at\": \"2025-12-31T23:59:59Z\""
            + "}"
            + "}";

        PolicyApprovalResult result = objectMapper.readValue(json, PolicyApprovalResult.class);

        assertThat(result.getRateLimitInfo()).isNotNull();
        assertThat(result.getRateLimitInfo().getLimit()).isEqualTo(100);
        assertThat(result.getRateLimitInfo().getRemaining()).isEqualTo(95);
    }
}
