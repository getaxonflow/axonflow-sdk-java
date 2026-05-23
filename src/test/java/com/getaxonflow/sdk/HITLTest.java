/*
 * Copyright 2026 AxonFlow
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
import com.getaxonflow.sdk.types.hitl.HITLTypes.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for HITL (Human-in-the-Loop) Queue API methods. */
@WireMockTest
@DisplayName("HITL Queue Methods")
class HITLTest {

  private AxonFlow axonflow;

  private static final String SAMPLE_APPROVAL_REQUEST =
      "{"
          + "\"request_id\": \"hitl_req_001\","
          + "\"org_id\": \"org_123\","
          + "\"tenant_id\": \"tenant_456\","
          + "\"client_id\": \"client_789\","
          + "\"user_id\": \"user_abc\","
          + "\"original_query\": \"Transfer $50,000 to account 12345\","
          + "\"request_type\": \"llm_chat\","
          + "\"request_context\": {\"session_id\": \"sess_001\"},"
          + "\"triggered_policy_id\": \"pol_high_value\","
          + "\"triggered_policy_name\": \"High Value Transaction Check\","
          + "\"trigger_reason\": \"Transaction amount exceeds $10,000 threshold\","
          + "\"severity\": \"high\","
          + "\"eu_ai_act_article\": \"Article 14\","
          + "\"compliance_framework\": \"EU AI Act\","
          + "\"risk_classification\": \"high-risk\","
          + "\"status\": \"pending\","
          + "\"expires_at\": \"2026-02-13T00:00:00Z\","
          + "\"created_at\": \"2026-02-12T12:00:00Z\","
          + "\"updated_at\": \"2026-02-12T12:00:00Z\""
          + "}";

