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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ClientResponse")
class ClientResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("should deserialize success response")
    void shouldDeserializeSuccessResponse() throws Exception {
        String json = "{"
            + "\"success\": true,"
            + "\"data\": {\"message\": \"Hello, world!\"},"
            + "\"blocked\": false,"
            + "\"policy_info\": {"
            + "\"policies_evaluated\": [\"policy1\", \"policy2\"],"
            + "\"processing_time\": \"5.23ms\""
            + "}"
            + "}";

        ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isBlocked()).isFalse();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getPolicyInfo()).isNotNull();
        assertThat(response.getPolicyInfo().getPoliciesEvaluated()).containsExactly("policy1", "policy2");
        assertThat(response.getPolicyInfo().getProcessingTime()).isEqualTo("5.23ms");
    }

    @Test
    @DisplayName("should deserialize blocked response")
    void shouldDeserializeBlockedResponse() throws Exception {
        String json = "{"
            + "\"success\": false,"
            + "\"blocked\": true,"
            + "\"block_reason\": \"Request blocked by policy: sql_injection_detection\","
            + "\"policy_info\": {"
            + "\"policies_evaluated\": [\"sql_injection_detection\"],"
            + "\"static_checks\": [\"sql_injection\"]"
            + "}"
            + "}";

        ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isBlocked()).isTrue();
        assertThat(response.getBlockReason()).isEqualTo("Request blocked by policy: sql_injection_detection");
        assertThat(response.getBlockingPolicyName()).isEqualTo("sql_injection_detection");
    }

    @ParameterizedTest
    @CsvSource({
        "Request blocked by policy: pii_detection,pii_detection",
        "Blocked by policy: sql_injection,sql_injection",
        "[rate_limit] Too many requests,rate_limit",
        "Some other reason,Some other reason"
    })
    @DisplayName("should extract policy name from various formats")
    void shouldExtractPolicyName(String blockReason, String expectedPolicy) throws Exception {
        String json = String.format("{"
            + "\"success\": false,"
            + "\"blocked\": true,"
            + "\"block_reason\": \"%s\""
            + "}", blockReason);

        ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response.getBlockingPolicyName()).isEqualTo(expectedPolicy);
    }

    @Test
    @DisplayName("should handle null block reason")
    void shouldHandleNullBlockReason() throws Exception {
        String json = "{"
            + "\"success\": true,"
            + "\"blocked\": false"
            + "}";

        ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response.getBlockingPolicyName()).isNull();
    }

    @Test
    @DisplayName("should deserialize plan response")
    void shouldDeserializePlanResponse() throws Exception {
        String json = "{"
            + "\"success\": true,"
            + "\"blocked\": false,"
            + "\"result\": \"Plan executed successfully\","
            + "\"plan_id\": \"plan_123\""
            + "}";

        ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response.getResult()).isEqualTo("Plan executed successfully");
        assertThat(response.getPlanId()).isEqualTo("plan_123");
    }

    @Test
    @DisplayName("should handle error response")
    void shouldHandleErrorResponse() throws Exception {
        String json = "{"
            + "\"success\": false,"
            + "\"blocked\": false,"
            + "\"error\": \"Internal server error\""
            + "}";

        ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Internal server error");
    }

    @Test
    @DisplayName("should ignore unknown properties")
    void shouldIgnoreUnknownProperties() throws Exception {
        String json = "{"
            + "\"success\": true,"
            + "\"blocked\": false,"
            + "\"unknown_field\": \"value\","
            + "\"another_unknown\": 123"
            + "}";

        ClientResponse response = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("should implement equals and hashCode")
    void shouldImplementEqualsAndHashCode() throws Exception {
        String json = "{"
            + "\"success\": true,"
            + "\"blocked\": false,"
            + "\"data\": \"test\""
            + "}";

        ClientResponse response1 = objectMapper.readValue(json, ClientResponse.class);
        ClientResponse response2 = objectMapper.readValue(json, ClientResponse.class);

        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }
}
