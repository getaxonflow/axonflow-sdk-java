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

import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for Audit Log Read Methods. Part of Issue #878 - Add audit log read capabilities to SDK.
 */
@WireMockTest
@DisplayName("Audit Log Read Methods")
class AuditReadTest {

  private AxonFlow axonflow;

  private static final String SAMPLE_AUDIT_ENTRY_1 =
      "{"
          + "\"id\": \"audit-1\","
          + "\"request_id\": \"req-1\","
          + "\"timestamp\": \"2026-01-05T10:00:00Z\","
          + "\"user_email\": \"user@example.com\","
          + "\"client_id\": \"client-1\","
          + "\"tenant_id\": \"tenant-1\","
          + "\"request_type\": \"llm_chat\","
          + "\"query_summary\": \"Test query\","
          + "\"success\": true,"
          + "\"blocked\": false,"
          + "\"risk_score\": 0.1,"
          + "\"provider\": \"openai\","
          + "\"model\": \"gpt-4\","
          + "\"tokens_used\": 150,"
          + "\"latency_ms\": 250,"
          + "\"policy_violations\": [],"
          + "\"metadata\": {}"
          + "}";

  private static final String SAMPLE_AUDIT_ENTRY_2 =
      "{"
          + "\"id\": \"audit-2\","
          + "\"request_id\": \"req-2\","
          + "\"timestamp\": \"2026-01-05T11:00:00Z\","
          + "\"user_email\": \"user@example.com\","
          + "\"client_id\": \"client-1\","
          + "\"tenant_id\": \"tenant-1\","
          + "\"request_type\": \"llm_chat\","
          + "\"query_summary\": \"Blocked query\","
          + "\"success\": false,"
          + "\"blocked\": true,"
          + "\"risk_score\": 0.9,"
          + "\"provider\": \"openai\","
          + "\"model\": \"gpt-4\","
          + "\"tokens_used\": 0,"
          + "\"latency_ms\": 50,"
          + "\"policy_violations\": [\"policy-1\"],"
          + "\"metadata\": {\"reason\": \"pii_detected\"}"
          + "}";

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .endpoint(wmRuntimeInfo.getHttpBaseUrl())
                .build());
  }

  // ========================================================================
  // searchAuditLogs Tests
  // ========================================================================

  @Nested
  @DisplayName("searchAuditLogs")
  class SearchAuditLogs {

    @Test
    @DisplayName("should search audit logs with all filters")
    void searchAuditLogsWithAllFilters() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "," + SAMPLE_AUDIT_ENTRY_2 + "]")));

      AuditSearchRequest request =
          AuditSearchRequest.builder()
              .userEmail("user@example.com")
              .clientId("client-1")
              .startTime(Instant.now().minus(7, ChronoUnit.DAYS))
              .endTime(Instant.now())
              .requestType("llm_chat")
              .limit(50)
              .offset(10)
              .build();

      AuditSearchResponse response = axonflow.searchAuditLogs(request);

      assertThat(response.getEntries()).hasSize(2);
      assertThat(response.getEntries().get(0).getId()).isEqualTo("audit-1");
      assertThat(response.getEntries().get(1).isBlocked()).isTrue();

      verify(
          postRequestedFor(urlEqualTo("/api/v1/audit/search"))
              .withRequestBody(containing("\"user_email\":\"user@example.com\""))
              .withRequestBody(containing("\"client_id\":\"client-1\""))
              .withRequestBody(containing("\"request_type\":\"llm_chat\""))
              .withRequestBody(containing("\"limit\":50"))
              .withRequestBody(containing("\"offset\":10")));
    }

    @Test
    @DisplayName("should use default limit when not specified")
    void searchAuditLogsWithDefaultLimit() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "]")));

      AuditSearchResponse response = axonflow.searchAuditLogs();

      assertThat(response.getLimit()).isEqualTo(100);

      verify(
          postRequestedFor(urlEqualTo("/api/v1/audit/search"))
              .withRequestBody(containing("\"limit\":100")));
    }

    @Test
    @DisplayName("should cap limit at 1000")
    void searchAuditLogsWithCapLimit() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[]")));

      AuditSearchRequest request = AuditSearchRequest.builder().limit(5000).build();

      axonflow.searchAuditLogs(request);

      verify(
          postRequestedFor(urlEqualTo("/api/v1/audit/search"))
              .withRequestBody(containing("\"limit\":1000")));
    }

    @Test
    @DisplayName("should handle empty results")
    void searchAuditLogsWithEmptyResults() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[]")));

      AuditSearchResponse response = axonflow.searchAuditLogs();

      assertThat(response.getEntries()).isEmpty();
      assertThat(response.getTotal()).isZero();
    }

    @Test
    @DisplayName("should handle wrapped response format")
    void searchAuditLogsWithWrappedResponse() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"entries\": ["
                              + SAMPLE_AUDIT_ENTRY_1
                              + "], \"total\": 100, \"limit\": 10, \"offset\": 0}")));

      AuditSearchResponse response = axonflow.searchAuditLogs();

      assertThat(response.getEntries()).hasSize(1);
      assertThat(response.getTotal()).isEqualTo(100);
      assertThat(response.getLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("should throw on 400 error")
    void searchAuditLogsWithBadRequest() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"invalid request\"}")));

      assertThatThrownBy(() -> axonflow.searchAuditLogs()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should throw on 401 error")
    void searchAuditLogsWithUnauthorized() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(401)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"unauthorized\"}")));

      assertThatThrownBy(() -> axonflow.searchAuditLogs()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should throw on 500 error")
    void searchAuditLogsWithServerError() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(500)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"server error\"}")));

      assertThatThrownBy(() -> axonflow.searchAuditLogs()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should parse dates correctly")
    void searchAuditLogsWithDateParsing() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "]")));

      AuditSearchResponse response = axonflow.searchAuditLogs();

      assertThat(response.getEntries().get(0).getTimestamp()).isNotNull();
      assertThat(response.getEntries().get(0).getTimestamp().toString()).startsWith("2026-01-05");
    }

    @Test
    @DisplayName("should include offset in request when > 0")
    void searchAuditLogsWithOffset() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "]")));

      AuditSearchRequest request = AuditSearchRequest.builder().offset(50).build();

      axonflow.searchAuditLogs(request);

      verify(
          postRequestedFor(urlEqualTo("/api/v1/audit/search"))
              .withRequestBody(containing("\"offset\":50")));
    }

    @Test
    @DisplayName("should parse policy violations correctly")
    void searchAuditLogsWithPolicyViolations() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_2 + "]")));

      AuditSearchResponse response = axonflow.searchAuditLogs();

      assertThat(response.getEntries().get(0).getPolicyViolations()).containsExactly("policy-1");
    }

    @Test
    @DisplayName("async should complete successfully")
    void searchAuditLogsAsync() throws Exception {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "]")));

      CompletableFuture<AuditSearchResponse> future = axonflow.searchAuditLogsAsync(null);
      AuditSearchResponse response = future.get();

      assertThat(response.getEntries()).hasSize(1);
    }
  }

  // ========================================================================
  // getAuditLogsByTenant Tests
  // ========================================================================

  @Nested
  @DisplayName("getAuditLogsByTenant")
  class GetAuditLogsByTenant {

    @Test
    @DisplayName("should get audit logs for tenant with defaults")
    void getAuditLogsByTenantWithDefaults() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/tenant-abc"))
              .withQueryParam("limit", equalTo("50"))
              .withQueryParam("offset", equalTo("0"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "," + SAMPLE_AUDIT_ENTRY_2 + "]")));

      AuditSearchResponse response = axonflow.getAuditLogsByTenant("tenant-abc");

      assertThat(response.getEntries()).hasSize(2);
      assertThat(response.getLimit()).isEqualTo(50);
    }

    @Test
    @DisplayName("should get audit logs with custom options")
    void getAuditLogsByTenantWithCustomOptions() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/tenant-abc"))
              .withQueryParam("limit", equalTo("100"))
              .withQueryParam("offset", equalTo("25"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "]")));

      AuditQueryOptions options = AuditQueryOptions.builder().limit(100).offset(25).build();

      AuditSearchResponse response = axonflow.getAuditLogsByTenant("tenant-abc", options);

      assertThat(response.getLimit()).isEqualTo(100);
      assertThat(response.getOffset()).isEqualTo(25);
    }

    @Test
    @DisplayName("should throw error for empty tenant ID")
    void getAuditLogsByTenantWithEmptyId() {
      assertThatThrownBy(() -> axonflow.getAuditLogsByTenant(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("tenantId is required");
    }

    @Test
    @DisplayName("should throw error for null tenant ID")
    void getAuditLogsByTenantWithNullId() {
      assertThatThrownBy(() -> axonflow.getAuditLogsByTenant(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("tenantId is required");
    }

    @Test
    @DisplayName("should cap limit at 1000")
    void getAuditLogsByTenantWithCapLimit() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/tenant-abc"))
              .withQueryParam("limit", equalTo("1000"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[]")));

      AuditQueryOptions options = AuditQueryOptions.builder().limit(5000).build();

      axonflow.getAuditLogsByTenant("tenant-abc", options);

      verify(
          getRequestedFor(urlPathEqualTo("/api/v1/audit/tenant/tenant-abc"))
              .withQueryParam("limit", equalTo("1000")));
    }

    @Test
    @DisplayName("should handle empty results")
    void getAuditLogsByTenantWithEmptyResults() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/tenant-abc"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[]")));

      AuditSearchResponse response = axonflow.getAuditLogsByTenant("tenant-abc");

      assertThat(response.getEntries()).isEmpty();
    }

    @Test
    @DisplayName("should handle wrapped response format")
    void getAuditLogsByTenantWithWrappedResponse() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/tenant-abc"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"entries\": ["
                              + SAMPLE_AUDIT_ENTRY_1
                              + "], \"total\": 50, \"limit\": 50, \"offset\": 0}")));

      AuditSearchResponse response = axonflow.getAuditLogsByTenant("tenant-abc");

      assertThat(response.getTotal()).isEqualTo(50);
    }

    @Test
    @DisplayName("should throw on 404 error")
    void getAuditLogsByTenantWithNotFound() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/nonexistent"))
              .willReturn(
                  aResponse()
                      .withStatus(404)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"tenant not found\"}")));

      assertThatThrownBy(() -> axonflow.getAuditLogsByTenant("nonexistent"))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should throw on 403 error")
    void getAuditLogsByTenantWithForbidden() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/other-tenant"))
              .willReturn(
                  aResponse()
                      .withStatus(403)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"forbidden\"}")));

      assertThatThrownBy(() -> axonflow.getAuditLogsByTenant("other-tenant"))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should URL encode tenant ID")
    void getAuditLogsByTenantWithUrlEncoding() {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/tenant%2Fwith%2Fslashes"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[]")));

      axonflow.getAuditLogsByTenant("tenant/with/slashes");

      verify(getRequestedFor(urlPathEqualTo("/api/v1/audit/tenant/tenant%2Fwith%2Fslashes")));
    }

    @Test
    @DisplayName("async should complete successfully")
    void getAuditLogsByTenantAsync() throws Exception {
      stubFor(
          get(urlPathEqualTo("/api/v1/audit/tenant/tenant-abc"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "]")));

      CompletableFuture<AuditSearchResponse> future =
          axonflow.getAuditLogsByTenantAsync("tenant-abc", null);
      AuditSearchResponse response = future.get();

      assertThat(response.getEntries()).hasSize(1);
    }
  }

  // ========================================================================
  // Type Validation Tests
  // ========================================================================

  @Nested
  @DisplayName("Type Validation")
  class TypeValidation {

    @Test
    @DisplayName("should parse all AuditLogEntry fields correctly")
    void parseAllAuditLogEntryFields() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[" + SAMPLE_AUDIT_ENTRY_1 + "]")));

      AuditSearchResponse response = axonflow.searchAuditLogs();
      AuditLogEntry entry = response.getEntries().get(0);

      assertThat(entry.getId()).isEqualTo("audit-1");
      assertThat(entry.getRequestId()).isEqualTo("req-1");
      assertThat(entry.getUserEmail()).isEqualTo("user@example.com");
      assertThat(entry.getClientId()).isEqualTo("client-1");
      assertThat(entry.getTenantId()).isEqualTo("tenant-1");
      assertThat(entry.getRequestType()).isEqualTo("llm_chat");
      assertThat(entry.getQuerySummary()).isEqualTo("Test query");
      assertThat(entry.isSuccess()).isTrue();
      assertThat(entry.isBlocked()).isFalse();
      assertThat(entry.getRiskScore()).isEqualTo(0.1);
      assertThat(entry.getProvider()).isEqualTo("openai");
      assertThat(entry.getModel()).isEqualTo("gpt-4");
      assertThat(entry.getTokensUsed()).isEqualTo(150);
      assertThat(entry.getLatencyMs()).isEqualTo(250);
      assertThat(entry.getPolicyViolations()).isEmpty();
      assertThat(entry.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("should handle missing optional fields with defaults")
    void handleMissingOptionalFields() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "[{\"id\": \"audit-minimal\", \"timestamp\": \"2026-01-05T10:00:00Z\"}]")));

      AuditSearchResponse response = axonflow.searchAuditLogs();
      AuditLogEntry entry = response.getEntries().get(0);

      assertThat(entry.getId()).isEqualTo("audit-minimal");
      assertThat(entry.getRequestId()).isEmpty();
      assertThat(entry.getUserEmail()).isEmpty();
      assertThat(entry.isSuccess()).isTrue();
      assertThat(entry.isBlocked()).isFalse();
      assertThat(entry.getRiskScore()).isZero();
      assertThat(entry.getTokensUsed()).isZero();
      assertThat(entry.getPolicyViolations()).isEmpty();
      assertThat(entry.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("AuditSearchResponse hasMore should work correctly")
    void auditSearchResponseHasMore() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"entries\": ["
                              + SAMPLE_AUDIT_ENTRY_1
                              + "], \"total\": 100, \"limit\": 10, \"offset\": 0}")));

      AuditSearchResponse response = axonflow.searchAuditLogs();

      assertThat(response.hasMore()).isTrue();
    }

    @Test
    @DisplayName("AuditSearchResponse hasMore should return false when no more results")
    void auditSearchResponseHasMoreFalse() {
      stubFor(
          post(urlEqualTo("/api/v1/audit/search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"entries\": ["
                              + SAMPLE_AUDIT_ENTRY_1
                              + "], \"total\": 1, \"limit\": 10, \"offset\": 0}")));

      AuditSearchResponse response = axonflow.searchAuditLogs();

      assertThat(response.hasMore()).isFalse();
    }

    @Test
    @DisplayName("AuditQueryOptions defaults should be correct")
    void auditQueryOptionsDefaults() {
      AuditQueryOptions options = AuditQueryOptions.defaults();

      assertThat(options.getLimit()).isEqualTo(50);
      assertThat(options.getOffset()).isZero();
    }

    @Test
    @DisplayName("AuditSearchRequest builder should set all fields")
    void auditSearchRequestBuilder() {
      Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
      Instant end = Instant.now();

      AuditSearchRequest request =
          AuditSearchRequest.builder()
              .userEmail("test@example.com")
              .clientId("client-123")
              .startTime(start)
              .endTime(end)
              .requestType("llm_chat")
              .limit(50)
              .offset(10)
              .build();

      assertThat(request.getUserEmail()).isEqualTo("test@example.com");
      assertThat(request.getClientId()).isEqualTo("client-123");
      assertThat(request.getStartTime()).isEqualTo(start.toString());
      assertThat(request.getEndTime()).isEqualTo(end.toString());
      assertThat(request.getRequestType()).isEqualTo("llm_chat");
      assertThat(request.getLimit()).isEqualTo(50);
      assertThat(request.getOffset()).isEqualTo(10);
    }
  }
}
