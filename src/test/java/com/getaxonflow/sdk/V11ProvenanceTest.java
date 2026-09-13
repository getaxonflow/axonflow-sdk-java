// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.LegacyPolicyWriteFrozenException;
import com.getaxonflow.sdk.types.ClientResponse;
import com.getaxonflow.sdk.types.ConnectorQuery;
import com.getaxonflow.sdk.types.ConnectorResponse;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;
import com.getaxonflow.sdk.types.LegacyValidatorAction;
import com.getaxonflow.sdk.types.MCPCheckOutputResponse;
import com.getaxonflow.sdk.types.PolicyApprovalRequest;
import com.getaxonflow.sdk.types.PolicyApprovalResult;
import com.getaxonflow.sdk.types.PolicyIdentity;
import com.getaxonflow.sdk.types.policies.PolicyTypes.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@WireMockTest
@DisplayName("v11.0.0: decision provenance, the frozen-write refusal, route deprecation")
class V11ProvenanceTest {

  private static final String PROVENANCE =
      "\"engine\":\"anchored\",\"subject_type\":\"Client\",\"policy_bundle\":\"sha256:bundle\","
          + "\"legacy_validators\":[{\"validator\":\"india_pii\",\"action\":\"masked\"}]";

  private static final List<LegacyValidatorAction> VALIDATORS =
      List.of(new LegacyValidatorAction("india_pii", "masked"));

  private static final String FROZEN =
      "{\"error\":{\"code\":\"LEGACY_POLICY_WRITE_FROZEN\",\"message\":"
          + "\"legacy policy writes are frozen; author policy at /api/v1/typed-policies\"}}";

  private final List<PlatformRouteDeprecation> reported =
      Collections.synchronizedList(new ArrayList<>());

