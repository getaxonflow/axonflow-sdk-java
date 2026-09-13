// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.authzen.AuthZENObligationType;
import com.getaxonflow.sdk.exceptions.AuthenticationException;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.TypedPolicyRefusalException;
import com.getaxonflow.sdk.identity.ReadIdentity;
import com.getaxonflow.sdk.types.PEPCapability;
import com.getaxonflow.sdk.types.PEPHandshake;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.ActiveTypedPolicy;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.AuthoringFinding;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedAuthoringEdition;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyActivation;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyPublication;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicySystemCorpus;
import com.getaxonflow.sdk.types.policies.TypedPolicyTypes.TypedPolicyValidation;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Typed policy authoring: each operation on the wire (method, route and exact body) and on its
 * answer, every documented refusal, the null shapes a real platform sends, and the identity and
 * handshake rules of the namespace.
 */
@WireMockTest
@DisplayName("v11.0.0: typed policy authoring")
class TypedPoliciesTest {

  private static final String ROUTE = AxonFlow.TYPED_POLICIES_PATH;
  private static final Map<String, Object> DOCUMENT =
      Map.of("api_version", "v1", "metadata", Map.of("document_id", "doc-1"));
  private static final List<Map<String, Object>> FIXTURES =
      List.of(Map.of("name", "a refund is denied", "expect", "deny"));
  private static final String DOCUMENT_JSON =
      "{\"api_version\":\"v1\",\"metadata\":{\"document_id\":\"doc-1\"}}";
  private static final String FIXTURES_JSON =
      "[{\"name\":\"a refund is denied\",\"expect\":\"deny\"}]";
  private static final String DIGEST = "sha256:abc";

