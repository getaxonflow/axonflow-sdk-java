/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.authzen.AuthZENResponse;
import com.getaxonflow.sdk.exceptions.ReadScopeException;
import com.getaxonflow.sdk.identity.ReadIdentity;
import com.getaxonflow.sdk.identity.ReadScope;
import com.getaxonflow.sdk.types.*;
import com.getaxonflow.sdk.types.ClientRequest;
import com.getaxonflow.sdk.types.RequestType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Read-path per-user identity (X-User-Token) and the read-scope contract (platform #2922).
 *
 * <p>Companion to {@link ReadIdentity}.
 */
@WireMockTest
@DisplayName("Read-path identity (#2922)")
class ReadIdentityTest {

  /** Distinctive on purpose: the leak tests grep whole strings for it. */
  private static final String TEST_TOKEN = "eyJhbGciOiJIUzI1NiJ9.SENTINEL-USER-TOKEN-a7f3c91e.sig";

  private static final String ROW_PAGE =
      "{\"decisions\":[{\"decision_id\":\"d1\",\"timestamp\":\"2026-04-17T12:00:00Z\","
          + "\"decision\":\"blocked\"}]}";
  private static final String EXPLANATION =
      "{\"decision_id\":\"d1\",\"timestamp\":\"2026-04-17T12:00:00Z\",\"decision\":\"blocked\"}";

  private String baseUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    baseUrl = wmRuntimeInfo.getHttpBaseUrl();
  }

  private AxonFlow client(String userToken) {
    return AxonFlow.create(
        AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .clientId("org")
            .clientSecret("secret")
            .userToken(userToken)
            .build());
  }

  // ========================================================================
  // Option plumbing: present when configured, absent when not
  // ========================================================================

  @Test
  @DisplayName("a client with no identity sends no identity header at all")
  void headerAbsentWhenNotConfigured() {
    stubFor(get(urlPathEqualTo("/api/v1/decisions")).willReturn(okJson(ROW_PAGE)));

    client(null).listDecisions(null);

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions"))
            .withoutHeader(ReadIdentity.HEADER_USER_TOKEN));
  }

  @Test
  @DisplayName("a client-wide identity travels on the reads AND on a non-read method")
  void clientLevelTokenTravels() {
    // listConnectors is NOT a scoped read, and that is the point: the agent
    // validates this header on every route it proxies, so a stale token breaks
    // it too. The docstrings say so; this makes it a checked claim.
    stubFor(get(urlPathEqualTo("/api/v1/decisions")).willReturn(okJson(ROW_PAGE)));
    stubFor(get(urlPathEqualTo("/api/v1/decisions/d1/explain")).willReturn(okJson(EXPLANATION)));
    stubFor(get(urlPathEqualTo("/api/v1/connectors")).willReturn(okJson("{\"connectors\":[]}")));

    AxonFlow axonflow = client(TEST_TOKEN);
    axonflow.listDecisions(null);
    axonflow.explainDecision("d1");
    try {
      axonflow.listConnectors();
    } catch (RuntimeException ignored) {
      // The shape of the connectors response is not what this test is about;
      // the header assertion below is.
    }

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN)));
    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions/d1/explain"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN)));
    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/connectors"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN)));
  }

  @Test
  @DisplayName("a per-call identity overrides the client-wide one")
  void perCallOverridesClientLevel() {
    stubFor(get(urlPathEqualTo("/api/v1/decisions/d1/explain")).willReturn(okJson(EXPLANATION)));

    client("client-level").explainDecision("d1", TEST_TOKEN);

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions/d1/explain"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN)));
  }

  @Test
  @DisplayName("an explicitly empty per-call identity clears the client-wide one")
  void perCallEmptyClearsClientLevel() {
    // Falling back would make the option unable to express the very state the
    // platform treats as distinct (ReadScope.NONE).
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                okJson("{\"decisions\":[]}")
                    .withHeader(ReadIdentity.HEADER_READ_SCOPE, "own-rows")));

    client(TEST_TOKEN).listDecisions(null, "   ");

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions"))
            .withoutHeader(ReadIdentity.HEADER_USER_TOKEN));
  }

  @Test
  @DisplayName("the token is trimmed")
  void tokenIsTrimmed() {
    stubFor(get(urlPathEqualTo("/api/v1/decisions/d1/explain")).willReturn(okJson(EXPLANATION)));

    client("  " + TEST_TOKEN + "\n").explainDecision("d1");

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions/d1/explain"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN)));
  }

  // ========================================================================
  // asUser — a derived client must own its identity
  // ========================================================================

  @Test
  @DisplayName("asUser reaches every method, including ones with no per-call overload")
  void asUserReachesEveryMethod() {
    stubFor(get(urlPathEqualTo("/api/v1/connectors")).willReturn(okJson("{\"connectors\":[]}")));

    AxonFlow admin = client("ADMIN-TOKEN");
    try {
      admin.asUser("ALICE-TOKEN").listConnectors();
    } catch (RuntimeException ignored) {
      // See clientLevelTokenTravels.
    }

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/connectors"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo("ALICE-TOKEN")));
  }

  @Test
  @DisplayName("asUser does not mutate the client it was derived from")
  void asUserDoesNotMutateParent() {
    stubFor(get(urlPathEqualTo("/api/v1/decisions")).willReturn(okJson(ROW_PAGE)));

    AxonFlow admin = client("ADMIN-TOKEN");
    admin.asUser("ALICE-TOKEN");
    admin.listDecisions(null);

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo("ADMIN-TOKEN")));
  }

  @Test
  @DisplayName("asUser with no token presents no identity at all")
  void asUserWithNoTokenPresentsNothing() {
    stubFor(get(urlPathEqualTo("/api/v1/decisions")).willReturn(okJson(ROW_PAGE)));

    client(TEST_TOKEN).asUser("").listDecisions(null);

    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions"))
            .withoutHeader(ReadIdentity.HEADER_USER_TOKEN));
  }

  @Test
  @DisplayName("a derived client carries every other config value across")
  void asUserCarriesTheRestOfTheConfig() {
    // A derivation that quietly lost a timeout, a TLS setting or the retry
    // policy would be a much larger change than "act as this person".
    AxonFlowConfig original =
        AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .clientId("org")
            .clientSecret("secret")
            .userToken("ADMIN")
            .timeout(java.time.Duration.ofSeconds(17))
            .mapTimeout(java.time.Duration.ofSeconds(99))
            .debug(true)
            .build();
    AxonFlowConfig derived = original.toBuilder().userToken("ALICE").build();

    assertThat(derived.getUserToken()).isEqualTo("ALICE");
    assertThat(derived.getClientId()).isEqualTo(original.getClientId());
    assertThat(derived.getClientSecret()).isEqualTo(original.getClientSecret());
    assertThat(derived.getTimeout()).isEqualTo(original.getTimeout());
    assertThat(derived.getMapTimeout()).isEqualTo(original.getMapTimeout());
    assertThat(derived.isDebug()).isEqualTo(original.isDebug());
    assertThat(derived.getEndpoint()).isEqualTo(original.getEndpoint());
  }

  // ========================================================================
  // The credential goes to the header and nowhere else
  // ========================================================================

  @Test
  @DisplayName("the identity is not carried in an error message, even when the body echoes it")
  void tokenNotInErrorMessage() {
    // The strongest form of the mistake: the natural implementation puts the
    // response body into the error verbatim.
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions/d1/explain"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withHeader(ReadIdentity.HEADER_READ_SCOPE, "own-rows")
                    .withBody("{\"error\":\"not found\",\"echo\":\"" + TEST_TOKEN + "\"}")));

    Throwable thrown = catchThrowable(() -> client(TEST_TOKEN).explainDecision("d1"));

    // Precondition: the header DID carry it, or the assertion below is vacuous.
    verify(
        getRequestedFor(urlPathEqualTo("/api/v1/decisions/d1/explain"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN)));
    assertThat(thrown).isInstanceOf(ReadScopeException.class);
    assertThat(thrown.getMessage()).doesNotContain(TEST_TOKEN);
  }

  @Test
  @DisplayName("the identity never appears in the config's toString")
  void configToStringHidesCredentials() {
    // A config object reaches log lines, exception messages and debugger
    // frames; a credential that rides along has left the process in all of
    // them. Both credentials are asserted together — the read-path identity is
    // one of the same class, and marking one while forgetting the other is the
    // likely failure.
    AxonFlowConfig config =
        AxonFlowConfig.builder()
            .endpoint(baseUrl)
            .clientId("org")
            .clientSecret("SECRET-VALUE")
            .userToken("TOKEN-VALUE")
            .build();

    assertThat(config.toString()).doesNotContain("SECRET-VALUE");
    assertThat(config.toString()).doesNotContain("TOKEN-VALUE");
    // ...and still renders something, or this passes by rendering nothing.
    assertThat(config.toString()).contains("org");
  }

  @Test
  @DisplayName("a cross-origin redirect delivers NO credential to the far end")
  void noCredentialSurvivesACrossOriginRedirect() {
    // Through TWO REAL SERVERS and OkHttp's own follower, not a hand-rolled
    // Chain. The previous version drove a fake Chain, which meant the decision
    // that actually carries the property — addNetworkInterceptor rather than
    // addInterceptor — was never exercised: an application interceptor runs
    // once, BEFORE redirects, and that mutant survived all 33 tests while a
    // real redirect delivered the identity cross-origin. A test that rebuilds a
    // production object tests a lookalike.
    WireMockServer elsewhere = new WireMockServer(options().dynamicPort());
    elsewhere.start();
    try {
      elsewhere.stubFor(
          get(urlPathEqualTo("/api/v1/decisions")).willReturn(okJson("{\"decisions\":[]}")));
      stubFor(
          get(urlPathEqualTo("/api/v1/decisions"))
              .willReturn(
                  aResponse()
                      .withStatus(302)
                      .withHeader("Location", elsewhere.baseUrl() + "/api/v1/decisions")));

      try {
        client(TEST_TOKEN).listDecisions(null);
      } catch (RuntimeException ignored) {
        // The far end's response shape is not what this test is about.
      }

      // Precondition: the ORIGIN request carried them all, or the assertions
      // below are vacuous.
      verify(
          getRequestedFor(urlPathEqualTo("/api/v1/decisions"))
              .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN))
              .withHeader("Authorization", matching("Basic .*")));
      // Precondition: the redirect was actually FOLLOWED, or "no credential
      // arrived" is true of a request that never happened.
      elsewhere.verify(getRequestedFor(urlPathEqualTo("/api/v1/decisions")));

      for (String credential :
          new String[] {
            "Authorization", ReadIdentity.HEADER_USER_TOKEN, "X-Client-ID", "X-Axonflow-Client"
          }) {
        elsewhere.verify(
            getRequestedFor(urlPathEqualTo("/api/v1/decisions")).withoutHeader(credential));
      }
    } finally {
      elsewhere.stop();
    }
  }

  @Test
  @DisplayName("a same-HOST but different-PORT redirect is also off-origin")
  void aDifferentPortIsADifferentOrigin() {
    // OkHttp compares only the HOST for its own sensitive-header stripping, so
    // a port change forwards Authorization. The rule here is stricter on
    // purpose: a different port is a different service, and an identity
    // assertion should not have a "close enough".
    WireMockServer otherPort = new WireMockServer(options().dynamicPort());
    otherPort.start();
    try {
      otherPort.stubFor(
          get(urlPathEqualTo("/api/v1/decisions")).willReturn(okJson("{\"decisions\":[]}")));
      stubFor(
          get(urlPathEqualTo("/api/v1/decisions"))
              .willReturn(
                  aResponse()
                      .withStatus(302)
                      .withHeader("Location", otherPort.baseUrl() + "/api/v1/decisions")));

      try {
        client(TEST_TOKEN).listDecisions(null);
      } catch (RuntimeException ignored) {
        // See above.
      }

      otherPort.verify(getRequestedFor(urlPathEqualTo("/api/v1/decisions")));
      otherPort.verify(
          getRequestedFor(urlPathEqualTo("/api/v1/decisions"))
              .withoutHeader(ReadIdentity.HEADER_USER_TOKEN));
      otherPort.verify(
          getRequestedFor(urlPathEqualTo("/api/v1/decisions")).withoutHeader("Authorization"));
    } finally {
      otherPort.stop();
    }
  }

  @Test
  @DisplayName("every credential SURVIVES a same-origin redirect")
  void credentialsSurviveASameOriginRedirect() {
    // The other failure direction: stripping too eagerly turns an ordinary
    // redirect into an unauthenticated request.
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                aResponse().withStatus(302).withHeader("Location", "/api/v1/decisions/p2")));
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions/p2"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo(TEST_TOKEN))
            .withHeader("Authorization", matching("Basic .*"))
            .willReturn(okJson(ROW_PAGE)));

    assertThat(client(TEST_TOKEN).listDecisions(null)).hasSize(1);
  }

  // ========================================================================
  // The read outcomes
  // ========================================================================

  @ParameterizedTest(name = "explain: {0}")
  @CsvSource({
    "no identity resolved,          404, none,         true,  true",
    "not among this identity rows,  404, own-rows,     true,  false",
    "tenant-wide caller a real miss, 404, tenant,      false, false",
    "a scope this build does not know, 404, segment-rows, false, false",
    "a server fault under a scoped read, 500, none,     false, false",
  })
  void explainScopeSurfacing(
      String name, int status, String scope, boolean wantTyped, boolean wantMissing) {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions/dec-1/explain"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withHeader(ReadIdentity.HEADER_READ_SCOPE, scope)
                    .withBody("{\"error\":\"Decision not found or past retention window\"}")));

    Throwable thrown = catchThrowable(() -> client(null).explainDecision("dec-1"));

    assertThat(thrown).isNotNull();
    if (!wantTyped) {
      assertThat(thrown).isNotInstanceOf(ReadScopeException.class);
      return;
    }
    assertThat(thrown).isInstanceOf(ReadScopeException.class);
    ReadScopeException scoped = (ReadScopeException) thrown;
    assertThat(scoped.isIdentityMissing()).isEqualTo(wantMissing);
    assertThat(scoped.getScope().value()).isEqualTo(scope);
    assertThat(scoped.getIdentifier()).isEqualTo("dec-1");
  }

  @Test
  @DisplayName("explain: a pre-#2922 platform states no scope, so nothing is diagnosed")
  void explainWithNoScopeHeader() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions/dec-1/explain"))
            .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"not found\"}")));

    assertThat(catchThrowable(() -> client(null).explainDecision("dec-1")))
        .isNotInstanceOf(ReadScopeException.class);
  }

  @Test
  @DisplayName("list: an empty page under scope none is refused")
  void listEmptyUnderNoneIsRefused() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                okJson("{\"decisions\":[]}").withHeader(ReadIdentity.HEADER_READ_SCOPE, "none")));

    Throwable thrown = catchThrowable(() -> client(null).listDecisions(null));

    assertThat(thrown).isInstanceOf(ReadScopeException.class);
    ReadScopeException scoped = (ReadScopeException) thrown;
    assertThat(scoped.isIdentityMissing()).isTrue();
    // The platform answered successfully; it is the SCOPE that makes the page
    // meaningless.
    assertThat(scoped.getStatusCode()).isEqualTo(200);
  }

  @ParameterizedTest(name = "list: an honestly-empty read under scope {0} is not an error")
  @ValueSource(strings = {"own-rows", "tenant", "segment-rows"})
  void listLegitimateEmptyIsNotAnError(String scope) {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                okJson("{\"decisions\":[]}").withHeader(ReadIdentity.HEADER_READ_SCOPE, scope)));

    assertThat(client(null).listDecisions(null)).isEmpty();
  }

  @Test
  @DisplayName("list: a populated page is never discarded on the strength of a header")
  void listPopulatedIsNeverRefused() {
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(okJson(ROW_PAGE).withHeader(ReadIdentity.HEADER_READ_SCOPE, "none")));

    assertThat(client(null).listDecisions(null)).hasSize(1);
  }

  @ParameterizedTest(name = "the scope {0} is matched case-insensitively")
  @ValueSource(strings = {"none", "None", "NONE", " none "})
  void scopeMatchedCaseInsensitively(String spelling) {
    // A scope spelled `None` degrading to "no opinion" would restore the
    // vacuous empty list — too quiet a failure to leave to a constant staying
    // put.
    stubFor(
        get(urlPathEqualTo("/api/v1/decisions"))
            .willReturn(
                okJson("{\"decisions\":[]}").withHeader(ReadIdentity.HEADER_READ_SCOPE, spelling)));

    assertThat(catchThrowable(() -> client(null).listDecisions(null)))
        .isInstanceOf(ReadScopeException.class);
  }

  @ParameterizedTest(name = "audit reads refuse an empty {0} under scope none")
  @ValueSource(strings = {"[]", "{\"entries\":[],\"total\":0}"})
  void auditReadsRefuseEmptyUnderNone(String body) {
    stubFor(
        post(urlPathEqualTo("/api/v1/audit/search"))
            .willReturn(okJson(body).withHeader(ReadIdentity.HEADER_READ_SCOPE, "none")));
    stubFor(
        get(urlPathMatching("/api/v1/audit/tenant/.*"))
            .willReturn(okJson(body).withHeader(ReadIdentity.HEADER_READ_SCOPE, "none")));

    AxonFlow axonflow = client(null);
    assertThat(catchThrowable(axonflow::searchAuditLogs)).isInstanceOf(ReadScopeException.class);
    assertThat(catchThrowable(() -> axonflow.getAuditLogsByTenant("t1")))
        .isInstanceOf(ReadScopeException.class);
  }

  @Test
  @DisplayName("audit reads: an honestly-empty own-rows page is not an error")
  void auditReadsLegitimateEmpty() {
    stubFor(
        post(urlPathEqualTo("/api/v1/audit/search"))
            .willReturn(okJson("[]").withHeader(ReadIdentity.HEADER_READ_SCOPE, "own-rows")));

    assertThat(client(null).searchAuditLogs().getEntries()).isEmpty();
  }

  @Test
  @DisplayName("the own-rows message reports the SCOPE, not a claim about what exists")
  void ownRowsMessageDoesNotClaimTheRowExists() {
    ReadScopeException notYours = new ReadScopeException(ReadScope.OWN_ROWS, 404, "decision", "d1");
    assertThat(notYours.isIdentityMissing()).isFalse();
    assertThat(notYours.getMessage()).doesNotContain("resolved no per-user identity");
    // It must not assert the row exists and is someone else's — the platform
    // answers "not yours" and "not there" identically, on purpose.
    assertThat(notYours.getMessage()).contains("not there at all");

    ReadScopeException missing = new ReadScopeException(ReadScope.NONE, 404, "decision", null);
    assertThat(missing.getMessage()).contains("userToken");
    assertThat(missing.getMessage()).contains("@axonflow.local");
  }

  @Test
  @DisplayName("absent is not none, and an unrecognised scope round-trips verbatim")
  void absentIsNotNone() {
    assertThat(ReadScope.of(null)).isEqualTo(ReadScope.ABSENT);
    assertThat(ReadScope.ABSENT).isNotEqualTo(ReadScope.NONE);
    assertThat(ReadScope.of("segment-rows").value()).isEqualTo("segment-rows");
    assertThat(ReadScope.of("  TENANT  ")).isEqualTo(ReadScope.TENANT);
  }

  // ========================================================================
  // A derived client shares the cache — so the key must know the identity
  // ========================================================================

  @Test
  @DisplayName("two derived clients do not share a cached response")
  void twoDerivedClientsDoNotShareACachedResponse() {
    // The Rust sibling shipped this as a live cross-user data leak once its
    // identity started riding /api/request. Here it is LATENT rather than live:
    // that handler sits outside proxyAuthMiddleware and resolves the caller from
    // the BODY token, which is already in the key. Fixed anyway, for two
    // reasons. asUser's javadoc promises that no path widens back to the
    // process's own identity, and a cache that answers one caller with another's
    // response is exactly that widening. And "latent" here means "depends on a
    // platform routing decision this SDK does not control" — the day
    // /api/request moves behind the middleware, the leak is live and nothing in
    // this repo changed.
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(okJson("{\"success\":true,\"data\":{\"content\":\"answer\"}}")));

    AxonFlow base = client(null);
    String query = "the same question from two people";

    base.asUser("ALICE-TOKEN")
        .proxyLLMCall(
            ClientRequest.builder()
                .userToken("")
                .query(query)
                .requestType(RequestType.MCP_QUERY)
                .build());
    base.asUser("BOB-TOKEN")
        .proxyLLMCall(
            ClientRequest.builder()
                .userToken("")
                .query(query)
                .requestType(RequestType.MCP_QUERY)
                .build());

    List<LoggedRequest> seen = findAll(postRequestedFor(urlEqualTo("/api/request")));
    List<String> identities =
        seen.stream()
            .map(
                r ->
                    r.getHeader(ReadIdentity.HEADER_USER_TOKEN) == null
                        ? "NO IDENTITY"
                        : r.getHeader(ReadIdentity.HEADER_USER_TOKEN))
            .toList();

    assertThat(seen)
        .as(
            "two identities asking the same question must produce TWO governed requests. One means"
                + " the second caller was served the FIRST caller's response from a shared cache,"
                + " without the platform evaluating anything on their behalf. Identities seen: %s",
            identities)
        .hasSize(2);
    assertThat(identities).contains("ALICE-TOKEN", "BOB-TOKEN");
  }

  @Test
  @DisplayName("one identity asking twice still hits the cache")
  void oneIdentityAskingTwiceStillHitsTheCache() {
    // The other failure direction: a key that never matches is a disabled cache
    // wearing a fix's name, and it would satisfy the test above on its own.
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(okJson("{\"success\":true,\"data\":{\"content\":\"answer\"}}")));

    AxonFlow alice = client(null).asUser("ALICE-TOKEN");
    for (int i = 0; i < 2; i++) {
      alice.proxyLLMCall(
          ClientRequest.builder()
              .userToken("")
              .query("one question")
              .requestType(RequestType.MCP_QUERY)
              .build());
    }

    assertThat(findAll(postRequestedFor(urlEqualTo("/api/request"))))
        .as("the same identity asking the same question twice must be served from the cache")
        .hasSize(1);
  }

  // ========================================================================
  // One transport site
  // ========================================================================

  @Test
  @DisplayName("the header is spelled once and stamped at one site")
  void oneTransportSite() throws Exception {
    // Deliberately wider than the one spelling the fix uses: a guard is only as
    // wide as the syntax it matches, and there are several ways to write a
    // header onto an OkHttp request.
    // `add` and `set` are Headers.Builder's spellings, and `add` is the one
    // that does NOT replace — a second stamp written with it would put TWO
    // X-User-Token values on the wire, which is worse than the duplicate this
    // guard exists to catch. A census that knew only Request.Builder's two
    // verbs would have read that as clean.
    // The constant may be written QUALIFIED (`ReadIdentity.HEADER_USER_TOKEN`,
    // or fully package-qualified). A pattern anchored on the bare name reads a
    // qualified stamping site as clean — verified: it did, on a planted one.
    Pattern setter =
        Pattern.compile(
            "\\.(header|addHeader|addUnsafeNonAscii|add|set)\\(\\s*"
                + "([A-Za-z0-9_.]*\\bHEADER_USER_TOKEN\\b|\"[Xx]-[Uu]ser-[Tt]oken\")");
    Pattern literal = Pattern.compile("\"X-User-Token\"", Pattern.CASE_INSENSITIVE);

    List<String> setters = new ArrayList<>();
    List<String> literals = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        // Flattened rather than scanned per LINE. google-java-format wraps a
        // long call across lines, so `.header(` and the constant naming the
        // header land on different lines and no single line carries both — a
        // second stamping site then reads as clean, and the guard's coverage
        // depends on how long the surrounding identifiers happen to be.
        //
        // Comments are dropped first: the claim is about CODE. The header is
        // named in prose in several javadocs on purpose, and counting those
        // would make the guard fail for being well documented, which teaches
        // the next author to delete the explanation rather than the duplicate.
        StringBuilder flat = new StringBuilder();
        List<Integer> lineOf = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        boolean inBlockComment = false;
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i).trim();
          if (inBlockComment) {
            int close = line.indexOf("*/");
            if (close < 0) {
              continue;
            }
            line = line.substring(close + 2);
            inBlockComment = false;
          }
          int open = line.indexOf("/*");
          if (open >= 0) {
            int close = line.indexOf("*/", open + 2);
            if (close < 0) {
              inBlockComment = true;
              line = line.substring(0, open);
            } else {
              line = line.substring(0, open) + line.substring(close + 2);
            }
          }
          int lineComment = line.indexOf("//");
          if (lineComment >= 0) {
            line = line.substring(0, lineComment);
          }
          for (char c : line.toCharArray()) {
            if (Character.isWhitespace(c)) {
              continue;
            }
            flat.append(c);
            lineOf.add(i + 1);
          }
        }

        String text = flat.toString();
        String folded = text.toLowerCase(java.util.Locale.ROOT);

        // Every verb that writes a header, each paired with the header it names
        // IN THE SAME MATCH so the two cannot be satisfied by unrelated
        // statements. `add` matters as much as `header`: on Headers.Builder it
        // does NOT replace, so a second stamp written with it puts TWO values
        // on the wire.
        for (String verb : List.of(".header(", ".addheader(", ".add(", ".set(", ".headers(")) {
          int from = 0;
          while (true) {
            int at = folded.indexOf(verb, from);
            if (at < 0) {
              break;
            }
            from = at + verb.length();
            // The argument list, bounded so a match cannot reach past the call
            // it belongs to.
            String window = folded.substring(from, Math.min(from + 160, folded.length()));
            String arg = window.contains(",") ? window.substring(0, window.indexOf(',')) : window;
            if (arg.contains("header_user_token") || arg.contains("\"x-user-token\"")) {
              setters.add(path + ":" + lineOf.get(at));
            }
          }
        }

        int from = 0;
        while (true) {
          int at = folded.indexOf("\"x-user-token\"", from);
          if (at < 0) {
            break;
          }
          from = at + 1;
          literals.add(path + ":" + lineOf.get(at));
        }
      }
    }

    assertThat(setters)
        .as(
            "X-User-Token must be stamped at exactly one site — the platform reads it once in its"
                + " proxy middleware, not per route, so a per-method sprinkle here is a second copy"
                + " of a decision made in one place on both sides")
        .hasSize(1);
    assertThat(literals)
        .as("the literal belongs in the HEADER_USER_TOKEN constant alone")
        .hasSize(1);
  }

  // ========================================================================
  // Round 2: a derived client owns every client-bound member, the AuthZEN
  // reader does not coerce, and the refusal survives serialization
  // ========================================================================

  @Test
  @DisplayName("a derived client rebuilds every member that holds a client reference")
  void aDerivedClientRebuildsEveryClientBoundMember() throws Exception {
    // The mutant `this.masfeatNamespace = parent.masfeatNamespace` SURVIVED the
    // round-1 suite: the derivation constructor was right and nothing checked
    // it. A structural argument is testimony until something can fail.
    AxonFlow parent = client("ADMIN-TOKEN");
    // Bound on the PARENT first — the only ordering in which the bug appears,
    // and exactly the ordering a long-lived gateway has.
    Object parentNamespace = parent.masfeat();
    AxonFlow derived = parent.asUser("ALICE-TOKEN");

    assertThat(derived.masfeat())
        .as(
            "the derived client inherited the parent's namespace, which still calls through the"
                + " parent and therefore under the PARENT's identity")
        .isNotSameAs(parentNamespace);

    // The census behind the fix: ANY field whose declared type is a member
    // class of AxonFlow holds a client reference, so none may be shared.
    // Naming only masfeatNamespace would rot the moment a second is added.
    for (java.lang.reflect.Field field : AxonFlow.class.getDeclaredFields()) {
      if (field.getType().getEnclosingClass() != AxonFlow.class) {
        continue;
      }
      field.setAccessible(true);
      assertThat(field.get(derived))
          .as(
              "%s holds a reference to the client that created it and must be rebuilt on"
                  + " derivation, not copied",
              field.getName())
          .isNotSameAs(field.get(parent));
    }
  }

  @Test
  @DisplayName("a derived client's plan transport is its own, not the parent's")
  void aDerivedClientHasItsOwnPlanTransport() throws Exception {
    // The plan client is built from the DERIVED httpClient, so it carries the
    // derived identity. Building it from the parent's survived round 1.
    stubFor(
        get(urlPathMatching("/api/v1/plan/.*"))
            .willReturn(okJson("{\"plan_id\":\"p1\",\"status\":\"completed\"}")));

    try {
      client("ADMIN-TOKEN").asUser("ALICE-TOKEN").getPlanStatus("p1");
    } catch (RuntimeException ignored) {
      // The response shape is not what this test is about; the header is.
    }

    verify(
        getRequestedFor(urlPathMatching("/api/v1/plan/.*"))
            .withHeader(ReadIdentity.HEADER_USER_TOKEN, equalTo("ALICE-TOKEN")));
  }

  @Test
  @DisplayName("the AuthZEN reader refuses a coerced scalar")
  void theAuthzenReaderDoesNotCoerceScalars() throws Exception {
    // Superseded by the two CsvSource tests below, which vary the payload
    // rather than asserting one entry. Kept because it is the one that pins the
    // OTHER failure direction on a well-formed body.

    // Without the coercion settings Jackson REPAIRS type errors: a decision
    // arriving as the string "true" was read as the boolean true, and an
    // obligation whose `mandatory` arrived as 1 was read as true — on exactly
    // the members that decide whether an unsupported obligation must DENY.
    java.lang.reflect.Field readerField = AxonFlow.class.getDeclaredField("authzenReader");
    readerField.setAccessible(true);
    com.fasterxml.jackson.databind.ObjectMapper reader =
        (com.fasterxml.jackson.databind.ObjectMapper) readerField.get(client(null));

    assertThatThrownBy(
            () ->
                reader.readValue(
                    "{\"decision\":\"true\"}", com.getaxonflow.sdk.authzen.AuthZENResponse.class))
        .as("a decision arriving as a STRING must be refused, not coerced")
        .isInstanceOf(Exception.class);

    // The other failure direction: strictness that refuses valid payloads is an
    // outage, not a guard.
    assertThat(
            reader
                .readValue(
                    "{\"decision\":false}", com.getaxonflow.sdk.authzen.AuthZENResponse.class)
                .getDecision())
        .isFalse();
  }

  /**
   * Every scalar shape the reader must REFUSE, one row each.
   *
   * <p>The single-assertion version of this test pinned {@code "decision":"true"} alone, and that
   * was not a strictness gate: re-enabling coercion for everything except that one entry left
   * {@code "mandatory":1}, {@code "decision":1} and {@code "quorum":"3"} accepted with the whole
   * suite green. A guard that names one member certifies that member, not the class.
   *
   * <p>{@code quorum: 2.7} is the row that found a real hole: {@code
   * ALLOW_COERCION_OF_SCALARS=false} does NOT cover Float -> Integer, so it decoded as 2 — silently
   * discarding the fraction on a count of required approvers.
   */
  @ParameterizedTest(name = "refused: {0}")
  @CsvSource({
    "'{\"decision\":\"true\"}', a decision as a STRING",
    "'{\"decision\":1}', a decision as an INTEGER",
    "'{\"decision\":\"false\"}', a false decision as a STRING",
    "'{\"context\":{\"obligations\":[{\"type\":\"log\",\"mandatory\":1}]}}', mandatory as an INTEGER",
    "'{\"context\":{\"obligations\":[{\"type\":\"log\",\"mandatory\":\"true\"}]}}', mandatory as a STRING",
    "'{\"context\":{\"approval\":{\"all_of\":[{\"quorum\":\"3\"}]}}}', quorum as a STRING",
    "'{\"context\":{\"approval\":{\"all_of\":[{\"quorum\":2.7}]}}}', quorum as a FLOAT",
    "'{\"context\":{\"approval\":{\"all_of\":[{\"quorum\":true}]}}}', quorum as a BOOLEAN"
  })
  void theAuthzenReaderRefusesEveryCoercibleScalarShape(String body, String description)
      throws Exception {
    assertThatThrownBy(() -> authzenReader().readValue(body, AuthZENResponse.class))
        .as(
            "%s must be REFUSED, not repaired into a reading nobody sent: these are the members"
                + " that decide whether an unsupported obligation denies",
            description)
        .isInstanceOf(Exception.class);
  }

  /**
   * The other failure direction, one row each: strictness that refuses well-formed payloads is an
   * outage, not a guard.
   *
   * <p>Without this set, the refusal test above is satisfied by a reader that refuses everything.
   */
  @ParameterizedTest(name = "accepted: {0}")
  @CsvSource({
    "'{\"decision\":true}', a decision as a real boolean",
    "'{\"decision\":false}', a false decision as a real boolean",
    "'{\"context\":{\"obligations\":[{\"type\":\"log\",\"mandatory\":true}]}}', mandatory as a real boolean",
    "'{\"context\":{\"approval\":{\"all_of\":[{\"quorum\":3}]}}}', quorum as a real integer"
  })
  void theAuthzenReaderStillAcceptsWellFormedScalars(String body, String description)
      throws Exception {
    assertThatCode(() -> authzenReader().readValue(body, AuthZENResponse.class))
        .as("%s is valid and must decode", description)
        .doesNotThrowAnyException();
  }

  private com.fasterxml.jackson.databind.ObjectMapper authzenReader() throws Exception {
    java.lang.reflect.Field readerField = AxonFlow.class.getDeclaredField("authzenReader");
    readerField.setAccessible(true);
    return (com.fasterxml.jackson.databind.ObjectMapper) readerField.get(client(null));
  }

  @Test
  @DisplayName("the refusal survives serialization with its diagnosis intact")
  void theRefusalSurvivesSerialization() throws Exception {
    // A `transient` scope made isIdentityMissing() report FALSE and getScope()
    // null at the far end — a confidently wrong answer from the type whose
    // whole job is to stop confidently wrong answers.
    ReadScopeException original = new ReadScopeException(ReadScope.NONE, 200, "decisions", null);

    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(original);
    }
    ReadScopeException restored;
    try (java.io.ObjectInputStream in =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (ReadScopeException) in.readObject();
    }

    assertThat(restored.getScope()).isEqualTo(ReadScope.NONE);
    assertThat(restored.isIdentityMissing()).isTrue();
    assertThat(restored.getStatusCode()).isEqualTo(200);
    assertThat(restored.getMessage()).isEqualTo(original.getMessage());
  }
}