  private String baseUrl;
  private AxonFlow axonflow;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wm) {
    baseUrl = wm.getHttpBaseUrl();
    axonflow = client(reported::add);
  }

  private AxonFlow client(Consumer<PlatformRouteDeprecation> listener) {
    return AxonFlow.create(
        AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .clientId("test-client")
            .clientSecret("test-secret")
            .onRouteDeprecation(listener)
            .build());
  }

  private static void assertProvenance(
      String engine, String subjectType, String bundle, List<LegacyValidatorAction> validators) {
    assertThat(engine).isEqualTo("anchored");
    assertThat(subjectType).isEqualTo("Client");
    assertThat(bundle).isEqualTo("sha256:bundle");
    assertThat(validators).isEqualTo(VALIDATORS);
  }

  private static CreateStaticPolicyRequest staticWrite() {
    return CreateStaticPolicyRequest.builder()
        .name("p")
        .category(PolicyCategory.SECURITY_SQLI)
        .pattern("x")
        .build();
  }

  @Test
  @DisplayName("decide carries the provenance, each policy named, the packs and the version")
  void decideCarriesTheV11Provenance() {
    stubFor(
        post(urlPathEqualTo("/api/v1/decide"))
            .willReturn(
                okJson(
                    "{\"verdict\":\"allow\",\"decision_id\":\"dec-1\",\"obligations\":[],"
                        + "\"evaluated_policies\":[\"sys_a\",\"org_b\"],"
                        + "\"policy_identities\":["
                        + "{\"id\":\"sys_a\",\"name\":\"Block SQL injection\",\"source\":\"shipped\"},"
                        + "{\"id\":\"org_b\",\"source\":\"organization\",\"version\":3}],"
                        + "\"policy_packs\":[\"rbi@sha256:pack\"],\"document_version\":3,"
                        + PROVENANCE
                        + "}")));

    DecideResponse resp =
        axonflow.decide(
            DecideRequest.builder("tool", "look up the weather")
                .target(new DecisionTarget("tool", null, null, "search"))
                .build());

    assertProvenance(
        resp.getEngine(),
        resp.getSubjectType(),
        resp.getPolicyBundle(),
        resp.getLegacyValidators());
    assertThat(resp.getPolicyIdentities())
        .containsExactly(
            new PolicyIdentity("sys_a", "Block SQL injection", "shipped", null),
            new PolicyIdentity("org_b", null, "organization", 3));
    assertThat(resp.getPolicyPacks()).containsExactly("rbi@sha256:pack");
    assertThat(resp.getDocumentVersion()).isEqualTo(3);
  }

  @Test
  @DisplayName("the pre-check carries the decision id, the verdict and the provenance")
  void preCheckCarriesDecisionIdVerdictAndProvenance() {
    stubFor(
        post(urlPathEqualTo("/api/policy/pre-check"))
            .willReturn(
                okJson(
                    "{\"context_id\":\"ctx-1\",\"approved\":true,\"policies\":[],"
                        + "\"expires_at\":\"2030-01-01T00:00:00Z\","
                        + "\"decision_id\":\"dec-2\",\"verdict\":\"allow\","
                        + PROVENANCE
                        + "}")));

    PolicyApprovalResult result =
        axonflow.getPolicyApprovedContext(
            PolicyApprovalRequest.builder().userToken("user-token").query("hello").build());

    assertThat(result.getDecisionId()).isEqualTo("dec-2");
    assertThat(result.getVerdict()).isEqualTo("allow");
    assertProvenance(
        result.getEngine(),
        result.getSubjectType(),
        result.getPolicyBundle(),
        result.getLegacyValidators());
  }

  @Test
  @DisplayName("MCP check-output carries the provenance")
  void checkOutputCarriesTheProvenance() {
    stubFor(
        post(urlPathEqualTo("/api/v1/mcp/check-output"))
            .willReturn(okJson("{\"allowed\":true,\"policies_evaluated\":1," + PROVENANCE + "}")));

    MCPCheckOutputResponse resp =
        axonflow.mcpCheckOutput("postgres", null, Map.of("message", "hello"));

    assertProvenance(
        resp.getEngine(),
        resp.getSubjectType(),
        resp.getPolicyBundle(),
        resp.getLegacyValidators());
  }

  @Test
  @DisplayName("both connector paths carry the provenance: the /api/request copy and the decode")
  void connectorResponsesCarryTheProvenance() {
    stubFor(
        post(urlPathEqualTo("/api/request"))
            .willReturn(okJson("{\"success\":true,\"data\":{\"rows\":[]}," + PROVENANCE + "}")));
    stubFor(
        post(urlPathEqualTo("/mcp/resources/query"))
            .willReturn(okJson("{\"success\":true,\"data\":[]," + PROVENANCE + "}")));

    ConnectorResponse queried =
        axonflow.queryConnector(
            ConnectorQuery.builder().connectorId("postgres").operation("select 1").build());
    ConnectorResponse direct = axonflow.mcpQuery("postgres", "select 1");

    for (ConnectorResponse resp : List.of(queried, direct)) {
      assertProvenance(
          resp.getEngine(),
          resp.getSubjectType(),
          resp.getPolicyBundle(),
          resp.getLegacyValidators());
    }
  }

  @Test
  @DisplayName("a frozen legacy write throws the typed exception on both policy routes")
  void legacyPolicyWritesThrowTheFrozenException() {
    stubFor(
        post(urlPathEqualTo("/api/v1/static-policies"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(FROZEN)));
    stubFor(
        post(urlPathEqualTo("/api/v1/dynamic-policies"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(FROZEN)));

    assertFrozen(() -> axonflow.createStaticPolicy(staticWrite()));
    assertFrozen(
        () ->
            axonflow.createDynamicPolicy(
                CreateDynamicPolicyRequest.builder()
                    .name("p")
                    .type("risk")
                    .conditions(
                        List.of(new DynamicPolicyCondition("risk_score", "greater_than", 0.99)))
                    .actions(List.of(new DynamicPolicyAction("log", Map.of())))
                    .build()));
  }

  private static void assertFrozen(ThrowingCallable call) {
    Throwable thrown = catchThrowable(call);
    assertThat(thrown)
        .isInstanceOf(LegacyPolicyWriteFrozenException.class)
        .hasMessageContaining("/api/v1/typed-policies");
    AxonFlowException refused = (AxonFlowException) thrown;
    assertThat(refused.getStatusCode()).isEqualTo(409);
    assertThat(refused.getErrorCode()).isEqualTo(LegacyPolicyWriteFrozenException.CODE);
  }

  @Test
  @DisplayName("only a 409 carrying the frozen code is the typed exception")
  void otherErrorsAreNotTheFrozenException() {
    Object[][] cases = {
      {
        409, "{\"error\":{\"code\":\"VERSION_CONFLICT\",\"message\":\"stale\"}}", "VERSION_CONFLICT"
      },
      {409, "{\"error\":\"conflict\"}", "VERSION_CONFLICT"},
      {409, "conflict", "VERSION_CONFLICT"},
      {400, FROZEN, null},
    };
    for (Object[] c : cases) {
      int status = (int) c[0];
      String body = (String) c[1];
      stubFor(
          post(urlPathEqualTo("/api/v1/static-policies"))
              .willReturn(aResponse().withStatus(status).withBody(body)));

      Throwable thrown = catchThrowable(() -> axonflow.createStaticPolicy(staticWrite()));

      assertThat(thrown)
          .as("status %d, body %s", status, body)
          .isInstanceOf(AxonFlowException.class)
          .isNotInstanceOf(LegacyPolicyWriteFrozenException.class);
      AxonFlowException refused = (AxonFlowException) thrown;
      assertThat(refused.getStatusCode()).as("status %d", status).isEqualTo(status);
      assertThat(refused.getErrorCode()).as("status %d", status).isEqualTo(c[2]);
    }
  }

  /**
   * The headers a v11 platform stamps: the removal release and the successor (in a SECOND Link
   * value) on the static route, an RFC 9745 Deprecation on the dynamic route, nothing elsewhere.
   */
  private static void stubDeprecatedRoutes() {
    stubFor(
        get(urlPathEqualTo("/api/v1/static-policies"))
            .willReturn(
                okJson("{\"policies\": []}")
                    .withHeader("X-AxonFlow-Removed-In", "v11.1")
                    .withHeader(
                        "Link",
                        "</api/v1/audit>; rel=\"related\"",
                        "</api/v1/typed-policies>; rel=\"successor-version\"")));
    stubFor(
        get(urlPathEqualTo("/api/v1/dynamic-policies"))
            .willReturn(okJson("{\"policies\": []}").withHeader("Deprecation", "@1788220800")));
    stubFor(
        get(urlPathEqualTo("/api/v1/static-policies/pol_1"))
            .willReturn(okJson("{\"id\":\"pol_1\",\"name\":\"p\"}")));
  }

  @Test
  @DisplayName("a deprecated route is reported once per client family, from either marker")
  void routeDeprecationIsReportedOncePerRoute() {
    stubDeprecatedRoutes();

    axonflow.listStaticPolicies();
    axonflow.listStaticPolicies();
    axonflow.asUser("someone").listStaticPolicies();
    axonflow.listDynamicPolicies();
    axonflow.getStaticPolicy("pol_1");

    // Three responses carried the headers; the record, not a cache, kept it to one report.
    verify(3, getRequestedFor(urlPathEqualTo("/api/v1/static-policies")));
    assertThat(reported)
        .containsExactly(
            new PlatformRouteDeprecation(
                "GET /api/v1/static-policies", "/api/v1/typed-policies", "v11.1", null),
            new PlatformRouteDeprecation(
                "GET /api/v1/dynamic-policies", null, null, "@1788220800"));
  }

  @Test
  @DisplayName("a client asUser derives keeps the listener")
  void aDerivedClientKeepsTheListener() {
    stubDeprecatedRoutes();

    axonflow.asUser("someone").listStaticPolicies();

    assertThat(reported)
        .extracting(PlatformRouteDeprecation::getRoute)
        .containsExactly("GET /api/v1/static-policies");
  }

  @Test
  @DisplayName("without a listener the deprecation is logged once at WARN")
  void routeDeprecationIsLoggedOnceWithoutAListener() {
    stubDeprecatedRoutes();
    Logger logger = (Logger) LoggerFactory.getLogger(AxonFlow.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      AxonFlow quiet = client(null);
      quiet.listStaticPolicies();
      quiet.listStaticPolicies();
    } finally {
      logger.detachAppender(appender);
    }

    String want =
        "GET /api/v1/static-policies is deprecated by the AxonFlow platform; "
            + "use /api/v1/typed-policies instead; it is removed in v11.1.";
    assertThat(appender.list)
        .filteredOn(e -> e.getLevel() == Level.WARN && want.equals(e.getFormattedMessage()))
        .hasSize(1);
  }

  @Test
  @DisplayName("the pre-v11 constructors still compile and leave the new fields unset")
  void thePreV11ConstructorsLeaveTheNewFieldsUnset() {
    ClientResponse client =
        new ClientResponse(true, null, null, null, false, null, null, null, null, null);
    DecideResponse decide =
        new DecideResponse("allow", null, null, null, null, null, null, null, null);

    assertThat(client.getEngine()).isNull();
    assertThat(client.getLegacyValidators()).isEmpty();
    assertThat(decide.getPolicyIdentities()).isEmpty();
    assertThat(decide.getPolicyPacks()).isEmpty();
    assertThat(decide.getDocumentVersion()).isNull();
  }
}
