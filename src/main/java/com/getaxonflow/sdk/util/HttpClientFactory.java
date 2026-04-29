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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
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
        configureInsecureSsl(builder);
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
}
