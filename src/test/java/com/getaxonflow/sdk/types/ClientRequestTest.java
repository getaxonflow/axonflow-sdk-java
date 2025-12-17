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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ClientRequest")
class ClientRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should build with required fields")
    void shouldBuildWithRequiredFields() {
        ClientRequest request = ClientRequest.builder()
            .query("What is the weather?")
            .build();

        assertThat(request.getQuery()).isEqualTo("What is the weather?");
        assertThat(request.getRequestType()).isEqualTo("chat");
    }

    @Test
    @DisplayName("should build with all fields")
    void shouldBuildWithAllFields() {
        ClientRequest request = ClientRequest.builder()
            .query("SELECT * FROM users")
            .userToken("user-123")
            .clientId("client-456")
            .requestType(RequestType.SQL)
            .context(Map.of("role", "admin"))
            .llmProvider("openai")
            .model("gpt-4")
            .build();

        assertThat(request.getQuery()).isEqualTo("SELECT * FROM users");
        assertThat(request.getUserToken()).isEqualTo("user-123");
        assertThat(request.getClientId()).isEqualTo("client-456");
        assertThat(request.getRequestType()).isEqualTo("sql");
        assertThat(request.getContext()).containsEntry("role", "admin");
        assertThat(request.getLlmProvider()).isEqualTo("openai");
        assertThat(request.getModel()).isEqualTo("gpt-4");
    }

    @Test
    @DisplayName("should throw on null query")
    void shouldThrowOnNullQuery() {
        assertThatThrownBy(() -> ClientRequest.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("query");
    }

    @Test
    @DisplayName("should add context entries")
    void shouldAddContextEntries() {
        ClientRequest request = ClientRequest.builder()
            .query("test")
            .addContext("key1", "value1")
            .addContext("key2", 42)
            .build();

        assertThat(request.getContext())
            .containsEntry("key1", "value1")
            .containsEntry("key2", 42);
    }

    @Test
    @DisplayName("should return immutable context")
    void shouldReturnImmutableContext() {
        ClientRequest request = ClientRequest.builder()
            .query("test")
            .context(Map.of("key", "value"))
            .build();

        assertThatThrownBy(() -> request.getContext().put("new", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should serialize to JSON")
    void shouldSerializeToJson() throws Exception {
        ClientRequest request = ClientRequest.builder()
            .query("What is the weather?")
            .userToken("user-123")
            .requestType(RequestType.CHAT)
            .build();

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"query\":\"What is the weather?\"");
        assertThat(json).contains("\"user_token\":\"user-123\"");
        assertThat(json).contains("\"request_type\":\"chat\"");
    }

    @Test
    @DisplayName("should implement equals and hashCode")
    void shouldImplementEqualsAndHashCode() {
        ClientRequest request1 = ClientRequest.builder()
            .query("test")
            .userToken("user-123")
            .build();

        ClientRequest request2 = ClientRequest.builder()
            .query("test")
            .userToken("user-123")
            .build();

        ClientRequest request3 = ClientRequest.builder()
            .query("different")
            .userToken("user-123")
            .build();

        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        assertThat(request1).isNotEqualTo(request3);
    }

    @Test
    @DisplayName("should have meaningful toString")
    void shouldHaveMeaningfulToString() {
        ClientRequest request = ClientRequest.builder()
            .query("What is the weather?")
            .userToken("user-123")
            .llmProvider("openai")
            .model("gpt-4")
            .build();

        String str = request.toString();
        assertThat(str).contains("What is the weather?");
        assertThat(str).contains("user-123");
        assertThat(str).contains("openai");
        assertThat(str).contains("gpt-4");
    }
}
