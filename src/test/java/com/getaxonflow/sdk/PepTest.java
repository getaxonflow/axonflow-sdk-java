// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.AuthenticationException;
import com.getaxonflow.sdk.exceptions.ObligationNotFulfillableException;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;
import com.getaxonflow.sdk.types.Obligation;
import com.getaxonflow.sdk.types.ObligationFulfillment;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Decision Mode PEP: decide → fulfill → forward (ADR-056, epic #2563).
 *
 * <p>Pins the wire shape of {@code /api/v1/decide} and {@code /api/v1/mcp/check-input}, and every
 * fail-closed branch of {@code fulfillRequest} / {@code decideAndFulfill}. There is NO local
 * redaction anywhere in the SDK — fulfillment is always the engine round-trip — so every "redacted"
 * assertion below is satisfied only by the stubbed engine response, never by client logic.
 */
@WireMockTest
@DisplayName("Decision Mode PEP (ADR-056 / #2563)")
class PepTest {

  private AxonFlow axonflow;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wm) {
    axonflow =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(wm.getHttpBaseUrl())
                .clientId("org-test")
                .clientSecret("license-test")
                .build());
  }

  // Builds a /decide allow body carrying a request-phase redact_pii obligation pointing at the
  // request-redaction endpoint.
  private static String allowWithRedactObligation(String endpoint, String contentTypesJson) {
    String fulfillment =
        "{\"endpoint\":\""
            + endpoint
            + "\",\"method\":\"POST\",\"phase\":\"request\""
            + (contentTypesJson == null ? "" : ",\"content_types\":" + contentTypesJson)
            + "}";
    return "{"
        + "\"verdict\":\"allow\","
        + "\"decision_id\":\"dec-1\","
        + "\"trace_id\":\"04110a0b50577bbbdda23a00dcbaf6da\","
        + "\"obligations\":[{\"type\":\"redact_pii\",\"fulfillment\":"
        + fulfillment
        + "}],"
        + "\"evaluated_policies\":[\"sys_pii_email\",\"sys_pii_credit_card\"],"
        + "\"stage\":\"tool\","
        + "\"expires_at\":\"2026-06-09T05:05:06.8Z\""
        + "}";
  }

  private void stubDecide(String body) {
    stubFor(post(urlEqualTo("/api/v1/decide")).willReturn(okJson(body)));
  }

  private void stubCheckInput(String body) {
    stubFor(post(urlEqualTo("/api/v1/mcp/check-input")).willReturn(okJson(body)));
  }

  private static String checkInputResponse(
      boolean redacted, String redactedStatement, boolean redactionEvaluated) {
    return "{"
        + "\"allowed\":true,"
        + "\"policies_evaluated\":124,"
        + "\"decision_id\":\"ci-1\","
        + "\"redacted\":"
        + redacted
        + ","
        + (redactedStatement == null ? "" : "\"redacted_statement\":\"" + redactedStatement + "\",")
        + "\"redaction_evaluated\":"
        + redactionEvaluated
        + "}";
  }

  @Nested
  @DisplayName("decide()")
  class Decide {

    @Test
    @DisplayName("parses an allow verdict with a redact_pii obligation")
    void parsesAllowWithObligation() {
      stubDecide(allowWithRedactObligation("/api/v1/mcp/check-input", "[\"text/plain\"]"));

      DecideResponse r =
          axonflow.decide(
              DecideRequest.builder("tool", "Email me at a@b.com")
                  .target(new DecisionTarget("tool", null, null, "send_email"))
                  .build());

      assertThat(r.getVerdict()).isEqualTo("allow");
      assertThat(r.getDecisionId()).isEqualTo("dec-1");
      assertThat(r.getTraceId()).isEqualTo("04110a0b50577bbbdda23a00dcbaf6da");
      assertThat(r.getObligations()).hasSize(1);
      Obligation ob = r.getObligations().get(0);
      assertThat(ob.getType()).isEqualTo("redact_pii");
      assertThat(ob.getFulfillment().getPhase()).isEqualTo("request");
      assertThat(ob.getFulfillment().getEndpoint()).isEqualTo("/api/v1/mcp/check-input");
      assertThat(ob.getFulfillment().getContentTypes()).containsExactly("text/plain");
      assertThat(r.getEvaluatedPolicies()).contains("sys_pii_email");
      assertThat(r.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("sends the stage and query on the wire")
    void sendsStageAndQuery() {
      stubDecide("{\"verdict\":\"allow\",\"obligations\":[]}");

      axonflow.decide(DecideRequest.builder("llm", "hello").build());

      verify(
          postRequestedFor(urlEqualTo("/api/v1/decide"))
              .withRequestBody(matchingJsonPath("$.stage", equalTo("llm")))
              .withRequestBody(matchingJsonPath("$.query", equalTo("hello"))));
    }

    @Test
    @DisplayName("omits a blank user_token and empty context from the wire")
    void omitsEmptyOptionalFields() {
      stubDecide("{\"verdict\":\"allow\",\"obligations\":[]}");

      axonflow.decide(DecideRequest.builder("llm", "hello").userToken("").build());

      verify(
          postRequestedFor(urlEqualTo("/api/v1/decide"))
              .withRequestBody(notMatching(".*user_token.*"))
              .withRequestBody(notMatching(".*\"context\".*")));
    }

    @Test
    @DisplayName("returns a deny verdict in the body (HTTP 200), not as an error")
    void denyIsBodyNotError() {
      stubDecide("{\"verdict\":\"deny\",\"error\":\"stage is required\",\"obligations\":[]}");

      DecideResponse r = axonflow.decide(DecideRequest.builder("", "q").build());

      assertThat(r.getVerdict()).isEqualTo("deny");
      assertThat(r.getError()).contains("stage is required");
      assertThat(r.getObligations()).isEmpty();
    }

    @Test
    @DisplayName("raises AuthenticationException on HTTP 401")
    void raisesAuthOn401() {
      stubFor(
          post(urlEqualTo("/api/v1/decide"))
              .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"Invalid\"}")));

      assertThatThrownBy(() -> axonflow.decide(DecideRequest.builder("tool", "q").build()))
          .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("sends HTTP Basic org:license auth")
    void sendsBasicAuth() {
      stubDecide("{\"verdict\":\"allow\",\"obligations\":[]}");

      axonflow.decide(DecideRequest.builder("tool", "q").build());

      verify(
          postRequestedFor(urlEqualTo("/api/v1/decide"))
              .withBasicAuth(
                  new com.github.tomakehurst.wiremock.client.BasicCredentials(
                      "org-test", "license-test")));
    }
  }

  @Nested
  @DisplayName("fulfillRequest()")
  class FulfillRequest {

    @Test
    @DisplayName("passthrough: null decision returns the original statement, did_redact=false")
    void nullDecisionPassthrough() {
      AxonFlow.FulfillResult res = axonflow.fulfillRequest(null, "secret a@b.com");
      assertThat(res.getContent()).isEqualTo("secret a@b.com");
      assertThat(res.didRedact()).isFalse();
    }

    @Test
    @DisplayName("passthrough: no obligations returns the original statement")
    void noObligationsPassthrough() {
      DecideResponse d =
          new DecideResponse(
              "allow", "d", "t", null, Collections.emptyList(), null, null, null, null);
      AxonFlow.FulfillResult res = axonflow.fulfillRequest(d, "secret a@b.com");
      assertThat(res.getContent()).isEqualTo("secret a@b.com");
      assertThat(res.didRedact()).isFalse();
    }

    @Test
    @DisplayName("passthrough: a non-redact obligation is ignored")
    void nonRedactObligationIgnored() {
      DecideResponse d =
          new DecideResponse(
              "allow",
              "d",
              "t",
              null,
              Arrays.asList(new Obligation("log_only", null, null)),
              null,
              null,
              null,
              null);
      AxonFlow.FulfillResult res = axonflow.fulfillRequest(d, "keep me");
      assertThat(res.getContent()).isEqualTo("keep me");
      assertThat(res.didRedact()).isFalse();
    }

    @Test
    @DisplayName("engine-redacts and returns masked content; sends content_type=text/plain")
    void engineRedacts() {
      stubCheckInput(checkInputResponse(true, "Email jo****om and card 4****1", true));
      DecideResponse d = decisionWithRedactObligation("/api/v1/mcp/check-input", null);

      AxonFlow.FulfillResult res =
          axonflow.fulfillRequest(d, "Email john.doe@example.com and card 4111111111111111");

      assertThat(res.getContent()).isEqualTo("Email jo****om and card 4****1");
      assertThat(res.getContent()).doesNotContain("john.doe@example.com");
      assertThat(res.getContent()).doesNotContain("4111111111111111");
      assertThat(res.didRedact()).isTrue();
      verify(
          postRequestedFor(urlEqualTo("/api/v1/mcp/check-input"))
              .withRequestBody(matchingJsonPath("$.content_type", equalTo("text/plain")))
              .withRequestBody(matchingJsonPath("$.connector_type", equalTo("gateway"))));
    }

    @Test
    @DisplayName("engine found nothing: forwards the statement unchanged, did_redact=false")
    void engineFoundNothing() {
      stubCheckInput(checkInputResponse(false, null, true));
      DecideResponse d = decisionWithRedactObligation("/api/v1/mcp/check-input", null);

      AxonFlow.FulfillResult res = axonflow.fulfillRequest(d, "no pii here");

      assertThat(res.getContent()).isEqualTo("no pii here");
      assertThat(res.didRedact()).isFalse();
    }

    @Test
    @DisplayName("fail-closed: redact_pii with no fulfillment block")
    void failClosedNoFulfillment() {
      DecideResponse d =
          new DecideResponse(
              "allow",
              "d",
              "t",
              null,
              Arrays.asList(new Obligation("redact_pii", null, null)),
              null,
              null,
              null,
              null);
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("request-phase fulfillment");
    }

    @Test
    @DisplayName("fail-closed: redact_pii with a response-phase fulfillment")
    void failClosedResponsePhase() {
      DecideResponse d =
          new DecideResponse(
              "allow",
              "d",
              "t",
              null,
              Arrays.asList(
                  new Obligation(
                      "redact_pii",
                      null,
                      new ObligationFulfillment(
                          "/api/v1/mcp/check-output", "POST", "response", null))),
              null,
              null,
              null,
              null);
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("request-phase fulfillment");
    }

    @Test
    @DisplayName("fail-closed: endpoint advertises content types that exclude text/plain")
    void failClosedUnadvertisedContentType() {
      DecideResponse d =
          decisionWithRedactObligation(
              "/api/v1/mcp/check-input", Arrays.asList("image/png", "application/pdf"));
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("text/plain");
    }

    @Test
    @DisplayName("fail-closed: foreign endpoint is rejected (no arbitrary URL POST)")
    void failClosedForeignEndpoint() {
      DecideResponse d = decisionWithRedactObligation("https://evil.example/steal", null);
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("request-redaction endpoint");
    }

    @Test
    @DisplayName("fail-closed: engine returns a non-200")
    void failClosedEngineError() {
      stubFor(
          post(urlEqualTo("/api/v1/mcp/check-input"))
              .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));
      DecideResponse d = decisionWithRedactObligation("/api/v1/mcp/check-input", null);
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("engine call failed");
    }

    @Test
    @DisplayName("fail-closed: redaction_evaluated=false (redactor did not run)")
    void failClosedRedactionNotEvaluated() {
      stubCheckInput(checkInputResponse(false, null, false));
      DecideResponse d = decisionWithRedactObligation("/api/v1/mcp/check-input", null);
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("redactor did not run");
    }

    @Test
    @DisplayName("fail-closed: redacted=true but no redacted_statement (self-contradictory)")
    void failClosedRedactedTrueNoStatement() {
      // Engine claims it masked something but returned nothing to forward —
      // must fail closed, never fall back to the unredacted original.
      stubCheckInput(checkInputResponse(true, null, true));
      DecideResponse d = decisionWithRedactObligation("/api/v1/mcp/check-input", null);
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "secret a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("no redacted_statement");
    }

    @Test
    @DisplayName("fail-closed: redaction_evaluated absent defaults false → fails closed")
    void failClosedRedactionEvaluatedAbsent() {
      // No redaction_evaluated key at all — Jackson defaults the primitive to false.
      stubCheckInput("{\"allowed\":true,\"policies_evaluated\":1,\"redacted\":false}");
      DecideResponse d = decisionWithRedactObligation("/api/v1/mcp/check-input", null);
      assertThatThrownBy(() -> axonflow.fulfillRequest(d, "a@b.com"))
          .isInstanceOf(ObligationNotFulfillableException.class)
          .hasMessageContaining("redactor did not run");
    }

    @Test
    @DisplayName("accepts an absolute fulfillment URL whose path is the request-redaction path")
    void acceptsAbsoluteUrlWithMatchingPath(WireMockRuntimeInfo wm) {
      stubCheckInput(checkInputResponse(true, "masked", true));
      DecideResponse d =
          decisionWithRedactObligation(wm.getHttpBaseUrl() + "/api/v1/mcp/check-input", null);
      AxonFlow.FulfillResult res = axonflow.fulfillRequest(d, "secret");
      assertThat(res.getContent()).isEqualTo("masked");
      assertThat(res.didRedact()).isTrue();
    }
  }

  @Nested
  @DisplayName("decideAndFulfill()")
  class DecideAndFulfill {

    @Test
    @DisplayName("allow: returns engine-redacted content")
    void allowRedacts() {
      stubDecide(allowWithRedactObligation("/api/v1/mcp/check-input", "[\"text/plain\"]"));
      stubCheckInput(checkInputResponse(true, "Email jo****om", true));

      AxonFlow.DecideAndFulfillResult res =
          axonflow.decideAndFulfill(
              DecideRequest.builder("tool", "Email john.doe@example.com").build());

      assertThat(res.getVerdict()).isEqualTo("allow");
      assertThat(res.getContent()).isEqualTo("Email jo****om");
      assertThat(res.getContent()).doesNotContain("john.doe@example.com");
      assertThat(res.getDecision().getDecisionId()).isEqualTo("dec-1");
    }

    @Test
    @DisplayName("deny: returns the original query and the deny verdict, no fulfill call")
    void denyReturnsOriginal() {
      stubDecide("{\"verdict\":\"deny\",\"obligations\":[],\"error\":\"blocked\"}");

      AxonFlow.DecideAndFulfillResult res =
          axonflow.decideAndFulfill(DecideRequest.builder("tool", "original query").build());

      assertThat(res.getVerdict()).isEqualTo("deny");
      assertThat(res.getContent()).isEqualTo("original query");
      verify(0, postRequestedFor(urlEqualTo("/api/v1/mcp/check-input")));
    }

    @Test
    @DisplayName("unfulfillable allow: throws — caller has no content to forward")
    void unfulfillableThrows() {
      stubDecide(allowWithRedactObligation("https://evil.example/steal", null));

      assertThatThrownBy(
              () ->
                  axonflow.decideAndFulfill(
                      DecideRequest.builder("tool", "leak john.doe@example.com").build()))
          .isInstanceOf(ObligationNotFulfillableException.class);
    }
  }

  @Nested
  @DisplayName("Pep.hasRequestRedaction()")
  class HasRequestRedaction {

    @Test
    @DisplayName("true when a redact_pii request-phase obligation is present")
    void trueForRequestPhase() {
      List<Obligation> obs =
          Arrays.asList(
              new Obligation(
                  "redact_pii",
                  null,
                  new ObligationFulfillment("/api/v1/mcp/check-input", "POST", "request", null)));
      assertThat(Pep.hasRequestRedaction(obs)).isTrue();
    }

    @Test
    @DisplayName("false for response-phase, non-redact, no-fulfillment, null, and empty")
    void falseOtherwise() {
      assertThat(Pep.hasRequestRedaction(null)).isFalse();
      assertThat(Pep.hasRequestRedaction(Collections.emptyList())).isFalse();
      assertThat(Pep.hasRequestRedaction(Arrays.asList(new Obligation("redact_pii", null, null))))
          .isFalse();
      assertThat(
              Pep.hasRequestRedaction(
                  Arrays.asList(
                      new Obligation(
                          "redact_pii",
                          null,
                          new ObligationFulfillment(
                              "/api/v1/mcp/check-output", "POST", "response", null)))))
          .isFalse();
      assertThat(
              Pep.hasRequestRedaction(
                  Arrays.asList(
                      new Obligation(
                          "log_only",
                          null,
                          new ObligationFulfillment(
                              "/api/v1/mcp/check-input", "POST", "request", null)))))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("Pep.endpointPathMatches()")
  class EndpointPathMatches {
    @Test
    @DisplayName("matches exact path, absolute URL path, and rejects mismatches")
    void matches() {
      assertThat(Pep.endpointPathMatches("/api/v1/mcp/check-input", "/api/v1/mcp/check-input"))
          .isTrue();
      assertThat(
              Pep.endpointPathMatches(
                  "https://pdp:8443/api/v1/mcp/check-input?x=1", "/api/v1/mcp/check-input"))
          .isTrue();
      assertThat(Pep.endpointPathMatches("/api/v1/mcp/check-output", "/api/v1/mcp/check-input"))
          .isFalse();
      assertThat(Pep.endpointPathMatches("", "/api/v1/mcp/check-input")).isFalse();
      assertThat(Pep.endpointPathMatches(null, "/api/v1/mcp/check-input")).isFalse();
      assertThat(Pep.endpointPathMatches("https://evil.example", "/api/v1/mcp/check-input"))
          .isFalse();
    }
  }

  private static DecideResponse decisionWithRedactObligation(
      String endpoint, List<String> contentTypes) {
    return new DecideResponse(
        "allow",
        "dec-1",
        "trace-1",
        null,
        Arrays.asList(
            new Obligation(
                "redact_pii",
                null,
                new ObligationFulfillment(endpoint, "POST", "request", contentTypes))),
        Arrays.asList("sys_pii_email"),
        "tool",
        null,
        null);
  }
}
