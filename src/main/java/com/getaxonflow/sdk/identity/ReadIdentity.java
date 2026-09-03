/* Copyright 2026 AxonFlow */
package com.getaxonflow.sdk.identity;

import com.getaxonflow.sdk.exceptions.ReadScopeException;
import java.io.IOException;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Read-path per-user identity and the platform's read-scope contract (platform #2922).
 *
 * <p>Since #2922 the role-scoped read routes (audit / decisions / overrides) answer from the
 * identity the CALLER presents, not from the tenant credential alone. The tenant credential in
 * {@code Authorization} says which organization is asking; it does not say WHO. A caller that
 * presents no per-user identity to an enterprise stack is not "a caller who sees everything" and is
 * not "a caller who sees nothing by coincidence" — it is a caller the platform cannot scope, and
 * every scoped read it makes returns zero rows by construction.
 */
public final class ReadIdentity {

  /**
   * The request header carrying the per-user identity.
   *
   * <p>This constant is the SDK's only spelling of it. The header is set in exactly one place —
   * {@link #interceptor} — and if you find yourself setting it in a method, the method is the wrong
   * altitude.
   */
  public static final String HEADER_USER_TOKEN = "X-User-Token";

  /** The response header the platform stamps on scoped reads. */
  public static final String HEADER_READ_SCOPE = "X-Axonflow-Read-Scope";

  private ReadIdentity() {}

  /**
   * The SDK's one identity site: an OkHttp NETWORK interceptor that stamps the per-user identity on
   * every request bound for the configured endpoint.
   *
   * <p>A <em>network</em> interceptor rather than an application one, deliberately. Network
   * interceptors run once per HOP, including every redirect OkHttp follows, so the origin check
   * below is re-evaluated on each. An application interceptor runs once, before redirects, and the
   * header would then ride the follow-up request to a host the caller never named. That is not
   * hypothetical: OkHttp strips {@code Authorization} across a host change and its list is fixed —
   * measured in the sibling SDKs, a redirect target saw the tenant credential dropped and the
   * per-user one intact.
   *
   * <p>The identity is therefore sent to the configured endpoint's origin and nowhere else. Scheme,
   * host AND port must match; subdomains are not trusted, because this header is an identity
   * assertion, not a session cookie, and "close enough" is not a property an identity should have.
   *
   * <p><b>The header is NOT inert on the routes that are not reads.</b> It is validated on every
   * route the agent proxies: {@code proxyAuthMiddleware} resolves it before dispatch and answers
   * {@code 401 invalid user token} for a present-but-INVALID one — on {@code /api/v1/plans}, {@code
   * /api/v1/policies}, {@code /api/v1/connectors}, {@code /api/v1/process}, {@code
   * /api/v1/budgets}, {@code /api/v1/cost}, {@code /api/v1/executions} and the rest, not only on
   * the scoped reads. So a stale or rotated token does not degrade to "unscoped reads"; it turns
   * {@code listConnectors}, {@code installConnector} and policy CRUD into 401s. Fail-closed is the
   * right direction, but it puts the value in the same rotation story as the client secret.
   *
   * <p>Genuinely inert only on the routes the agent SERVES ITSELF — only {@code proxy.go} and
   * {@code mcp_identity.go} read the header at all: {@code /api/request}, {@code /api/v1/decide}
   * (whose identity comes from the request BODY's {@code user_token}, which is the whole reason the
   * read path needed a surface of its own), {@code /api/v1/access/evaluation}, {@code
   * /api/v1/static-policies/*}, {@code /api/v1/circuit-breaker/*}, {@code /api/v1/hitl/*}, {@code
   * /api/v1/mcp/check-input}, {@code /api/v1/mcp/check-output}, {@code /api/v1/register}, {@code
   * /api/policy/pre-check}, {@code /api/audit/llm-call} and {@code /health}.
   *
   * <p>The token is a CREDENTIAL. It is written to the header and nowhere else: never logged, never
   * carried in an exception message, and never reaching telemetry — the heartbeat builds its own
   * client and never passes through this chain.
   *
   * @param endpoint the configured endpoint; the identity is sent to this origin and no other
   * @param token supplies the identity, re-read per request so a derived client can carry its own
   * @return the interceptor to install on the client
   */
  public static IdentityInterceptor interceptor(String endpoint, Supplier<String> token) {
    return new IdentityInterceptor(endpoint, token);
  }

  /**
   * A NAMED type rather than a lambda, so a derived client can find and replace it.
   *
   * <p>{@code AxonFlow.asUser} shares the parent's connection pool via {@code newBuilder()} and
   * must swap this interceptor for one bound to the derived config. A lambda cannot be identified
   * in the interceptor list, so the derived client would have kept the PARENT's identity and {@code
   * asUser} would have silently done nothing — which is exactly the bug the Python sibling shipped
   * and its test caught.
   */
  public static final class IdentityInterceptor implements Interceptor {
    private final HttpUrl configured;
    private final Supplier<String> token;

