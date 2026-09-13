// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.authzen.Attribute;
import com.getaxonflow.sdk.authzen.AuthZENAction;
import com.getaxonflow.sdk.authzen.AuthZENBulk;
import com.getaxonflow.sdk.authzen.AuthZENContract;
import com.getaxonflow.sdk.authzen.AuthZENEvaluation;
import com.getaxonflow.sdk.authzen.AuthZENObligationType;
import com.getaxonflow.sdk.authzen.AuthZENRequest;
import com.getaxonflow.sdk.authzen.AuthZENResource;
import com.getaxonflow.sdk.authzen.AuthZENSubject;
import com.getaxonflow.sdk.exceptions.PEPHandshakeException;
import com.getaxonflow.sdk.types.ConnectorQuery;
import com.getaxonflow.sdk.types.DecideRequest;
import com.getaxonflow.sdk.types.DecideResponse;
import com.getaxonflow.sdk.types.DecisionTarget;
import com.getaxonflow.sdk.types.Obligation;
import com.getaxonflow.sdk.types.ObligationFulfillment;
import com.getaxonflow.sdk.types.PEPCapability;
import com.getaxonflow.sdk.types.PEPHandshake;
import com.getaxonflow.sdk.types.PolicyApprovalRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The PEP capability handshake: the declaration, its bytes, and where it goes.
 *
 * <ol>
 *   <li>PARITY. A declaration encodes to exactly the bytes the platform's reference encoder
 *       produces ({@link PEPHandshakeGolden}), and one the platform would refuse fails here, naming
 *       the same member.
 *   <li>PLACEMENT. Every request of every method whose route reads the declaration carries exactly
 *       the cached header value, and no other route receives it.
 *   <li>DERIVATION. {@code withPEPHandshake} replaces the declaration on the derived client only,
 *       and {@code asUser} keeps the declaration it derives from.
 * </ol>
 */