  private AxonFlow client;
  private String baseUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wm) {
    baseUrl = wm.getHttpBaseUrl();
    client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());
  }

  private static void answering(String method, String route, int status, String body) {
    answering(method, route, status, body, Map.of());
  }

  private static void answering(
      String method, String route, int status, String body, Map<String, String> headers) {
    var response = aResponse().withStatus(status).withHeader("Content-Type", "application/json");
    for (Map.Entry<String, String> h : headers.entrySet()) {
      response = response.withHeader(h.getKey(), h.getValue());
    }
    var mapping =
        "GET".equals(method)
            ? get(urlEqualTo(ROUTE + route)).willReturn(response.withBody(body))
            : post(urlEqualTo(ROUTE + route)).willReturn(response.withBody(body));
    stubFor(mapping);
  }

  // -------------------------------------------------------------------------
  // Each operation
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("edition")
  void edition() {
    answering(
        "GET",
        "/edition",
        200,
        "{\"success\":true,\"catalog\":\"default\",\"root\":\"organization\",\"max_documents\":20,"
            + "\"constructs\":{\"edition\":\"community\",\"obligation_families\":[\"field_redact\"],"
            + "\"attribute_namespaces\":[\"subject\"],\"group_scope\":false,"
            + "\"separation_of_duties\":false,\"tier_established\":true,\"reserved\":[\"group_scope\"]},"
            + "\"persistence\":\"database\",\"signing_key_custody\":\"local\"}");
    TypedAuthoringEdition edition = client.typedPolicies().edition();
    verify(getRequestedFor(urlEqualTo(ROUTE + "/edition")));
    assertThat(edition.isSuccess()).isTrue();
    assertThat(edition.getRoot()).isEqualTo("organization");
    assertThat(edition.getMaxDocuments()).isEqualTo(20);
    assertThat(edition.getPersistence()).isEqualTo("database");
    assertThat(edition.getConstructs().getEdition()).isEqualTo("community");
    assertThat(edition.getConstructs().getObligationFamilies()).containsExactly("field_redact");
    assertThat(edition.getConstructs().getTierEstablished()).isTrue();
    assertThat(edition.getConstructs().getReserved()).containsExactly("group_scope");
  }

  @Test
  @DisplayName("validate sends the document and fixtures and returns every finding")
  void validate() {
    answering(
        "POST",
        "/validate",
        200,
        "{\"success\":false,\"findings\":[{\"code\":\"ACTION_NOT_REGISTERED\",\"severity\":\"reject\","
            + "\"policy_id\":\"grant.refund\",\"summary\":\"not registered\",\"detail\":\"tool.x\"}]}");
    TypedPolicyValidation validation = client.typedPolicies().validate(DOCUMENT, FIXTURES);
    verify(
        postRequestedFor(urlEqualTo(ROUTE + "/validate"))
            .withRequestBody(
                equalToJson(
                    "{\"document\":" + DOCUMENT_JSON + ",\"fixtures\":" + FIXTURES_JSON + "}")));
    assertThat(validation.isSuccess()).isFalse();
    AuthoringFinding finding = validation.getFindings().get(0);
    assertThat(finding.getCode()).isEqualTo("ACTION_NOT_REGISTERED");
    assertThat(finding.getSeverity()).isEqualTo("reject");
    assertThat(finding.getPolicyId()).isEqualTo("grant.refund");
    assertThat(finding.getDetail()).isEqualTo("tool.x");
  }

  @Test
  @DisplayName("null fixtures send none, and an empty list sends an empty array")
  void fixturesMember() {
    answering("POST", "/validate", 200, "{\"success\":true,\"findings\":[]}");
    client.typedPolicies().validate(DOCUMENT, null);
    verify(
        postRequestedFor(urlEqualTo(ROUTE + "/validate"))
            .withRequestBody(equalToJson("{\"document\":" + DOCUMENT_JSON + "}")));
    resetAllRequests();
    client.typedPolicies().validate(DOCUMENT, List.of());
    verify(
        postRequestedFor(urlEqualTo(ROUTE + "/validate"))
            .withRequestBody(equalToJson("{\"document\":" + DOCUMENT_JSON + ",\"fixtures\":[]}")));
  }

  @Test
  @DisplayName("publish")
  void publish() {
    answering(
        "POST",
        "/publish",
        200,
        "{\"success\":true,\"digest\":\"sha256:abc\",\"version\":2,"
            + "\"findings\":[{\"code\":\"UNUSED_ATTRIBUTE\",\"severity\":\"warn\"}]}");
    TypedPolicyPublication published = client.typedPolicies().publish(DOCUMENT, FIXTURES);
    verify(
        postRequestedFor(urlEqualTo(ROUTE + "/publish"))
            .withRequestBody(
                equalToJson(
                    "{\"document\":" + DOCUMENT_JSON + ",\"fixtures\":" + FIXTURES_JSON + "}")));
    assertThat(published.getDigest()).isEqualTo(DIGEST);
    assertThat(published.getVersion()).isEqualTo(2);
    assertThat(published.getFindings())
        .extracting(AuthoringFinding::getCode)
        .containsExactly("UNUSED_ATTRIBUTE");
  }

  @Test
  @DisplayName("activate sends the reason only when there is one")
  void activate() {
    answering(
        "POST",
        "/activate",
        200,
        "{\"success\":true,\"activation\":{\"digest\":\"sha256:abc\",\"actor\":\"client:test-client\"}}");
    TypedPolicyActivation activation = client.typedPolicies().activate(DIGEST, null);
    verify(
        postRequestedFor(urlEqualTo(ROUTE + "/activate"))
            .withRequestBody(equalToJson("{\"digest\":\"sha256:abc\"}")));
    assertThat(activation.isSuccess()).isTrue();
    assertThat(activation.getActivation()).containsEntry("actor", "client:test-client");
    resetAllRequests();
    client.typedPolicies().activate(DIGEST, "rollout");
    verify(
        postRequestedFor(urlEqualTo(ROUTE + "/activate"))
            .withRequestBody(equalToJson("{\"digest\":\"sha256:abc\",\"reason\":\"rollout\"}")));
  }

  @Test
  @DisplayName("active keeps the exact bytes that were signed")
  void activeKeepsTheSignedBytes() {
    String signed = "{\"api_version\": \"v1\",   \"metadata\": {\"document_id\": \"doc-1\"}}";
    answering("GET", "/active", 200, signed);
    Optional<ActiveTypedPolicy> active = client.typedPolicies().active();
    assertThat(active).isPresent();
    assertThat(active.get().getSource()).isEqualTo(signed);
    assertThat(active.get().getDocument()).containsEntry("api_version", "v1");
  }

  @Test
  @DisplayName("nothing active is empty")
  void nothingActive() {
    answering(
        "GET",
        "/active",
        404,
        "{\"success\":false,\"reason\":\"no_active_policy\",\"error\":\"nothing is active\"}");
    assertThat(client.typedPolicies().active()).isEmpty();
  }

  @Test
  @DisplayName("system")
  void system() {
    answering(
        "GET",
        "/system",
        200,
        "{\"success\":true,\"system\":{\"root\":\"system\",\"version\":3,\"digest\":\"sha256:system\","
            + "\"authority\":\"shipped_corpus\",\"controls\":[{\"id\":\"sys.pii.ssn\","
            + "\"authority\":\"constraint\",\"assurance\":\"enforcement\",\"mandatory\":true,"
            + "\"description\":\"SSN\",\"obligations\":[{\"type\":\"field_redact\"}]}],"
            + "\"assurance_counts\":{\"enforcement\":1},\"document\":{\"api_version\":\"v1\"}}}");
    TypedPolicySystemCorpus system = client.typedPolicies().system();
    assertThat(system.getRoot()).isEqualTo("system");
    assertThat(system.getVersion()).isEqualTo(3);
    assertThat(system.getDigest()).isEqualTo("sha256:system");
    assertThat(system.getControls())
        .extracting(c -> c.getAssurance())
        .containsExactly("enforcement");
    assertThat(system.getAssuranceCounts()).containsEntry("enforcement", 1);
    assertThat(system.getDocument()).containsEntry("api_version", "v1");
  }

  // -------------------------------------------------------------------------
  // Refusals
  // -------------------------------------------------------------------------

  private static String refusal(String reason, String extra) {
    return "{\"success\":false,\"reason\":\""
        + reason
        + "\",\"error\":\"refused: "
        + reason
        + "\""
        + extra
        + "}";
  }

  private void call(String operation) {
    switch (operation) {
      case "publish":
        client.typedPolicies().publish(DOCUMENT, FIXTURES);
        break;
      case "validate":
        client.typedPolicies().validate(DOCUMENT, FIXTURES);
        break;
      case "activate":
        client.typedPolicies().activate(DIGEST, null);
        break;
      case "edition":
        client.typedPolicies().edition();
        break;
      default:
        throw new IllegalArgumentException(operation);
    }
  }

  @Test
  @DisplayName("every documented refusal is typed")
  void everyRefusalIsTyped() {
    String approver =
        "{\"code\":\"APPROVER_IS_AUTHOR\",\"severity\":\"reject\","
            + "\"summary\":\"the author may not approve their own publication\"}";
    Object[][] cases = {
      {
        "publish",
        "POST",
        422,
        "publication_refused",
        null,
        ",\"findings\":[" + approver + "]",
        Map.of(),
        List.of("APPROVER_IS_AUTHOR"),
        null
      },
      {
        "publish",
        "POST",
        422,
        "document_refused",
        null,
        ",\"findings\":[]",
        Map.of(),
        List.of(),
        null
      },
      {
        "publish",
        "POST",
        402,
        "tier_limit",
        "ERR_TIER_LIMIT_ORG_ROOT_POLICY",
        ",\"code\":\"ERR_TIER_LIMIT_ORG_ROOT_POLICY\"",
        Map.of(),
        List.of(),
        null
      },
      {
        "publish",
        "POST",
        402,
        "tier_limit",
        "ERR_TIER_LIMIT_ORG_ROOT_POLICY",
        ",\"code\":\"ERR_TIER_LIMIT_ORG_ROOT_POLICY\"",
        Map.of("Retry-After", "30"),
        List.of(),
        30
      },
      {"publish", "POST", 429, "artifact_cap", null, "", Map.of(), List.of(), null},
      {"publish", "POST", 400, "document_id_required", null, "", Map.of(), List.of(), null},
      {"activate", "POST", 409, "activation_refused", null, "", Map.of(), List.of(), null},
      {"validate", "POST", 503, "catalog_not_configured", null, "", Map.of(), List.of(), null},
      {"edition", "GET", 404, "no_such_endpoint", null, "", Map.of(), List.of(), null},
    };
    for (Object[] c : cases) {
      String operation = (String) c[0];
      int status = (Integer) c[2];
      String reason = (String) c[3];
      @SuppressWarnings("unchecked")
      Map<String, String> headers = (Map<String, String>) c[6];
      @SuppressWarnings("unchecked")
      List<String> findings = (List<String>) c[7];
      reset();
      answering((String) c[1], "/" + operation, status, refusal(reason, (String) c[5]), headers);
      assertThatThrownBy(() -> call(operation))
          .as("%s %d %s", operation, status, reason)
          .isInstanceOfSatisfying(
              TypedPolicyRefusalException.class,
              e -> {
                assertThat(e.getStatus()).isEqualTo(status);
                assertThat(e.getReason()).isEqualTo(reason);
                assertThat(e.getCode()).isEqualTo(c[4]);
                assertThat(e.getMessage()).isEqualTo("refused: " + reason);
                assertThat(
                        e.getFindings().stream()
                            .map(AuthoringFinding::getCode)
                            .collect(Collectors.toList()))
                    .isEqualTo(findings);
                assertThat(e.getRetryAfter()).isEqualTo(c[8]);
              });
    }
  }

  @Test
  @DisplayName("a 401 is the client's AuthenticationException, carrying the platform's text")
  void a401IsAuthentication() {
    answering("GET", "/system", 401, refusal("org_not_stamped", ""));
    assertThatThrownBy(() -> client.typedPolicies().system())
        .isInstanceOf(AuthenticationException.class)
        .isNotInstanceOf(TypedPolicyRefusalException.class)
        .hasMessageContaining("refused: org_not_stamped");
  }

  @Test
  @DisplayName("a refusal without a JSON body still names its status")
  void aRefusalWithoutJson() {
    stubFor(
        get(urlEqualTo(ROUTE + "/edition"))
            .willReturn(aResponse().withStatus(502).withBody("bad gateway")));
    assertThatThrownBy(() -> client.typedPolicies().edition())
        .isInstanceOfSatisfying(
            TypedPolicyRefusalException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(502);
              assertThat(e.getReason()).isNull();
              assertThat(e.getMessage()).isEqualTo("HTTP 502 from /edition");
            });
  }

  @Test
  @DisplayName("a success whose body is not an object is an error")
  void aNonObjectSuccess() {
    answering("GET", "/edition", 200, "[\"not\",\"an\",\"object\"]");
    answering("GET", "/active", 200, "[1]");
    assertThatThrownBy(() -> client.typedPolicies().edition())
        .isInstanceOf(AxonFlowException.class)
        .hasMessageContaining("not an object");
    assertThatThrownBy(() -> client.typedPolicies().active())
        .isInstanceOf(AxonFlowException.class)
        .hasMessageContaining("not an object");
  }

  // -------------------------------------------------------------------------
  // The platform's null shapes
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("a nil Go collection, sent as JSON null, reads as empty")
  void nullCollectionsReadAsEmpty() {
    answering("POST", "/validate", 200, "{\"success\":true,\"findings\":null}");
    assertThat(client.typedPolicies().validate(DOCUMENT, null).getFindings()).isEmpty();
    answering(
        "POST",
        "/publish",
        200,
        "{\"success\":true,\"digest\":\"sha256:abc\",\"version\":1,\"findings\":null}");
    assertThat(client.typedPolicies().publish(DOCUMENT, FIXTURES).getFindings()).isEmpty();
    answering(
        "GET",
        "/edition",
        200,
        "{\"success\":true,\"constructs\":{\"edition\":\"community\","
            + "\"obligation_families\":null,\"attribute_namespaces\":null}}");
    TypedAuthoringEdition edition = client.typedPolicies().edition();
    assertThat(edition.getConstructs().getObligationFamilies()).isEmpty();
    assertThat(edition.getConstructs().getAttributeNamespaces()).isEmpty();
    answering(
        "GET",
        "/system",
        200,
        "{\"success\":true,\"system\":{\"root\":\"system\",\"controls\":null,"
            + "\"assurance_counts\":null,\"document\":null}}");
    TypedPolicySystemCorpus system = client.typedPolicies().system();
    assertThat(system.getControls()).isEmpty();
    assertThat(system.getAssuranceCounts()).isEmpty();
    assertThat(system.getDocument()).isEmpty();
    answering("GET", "/system", 200, "{\"success\":true,\"system\":null}");
    assertThat(client.typedPolicies().system().getControls()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // The namespace
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("the namespace is built once per client, and a derived client has its own")
  void theNamespace() {
    assertThat(client.typedPolicies()).isSameAs(client.typedPolicies());
    assertThat(client.asUser("someone").typedPolicies()).isNotSameAs(client.typedPolicies());
  }

  @Test
  @DisplayName("a derived client's identity reaches all six routes, and the parent's does not")
  void aDerivedClientsIdentity() {
    answering("GET", "/edition", 200, "{\"success\":true}");
    answering("POST", "/validate", 200, "{\"success\":true}");
    answering("POST", "/publish", 200, "{\"success\":true,\"digest\":\"sha256:abc\"}");
    answering("POST", "/activate", 200, "{\"success\":true}");
    answering("GET", "/active", 404, "{}");
    answering("GET", "/system", 200, "{\"success\":true}");
    Consumer<AxonFlow> everyRoute =
        c -> {
          c.typedPolicies().edition();
          c.typedPolicies().validate(DOCUMENT, null);
          c.typedPolicies().publish(DOCUMENT, FIXTURES);
          c.typedPolicies().activate(DIGEST, null);
          c.typedPolicies().active();
          c.typedPolicies().system();
        };
    everyRoute.accept(client.asUser("user-jwt"));
    for (String route : List.of("/edition", "/active", "/system")) {
      verify(
          getRequestedFor(urlEqualTo(ROUTE + route))
              .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo("user-jwt")));
    }
    for (String route : List.of("/validate", "/publish", "/activate")) {
      verify(
          postRequestedFor(urlEqualTo(ROUTE + route))
              .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo("user-jwt")));
    }
    resetAllRequests();
    everyRoute.accept(client);
    assertThat(findAll(anyRequestedFor(urlMatching(ROUTE + "/.*"))))
        .hasSize(6)
        .allSatisfy(r -> assertThat(r.containsHeader(ReadIdentity.HEADER_USER_TOKEN)).isFalse());
  }

  @Test
  @DisplayName("no typed policy route receives the PEP capability handshake")
  void noHandshake() {
    answering("POST", "/validate", 200, "{\"success\":true}");
    PEPHandshake declared =
        PEPHandshake.of(
            "request-path",
            "https://pep.example.test",
            List.of(PEPCapability.of(AuthZENObligationType.FIELD_REDACT, 1)));
    AxonFlow declaring =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .endpoint(baseUrl)
                .clientId("test-client")
                .clientSecret("test-secret")
                .pepHandshake(declared)
                .build());
    declaring.typedPolicies().validate(DOCUMENT, null);
    declaring.withPEPHandshake(declared).typedPolicies().validate(DOCUMENT, null);
    verify(
        2,
        postRequestedFor(urlEqualTo(ROUTE + "/validate"))
            .withHeader(PEPHandshake.HEADER, absent()));
  }
}
