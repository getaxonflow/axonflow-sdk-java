// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wire-shape + value-semantics tests for the Decision Mode PEP DTOs (ADR-056 / #2563). Pins the
 * snake_case JSON field names, the request/response (de)serialization, and the
 * equals/hashCode/toString contracts of the new types.
 */
@DisplayName("PEP DTO wire shape + value semantics (#2563)")
class PepTypesTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("DecideRequest serializes snake_case and omits blank user_token + empty context")
  void decideRequestWireShape() throws Exception {
    DecideRequest req =
        DecideRequest.builder("tool", "send to a@b.com")
            .callerIdentity(new DecisionCallerIdentity("gw-1", "org-1", "tenant-1"))
            .target(new DecisionTarget("tool", null, null, "send_email"))
            .userToken("")
            .context(new HashMap<>())
            .build();

    String json = MAPPER.writeValueAsString(req);

    assertThat(json).contains("\"stage\":\"tool\"");
    assertThat(json).contains("\"caller_identity\"");
    assertThat(json).contains("\"gateway_id\":\"gw-1\"");
    assertThat(json).contains("\"org_id\":\"org-1\"");
    assertThat(json).contains("\"tenant_id\":\"tenant-1\"");
    assertThat(json).contains("\"tool\":\"send_email\"");
    assertThat(json).doesNotContain("user_token");
    assertThat(json).doesNotContain("\"context\"");

    // toString masks the token and exposes the rest.
    assertThat(req.toString()).contains("stage='tool'");
    assertThat(req.getStage()).isEqualTo("tool");
    assertThat(req.getQuery()).isEqualTo("send to a@b.com");
    assertThat(req.getUserToken()).isNull();
    assertThat(req.getContext()).isNull();
    assertThat(req.getTarget().getTool()).isEqualTo("send_email");
    assertThat(req.getCallerIdentity().getOrgId()).isEqualTo("org-1");
  }

  @Test
  @DisplayName("DecideRequest keeps a non-empty user_token + context")
  void decideRequestKeepsPopulatedOptionals() throws Exception {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("k", "v");
    DecideRequest req = DecideRequest.builder("llm", "q").userToken("jwt-123").context(ctx).build();
    String json = MAPPER.writeValueAsString(req);
    assertThat(json).contains("\"user_token\":\"jwt-123\"");
    assertThat(json).contains("\"context\":{\"k\":\"v\"}");
    assertThat(req.getUserToken()).isEqualTo("jwt-123");
    assertThat(req.getContext()).containsEntry("k", "v");
    assertThat(req.toString()).contains("<redacted>");
  }

  @Test
  @DisplayName("DecideResponse parses the canonical /decide allow body")
  void decideResponseParse() throws Exception {
    String body =
        "{"
            + "\"verdict\":\"allow\",\"decision_id\":\"66e7c30a\","
            + "\"trace_id\":\"04110a0b50577bbbdda23a00dcbaf6da\","
            + "\"reasons\":[\"r1\"],"
            + "\"obligations\":[{\"type\":\"redact_pii\",\"detail\":\"mask\","
            + "\"fulfillment\":{\"endpoint\":\"/api/v1/mcp/check-input\",\"method\":\"POST\","
            + "\"phase\":\"request\",\"content_types\":[\"text/plain\"]}}],"
            + "\"evaluated_policies\":[\"sys_pii_email\"],\"stage\":\"tool\","
            + "\"expires_at\":\"2026-06-09T05:05:06.8Z\",\"future\":\"ignored\"}";

    DecideResponse r = MAPPER.readValue(body, DecideResponse.class);

    assertThat(r.getVerdict()).isEqualTo("allow");
    assertThat(r.getDecisionId()).isEqualTo("66e7c30a");
    assertThat(r.getTraceId()).isEqualTo("04110a0b50577bbbdda23a00dcbaf6da");
    assertThat(r.getReasons()).containsExactly("r1");
    assertThat(r.getObligations()).hasSize(1);
    Obligation ob = r.getObligations().get(0);
    assertThat(ob.getType()).isEqualTo("redact_pii");
    assertThat(ob.getDetail()).isEqualTo("mask");
    ObligationFulfillment f = ob.getFulfillment();
    assertThat(f.getEndpoint()).isEqualTo("/api/v1/mcp/check-input");
    assertThat(f.getMethod()).isEqualTo("POST");
    assertThat(f.getPhase()).isEqualTo("request");
    assertThat(f.getContentTypes()).containsExactly("text/plain");
    assertThat(r.getEvaluatedPolicies()).containsExactly("sys_pii_email");
    assertThat(r.getStage()).isEqualTo("tool");
    assertThat(r.getExpiresAt()).isNotNull();
    assertThat(r.getError()).isNull();
    assertThat(r.toString()).contains("verdict='allow'");
  }

  @Test
  @DisplayName("DecideResponse normalizes null obligations + evaluated_policies to empty lists")
  void decideResponseNormalizesNulls() throws Exception {
    DecideResponse r =
        MAPPER.readValue("{\"verdict\":\"deny\",\"error\":\"bad\"}", DecideResponse.class);
    assertThat(r.getObligations()).isEmpty();
    assertThat(r.getEvaluatedPolicies()).isEmpty();
    assertThat(r.getError()).isEqualTo("bad");
  }

  @Test
  @DisplayName("DecideResponse obligations list is unmodifiable")
  void obligationsUnmodifiable() {
    DecideResponse r =
        new DecideResponse(
            "allow",
            "d",
            "t",
            null,
            Arrays.asList(new Obligation("redact_pii", null, null)),
            Arrays.asList("p"),
            "tool",
            null,
            null);
    assertThatThrownBy(() -> r.getObligations().add(new Obligation("x", null, null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("MCPCheckInputRequest serializes content_type")
  void checkInputRequestContentType() throws Exception {
    MCPCheckInputRequest req =
        new MCPCheckInputRequest("gateway", "secret", null, "execute", "text/plain");
    String json = MAPPER.writeValueAsString(req);
    assertThat(json).contains("\"content_type\":\"text/plain\"");
    assertThat(req.getContentType()).isEqualTo("text/plain");
    // Null content type is omitted (NON_NULL).
    MCPCheckInputRequest noCt = new MCPCheckInputRequest("gateway", "x");
    assertThat(MAPPER.writeValueAsString(noCt)).doesNotContain("content_type");
    assertThat(noCt.getContentType()).isNull();
  }

  @Test
  @DisplayName("MCPCheckInputResponse parses redacted/redacted_statement/redaction_evaluated")
  void checkInputResponseRedactionFields() throws Exception {
    String body =
        "{\"allowed\":true,\"policies_evaluated\":124,\"redacted\":true,"
            + "\"redacted_statement\":\"Email jo****om\",\"redaction_evaluated\":true}";
    MCPCheckInputResponse r = MAPPER.readValue(body, MCPCheckInputResponse.class);
    assertThat(r.isRedacted()).isTrue();
    assertThat(r.getRedactedStatement()).isEqualTo("Email jo****om");
    assertThat(r.isRedactionEvaluated()).isTrue();
    assertThat(r.toString()).contains("redactionEvaluated=true");
  }

  @Test
  @DisplayName("MCPCheckOutputResponse parses redaction_evaluated; default is false")
  void checkOutputResponseRedactionEvaluated() throws Exception {
    MCPCheckOutputResponse r =
        MAPPER.readValue(
            "{\"allowed\":true,\"redaction_evaluated\":true}", MCPCheckOutputResponse.class);
    assertThat(r.isRedactionEvaluated()).isTrue();
    MCPCheckOutputResponse def =
        MAPPER.readValue("{\"allowed\":true}", MCPCheckOutputResponse.class);
    assertThat(def.isRedactionEvaluated()).isFalse();
    assertThat(r.toString()).contains("redactionEvaluated=true");
  }

  @Test
  @DisplayName("value semantics: equals / hashCode / getters on all PEP DTOs")
  void valueSemantics() {
    DecisionCallerIdentity ci1 = new DecisionCallerIdentity("g", "o", "t");
    DecisionCallerIdentity ci2 = new DecisionCallerIdentity("g", "o", "t");
    DecisionCallerIdentity ci3 = new DecisionCallerIdentity("g", "o", "other");
    assertThat(ci1).isEqualTo(ci2).hasSameHashCodeAs(ci2).isNotEqualTo(ci3).isNotEqualTo(null);
    assertThat(ci1.getGatewayId()).isEqualTo("g");
    assertThat(ci1.getTenantId()).isEqualTo("t");
    assertThat(ci1.toString()).contains("orgId='o'");

    DecisionTarget t1 = new DecisionTarget("llm", "gpt-4o", "openai", null);
    DecisionTarget t2 = new DecisionTarget("llm", "gpt-4o", "openai", null);
    assertThat(t1)
        .isEqualTo(t2)
        .hasSameHashCodeAs(t2)
        .isNotEqualTo(new DecisionTarget(null, null, null, null));
    assertThat(t1.getType()).isEqualTo("llm");
    assertThat(t1.getModel()).isEqualTo("gpt-4o");
    assertThat(t1.getProvider()).isEqualTo("openai");
    assertThat(t1.toString()).contains("provider='openai'");

    ObligationFulfillment f1 =
        new ObligationFulfillment(
            "/api/v1/mcp/check-input", "POST", "request", List.of("text/plain"));
    ObligationFulfillment f2 =
        new ObligationFulfillment(
            "/api/v1/mcp/check-input", "POST", "request", List.of("text/plain"));
    assertThat(f1).isEqualTo(f2).hasSameHashCodeAs(f2).isNotEqualTo("nope");
    assertThat(f1.toString()).contains("phase='request'");

    Obligation o1 = new Obligation("redact_pii", "d", f1);
    Obligation o2 = new Obligation("redact_pii", "d", f2);
    Obligation o3 = new Obligation("log_only", null, null);
    assertThat(o1).isEqualTo(o2).hasSameHashCodeAs(o2).isNotEqualTo(o3);
    assertThat(o1.getType()).isEqualTo("redact_pii");
    assertThat(o1.getDetail()).isEqualTo("d");
    assertThat(o1.getFulfillment()).isEqualTo(f1);
    assertThat(o1.toString()).contains("type='redact_pii'");

    DecideRequest req1 = DecideRequest.builder("tool", "q").build();
    DecideRequest req2 = DecideRequest.builder("tool", "q").build();
    assertThat(req1).isEqualTo(req2).hasSameHashCodeAs(req2);

    DecideResponse r1 =
        new DecideResponse("allow", "d", "t", null, List.of(o1), List.of("p"), "tool", null, null);
    DecideResponse r2 =
        new DecideResponse("allow", "d", "t", null, List.of(o2), List.of("p"), "tool", null, null);
    assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
    assertThat(r1)
        .isNotEqualTo(new DecideResponse("deny", null, null, null, null, null, null, null, null));
  }
}
