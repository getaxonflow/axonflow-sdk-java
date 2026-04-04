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
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for auditToolCall method. */
@WireMockTest
@DisplayName("Audit Tool Call")
class AuditToolCallTest {

  private AxonFlow axonflow;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());
  }

  @Test
  @DisplayName("should audit tool call with all fields")
  void shouldAuditToolCallWithAllFields() {
    stubFor(
        post(urlEqualTo("/api/v1/audit/tool-call"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"audit_id\":\"aud_tc_001\",\"status\":\"recorded\",\"timestamp\":\"2026-03-14T12:00:00Z\"}")));

    Map<String, Object> input = new HashMap<>();
    input.put("query", "latest news");
    input.put("limit", 10);

    Map<String, Object> output = new HashMap<>();
    output.put("results", 5);
    output.put("source", "web");

    AuditToolCallRequest request =
        AuditToolCallRequest.builder()
            .toolName("web_search")
            .toolType("function")
            .input(input)
            .output(output)
            .workflowId("wf_123")
            .stepId("step_1")
            .userId("user_456")
            .durationMs(450L)
            .policiesApplied(Arrays.asList("policy_a", "policy_b"))
            .success(true)
            .errorMessage(null)
            .build();

    AuditToolCallResponse response = axonflow.auditToolCall(request);

    assertThat(response).isNotNull();
    assertThat(response.getAuditId()).isEqualTo("aud_tc_001");
    assertThat(response.getStatus()).isEqualTo("recorded");
    assertThat(response.getTimestamp()).isEqualTo("2026-03-14T12:00:00Z");

    verify(
        postRequestedFor(urlEqualTo("/api/v1/audit/tool-call"))
            .withRequestBody(matchingJsonPath("$.tool_name", equalTo("web_search")))
            .withRequestBody(matchingJsonPath("$.tool_type", equalTo("function")))
            .withRequestBody(matchingJsonPath("$.workflow_id", equalTo("wf_123")))
            .withRequestBody(matchingJsonPath("$.step_id", equalTo("step_1")))
            .withRequestBody(matchingJsonPath("$.user_id", equalTo("user_456")))
            .withRequestBody(matchingJsonPath("$.duration_ms", equalTo("450")))
            .withRequestBody(matchingJsonPath("$.success", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.policies_applied[0]", equalTo("policy_a")))
            .withRequestBody(matchingJsonPath("$.policies_applied[1]", equalTo("policy_b"))));
  }

  @Test
  @DisplayName("should audit tool call with required fields only")
  void shouldAuditToolCallWithRequiredFieldsOnly() {
    stubFor(
        post(urlEqualTo("/api/v1/audit/tool-call"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"audit_id\":\"aud_tc_002\",\"status\":\"recorded\",\"timestamp\":\"2026-03-14T12:01:00Z\"}")));

    AuditToolCallRequest request = AuditToolCallRequest.builder().toolName("db_lookup").build();

    AuditToolCallResponse response = axonflow.auditToolCall(request);

    assertThat(response).isNotNull();
    assertThat(response.getAuditId()).isEqualTo("aud_tc_002");
    assertThat(response.getStatus()).isEqualTo("recorded");

    verify(
        postRequestedFor(urlEqualTo("/api/v1/audit/tool-call"))
            .withRequestBody(matchingJsonPath("$.tool_name", equalTo("db_lookup"))));
  }

  @Test
  @DisplayName("should reject null request")
  void shouldRejectNullRequest() {
    assertThatThrownBy(() -> axonflow.auditToolCall(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("request cannot be null");
  }

  @Test
  @DisplayName("should reject null tool name")
  void shouldRejectNullToolName() {
    assertThatThrownBy(() -> AuditToolCallRequest.builder().build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("toolName cannot be null");
  }

  @Test
  @DisplayName("should reject empty tool name")
  void shouldRejectEmptyToolName() {
    assertThatThrownBy(() -> AuditToolCallRequest.builder().toolName("").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("toolName cannot be empty");
  }

  @Test
  @DisplayName("should handle server error")
  void shouldHandleServerError() {
    stubFor(
        post(urlEqualTo("/api/v1/audit/tool-call"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"internal server error\"}")));

    AuditToolCallRequest request = AuditToolCallRequest.builder().toolName("failing_tool").build();

    assertThatThrownBy(() -> axonflow.auditToolCall(request)).isInstanceOf(AxonFlowException.class);
  }

  @Test
  @DisplayName("auditToolCallAsync should return future")
  void auditToolCallAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/audit/tool-call"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"audit_id\":\"aud_tc_async\",\"status\":\"recorded\",\"timestamp\":\"2026-03-14T12:02:00Z\"}")));

    AuditToolCallRequest request =
        AuditToolCallRequest.builder().toolName("async_tool").toolType("mcp").build();

    CompletableFuture<AuditToolCallResponse> future = axonflow.auditToolCallAsync(request);
    AuditToolCallResponse response = future.get();

    assertThat(response).isNotNull();
    assertThat(response.getAuditId()).isEqualTo("aud_tc_async");
    assertThat(response.getStatus()).isEqualTo("recorded");
  }

  @Test
  @DisplayName("request should have correct equals and hashCode")
  void requestShouldHaveCorrectEqualsAndHashCode() {
    AuditToolCallRequest req1 =
        AuditToolCallRequest.builder().toolName("tool_a").toolType("function").build();
    AuditToolCallRequest req2 =
        AuditToolCallRequest.builder().toolName("tool_a").toolType("function").build();
    AuditToolCallRequest req3 = AuditToolCallRequest.builder().toolName("tool_b").build();

    assertThat(req1).isEqualTo(req2);
    assertThat(req1.hashCode()).isEqualTo(req2.hashCode());
    assertThat(req1).isNotEqualTo(req3);
  }

  @Test
  @DisplayName("response should have correct equals and hashCode")
  void responseShouldHaveCorrectEqualsAndHashCode() {
    AuditToolCallResponse res1 = new AuditToolCallResponse("id1", "ok", "2026-01-01T00:00:00Z");
    AuditToolCallResponse res2 = new AuditToolCallResponse("id1", "ok", "2026-01-01T00:00:00Z");
    AuditToolCallResponse res3 = new AuditToolCallResponse("id2", "ok", "2026-01-01T00:00:00Z");

    assertThat(res1).isEqualTo(res2);
    assertThat(res1.hashCode()).isEqualTo(res2.hashCode());
    assertThat(res1).isNotEqualTo(res3);
  }

  @Test
  @DisplayName("request toString should include key fields")
  void requestToStringShouldIncludeKeyFields() {
    AuditToolCallRequest request =
        AuditToolCallRequest.builder()
            .toolName("my_tool")
            .toolType("api")
            .workflowId("wf_1")
            .build();

    String str = request.toString();
    assertThat(str).contains("my_tool");
    assertThat(str).contains("api");
    assertThat(str).contains("wf_1");
  }

  @Test
  @DisplayName("response toString should include key fields")
  void responseToStringShouldIncludeKeyFields() {
    AuditToolCallResponse response =
        new AuditToolCallResponse("aud_1", "recorded", "2026-01-01T00:00:00Z");

    String str = response.toString();
    assertThat(str).contains("aud_1");
    assertThat(str).contains("recorded");
  }
}
