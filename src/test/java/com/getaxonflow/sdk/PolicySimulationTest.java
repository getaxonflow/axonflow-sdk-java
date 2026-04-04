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
import com.getaxonflow.sdk.simulation.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for policy simulation methods. */
@WireMockTest
@DisplayName("Policy Simulation")
class PolicySimulationTest {

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

  // ========================================================================
  // simulatePolicies
  // ========================================================================

  @Test
  @DisplayName("should simulate policies and return blocked result")
  void shouldSimulatePoliciesBlocked() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/simulate"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"allowed\":false,\"applied_policies\":[\"block-pii\",\"block-financial\"],\"risk_score\":0.85,\"required_actions\":[\"redact_pii\"],\"processing_time_ms\":12,\"total_policies\":5,\"dry_run\":true,\"simulated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\",\"daily_usage\":{\"used\":3,\"limit\":100}}}")));

    SimulatePoliciesResponse result =
        axonflow.simulatePolicies(
            SimulatePoliciesRequest.builder()
                .query("My SSN is 123-45-6789")
                .requestType("query")
                .build());

    assertThat(result).isNotNull();
    assertThat(result.isAllowed()).isFalse();
    assertThat(result.getAppliedPolicies()).containsExactly("block-pii", "block-financial");
    assertThat(result.getRiskScore()).isEqualTo(0.85);
    assertThat(result.getRequiredActions()).containsExactly("redact_pii");
    assertThat(result.getProcessingTimeMs()).isEqualTo(12);
    assertThat(result.getTotalPolicies()).isEqualTo(5);
    assertThat(result.isDryRun()).isTrue();
    assertThat(result.getSimulatedAt()).isEqualTo("2026-03-24T10:00:00Z");
    assertThat(result.getTier()).isEqualTo("evaluation");
    assertThat(result.getDailyUsage()).isNotNull();
    assertThat(result.getDailyUsage().getUsed()).isEqualTo(3);
    assertThat(result.getDailyUsage().getLimit()).isEqualTo(100);

    verify(
        postRequestedFor(urlEqualTo("/api/v1/policies/simulate"))
            .withRequestBody(matchingJsonPath("$.query", equalTo("My SSN is 123-45-6789")))
            .withRequestBody(matchingJsonPath("$.request_type", equalTo("query"))));
  }

  @Test
  @DisplayName("should simulate policies and return allowed result")
  void shouldSimulatePoliciesAllowed() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/simulate"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"allowed\":true,\"applied_policies\":[],\"risk_score\":0.1,\"required_actions\":[],\"processing_time_ms\":5,\"total_policies\":5,\"dry_run\":true,\"simulated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\",\"daily_usage\":{\"used\":1,\"limit\":100}}}")));

    SimulatePoliciesResponse result =
        axonflow.simulatePolicies(
            SimulatePoliciesRequest.builder().query("What is the weather?").build());

    assertThat(result.isAllowed()).isTrue();
    assertThat(result.getAppliedPolicies()).isEmpty();
    assertThat(result.getRiskScore()).isEqualTo(0.1);
  }

  @Test
  @DisplayName("should simulate policies with user and context")
  void shouldSimulatePoliciesWithContext() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/simulate"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"allowed\":false,\"applied_policies\":[\"geo-block\"],\"risk_score\":0.9,\"required_actions\":[],\"processing_time_ms\":8,\"total_policies\":3,\"dry_run\":true,\"simulated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"enterprise\"}}")));

    SimulatePoliciesResponse result =
        axonflow.simulatePolicies(
            SimulatePoliciesRequest.builder()
                .query("Execute trade")
                .requestType("execute")
                .user(Map.of("role", "analyst"))
                .context(Map.of("region", "restricted"))
                .build());

    assertThat(result).isNotNull();
    assertThat(result.isAllowed()).isFalse();
    assertThat(result.getAppliedPolicies()).containsExactly("geo-block");

    verify(
        postRequestedFor(urlEqualTo("/api/v1/policies/simulate"))
            .withRequestBody(matchingJsonPath("$.user.role", equalTo("analyst")))
            .withRequestBody(matchingJsonPath("$.context.region", equalTo("restricted"))));
  }

  @Test
  @DisplayName("should reject null request for simulatePolicies")
  void shouldRejectNullRequestForSimulate() {
    assertThatThrownBy(() -> axonflow.simulatePolicies(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("request cannot be null");
  }

  @Test
  @DisplayName("should reject null query in SimulatePoliciesRequest builder")
  void shouldRejectNullQueryInBuilder() {
    assertThatThrownBy(() -> SimulatePoliciesRequest.builder().build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("query cannot be null");
  }

  @Test
  @DisplayName("should reject empty query in SimulatePoliciesRequest builder")
  void shouldRejectEmptyQueryInBuilder() {
    assertThatThrownBy(() -> SimulatePoliciesRequest.builder().query("").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("query cannot be empty");
  }

  @Test
  @DisplayName("simulatePoliciesAsync should return future")
  void simulatePoliciesAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/policies/simulate"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"allowed\":true,\"applied_policies\":[],\"risk_score\":0.0,\"required_actions\":[],\"processing_time_ms\":3,\"total_policies\":2,\"dry_run\":true,\"simulated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    CompletableFuture<SimulatePoliciesResponse> future =
        axonflow.simulatePoliciesAsync(SimulatePoliciesRequest.builder().query("Hello").build());
    SimulatePoliciesResponse result = future.get();

    assertThat(result).isNotNull();
    assertThat(result.isAllowed()).isTrue();
  }

  @Test
  @DisplayName("should handle server error on simulatePolicies")
  void shouldHandleServerErrorOnSimulate() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/simulate"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"internal server error\"}")));

    assertThatThrownBy(
            () ->
                axonflow.simulatePolicies(SimulatePoliciesRequest.builder().query("test").build()))
        .isInstanceOf(AxonFlowException.class);
  }

  @Test
  @DisplayName("should handle unwrapped response for simulatePolicies")
  void shouldHandleUnwrappedResponseForSimulate() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/simulate"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"allowed\":true,\"applied_policies\":[],\"risk_score\":0.0,\"required_actions\":[],\"processing_time_ms\":2,\"total_policies\":1,\"dry_run\":true,\"simulated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}")));

    SimulatePoliciesResponse result =
        axonflow.simulatePolicies(SimulatePoliciesRequest.builder().query("test").build());

    assertThat(result).isNotNull();
    assertThat(result.isAllowed()).isTrue();
  }

  // ========================================================================
  // getPolicyImpactReport
  // ========================================================================

  @Test
  @DisplayName("should get policy impact report")
  void shouldGetPolicyImpactReport() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/impact-report"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"policy_id\":\"policy_block_pii\",\"policy_name\":\"block-pii\",\"total_inputs\":3,\"matched\":2,\"blocked\":2,\"match_rate\":0.667,\"block_rate\":0.667,\"results\":[{\"input_index\":0,\"matched\":true,\"blocked\":true,\"actions\":[\"block\"]},{\"input_index\":1,\"matched\":false,\"blocked\":false,\"actions\":[\"allow\"]},{\"input_index\":2,\"matched\":true,\"blocked\":true,\"actions\":[\"block\"]}],\"processing_time_ms\":25,\"generated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    ImpactReportResponse report =
        axonflow.getPolicyImpactReport(
            ImpactReportRequest.builder()
                .policyId("policy_block_pii")
                .inputs(
                    List.of(
                        ImpactReportInput.builder().query("My SSN is 123-45-6789").build(),
                        ImpactReportInput.builder().query("What is the weather?").build(),
                        ImpactReportInput.builder().query("My email is test@example.com").build()))
                .build());

    assertThat(report).isNotNull();
    assertThat(report.getPolicyId()).isEqualTo("policy_block_pii");
    assertThat(report.getTotalInputs()).isEqualTo(3);
    assertThat(report.getMatched()).isEqualTo(2);
    assertThat(report.getBlocked()).isEqualTo(2);
    assertThat(report.getMatchRate()).isEqualTo(0.667);
    assertThat(report.getBlockRate()).isEqualTo(0.667);
    assertThat(report.getPolicyName()).isEqualTo("block-pii");
    assertThat(report.getResults()).hasSize(3);
    assertThat(report.getResults().get(0).getInputIndex()).isEqualTo(0);
    assertThat(report.getResults().get(0).isMatched()).isTrue();
    assertThat(report.getResults().get(0).isBlocked()).isTrue();
    assertThat(report.getResults().get(0).getActions()).containsExactly("block");
    assertThat(report.getResults().get(1).getInputIndex()).isEqualTo(1);
    assertThat(report.getResults().get(1).isMatched()).isFalse();
    assertThat(report.getResults().get(1).getActions()).containsExactly("allow");
    assertThat(report.getProcessingTimeMs()).isEqualTo(25);
    assertThat(report.getGeneratedAt()).isEqualTo("2026-03-24T10:00:00Z");
    assertThat(report.getTier()).isEqualTo("evaluation");

    verify(
        postRequestedFor(urlEqualTo("/api/v1/policies/impact-report"))
            .withRequestBody(matchingJsonPath("$.policy_id", equalTo("policy_block_pii"))));
  }

  @Test
  @DisplayName("should get impact report with no matches")
  void shouldGetImpactReportNoMatches() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/impact-report"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"policy_id\":\"policy_strict\",\"total_inputs\":1,\"matched\":0,\"blocked\":0,\"match_rate\":0.0,\"block_rate\":0.0,\"results\":[{\"input_index\":0,\"matched\":false,\"blocked\":false,\"actions\":[\"allow\"]}],\"processing_time_ms\":3,\"generated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"enterprise\"}}")));

    ImpactReportResponse report =
        axonflow.getPolicyImpactReport(
            ImpactReportRequest.builder()
                .policyId("policy_strict")
                .inputs(List.of(ImpactReportInput.builder().query("Hello world").build()))
                .build());

    assertThat(report.getMatched()).isEqualTo(0);
    assertThat(report.getMatchRate()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("should reject null request for getPolicyImpactReport")
  void shouldRejectNullRequestForImpactReport() {
    assertThatThrownBy(() -> axonflow.getPolicyImpactReport(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("request cannot be null");
  }

  @Test
  @DisplayName("should reject null policyId in ImpactReportRequest builder")
  void shouldRejectNullPolicyIdInImpactReportBuilder() {
    assertThatThrownBy(
            () ->
                ImpactReportRequest.builder()
                    .inputs(List.of(ImpactReportInput.builder().query("test").build()))
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("policyId cannot be null");
  }

  @Test
  @DisplayName("should reject empty policyId in ImpactReportRequest builder")
  void shouldRejectEmptyPolicyIdInImpactReportBuilder() {
    assertThatThrownBy(
            () ->
                ImpactReportRequest.builder()
                    .policyId("")
                    .inputs(List.of(ImpactReportInput.builder().query("test").build()))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyId cannot be empty");
  }

  @Test
  @DisplayName("should reject empty inputs in ImpactReportRequest builder")
  void shouldRejectEmptyInputsInImpactReportBuilder() {
    assertThatThrownBy(
            () -> ImpactReportRequest.builder().policyId("policy_1").inputs(List.of()).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inputs cannot be empty");
  }

  @Test
  @DisplayName("should reject null inputs in ImpactReportRequest builder")
  void shouldRejectNullInputsInImpactReportBuilder() {
    assertThatThrownBy(() -> ImpactReportRequest.builder().policyId("policy_1").build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("inputs cannot be null");
  }

  @Test
  @DisplayName("getPolicyImpactReportAsync should return future")
  void getPolicyImpactReportAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/policies/impact-report"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"policy_id\":\"p1\",\"total_inputs\":1,\"matched\":0,\"blocked\":0,\"match_rate\":0.0,\"block_rate\":0.0,\"results\":[],\"processing_time_ms\":2,\"generated_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    CompletableFuture<ImpactReportResponse> future =
        axonflow.getPolicyImpactReportAsync(
            ImpactReportRequest.builder()
                .policyId("p1")
                .inputs(List.of(ImpactReportInput.builder().query("test").build()))
                .build());
    ImpactReportResponse report = future.get();

    assertThat(report).isNotNull();
    assertThat(report.getPolicyId()).isEqualTo("p1");
  }

  @Test
  @DisplayName("should handle server error on getPolicyImpactReport")
  void shouldHandleServerErrorOnImpactReport() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/impact-report"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"internal server error\"}")));

    assertThatThrownBy(
            () ->
                axonflow.getPolicyImpactReport(
                    ImpactReportRequest.builder()
                        .policyId("p1")
                        .inputs(List.of(ImpactReportInput.builder().query("test").build()))
                        .build()))
        .isInstanceOf(AxonFlowException.class);
  }

  // ========================================================================
  // detectPolicyConflicts
  // ========================================================================

  @Test
  @DisplayName("should detect policy conflicts")
  void shouldDetectPolicyConflicts() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(containing("\"policy_id\":\"policy_block_pii\""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"conflicts\":[{\"policy_a\":{\"id\":\"policy_block_pii\",\"name\":\"block-pii\",\"type\":\"deny\"},\"policy_b\":{\"id\":\"policy_allow_internal\",\"name\":\"allow-internal\",\"type\":\"allow\"},\"conflict_type\":\"action_conflict\",\"description\":\"Policy 'block-pii' blocks requests that 'allow-internal' would allow\",\"severity\":\"high\",\"overlapping_field\":\"input.content\"}],\"total_policies\":8,\"conflict_count\":1,\"checked_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    PolicyConflictResponse result = axonflow.detectPolicyConflicts("policy_block_pii");

    assertThat(result).isNotNull();
    assertThat(result.getConflictCount()).isEqualTo(1);
    assertThat(result.getTotalPolicies()).isEqualTo(8);
    assertThat(result.getCheckedAt()).isEqualTo("2026-03-24T10:00:00Z");
    assertThat(result.getTier()).isEqualTo("evaluation");
    assertThat(result.getConflicts()).hasSize(1);

    PolicyConflict conflict = result.getConflicts().get(0);
    assertThat(conflict.getConflictType()).isEqualTo("action_conflict");
    assertThat(conflict.getSeverity()).isEqualTo("high");
    assertThat(conflict.getDescription()).contains("block-pii");
    assertThat(conflict.getOverlappingField()).isEqualTo("input.content");
    assertThat(conflict.getPolicyA()).isNotNull();
    assertThat(conflict.getPolicyA().getId()).isEqualTo("policy_block_pii");
    assertThat(conflict.getPolicyA().getName()).isEqualTo("block-pii");
    assertThat(conflict.getPolicyA().getType()).isEqualTo("deny");
    assertThat(conflict.getPolicyB()).isNotNull();
    assertThat(conflict.getPolicyB().getId()).isEqualTo("policy_allow_internal");
    assertThat(conflict.getPolicyB().getName()).isEqualTo("allow-internal");
    assertThat(conflict.getPolicyB().getType()).isEqualTo("allow");

    verify(
        postRequestedFor(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(containing("\"policy_id\":\"policy_block_pii\"")));
  }

  @Test
  @DisplayName("should detect no conflicts")
  void shouldDetectNoConflicts() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(containing("\"policy_id\":\"policy_safe\""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"conflicts\":[],\"total_policies\":5,\"conflict_count\":0,\"checked_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"enterprise\"}}")));

    PolicyConflictResponse result = axonflow.detectPolicyConflicts("policy_safe");

    assertThat(result).isNotNull();
    assertThat(result.getConflictCount()).isEqualTo(0);
    assertThat(result.getConflicts()).isEmpty();
    assertThat(result.getTotalPolicies()).isEqualTo(5);
  }

  @Test
  @DisplayName("should scan all policies when policyId is null")
  void shouldScanAllPoliciesWhenNull() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"conflicts\":[],\"total_policies\":5,\"conflict_count\":0,\"checked_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    PolicyConflictResponse result = axonflow.detectPolicyConflicts(null);

    assertThat(result).isNotNull();
    assertThat(result.getConflictCount()).isEqualTo(0);
    assertThat(result.getConflicts()).isEmpty();

    verify(
        postRequestedFor(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(equalToJson("{}")));
  }

  @Test
  @DisplayName("should scan all policies with no-arg overload")
  void shouldScanAllPoliciesNoArg() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"conflicts\":[],\"total_policies\":3,\"conflict_count\":0,\"checked_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    PolicyConflictResponse result = axonflow.detectPolicyConflicts();

    assertThat(result).isNotNull();
    assertThat(result.getConflictCount()).isEqualTo(0);

    verify(
        postRequestedFor(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(equalToJson("{}")));
  }

  @Test
  @DisplayName("should reject empty policyId for detectPolicyConflicts")
  void shouldRejectEmptyPolicyIdForConflicts() {
    assertThatThrownBy(() -> axonflow.detectPolicyConflicts(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyId cannot be empty");
  }

  @Test
  @DisplayName("detectPolicyConflictsAsync should return future")
  void detectPolicyConflictsAsyncShouldReturnFuture() throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(containing("\"policy_id\":\"async_policy\""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"conflicts\":[],\"total_policies\":3,\"conflict_count\":0,\"checked_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    CompletableFuture<PolicyConflictResponse> future =
        axonflow.detectPolicyConflictsAsync("async_policy");
    PolicyConflictResponse result = future.get();

    assertThat(result).isNotNull();
    assertThat(result.getConflictCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("should handle server error on detectPolicyConflicts")
  void shouldHandleServerErrorOnConflicts() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(containing("\"policy_id\":\"bad_policy\""))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"internal server error\"}")));

    assertThatThrownBy(() -> axonflow.detectPolicyConflicts("bad_policy"))
        .isInstanceOf(AxonFlowException.class);
  }

  @Test
  @DisplayName("should handle unwrapped response for detectPolicyConflicts")
  void shouldHandleUnwrappedResponseForConflicts() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(containing("\"policy_id\":\"unwrapped\""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"conflicts\":[],\"total_policies\":2,\"conflict_count\":0,\"checked_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}")));

    PolicyConflictResponse result = axonflow.detectPolicyConflicts("unwrapped");

    assertThat(result).isNotNull();
    assertThat(result.getConflictCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("should send policyId with special characters in request body")
  void shouldSendPolicyIdWithSpecialCharactersInBody() {
    stubFor(
        post(urlEqualTo("/api/v1/policies/conflicts"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"data\":{\"conflicts\":[],\"total_policies\":1,\"conflict_count\":0,\"checked_at\":\"2026-03-24T10:00:00Z\",\"tier\":\"evaluation\"}}")));

    PolicyConflictResponse result = axonflow.detectPolicyConflicts("policy with spaces");

    assertThat(result).isNotNull();

    verify(
        postRequestedFor(urlEqualTo("/api/v1/policies/conflicts"))
            .withRequestBody(containing("\"policy_id\":\"policy with spaces\"")));
  }
}