  private static final String SAMPLE_APPROVAL_REQUEST_2 =
      "{"
          + "\"request_id\": \"hitl_req_002\","
          + "\"org_id\": \"org_123\","
          + "\"tenant_id\": \"tenant_456\","
          + "\"client_id\": \"client_789\","
          + "\"original_query\": \"Access patient medical records\","
          + "\"request_type\": \"llm_chat\","
          + "\"triggered_policy_id\": \"pol_hipaa\","
          + "\"triggered_policy_name\": \"HIPAA PHI Access Control\","
          + "\"trigger_reason\": \"PHI access requires human approval\","
          + "\"severity\": \"critical\","
          + "\"status\": \"pending\","
          + "\"expires_at\": \"2026-02-13T00:00:00Z\","
          + "\"created_at\": \"2026-02-12T12:30:00Z\","
          + "\"updated_at\": \"2026-02-12T12:30:00Z\""
          + "}";

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(AxonFlowConfig.builder().endpoint(wmRuntimeInfo.getHttpBaseUrl()).build());
  }

  // ========================================================================
  // listHITLQueue Tests
  // ========================================================================

  @Nested
  @DisplayName("listHITLQueue")
  class ListHITLQueue {

    @Test
    @DisplayName("should return approval requests from queue")
    void shouldReturnApprovalRequests() {
      stubFor(
          get(urlPathEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": ["
                              + SAMPLE_APPROVAL_REQUEST
                              + ","
                              + SAMPLE_APPROVAL_REQUEST_2
                              + "], \"meta\": {\"total\": 2, \"limit\": 50, \"offset\": 0, \"has_more\": false}}")));

      HITLQueueListResponse result = axonflow.listHITLQueue();

      assertThat(result.getItems()).hasSize(2);
      assertThat(result.getTotal()).isEqualTo(2);
      assertThat(result.isHasMore()).isFalse();

      HITLApprovalRequest first = result.getItems().get(0);
      assertThat(first.getRequestId()).isEqualTo("hitl_req_001");
      assertThat(first.getOrgId()).isEqualTo("org_123");
      assertThat(first.getTenantId()).isEqualTo("tenant_456");
      assertThat(first.getOriginalQuery()).isEqualTo("Transfer $50,000 to account 12345");
      assertThat(first.getTriggeredPolicyName()).isEqualTo("High Value Transaction Check");
      assertThat(first.getSeverity()).isEqualTo("high");
      assertThat(first.getStatus()).isEqualTo("pending");
      assertThat(first.getEuAiActArticle()).isEqualTo("Article 14");
      assertThat(first.getComplianceFramework()).isEqualTo("EU AI Act");
      assertThat(first.getRiskClassification()).isEqualTo("high-risk");
    }

    @Test
    @DisplayName("should return empty list when no items")
    void shouldReturnEmptyList() {
      stubFor(
          get(urlPathEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": [], \"meta\": {\"total\": 0, \"has_more\": false}}")));

      HITLQueueListResponse result = axonflow.listHITLQueue();

      assertThat(result.getItems()).isEmpty();
      assertThat(result.getTotal()).isEqualTo(0);
    }

    @Test
    @DisplayName("should include query params when options provided")
    void shouldIncludeQueryParams() {
      stubFor(
          get(urlPathEqualTo("/api/v1/hitl/queue"))
              .withQueryParam("status", equalTo("pending"))
              .withQueryParam("severity", equalTo("critical"))
              .withQueryParam("limit", equalTo("10"))
              .withQueryParam("offset", equalTo("5"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": ["
                              + SAMPLE_APPROVAL_REQUEST_2
                              + "], \"meta\": {\"total\": 1, \"has_more\": false}}")));

      HITLQueueListOptions opts =
          HITLQueueListOptions.builder()
              .status("pending")
              .severity("critical")
              .limit(10)
              .offset(5)
              .build();

      HITLQueueListResponse result = axonflow.listHITLQueue(opts);

      assertThat(result.getItems()).hasSize(1);
      assertThat(result.getItems().get(0).getSeverity()).isEqualTo("critical");
    }

    @Test
    @DisplayName("should handle has_more pagination flag")
    void shouldHandleHasMore() {
      stubFor(
          get(urlPathEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": ["
                              + SAMPLE_APPROVAL_REQUEST
                              + "], \"meta\": {\"total\": 100, \"limit\": 1, \"offset\": 0, \"has_more\": true}}")));

      HITLQueueListResponse result =
          axonflow.listHITLQueue(HITLQueueListOptions.builder().limit(1).build());

      assertThat(result.getItems()).hasSize(1);
      assertThat(result.getTotal()).isEqualTo(100);
      assertThat(result.isHasMore()).isTrue();
    }
  }

  // ========================================================================
  // createHITLRequest Tests
  // ========================================================================

  @Nested
  @DisplayName("createHITLRequest")
  class CreateHITLRequest {

    private static final String CREATED_APPROVAL_REQUEST =
        "{"
            + "\"request_id\": \"hitl-req-new-001\","
            + "\"org_id\": \"org-1\","
            + "\"tenant_id\": \"tenant-1\","
            + "\"client_id\": \"loan-desk\","
            + "\"user_id\": \"cust-001\","
            + "\"original_query\": \"disburse $50000 to cust-001\","
            + "\"request_type\": \"adk-tool\","
            + "\"request_context\": {\"tool_name\": \"disburse_payment\"},"
            + "\"triggered_policy_id\": \"loan-amount-cap\","
            + "\"triggered_policy_name\": \"Loan amount cap\","
            + "\"trigger_reason\": \"Disbursement above $10k requires manager approval\","
            + "\"severity\": \"high\","
            + "\"notify_url\": \"https://workflows.example.com/hooks/loan-approve\","
            + "\"status\": \"pending\","
            + "\"expires_at\": \"2026-05-23T11:00:00Z\","
            + "\"created_at\": \"2026-05-23T10:00:00Z\","
            + "\"updated_at\": \"2026-05-23T10:00:00Z\""
            + "}";

    @Test
    @DisplayName("should POST a full create-input and return the created record")
    void shouldPostFullCreateInputAndReturnCreatedRecord() {
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse()
                      .withStatus(201)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": " + CREATED_APPROVAL_REQUEST + "}")));

      HITLCreateInput input =
          HITLCreateInput.builder()
              .clientId("loan-desk")
              .userId("cust-001")
              .originalQuery("disburse $50000 to cust-001")
              .requestType("adk-tool")
              .triggeredPolicyId("loan-amount-cap")
              .triggeredPolicyName("Loan amount cap")
              .triggerReason("Disbursement above $10k requires manager approval")
              .severity("high")
              .notifyUrl("https://workflows.example.com/hooks/loan-approve")
              .build();

      HITLApprovalRequest result = axonflow.createHITLRequest(input);

      assertThat(result.getRequestId()).isEqualTo("hitl-req-new-001");
      assertThat(result.getStatus()).isEqualTo("pending");
      assertThat(result.getNotifyUrl())
          .isEqualTo("https://workflows.example.com/hooks/loan-approve");
      assertThat(result.getTriggeredPolicyName()).isEqualTo("Loan amount cap");

      verify(
          postRequestedFor(urlEqualTo("/api/v1/hitl/queue"))
              .withRequestBody(matchingJsonPath("$.client_id", equalTo("loan-desk")))
              .withRequestBody(
                  matchingJsonPath(
                      "$.notify_url",
                      equalTo("https://workflows.example.com/hooks/loan-approve")))
              .withRequestBody(matchingJsonPath("$.severity", equalTo("high"))));
    }

    @Test
    @DisplayName("should accept minimal required-field set (clientId + originalQuery + requestType)")
    void shouldAcceptMinimalRequiredFields() {
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse()
                      .withStatus(201)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": {"
                              + "\"request_id\": \"hitl-req-minimal\","
                              + "\"org_id\": \"org-1\","
                              + "\"tenant_id\": \"tenant-1\","
                              + "\"client_id\": \"c1\","
                              + "\"original_query\": \"q\","
                              + "\"request_type\": \"chat\","
                              + "\"triggered_policy_id\": \"\","
                              + "\"triggered_policy_name\": \"\","
                              + "\"trigger_reason\": \"\","
                              + "\"severity\": \"high\","
                              + "\"status\": \"pending\","
                              + "\"expires_at\": \"2026-05-23T11:00:00Z\","
                              + "\"created_at\": \"2026-05-23T10:00:00Z\","
                              + "\"updated_at\": \"2026-05-23T10:00:00Z\""
                              + "}}")));

      HITLCreateInput input =
          HITLCreateInput.builder()
              .clientId("c1")
              .originalQuery("q")
              .requestType("chat")
              .build();

      HITLApprovalRequest result = axonflow.createHITLRequest(input);
      assertThat(result.getRequestId()).isEqualTo("hitl-req-minimal");
      assertThat(result.getNotifyUrl()).isNull();
    }

    @Test
    @DisplayName(
        "should surface a platform 400 on bad notify_url scheme as an exception from the SDK")
    void shouldSurfaceBadNotifyUrlSchemeAsException() {
      // Mirrors platform/agent/hitl/webhook.go:105 ValidateNotifyURL.
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\":false,\"error\":\"notify_url scheme \\\"javascript\\\""
                              + " is not allowed (use https:// or http://)\"}")));

      HITLCreateInput input =
          HITLCreateInput.builder()
              .clientId("loan-desk")
              .originalQuery("disburse $50000")
              .requestType("adk-tool")
              .notifyUrl("javascript:alert(1)")
              .build();

      assertThatThrownBy(() -> axonflow.createHITLRequest(input))
          .isInstanceOf(AxonFlowException.class);
    }

    @Test
    @DisplayName("should propagate 401 as an exception")
    void shouldPropagateAuthFailure() {
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse()
                      .withStatus(401)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"success\":false,\"error\":\"Invalid API key\"}")));

      HITLCreateInput input =
          HITLCreateInput.builder()
              .clientId("loan-desk")
              .originalQuery("disburse $50000")
              .requestType("adk-tool")
              .build();

      assertThatThrownBy(() -> axonflow.createHITLRequest(input))
          .isInstanceOf(AxonFlowException.class);
    }

    @Test
    @DisplayName("should propagate network/connect failure as an exception")
    void shouldPropagateNetworkFailure() {
      // Stop the WireMock-driven endpoint by stubbing a fault that closes
      // the connection before reply. Mirrors a real ECONNRESET on the
      // wire.
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue"))
              .willReturn(
                  aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

      HITLCreateInput input =
          HITLCreateInput.builder()
              .clientId("loan-desk")
              .originalQuery("disburse $50000")
              .requestType("adk-tool")
              .build();

      assertThatThrownBy(() -> axonflow.createHITLRequest(input))
          .isInstanceOf(AxonFlowException.class);
    }

    @Test
    @DisplayName("should reject missing client_id")
    void shouldRejectMissingClientId() {
      HITLCreateInput input =
          HITLCreateInput.builder().originalQuery("q").requestType("chat").build();
      assertThatThrownBy(() -> axonflow.createHITLRequest(input))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("client_id");
    }

    @Test
    @DisplayName("should reject missing original_query")
    void shouldRejectMissingOriginalQuery() {
      HITLCreateInput input =
          HITLCreateInput.builder().clientId("c1").requestType("chat").build();
      assertThatThrownBy(() -> axonflow.createHITLRequest(input))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("original_query");
    }

    @Test
    @DisplayName("should reject missing request_type")
    void shouldRejectMissingRequestType() {
      HITLCreateInput input =
          HITLCreateInput.builder().clientId("c1").originalQuery("q").build();
      assertThatThrownBy(() -> axonflow.createHITLRequest(input))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("request_type");
    }
  }

  // ========================================================================
  // getHITLRequest Tests
  // ========================================================================

  @Nested
  @DisplayName("getHITLRequest")
  class GetHITLRequest {

    @Test
    @DisplayName("should return approval request by ID")
    void shouldReturnRequestById() {
      stubFor(
          get(urlEqualTo("/api/v1/hitl/queue/hitl_req_001"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"success\": true, \"data\": " + SAMPLE_APPROVAL_REQUEST + "}")));

      HITLApprovalRequest result = axonflow.getHITLRequest("hitl_req_001");

      assertThat(result.getRequestId()).isEqualTo("hitl_req_001");
      assertThat(result.getTriggeredPolicyId()).isEqualTo("pol_high_value");
      assertThat(result.getTriggerReason())
          .isEqualTo("Transaction amount exceeds $10,000 threshold");
      assertThat(result.getUserId()).isEqualTo("user_abc");
      assertThat(result.getRequestContext()).containsEntry("session_id", "sess_001");
    }

    @Test
    @DisplayName("should require non-null requestId")
    void shouldRequireRequestId() {
      assertThatThrownBy(() -> axonflow.getHITLRequest(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw on 404 not found")
    void shouldThrowOnNotFound() {
      stubFor(
          get(urlEqualTo("/api/v1/hitl/queue/nonexistent"))
              .willReturn(
                  aResponse()
                      .withStatus(404)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"Approval request not found\"}")));

      assertThatThrownBy(() -> axonflow.getHITLRequest("nonexistent"))
          .isInstanceOf(Exception.class);
    }
  }

  // ========================================================================
  // approveHITLRequest Tests
  // ========================================================================

  @Nested
  @DisplayName("approveHITLRequest")
  class ApproveHITLRequest {

    @Test
    @DisplayName("should send approve request with review input")
    void shouldSendApproveRequest() {
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue/hitl_req_001/approve"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"success\": true}")));

      HITLReviewInput review =
          HITLReviewInput.builder()
              .reviewerId("reviewer_001")
              .reviewerEmail("reviewer@example.com")
              .reviewerRole("compliance_officer")
              .comment("Approved after manual verification")
              .build();

      axonflow.approveHITLRequest("hitl_req_001", review);

      verify(
          postRequestedFor(urlEqualTo("/api/v1/hitl/queue/hitl_req_001/approve"))
              .withHeader("Content-Type", containing("application/json"))
              .withRequestBody(containing("\"reviewer_id\":\"reviewer_001\""))
              .withRequestBody(containing("\"reviewer_email\":\"reviewer@example.com\""))
              .withRequestBody(containing("\"reviewer_role\":\"compliance_officer\""))
              .withRequestBody(containing("\"comment\":\"Approved after manual verification\"")));
    }

    @Test
    @DisplayName("should require non-null requestId")
    void shouldRequireRequestId() {
      HITLReviewInput review =
          HITLReviewInput.builder()
              .reviewerId("reviewer_001")
              .reviewerEmail("reviewer@example.com")
              .build();

      assertThatThrownBy(() -> axonflow.approveHITLRequest(null, review))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should require non-null review")
    void shouldRequireReview() {
      assertThatThrownBy(() -> axonflow.approveHITLRequest("hitl_req_001", null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should handle server error")
    void shouldHandleServerError() {
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue/hitl_req_001/approve"))
              .willReturn(
                  aResponse()
                      .withStatus(500)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"Internal server error\"}")));

      HITLReviewInput review =
          HITLReviewInput.builder()
              .reviewerId("reviewer_001")
              .reviewerEmail("reviewer@example.com")
              .build();

      assertThatThrownBy(() -> axonflow.approveHITLRequest("hitl_req_001", review))
          .isInstanceOf(Exception.class);
    }
  }

  // ========================================================================
  // rejectHITLRequest Tests
  // ========================================================================

  @Nested
  @DisplayName("rejectHITLRequest")
  class RejectHITLRequest {

    @Test
    @DisplayName("should send reject request with review input")
    void shouldSendRejectRequest() {
      stubFor(
          post(urlEqualTo("/api/v1/hitl/queue/hitl_req_001/reject"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"success\": true}")));

      HITLReviewInput review =
          HITLReviewInput.builder()
              .reviewerId("reviewer_002")
              .reviewerEmail("admin@example.com")
              .comment("Rejected: suspicious transaction pattern")
              .build();

      axonflow.rejectHITLRequest("hitl_req_001", review);

      verify(
          postRequestedFor(urlEqualTo("/api/v1/hitl/queue/hitl_req_001/reject"))
              .withHeader("Content-Type", containing("application/json"))
              .withRequestBody(containing("\"reviewer_id\":\"reviewer_002\""))
              .withRequestBody(containing("\"reviewer_email\":\"admin@example.com\""))
              .withRequestBody(
                  containing("\"comment\":\"Rejected: suspicious transaction pattern\"")));
    }

    @Test
    @DisplayName("should require non-null requestId")
    void shouldRequireRequestId() {
      HITLReviewInput review =
          HITLReviewInput.builder()
              .reviewerId("reviewer_001")
              .reviewerEmail("reviewer@example.com")
              .build();

      assertThatThrownBy(() -> axonflow.rejectHITLRequest(null, review))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should require non-null review")
    void shouldRequireReview() {
      assertThatThrownBy(() -> axonflow.rejectHITLRequest("hitl_req_001", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ========================================================================
  // getHITLStats Tests
  // ========================================================================

  @Nested
  @DisplayName("getHITLStats")
  class GetHITLStats {

    @Test
    @DisplayName("should return parsed stats")
    void shouldReturnParsedStats() {
      stubFor(
          get(urlEqualTo("/api/v1/hitl/stats"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": {"
                              + "\"total_pending\": 42,"
                              + "\"high_priority\": 8,"
                              + "\"critical_priority\": 3,"
                              + "\"oldest_pending_hours\": 12.5"
                              + "}}")));

      HITLStats stats = axonflow.getHITLStats();

      assertThat(stats.getTotalPending()).isEqualTo(42);
      assertThat(stats.getHighPriority()).isEqualTo(8);
      assertThat(stats.getCriticalPriority()).isEqualTo(3);
      assertThat(stats.getOldestPendingHours()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("should handle null oldest_pending_hours")
    void shouldHandleNullOldestPendingHours() {
      stubFor(
          get(urlEqualTo("/api/v1/hitl/stats"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"success\": true, \"data\": {"
                              + "\"total_pending\": 0,"
                              + "\"high_priority\": 0,"
                              + "\"critical_priority\": 0,"
                              + "\"oldest_pending_hours\": null"
                              + "}}")));

      HITLStats stats = axonflow.getHITLStats();

      assertThat(stats.getTotalPending()).isEqualTo(0);
      assertThat(stats.getOldestPendingHours()).isNull();
    }

    @Test
    @DisplayName("should handle stats without data wrapper")
    void shouldHandleStatsWithoutWrapper() {
      stubFor(
          get(urlEqualTo("/api/v1/hitl/stats"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{"
                              + "\"total_pending\": 5,"
                              + "\"high_priority\": 2,"
                              + "\"critical_priority\": 1,"
                              + "\"oldest_pending_hours\": 3.7"
                              + "}")));

      HITLStats stats = axonflow.getHITLStats();

      assertThat(stats.getTotalPending()).isEqualTo(5);
      assertThat(stats.getHighPriority()).isEqualTo(2);
      assertThat(stats.getCriticalPriority()).isEqualTo(1);
      assertThat(stats.getOldestPendingHours()).isEqualTo(3.7);
    }
  }
}
