/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for AxonFlow.explainDecision + audit search filter parity (ADR-043 / ADR-042). */
@WireMockTest
@DisplayName("Decision Explainability (ADR-043)")
class DecisionExplainTest {

  private AxonFlow axonflow;

  private static final String EXPLAIN_BODY =
      "{"
          + "\"decision_id\": \"dec_wf1_step2\","
          + "\"timestamp\": \"2026-04-17T12:00:00Z\","
          + "\"decision\": \"blocked\","
          + "\"reason\": \"SQL injection detected\","
          + "\"risk_level\": \"high\","
          + "\"policy_matches\": [{"
          + "  \"policy_id\": \"pol-sqli\","
          + "  \"policy_name\": \"SQL Injection Detector\","
          + "  \"action\": \"deny\","
          + "  \"risk_level\": \"high\","
          + "  \"allow_override\": true,"
          + "  \"policy_description\": \"Blocks SQL injection\""
          + "}],"
          + "\"matched_rules\": [{"
          + "  \"policy_id\": \"pol-sqli\","
          + "  \"rule_id\": \"r-1\","
          + "  \"rule_text\": \"UNION SELECT\","
          + "  \"matched_on\": \"query.sql\""
          + "}],"
          + "\"override_available\": true,"
          + "\"override_existing_id\": \"ov-abc\","
          + "\"historical_hit_count_session\": 3,"
          + "\"policy_source_link\": \"https://policies.axonflow/sqli\","
          + "\"tool_signature\": \"Bash\","
          + "\"future_field_unknown\": \"ignored\""  // forward-compat check
          + "}";

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder().endpoint(wmRuntimeInfo.getHttpBaseUrl()).build());
  }

  @Test
  @DisplayName("rejects empty decision ID")
  void rejectsEmptyDecisionId() {
    assertThatThrownBy(() -> axonflow.explainDecision(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
  }

  @Test
  @DisplayName("rejects null decision ID")
  void rejectsNullDecisionId() {
    assertThatThrownBy(() -> axonflow.explainDecision(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("parses full payload and calls correct endpoint")
  void parsesFullPayload() {
    stubFor(
        get(urlEqualTo("/api/v1/decisions/dec_wf1_step2/explain"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(EXPLAIN_BODY)));

    DecisionExplanation exp = axonflow.explainDecision("dec_wf1_step2");

    assertThat(exp.getDecisionId()).isEqualTo("dec_wf1_step2");
    assertThat(exp.getDecision()).isEqualTo("blocked");
    assertThat(exp.getReason()).isEqualTo("SQL injection detected");
    assertThat(exp.getRiskLevel()).isEqualTo("high");
    assertThat(exp.getPolicyMatches()).hasSize(1);
    assertThat(exp.getPolicyMatches().get(0).getPolicyId()).isEqualTo("pol-sqli");
    assertThat(exp.getPolicyMatches().get(0).isAllowOverride()).isTrue();
    assertThat(exp.getMatchedRules()).hasSize(1);
    assertThat(exp.getMatchedRules().get(0).getRuleText()).isEqualTo("UNION SELECT");
    assertThat(exp.isOverrideAvailable()).isTrue();
    assertThat(exp.getOverrideExistingId()).isEqualTo("ov-abc");
    assertThat(exp.getHistoricalHitCountSession()).isEqualTo(3);
    assertThat(exp.getToolSignature()).isEqualTo("Bash");
  }

  @Test
  @DisplayName("v8.4.0 — surfaces the full request context + contextTruncated")
  void surfacesRequestContext() {
    String body =
        "{"
            + "\"decision_id\": \"dec-ctx\","
            + "\"timestamp\": \"2026-05-30T12:00:00Z\","
            + "\"decision\": \"blocked\","
            + "\"reason\": \"\","
            + "\"policy_matches\": [],"
            + "\"override_available\": false,"
            + "\"historical_hit_count_session\": 0,"
            + "\"context\": {\"x_ai_agent\":\"refund-bot\",\"x_session_id\":\"sess-42\"},"
            + "\"context_truncated\": true"
            + "}";
    stubFor(
        get(urlEqualTo("/api/v1/decisions/dec-ctx/explain"))
            .willReturn(aResponse().withStatus(200).withBody(body)));

    DecisionExplanation exp = axonflow.explainDecision("dec-ctx");
    assertThat(exp.getContext())
        .containsEntry("x_ai_agent", "refund-bot")
        .containsEntry("x_session_id", "sess-42")
        .hasSize(2);
    assertThat(exp.isContextTruncated()).isTrue();
  }

  @Test
  @DisplayName("v8.4.0 — context is null + contextTruncated false for pre-v8.4.0 rows")
  void contextAbsentDefaults() {
    String body =
        "{\"decision_id\":\"dec-1\",\"timestamp\":\"2026-04-17T12:00:00Z\","
            + "\"decision\":\"allowed\",\"reason\":\"\",\"policy_matches\":[],"
            + "\"override_available\":false,\"historical_hit_count_session\":0}";
    stubFor(
        get(urlEqualTo("/api/v1/decisions/dec-1/explain"))
            .willReturn(aResponse().withStatus(200).withBody(body)));

    DecisionExplanation exp = axonflow.explainDecision("dec-1");
    assertThat(exp.getContext()).isNull();
    assertThat(exp.isContextTruncated()).isFalse();
  }

  @Test
  @DisplayName("ignores unknown fields for forward compatibility (ADR-043)")
  void forwardCompat() {
    // EXPLAIN_BODY contains future_field_unknown; parsing must succeed regardless.
    stubFor(
        get(urlEqualTo("/api/v1/decisions/dec_wf1_step2/explain"))
            .willReturn(aResponse().withStatus(200).withBody(EXPLAIN_BODY)));

    DecisionExplanation exp = axonflow.explainDecision("dec_wf1_step2");
    assertThat(exp.getDecisionId()).isEqualTo("dec_wf1_step2");
  }

  @Test
  @DisplayName("searchAuditLogs sends decision_id, policy_name, override_id when set")
  void auditSearchNewFilters() {
    stubFor(
        post(urlEqualTo("/api/v1/audit/search"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"entries\":[],\"total\":0,\"limit\":100,\"offset\":0}")));

    axonflow.searchAuditLogs(
        AuditSearchRequest.builder()
            .decisionId("dec-abc")
            .policyName("SQL Injection Detector")
            .overrideId("ov-xyz")
            .build());

    verify(
        postRequestedFor(urlEqualTo("/api/v1/audit/search"))
            .withRequestBody(containing("\"decision_id\":\"dec-abc\""))
            .withRequestBody(containing("\"policy_name\":\"SQL Injection Detector\""))
            .withRequestBody(containing("\"override_id\":\"ov-xyz\"")));
  }

  @Test
  @DisplayName("searchAuditLogs omits new filter fields when unset")
  void auditSearchFiltersAbsent() {
    stubFor(
        post(urlEqualTo("/api/v1/audit/search"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"entries\":[],\"total\":0,\"limit\":100,\"offset\":0}")));

    axonflow.searchAuditLogs(AuditSearchRequest.builder().build());

    verify(
        postRequestedFor(urlEqualTo("/api/v1/audit/search"))
            .withRequestBody(notMatching(".*decision_id.*"))
            .withRequestBody(notMatching(".*policy_name.*"))
            .withRequestBody(notMatching(".*override_id.*")));
  }

  @Test
  @DisplayName("path-encodes decision ID — space becomes %20 not + (path vs form encoding)")
  void pathEncodesDecisionIdSpaceAsPercent20() {
    // Decision IDs should never realistically contain spaces, but Go/Python/
    // TypeScript all use path-segment escaping — Java must match so the
    // request hits the same path in the unlikely event it does. Before the
    // fix this was URLEncoder.encode() alone, which produced '+' and would
    // have routed to a different audit_logs key.
    stubFor(
        get(urlEqualTo("/api/v1/decisions/dec%20with%20space/explain"))
            .willReturn(aResponse().withStatus(200).withBody(EXPLAIN_BODY)));

    axonflow.explainDecision("dec with space");

    verify(getRequestedFor(urlEqualTo("/api/v1/decisions/dec%20with%20space/explain")));
  }

  @Test
  @DisplayName("path-encodes decision ID — '+' in id is preserved as %2B")
  void pathEncodesPlusAsPercent2B() {
    // A literal '+' in the id must NOT be parsed as a space on the server.
    stubFor(
        get(urlEqualTo("/api/v1/decisions/dec%2Bplus/explain"))
            .willReturn(aResponse().withStatus(200).withBody(EXPLAIN_BODY)));

    axonflow.explainDecision("dec+plus");

    verify(getRequestedFor(urlEqualTo("/api/v1/decisions/dec%2Bplus/explain")));
  }

  @Test
  @DisplayName("DecisionExplanation getters return null-safe values")
  void decisionExplanationGetters() {
    DecisionExplanation exp =
        new DecisionExplanation(
            "d-1",
            java.time.Instant.now(),
            null, // policyMatches null should default to empty
            null,
            "allowed",
            "",
            null,
            false,
            null,
            0,
            null,
            null,
            null, // context
            false); // contextTruncated
    assertThat(exp.getPolicyMatches()).isEmpty();
    assertThat(exp.getMatchedRules()).isNull();
    assertThat(exp.getContext()).isNull();
    assertThat(exp.isContextTruncated()).isFalse();
  }

  @Test
  @DisplayName("ExplainPolicy defaults correctly")
  void explainPolicyDefaults() {
    ExplainPolicy p = new ExplainPolicy("p-1", null, null, null, false, null);
    assertThat(p.getPolicyId()).isEqualTo("p-1");
    assertThat(p.isAllowOverride()).isFalse();
    assertThat(p.getPolicyName()).isNull();
  }
}