@WireMockTest
@DisplayName("v11.0.0: the PEP capability handshake")
class PEPHandshakeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String CHECK_INPUT_PATH = "/api/v1/mcp/check-input";
  private static final String CHECK_OUTPUT_PATH = "/api/v1/mcp/check-output";
  private static final String PRE_CHECK_PATH = "/api/policy/pre-check";

  private String baseUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wm) {
    baseUrl = wm.getHttpBaseUrl();
    stubFor(
        post(urlPathEqualTo(Pep.DECIDE_PATH))
            .willReturn(
                okJson(
                    "{\"verdict\":\"allow\",\"decision_id\":\"dec-1\",\"trace_id\":\"t-1\","
                        + "\"obligations\":[],\"evaluated_policies\":[],\"stage\":\"tool\"}")));
    stubFor(
        post(urlPathEqualTo(AxonFlow.AUTHZEN_PATH))
            .willReturn(
                okJson(
                    "{\"decision\":true,\"context\":{\"profile\":\""
                        + AuthZENContract.PROFILE_V1
                        + "\",\"state\":\"ALLOW\",\"category\":\"allowed\","
                        + "\"reason\":\"permitted\",\"decision_id\":\"dec-1\","
                        + "\"schema_version\":\"2026-08-29\"}}")));
    stubFor(
        post(urlPathEqualTo(CHECK_INPUT_PATH))
            .willReturn(
                okJson(
                    "{\"allowed\":true,\"policies_evaluated\":1,\"redacted\":true,"
                        + "\"redacted_statement\":\"email [REDACTED]\","
                        + "\"redaction_evaluated\":true}")));
    stubFor(
        post(urlPathEqualTo(CHECK_OUTPUT_PATH))
            .willReturn(okJson("{\"allowed\":true,\"policies_evaluated\":1}")));
    stubFor(
        post(urlPathEqualTo(PRE_CHECK_PATH))
            .willReturn(okJson("{\"context_id\":\"ctx-1\",\"approved\":true,\"policies\":[]}")));
    stubFor(
        post(urlPathEqualTo("/api/request"))
            .willReturn(okJson("{\"success\":true,\"data\":{\"rows\":[]}}")));
    stubFor(
        post(urlPathEqualTo("/mcp/resources/query"))
            .willReturn(okJson("{\"success\":true,\"data\":[]}")));
    stubFor(get(urlPathEqualTo("/api/v1/static-policies")).willReturn(okJson("{\"policies\":[]}")));
  }

  // -------------------------------------------------------------------------
  // Fixtures
  // -------------------------------------------------------------------------

  private static PEPCapability cap(AuthZENObligationType type, int version) {
    return PEPCapability.of(type, version);
  }

  private static PEPHandshake declared() {
    return PEPHandshake.of(
        "request-path",
        "https://pep.example.test",
        List.of(cap(AuthZENObligationType.FIELD_REDACT, 1)));
  }

  private static PEPHandshake override() {
    return PEPHandshake.of(
        "response-path",
        "https://pep.example.test",
        List.of(cap(AuthZENObligationType.FIELD_MASK, 1)));
  }

  private AxonFlow client(PEPHandshake pepHandshake) {
    return AxonFlow.create(
        AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .clientId("test-client")
            .clientSecret("test-secret")
            .pepHandshake(pepHandshake)
            .build());
  }

  private static String document(PEPHandshake h) {
    return new String(Base64.getUrlDecoder().decode(h.headerValue()), StandardCharsets.UTF_8);
  }

  private static void assertRefused(Runnable build, String pointer) {
    assertThatThrownBy(build::run)
        .isInstanceOfSatisfying(
            PEPHandshakeException.class,
            e -> {
              assertThat(e.getPointer()).isEqualTo(pointer);
              assertThat(e.getMessage())
                  .startsWith(
                      PEPHandshake.HEADER + ": " + (pointer.isEmpty() ? "" : pointer + ": "));
            });
  }

  private static AuthZENRequest anEvaluation() {
    return AuthZENEvaluation.of(
            new AuthZENSubject("gateway", "llm-gateway-01"),
            new AuthZENAction("llm.completion"),
            new AuthZENResource("llm", "llm"))
        .query(Attribute.known("hello"))
        .build();
  }

  /** One call of a public method, and the path its request goes to. */
  private static final class Call {
    final String name;
    final Runnable run;
    final String path;

    Call(String name, Runnable run, String path) {
      this.name = name;
      this.run = run;
      this.path = path;
    }
  }

  /** Every method whose route reads the declaration, sync and async, aliases included. */
  private static List<Call> planeCalls(AxonFlow c) {
    DecideRequest decide =
        DecideRequest.builder("tool", "look up the weather")
            .target(new DecisionTarget("tool", null, null, "search"))
            .build();
    DecideResponse redacting =
        new DecideResponse(
            "allow",
            "dec-1",
            "trace-1",
            null,
            List.of(
                new Obligation(
                    "redact_pii",
                    null,
                    new ObligationFulfillment(CHECK_INPUT_PATH, "POST", "request", null))),
            List.of("sys_pii_email"),
            "tool",
            null,
            null);
    PolicyApprovalRequest preCheck =
        PolicyApprovalRequest.builder().userToken("user-token").query("hello").build();
    Map<String, Object> message = Map.of("message", "hello");
    return List.of(
        new Call("decide", () -> c.decide(decide), Pep.DECIDE_PATH),
        new Call("decideAsync", () -> c.decideAsync(decide).join(), Pep.DECIDE_PATH),
        new Call("decideAndFulfill", () -> c.decideAndFulfill(decide), Pep.DECIDE_PATH),
        new Call(
            "decideAndFulfillAsync", () -> c.decideAndFulfillAsync(decide).join(), Pep.DECIDE_PATH),
        new Call(
            "fulfillRequest",
            () -> c.fulfillRequest(redacting, "email jane.doe@example.com"),
            CHECK_INPUT_PATH),
        new Call("evaluate", () -> c.evaluate(anEvaluation()), AxonFlow.AUTHZEN_PATH),
        new Call(
            "evaluateAll",
            () -> c.evaluateAll(new AuthZENBulk(List.of(anEvaluation()))),
            AxonFlow.AUTHZEN_PATH),
        new Call("mcpCheckInput", () -> c.mcpCheckInput("postgres", "SELECT 1"), CHECK_INPUT_PATH),
        new Call(
            "mcpCheckInputAsync",
            () -> c.mcpCheckInputAsync("postgres", "SELECT 1").join(),
            CHECK_INPUT_PATH),
        new Call(
            "checkToolInput", () -> c.checkToolInput("postgres", "SELECT 1"), CHECK_INPUT_PATH),
        new Call(
            "mcpCheckOutput", () -> c.mcpCheckOutput("postgres", null, message), CHECK_OUTPUT_PATH),
        new Call(
            "mcpCheckOutputAsync",
            () -> c.mcpCheckOutputAsync("postgres", null, message).join(),
            CHECK_OUTPUT_PATH),
        new Call(
            "checkToolOutput",
            () -> c.checkToolOutput("postgres", null, message),
            CHECK_OUTPUT_PATH),
        new Call(
            "getPolicyApprovedContext", () -> c.getPolicyApprovedContext(preCheck), PRE_CHECK_PATH),
        new Call("preCheck", () -> c.preCheck(preCheck), PRE_CHECK_PATH),
        new Call(
            "getPolicyApprovedContextAsync",
            () -> c.getPolicyApprovedContextAsync(preCheck).join(),
            PRE_CHECK_PATH));
  }

  /** The header each request to {@code path} carried, "(none)" where it carried none. */
  private static List<String> sentTo(String path) {
    return findAll(anyRequestedFor(urlPathEqualTo(path))).stream()
        .map(
            r ->
                r.containsHeader(PEPHandshake.HEADER) ? r.getHeader(PEPHandshake.HEADER) : "(none)")
        .collect(Collectors.toList());
  }

  /** Runs each call alone and asserts its one request carried {@code expected}. */
  private static void assertEachCallSends(List<Call> calls, String expected) {
    for (Call call : calls) {
      resetAllRequests();
      call.run.run();
      assertThat(sentTo(call.path)).as(call.name).containsExactly(expected);
    }
  }

  // -------------------------------------------------------------------------
  // 1. Parity with the platform
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("the bytes are the platform encoder's")
  class Encoding {

    @Test
    @DisplayName("every golden vector encodes to the platform's header")
    void everyGoldenVector() {
      for (PEPHandshakeGolden.Vector v : PEPHandshakeGolden.VECTORS) {
        assertThat(PEPHandshake.of(v.pepId, v.audience, v.capabilities).headerValue())
            .as(v.name)
            .isEqualTo(v.header);
      }
    }

    @Test
    @DisplayName("64 capabilities encode under the byte cap, to the platform's bytes")
    void sixtyFourCapabilities() throws Exception {
      List<String> kinds = new ArrayList<>(AuthZENObligationType.KNOWN_WIRE_VALUES);
      Collections.sort(kinds);
      List<PEPCapability> capabilities = new ArrayList<>();
      for (int version = 1; version <= 5; version++) {
        for (String kind : kinds) {
          capabilities.add(cap(AuthZENObligationType.of(kind), version));
        }
      }
      String header =
          PEPHandshake.of("p", "a", capabilities.subList(0, PEPHandshake.MAX_CAPABILITIES))
              .headerValue();
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(header.getBytes(StandardCharsets.US_ASCII));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      assertThat(header).hasSize(PEPHandshakeGolden.SIXTY_FOUR_LENGTH);
      assertThat(hex.toString()).isEqualTo(PEPHandshakeGolden.SIXTY_FOUR_SHA256);
    }

    @Test
    @DisplayName("a document past the byte cap is refused as a whole")
    void pastTheByteCap() {
      List<PEPCapability> capabilities = new ArrayList<>();
      for (int i = 0; i < PEPHandshake.MAX_CAPABILITIES; i++) {
        capabilities.add(cap(AuthZENObligationType.STEP_UP_AUTHENTICATION, 1_000_000_000 + i));
      }
      assertRefused(() -> PEPHandshake.of("p", "a", capabilities), "");
      assertThatThrownBy(() -> PEPHandshake.of("p", "a", capabilities))
          .hasMessageStartingWith(PEPHandshake.HEADER + ": encodes to ");
    }

    @Test
    @DisplayName("the order of declaration does not change the bytes")
    void orderIndependent() {
      List<PEPCapability> given =
          List.of(
              cap(AuthZENObligationType.NOTIFICATION, 3),
              cap(AuthZENObligationType.FIELD_REDACT, 2),
              cap(AuthZENObligationType.APPROVAL_CHALLENGE, 1));
      List<PEPCapability> reversed = new ArrayList<>(given);
      Collections.reverse(reversed);
      PEPHandshake one = PEPHandshake.of("gw", "a", given);
      assertThat(one.headerValue()).isEqualTo(PEPHandshake.of("gw", "a", reversed).headerValue());
      assertThat(one.getCapabilities())
          .containsExactly(
              cap(AuthZENObligationType.APPROVAL_CHALLENGE, 1),
              cap(AuthZENObligationType.FIELD_REDACT, 2),
              cap(AuthZENObligationType.NOTIFICATION, 3));
    }

    @Test
    @DisplayName("the header is unpadded base64url of the canonical document")
    void theDocument() {
      PEPHandshake h =
          PEPHandshake.of(
              "gw",
              "https://pep.example.test",
              List.of(
                  cap(AuthZENObligationType.FIELD_REDACT, 2),
                  cap(AuthZENObligationType.FIELD_REDACT, 1)));
      assertThat(h.headerValue()).doesNotContain("=", "+", "/");
      assertThat(document(h))
          .isEqualTo(
              "{\"profile_version\":1,\"pep_id\":\"gw\",\"audience\":\"https://pep.example.test\","
                  + "\"capabilities\":[{\"type\":\"field_redact\",\"version\":1},"
                  + "{\"type\":\"field_redact\",\"version\":2}]}");
    }

    @Test
    @DisplayName("an empty capability list is a declaration")
    void emptyIsADeclaration() throws Exception {
      JsonNode doc = MAPPER.readTree(document(PEPHandshake.of("gw", "a", List.of())));
      assertThat(doc.get("capabilities").isArray()).isTrue();
      assertThat(doc.get("capabilities")).isEmpty();
    }

    @Test
    @DisplayName("the caller's list is copied, and the declaration's list is unmodifiable")
    void theListIsCopied() {
      List<PEPCapability> given =
          new ArrayList<>(List.of(cap(AuthZENObligationType.FIELD_REDACT, 1)));
      PEPHandshake h = PEPHandshake.of("gw", "a", given);
      String before = h.headerValue();
      given.set(0, cap(AuthZENObligationType.FIELD_MASK, 1));
      assertThat(h.getCapabilities()).containsExactly(cap(AuthZENObligationType.FIELD_REDACT, 1));
      assertThat(document(h)).isEqualTo(document(PEPHandshake.of("gw", "a", h.getCapabilities())));
      assertThat(h.headerValue()).isEqualTo(before);
      assertThatThrownBy(() -> h.getCapabilities().add(cap(AuthZENObligationType.FIELD_MASK, 1)))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("what the platform refuses is refused here, naming the member")
  class Refusals {

    @Test
    @DisplayName("identifiers")
    void identifiers() {
      String[][] cases = {
        {"", "a", "/pep_id"},
        {"Gateway", "a", "/pep_id"},
        {"client:gw", "a", "/pep_id"},
        {"-gw", "a", "/pep_id"},
        {"gw\n", "a", "/pep_id"},
        {"g".repeat(129), "a", "/pep_id"},
        {"gw", "", "/audience"},
        {"gw", "/aud", "/audience"},
        {"gw", "a b", "/audience"},
        {"gw", "aud\n", "/audience"},
        {"gw", "a".repeat(129), "/audience"},
      };
      for (String[] c : cases) {
        assertRefused(() -> PEPHandshake.of(c[0], c[1], List.of()), c[2]);
      }
      assertRefused(() -> PEPHandshake.of(null, "a", List.of()), "/pep_id");
      assertRefused(() -> PEPHandshake.of("gw", null, List.of()), "/audience");
    }

    @Test
    @DisplayName("the capability list")
    void capabilityList() {
      List<PEPCapability> sixtyFive = new ArrayList<>();
      for (int version = 1; version <= 65; version++) {
        sixtyFive.add(cap(AuthZENObligationType.FIELD_REDACT, version));
      }
      assertRefused(() -> PEPHandshake.of("gw", "a", null), "/capabilities");
      assertRefused(() -> PEPHandshake.of("gw", "a", sixtyFive), "/capabilities");
      assertRefused(
          () ->
              PEPHandshake.of(
                  "gw",
                  "a",
                  List.of(
                      cap(AuthZENObligationType.FIELD_REDACT, 1),
                      cap(AuthZENObligationType.FIELD_REDACT, 1))),
          "/capabilities");
      assertRefused(
          () ->
              PEPHandshake.of(
                  "gw", "a", Arrays.asList(cap(AuthZENObligationType.FIELD_REDACT, 1), null)),
          "/capabilities");
    }

    @Test
    @DisplayName("a capability")
    void capability() {
      for (String kind : new String[] {"redact_pii", "Field_Redact", ""}) {
        assertRefused(() -> PEPCapability.of(AuthZENObligationType.of(kind), 1), "/capabilities");
      }
      assertRefused(() -> PEPCapability.of(null, 1), "/capabilities");
      assertRefused(() -> PEPCapability.of(AuthZENObligationType.FIELD_REDACT, 0), "/capabilities");
      assertRefused(
          () -> PEPCapability.of(AuthZENObligationType.FIELD_REDACT, -1), "/capabilities");
    }

    @Test
    @DisplayName("what the platform accepts is accepted")
    void accepted() {
      assertThatCode(() -> PEPHandshake.of("g".repeat(128), "A".repeat(128), List.of()))
          .doesNotThrowAnyException();
      assertThatCode(() -> PEPHandshake.of("gw.request-1", "https://api.example.com/v1", List.of()))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the admitted obligation types are exactly the vendored platform contract's")
    void admittedTypesArePinned() throws Exception {
      JsonNode surface =
          MAPPER.readTree(Files.readAllBytes(Paths.get("testdata/authzen-surface.json")));
      List<String> contract = new ArrayList<>();
      for (JsonNode e : surface.get("enums")) {
        if ("obligation_type".equals(e.get("name").asText())) {
          e.get("values").forEach(v -> contract.add(v.asText()));
        }
      }
      assertThat(contract).as("the artifact's obligation_type enum").isNotEmpty();
      assertThat(AuthZENObligationType.KNOWN_WIRE_VALUES).isEqualTo(contract);
      for (String kind : contract) {
        assertThat(PEPCapability.of(AuthZENObligationType.of(kind), 1).getType().value())
            .isEqualTo(kind);
      }
    }
  }

  // -------------------------------------------------------------------------
  // 2. Placement
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("where the declaration goes")
  class Placement {

    @Test
    @DisplayName("every plane request carries exactly the cached header value")
    void everyPlaneRequest() {
      PEPHandshake h = declared();
      assertEachCallSends(planeCalls(client(h)), h.headerValue());
    }

    @Test
    @DisplayName("a client with no declaration sends none")
    void noDeclaration() {
      assertEachCallSends(planeCalls(client(null)), "(none)");
    }

    @Test
    @DisplayName("no other route receives it")
    void noOtherRoute() {
      AxonFlow c = client(declared());
      resetAllRequests();
      c.queryConnector(
          ConnectorQuery.builder().connectorId("postgres").operation("select 1").build());
      c.mcpQuery("postgres", "select 1");
      c.listStaticPolicies();
      for (String path :
          List.of("/api/request", "/mcp/resources/query", "/api/v1/static-policies")) {
        assertThat(sentTo(path)).as(path).containsExactly("(none)");
      }
    }
  }

  // -------------------------------------------------------------------------
  // 3. Derivation
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("a derived client")
  class Derivation {

    @Test
    @DisplayName("withPEPHandshake on a client with none sends it; the parent still sends none")
    void onAClientWithNone() {
      AxonFlow parent = client(null);
      PEPHandshake h = override();
      assertEachCallSends(planeCalls(parent.withPEPHandshake(h)), h.headerValue());
      assertEachCallSends(planeCalls(parent), "(none)");
    }

    @Test
    @DisplayName("withPEPHandshake replaces the declaration on the derived client only")
    void replacesOnTheDerivedClientOnly() {
      PEPHandshake d = declared();
      PEPHandshake o = override();
      AxonFlow parent = client(d);
      assertEachCallSends(planeCalls(parent.withPEPHandshake(o)), o.headerValue());
      assertEachCallSends(planeCalls(parent), d.headerValue());
    }

    @Test
    @DisplayName("asUser on a client with a declaration keeps sending it")
    void asUserKeepsIt() {
      PEPHandshake d = declared();
      assertEachCallSends(planeCalls(client(d).asUser("someone")), d.headerValue());
    }

    @Test
    @DisplayName("withPEPHandshake(null) is refused")
    void nullRefused() {
      AxonFlow parent = client(declared());
      assertThatThrownBy(() -> parent.withPEPHandshake(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // Unused-import guard for LoggedRequest: findAll returns it.
  @SuppressWarnings("unused")
  private static final Class<LoggedRequest> LOGGED = LoggedRequest.class;
}
