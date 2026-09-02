/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.getaxonflow.sdk.util;

import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.identity.ReadIdentity;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for creating configured HTTP clients. */
public final class HttpClientFactory {

  private static final Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);

  /**
   * Environment variable that must be explicitly set to {@code "true"} (or {@code "1"}) to allow
   * disabling TLS certificate verification, in addition to {@code insecureSkipVerify(true)} on the
   * config builder. Both the builder flag AND this environment variable are required to activate
   * the insecure path. This is intentional defense-in-depth: a stray builder flag in application
   * code is not sufficient to silently disable TLS validation in production environments.
   */
  static final String INSECURE_TLS_ENV_VAR = "AXONFLOW_INSECURE_TLS";

  /**
   * Common deployment-environment variables inspected by {@link #detectedProductionEnvSignal()}. If
   * any is set to a production token ({@code "prod"}/{@code "production"}, case-insensitive), the
   * insecure TLS path is refused outright as a hard production guard, even when both {@code
   * insecureSkipVerify(true)} and {@value #INSECURE_TLS_ENV_VAR} are set. This is
   * belt-and-suspenders on top of the existing double-gate: the trust-all path is a
   * development-only escape hatch for self-signed certificates and must never run in production,
   * even by operator error.
   */
  private static final String[] DEPLOYMENT_ENV_VARS = {
    "AXONFLOW_ENVIRONMENT",
    "AXONFLOW_ENV",
    "ENVIRONMENT",
    "APP_ENV",
    "APPLICATION_ENV",
    "DEPLOY_ENV",
    "DEPLOYMENT_ENV",
    "STAGE",
    "SPRING_PROFILES_ACTIVE",
    "NODE_ENV",
    "RAILS_ENV",
    "RACK_ENV",
    "DJANGO_ENV",
    "FLASK_ENV",
    "ASPNETCORE_ENVIRONMENT",
  };

  /**
   * Token delimiters used to split a deployment-environment variable's value before matching
   * production tokens. Includes whitespace, comma/semicolon/colon (e.g. {@code
   * SPRING_PROFILES_ACTIVE=prod,metrics}) AND hyphen/underscore/dot/slash so common compound names
   * such as {@code production-us}, {@code prod_west} or {@code us/east/prod} are tokenised
   * correctly.
   */
  private static final String ENV_VALUE_TOKEN_DELIMITERS = "[\\s,;:._/-]+";

  /**
   * Tokens that, when they immediately precede a {@code prod}/{@code production} token, negate it
   * so the guard does NOT trip. This keeps non-production labels such as {@code non-prod}, {@code
   * not-prod} and {@code pre-prod} (which tokenise to {@code [non, prod]} etc.) usable with the
   * development escape hatch.
   */
  private static final Set<String> NEGATION_PREFIXES = Set.of("non", "not", "pre", "no");

  private HttpClientFactory() {
    // Utility class
  }

  /**
   * Creates an OkHttpClient configured according to the SDK configuration.
   *
   * <p>By default, the client uses the JVM's default {@code TrustManager}, which validates server
   * certificates against the system + JDK trust store. Certificate validation is only disabled if
   * {@link AxonFlowConfig#isInsecureSkipVerify()} is {@code true} AND the environment variable
   * {@value #INSECURE_TLS_ENV_VAR} is set to {@code "true"} or {@code "1"}.
   *
   * @param config the SDK configuration
   * @return a configured OkHttpClient
   */
  public static OkHttpClient create(AxonFlowConfig config) {
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .callTimeout(config.getTimeout().toMillis() * 2, TimeUnit.MILLISECONDS);

    if (config.isInsecureSkipVerify()) {
      if (isInsecureTlsEnvVarEnabled()) {
        String productionSignal = detectedProductionEnvSignal();
        if (productionSignal != null) {
          // Hard production guard: refuse to disable TLS verification when a production-like
          // deployment environment is detected, even though both gates were set. Keep the default
          // (verifying) TrustManager in place.
          logger.error(
              "*** SECURITY *** insecureSkipVerify(true) and {}=true were both set, but a "
                  + "production-like deployment environment was detected ({}). REFUSING to disable "
                  + "TLS certificate verification. The insecure trust-all path is a development-only "
                  + "escape hatch for self-signed certificates and MUST NOT run in production. TLS "
                  + "certificate verification REMAINS ENABLED. Unset the production environment "
                  + "signal (or run against a non-production environment) to use insecureSkipVerify.",
              INSECURE_TLS_ENV_VAR,
              productionSignal);
        } else {
          configureInsecureSsl(builder);
        }
      } else {
        logger.warn(
            "insecureSkipVerify(true) was set on AxonFlowConfig but environment variable {} is "
                + "not set to 'true' or '1'. TLS certificate verification REMAINS ENABLED. To "
                + "disable verification (development/self-signed certs only), export {}=true. "
                + "This double-gate is intentional to prevent accidental TLS bypass in production.",
            INSECURE_TLS_ENV_VAR,
            INSECURE_TLS_ENV_VAR);
      }
    }

    if (config.isDebug()) {
      builder.addInterceptor(
          chain -> {
            okhttp3.Request request = chain.request();
            logger.debug("Request: {} {}", request.method(), request.url());
            okhttp3.Response response = chain.proceed(request);
            logger.debug(
                "Response: {} {} ({}ms)",
                response.code(),
                response.message(),
                response.receivedResponseAtMillis() - response.sentRequestAtMillis());
            return response;
          });
    }

    // The read-path per-user identity: the SDK's ONE identity site. A NETWORK
    // interceptor, deliberately — it runs once per HOP, including every redirect
    // OkHttp follows, so the origin check inside is re-evaluated on each. An
    // application interceptor runs once, before redirects, and the header would
    // then ride the follow-up request to a host the caller never named, on
    // exactly the hop where OkHttp drops Authorization. See ReadIdentity.
    builder.addNetworkInterceptor(
        ReadIdentity.interceptor(config.getEndpoint(), config::getUserToken));

    return builder.build();
  }

  @SuppressWarnings({
    "java:S4830",
    "java:S5527"
  }) // Intentionally trusting all certificates when insecureSkipVerify is enabled
  private static void configureInsecureSsl(OkHttpClient.Builder builder) {
    try {
      // CodeQL: java/insecure-trustmanager -- suppressed: opt-in for development/self-signed
      // certificates.
      // This trust manager is only activated when the user explicitly sets insecureSkipVerify=true
      // in AxonFlowConfig. It is never used by default.
      TrustManager[] trustAllCerts =
          new TrustManager[] {
            new X509TrustManager() { // lgtm[java/insecure-trustmanager]
              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // Intentionally empty: trust all client certificates when insecureSkipVerify is
                // enabled
              }

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // Intentionally empty: trust all server certificates when insecureSkipVerify is
                // enabled
              }

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          };

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new SecureRandom());

      builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
      builder.hostnameVerifier(
          (hostname, session) -> true); // lgtm[java/insecure-hostname-verifier]

      logger.warn(
          "*** SECURITY WARNING *** TLS certificate verification is DISABLED for AxonFlow SDK "
              + "HTTP client. Both insecureSkipVerify(true) and {}=true were set. All HTTPS calls "
              + "will accept ANY server certificate, including attacker-presented certificates in "
              + "MITM scenarios. This MUST NOT be used in production. Intended only for local "
              + "development against self-signed certificates.",
          INSECURE_TLS_ENV_VAR);
    } catch (Exception e) {
      logger.error("Failed to configure insecure SSL", e);
    }
  }

  /**
   * Returns {@code true} if the {@value #INSECURE_TLS_ENV_VAR} environment variable is set to
   * {@code "true"} (case-insensitive) or {@code "1"}. Package-private to allow test verification.
   */
  static boolean isInsecureTlsEnvVarEnabled() {
    String value = System.getenv(INSECURE_TLS_ENV_VAR);
    if (value == null) {
      return false;
    }
    return "true".equalsIgnoreCase(value) || "1".equals(value);
  }

  /**
   * Returns a human-readable {@code "VAR=value"} description of the first {@link
   * #DEPLOYMENT_ENV_VARS deployment environment variable} that signals a production-like
   * environment, or {@code null} if none do.
   *
   * <p>A variable signals production when any token of its value (split on whitespace, comma,
   * semicolon, colon, hyphen, underscore, dot or slash) equals (case-insensitively) {@code "prod"}
   * or {@code "production"}. Tokenising on those delimiters handles compound settings such as
   * {@code SPRING_PROFILES_ACTIVE=prod,metrics} and {@code ENVIRONMENT=production-us}. A {@code
   * prod}/{@code production} token immediately preceded by a {@link #NEGATION_PREFIXES negation
   * prefix} (for example {@code non-prod} or {@code pre-prod}) does NOT count, so non-production
   * labels remain usable with the development escape hatch.
   *
   * <p>Package-private to allow test verification.
   */
  static String detectedProductionEnvSignal() {
    for (String var : DEPLOYMENT_ENV_VARS) {
      String value = System.getenv(var);
      if (value == null || value.isEmpty()) {
        continue;
      }
      String[] tokens = value.toLowerCase(Locale.ROOT).split(ENV_VALUE_TOKEN_DELIMITERS);
      for (int i = 0; i < tokens.length; i++) {
        if (!tokens[i].equals("prod") && !tokens[i].equals("production")) {
          continue;
        }
        if (i > 0 && NEGATION_PREFIXES.contains(tokens[i - 1])) {
          // Negated form such as non-prod / pre-prod: not a production signal.
          continue;
        }
        return var + "=" + value;
      }
    }
    return null;
  }

  /**
   * Returns {@code true} if any common deployment environment variable signals a production-like
   * environment. Used as a hard production guard: even when both {@code insecureSkipVerify(true)}
   * and {@value #INSECURE_TLS_ENV_VAR} are set, TLS certificate verification is NOT disabled in a
   * production-like environment. Package-private to allow test verification.
   */
  static boolean isProductionLikeEnvironment() {
    return detectedProductionEnvSignal() != null;
  }
}
