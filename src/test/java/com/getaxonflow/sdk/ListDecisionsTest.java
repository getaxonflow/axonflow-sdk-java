// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.RateLimitException;
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for AxonFlow.listDecisions (Session γ / #1982). */
@WireMockTest
@DisplayName("Decision List (Session γ #1982)")
class ListDecisionsTest {

  private AxonFlow axonflow;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    axonflow =
        AxonFlow.create(AxonFlowConfig.builder().endpoint(wmRuntimeInfo.getHttpBaseUrl()).build());
  }

  @Test
  @DisplayName("happy path — parses 3-row payload")
  void happyPath() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"decisions\":["
                            + "{\"decision_id\":\"dec-1\",\"timestamp\":\"2026-05-07T12:00:00Z\","
                            + "\"decision\":\"blocked\",\"policy_id\":\"pol-sqli\","
                            + "\"tool_signature\":\"postgres.query\"},"
                            + "{\"decision_id\":\"dec-2\",\"timestamp\":\"2026-05-07T11:00:00Z\","
                            + "\"decision\":\"allowed\",\"policy_id\":\"pol-default\","
                            + "\"tool_signature\":\"github.status\"},"
                            + "{\"decision_id\":\"dec-3\",\"timestamp\":\"2026-05-07T10:00:00Z\","
                            + "\"decision\":\"needs_approval\",\"policy_id\":\"pol-amount\","
                            + "\"tool_signature\":\"stripe.charge\"}"
                            + "]}")));

    List<DecisionSummary> got = axonflow.listDecisions(null);
    assertThat(got).hasSize(3);
    assertThat(got.get(0).getDecisionId()).isEqualTo("dec-1");
    assertThat(got.get(0).getDecision()).isEqualTo("blocked");
    assertThat(got.get(0).getPolicyId()).isEqualTo("pol-sqli");
    assertThat(got.get(0).getToolSignature()).isEqualTo("postgres.query");
    assertThat(got.get(2).getDecision()).isEqualTo("needs_approval");
  }

  @Test
  @DisplayName("v8.4.0 — surfaces the PEP-forwarded request context on the summary")
  void surfacesRequestContext() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"decisions\":["
                            + "{\"decision_id\":\"dec-ctx\",\"timestamp\":\"2026-05-30T12:00:00Z\","
                            + "\"decision\":\"blocked\",\"context\":{"
                            + "\"x_ai_agent\":\"refund-bot\",\"x_session_id\":\"sess-42\","
                            + "\"x_leader_identity\":\"ops-lead\"}},"
                            + "{\"decision_id\":\"dec-noctx\",\"timestamp\":\"2026-05-30T11:00:00Z\","
                            + "\"decision\":\"allowed\"}"
                            + "]}")));

    List<DecisionSummary> got = axonflow.listDecisions(null);
    assertThat(got).hasSize(2);
    assertThat(got.get(0).getContext())
        .containsEntry("x_ai_agent", "refund-bot")
        .containsEntry("x_session_id", "sess-42")
        .containsEntry("x_leader_identity", "ops-lead")
        .hasSize(3);
    // A decision with no context keeps a null map (pre-v8.4.0 byte-shape).
    assertThat(got.get(1).getContext()).isNull();
  }

  @Test
  @DisplayName("filter serialization — every option lands in the URL")
  void filterSerialization() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .withQueryParam("since", equalTo("2026-05-07T00:00:00Z"))
            .withQueryParam("decision", equalTo("blocked"))
            .withQueryParam("policy_id", equalTo("pol-sqli"))
            .withQueryParam("tool_signature", equalTo("postgres.query"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(aResponse().withStatus(200).withBody("{\"decisions\":[]}")));

    ListDecisionsOptions opts =
        ListDecisionsOptions.builder()
            .since(Instant.parse("2026-05-07T00:00:00Z"))
            .decision("blocked")
            .policyId("pol-sqli")
            .toolSignature("postgres.query")
            .limit(25)
            .build();
    List<DecisionSummary> got = axonflow.listDecisions(opts);
    assertThat(got).isEmpty();
  }

  @Test
  @DisplayName("zero-valued filters are omitted from the URL")
  void omitsUnsetFilters() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .withQueryParam("decision", equalTo("blocked"))
            // wiremock fails the test if the URL contains any of these:
            .withQueryParam("policy_id", absent())
            .withQueryParam("tool_signature", absent())
            .withQueryParam("limit", absent())
            .withQueryParam("since", absent())
            .willReturn(aResponse().withStatus(200).withBody("{\"decisions\":[]}")));

    axonflow.listDecisions(ListDecisionsOptions.builder().decision("blocked").build());
  }

  @Test
  @DisplayName("429 surfaces typed RateLimitException with upgrade envelope")
  void rateLimit429UpgradeEnvelope() {
    String envelope =
        "{"
            + "\"error\":\"Free tier shows the last 5 decisions in 24h. Pro raises this to 100 decisions in the last 30 days.\","
            + "\"limit_type\":\"decision_list_size\","
            + "\"tier\":\"Community\","
            + "\"limit\":5,"
            + "\"remaining\":0,"
            + "\"upgrade\":{"
            + "  \"tier\":\"Pro\","
            + "  \"wording\":\"Free tier shows the last 5 decisions in 24h. Pro raises this to 100 decisions in the last 30 days.\","
            + "  \"compare_url\":\"https://getaxonflow.com/pricing/\","
            + "  \"buy_url\":\"https://buy.stripe.com/bJe28qbztcdVchjdkw8k800\""
            + "}}";
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody(envelope)));

    assertThatThrownBy(
            () -> axonflow.listDecisions(ListDecisionsOptions.builder().limit(10).build()))
        .isInstanceOfSatisfying(
            RateLimitException.class,
            rle -> {
              assertThat(rle.getTier()).isEqualTo("Community");
              assertThat(rle.getLimitType()).isEqualTo("decision_list_size");
              assertThat(rle.getLimit()).isEqualTo(5);
              assertThat(rle.getUpgrade()).isNotNull();
              assertThat(rle.getUpgrade().getTier()).isEqualTo("Pro");
              assertThat(rle.getUpgrade().getCompareUrl())
                  .isEqualTo("https://getaxonflow.com/pricing/");
              assertThat(rle.getUpgrade().getBuyUrl())
                  .isEqualTo("https://buy.stripe.com/bJe28qbztcdVchjdkw8k800");
            });
  }

  @Test
  @DisplayName("429 with malformed body falls back to AxonFlowException — never silently OK")
  void rateLimit429MalformedBody() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(aResponse().withStatus(429).withBody("not a json envelope")));

    assertThatThrownBy(() -> axonflow.listDecisions(null))
        .isInstanceOf(AxonFlowException.class)
        .satisfies(
            e -> {
              // Must NOT be a RateLimitException since we couldn't parse the envelope.
              assertThat(e).isNotInstanceOf(RateLimitException.class);
              AxonFlowException afe = (AxonFlowException) e;
              assertThat(afe.getStatusCode()).isEqualTo(429);
            });
  }

  @Test
  @DisplayName("401 surfaces as AxonFlowException")
  void status401() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withBody("{\"error\":\"X-Tenant-ID header is required\"}")));

    assertThatThrownBy(() -> axonflow.listDecisions(null)).isInstanceOf(AxonFlowException.class);
  }

  @Test
  @DisplayName("forward-compat — additive unknown fields ignored")
  void forwardCompat() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"decisions\":[{"
                            + "\"decision_id\":\"dec-fwd\","
                            + "\"timestamp\":\"2026-05-07T12:00:00Z\","
                            + "\"decision\":\"blocked\","
                            + "\"policy_id\":\"pol-x\","
                            + "\"tool_signature\":\"tool-x\","
                            + "\"policy_version\":7,"
                            + "\"latest_policy_version\":9,"
                            + "\"arbitrary_unknown\":\"ignored\""
                            + "}],\"next_cursor\":\"future_cursor_pagination\"}")));

    List<DecisionSummary> got = axonflow.listDecisions(null);
    assertThat(got).hasSize(1);
    assertThat(got.get(0).getDecisionId()).isEqualTo("dec-fwd");
  }

  @Test
  @DisplayName("DecisionSummary parses minimal shape (policy_id + tool_signature absent)")
  void summaryMinimalShape() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"decisions\":[{"
                            + "\"decision_id\":\"dec-min\","
                            + "\"timestamp\":\"2026-05-07T12:00:00Z\","
                            + "\"decision\":\"blocked\""
                            + "}]}")));

    List<DecisionSummary> got = axonflow.listDecisions(null);
    assertThat(got.get(0).getPolicyId()).isNull();
    assertThat(got.get(0).getToolSignature()).isNull();
  }

  @Test
  @DisplayName("buildListDecisionsQuery — null/empty options return empty query")
  void buildQueryEmpty() {
    assertThat(AxonFlow.buildListDecisionsQuery(null)).isEmpty();
    assertThat(AxonFlow.buildListDecisionsQuery(ListDecisionsOptions.empty())).isEmpty();
  }

  @Test
  @DisplayName("buildListDecisionsQuery — partial options omit None fields")
  void buildQueryPartial() {
    ListDecisionsOptions opts = ListDecisionsOptions.builder().decision("blocked").limit(7).build();
    assertThat(AxonFlow.buildListDecisionsQuery(opts)).isEqualTo("?decision=blocked&limit=7");
  }

  @Test
  @DisplayName("listDecisionsAsync delegates to listDecisions")
  void asyncDelegates() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(aResponse().withStatus(200).withBody("{\"decisions\":[]}")));

    List<DecisionSummary> got = axonflow.listDecisionsAsync(null).get();
    assertThat(got).isEmpty();
  }
}
