/*
 * Copyright 2026 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.ReadScopeException;
import com.getaxonflow.sdk.identity.ReadIdentity;
import com.getaxonflow.sdk.identity.ReadScope;
import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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
  @DisplayName("the identity is never sent to any origin but the configured endpoint")
  void identityNotSentOffOrigin() {
    // The redirect property, asserted at the stamping site. OkHttp strips
    // Authorization on a host change and its list is fixed; X-User-Token is not
    // on it, and the sibling SDKs measured the per-user credential outliving
    // the tenant one on exactly that hop.
    AxonFlow axonflow = client(TEST_TOKEN);
    okhttp3.Request onOrigin = new okhttp3.Request.Builder().url(baseUrl + "/api/v1/x").build();
    okhttp3.Request offOrigin =
        new okhttp3.Request.Builder().url("http://elsewhere.invalid/api/v1/x").build();

    ReadIdentity.IdentityInterceptor interceptor =
        ReadIdentity.interceptor(baseUrl, () -> TEST_TOKEN);

    assertThat(stampedHeader(interceptor, onOrigin)).isEqualTo(TEST_TOKEN);
    assertThat(stampedHeader(interceptor, offOrigin)).isNull();
    assertThat(axonflow).isNotNull();
  }

  /** Runs one request through the interceptor and reports what it stamped. */
  private static String stampedHeader(
      ReadIdentity.IdentityInterceptor interceptor, okhttp3.Request request) {
    final String[] seen = new String[1];
    okhttp3.Interceptor.Chain chain = new RecordingChain(request, seen);
    try {
      interceptor.intercept(chain);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return seen[0];
  }

  /** A minimal Chain that records the request the interceptor produced. */
  private static final class RecordingChain implements okhttp3.Interceptor.Chain {
    private final okhttp3.Request request;
    private final String[] seen;

    RecordingChain(okhttp3.Request request, String[] seen) {
      this.request = request;
      this.seen = seen;
    }

    @Override
    public okhttp3.Request request() {
      return request;
    }

    @Override
    public okhttp3.Response proceed(okhttp3.Request forwarded) {
      seen[0] = forwarded.header(ReadIdentity.HEADER_USER_TOKEN);
      return new okhttp3.Response.Builder()
          .request(forwarded)
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(okhttp3.ResponseBody.create("{}", okhttp3.MediaType.get("application/json")))
          .build();
    }

    @Override
    public okhttp3.Connection connection() {
      return null;
    }

    @Override
    public okhttp3.Call call() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int connectTimeoutMillis() {
      return 0;
    }

    @Override
    public okhttp3.Interceptor.Chain withConnectTimeout(
        int timeout, java.util.concurrent.TimeUnit unit) {
      return this;
    }

    @Override
    public int readTimeoutMillis() {
      return 0;
    }

    @Override
    public okhttp3.Interceptor.Chain withReadTimeout(
        int timeout, java.util.concurrent.TimeUnit unit) {
      return this;
    }

    @Override
    public int writeTimeoutMillis() {
      return 0;
    }

    @Override
    public okhttp3.Interceptor.Chain withWriteTimeout(
        int timeout, java.util.concurrent.TimeUnit unit) {
      return this;
    }
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
  // One transport site
  // ========================================================================

  @Test
  @DisplayName("the header is spelled once and stamped at one site")
  void oneTransportSite() throws Exception {
    // Deliberately wider than the one spelling the fix uses: a guard is only as
    // wide as the syntax it matches, and there are several ways to write a
    // header onto an OkHttp request.
    Pattern setter =
        Pattern.compile(
            "\\.(header|addHeader)\\(\\s*(HEADER_USER_TOKEN|\"[Xx]-[Uu]ser-[Tt]oken\")");
    Pattern literal = Pattern.compile("\"X-User-Token\"", Pattern.CASE_INSENSITIVE);

    List<String> setters = new ArrayList<>();
    List<String> literals = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i).trim();
          // Comments are excluded: the claim is about CODE. The header is named
          // in prose in several docstrings on purpose, and counting those would
          // make the guard fail for being well documented — which teaches the
          // next author to delete the explanation rather than the duplicate.
          if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
            continue;
          }
          Matcher setterMatch = setter.matcher(line);
          if (setterMatch.find()) {
            setters.add(path + ":" + (i + 1));
          }
          if (literal.matcher(line).find()) {
            literals.add(path + ":" + (i + 1));
          }
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
}