    IdentityInterceptor(String endpoint, Supplier<String> token) {
      this.configured = endpoint == null ? null : HttpUrl.parse(endpoint);
      this.token = token;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      String value = token.get();
      String trimmed = value == null ? "" : value.trim();
      boolean offOrigin = configured == null || !sameOrigin(request.url(), configured);
      if (offOrigin) {
        // EVERY credential is dropped, not just the identity. OkHttp follows
        // redirects itself and strips only Authorization on a host change, so
        // X-Client-ID and X-Axonflow-Client would otherwise arrive at a host
        // the caller never named. Authorization is dropped here too, defensively
        // — relying on OkHttp's list to keep covering it is the same bet the
        // TypeScript sibling lost, where a hand-rolled follower dropped only the
        // new header and leaked the tenant secret off-origin.
        Request.Builder stripped = request.newBuilder();
        for (String credential : CREDENTIAL_HEADERS) {
          stripped.removeHeader(credential);
        }
        return chain.proceed(stripped.build());
      }
      if (trimmed.isEmpty()) {
        // Never send an empty header. To the platform a present-but-empty
        // X-User-Token is still an absent one, but sending it advertises an
        // identity mechanism the caller is not using, and it is one refactor
        // away from a present-but-invalid token, which is a hard 401. The
        // removal also makes an explicit per-call clearing actually clear.
        return chain.proceed(request.newBuilder().removeHeader(HEADER_USER_TOKEN).build());
      }
      return chain.proceed(request.newBuilder().header(HEADER_USER_TOKEN, trimmed).build());
    }
  }

  /**
   * Every credential this SDK sends, so an off-origin hop can drop ALL of them.
   *
   * <p>Not just the new one. OkHttp's own follower strips {@code Authorization} on a host change
   * and that list is FIXED: {@code X-Client-ID} and {@code X-Axonflow-Client} are not on it, and
   * they name the caller to whoever receives them. {@code Authorization} is listed here anyway,
   * defensively — a guard that relies on another library's list staying correct is a guard with a
   * dependency nobody is watching.
   */
  private static final java.util.List<String> CREDENTIAL_HEADERS =
      java.util.List.of("Authorization", HEADER_USER_TOKEN, "X-Client-ID", "X-Axonflow-Client");

  private static boolean sameOrigin(HttpUrl a, HttpUrl b) {
    return a.scheme().equals(b.scheme()) && a.host().equals(b.host()) && a.port() == b.port();
  }

  /** The scope the platform reported on a response. */
  public static ReadScope scopeOf(Response response) {
    return response == null ? ReadScope.ABSENT : ReadScope.of(response.header(HEADER_READ_SCOPE));
  }

  /**
   * The typed refusal for a scoped read that came back with nothing, or {@code null} when the scope
   * does not explain the result.
   *
   * <p>{@code null} for {@link ReadScope#TENANT} (the caller could see the whole tenant and it
   * still was not there — a genuine miss), for {@link ReadScope#ABSENT} (the platform did not state
   * a scope; see {@link ReadScope} for why absent is not none), and for any scope value this build
   * does not recognise (a newer platform's; reporting a cause we cannot actually read would be a
   * confident wrong diagnosis).
   */
  public static ReadScopeException scopeErrorFor(
      String resource, String identifier, ReadScope scope, int statusCode) {
    if (ReadScope.NONE.equals(scope) || ReadScope.OWN_ROWS.equals(scope)) {
      return new ReadScopeException(scope, statusCode, resource, identifier);
    }
    return null;
  }

  /**
   * The typed refusal for a scoped read that came back EMPTY under a scope that could not have
   * returned a row; {@code null} in every other case.
   *
   * <p>One helper rather than a check at each read, because "the page is empty and the scope is
   * none" is one rule and the reads that need it decode their body on more than one path each. A
   * rule copied per return site is a rule that ends up applied on some of them.
   *
   * <p>The emptiness guard is as load-bearing as the scope guard: a non-empty page is never turned
   * into an error, whatever the header says. And only {@link ReadScope#NONE} refuses — an own-rows
   * or tenant-wide read that legitimately found nothing is a real answer, and replacing it with an
   * error would swap one wrong report for another.
   */
  public static ReadScopeException refuseVacuousScopedPage(
      Response response, String resource, int rows) {
    if (rows > 0 || !ReadScope.NONE.equals(scopeOf(response))) {
      return null;
    }
    return new ReadScopeException(
        ReadScope.NONE, response == null ? 0 : response.code(), resource, null);
  }
}
