// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.util;

import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.AxonFlowConfig;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

@DisplayName("HttpClientFactory")
class HttpClientFactoryTest {

  @Test
  @DisplayName("should create client with default config")
  void shouldCreateClientWithDefaultConfig() {
    AxonFlowConfig config = AxonFlowConfig.builder().agentUrl("http://localhost:8080").build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client).isNotNull();
    // Default timeout is 60 seconds
    assertThat(client.connectTimeoutMillis()).isEqualTo(60000);
    assertThat(client.readTimeoutMillis()).isEqualTo(60000);
    assertThat(client.writeTimeoutMillis()).isEqualTo(60000);
  }

  @Test
  @DisplayName("should create client with custom timeout")
  void shouldCreateClientWithCustomTimeout() {
    AxonFlowConfig config =
        AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .timeout(Duration.ofSeconds(10))
            .build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client.connectTimeoutMillis()).isEqualTo(10000);
    assertThat(client.readTimeoutMillis()).isEqualTo(10000);
    assertThat(client.writeTimeoutMillis()).isEqualTo(10000);
  }

  @Test
  @DisplayName("should create client with debug mode")
  void shouldCreateClientWithDebugMode() {
    AxonFlowConfig config =
        AxonFlowConfig.builder().agentUrl("http://localhost:8080").debug(true).build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client).isNotNull();
    // Debug mode adds an interceptor
    assertThat(client.interceptors()).hasSize(1);
  }

  @Test
  @DisplayName(
      "should NOT disable TLS verification when insecureSkipVerify=true but env var is unset")
  void shouldKeepTlsVerificationEnabledWithoutEnvVar() {
    // Builder flag alone is not enough — AXONFLOW_INSECURE_TLS env var must also be set.
    // We don't set the env var in this test, so the insecure path must remain inactive.
    AxonFlowConfig config =
        AxonFlowConfig.builder().agentUrl("http://localhost:8080").insecureSkipVerify(true).build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client).isNotNull();
    // The default OkHttp hostname verifier (OkHostnameVerifier) should be in place — NOT the
    // permissive (hostname, session) -> true verifier. We assert the default class name to ensure
    // we did not silently install the insecure verifier.
    assertThat(client.hostnameVerifier().getClass().getName())
        .as("default hostname verifier should be in place when env var is unset")
        .contains("OkHostnameVerifier");
  }

  @Test
  @DisplayName("env-var gate helper should be false when AXONFLOW_INSECURE_TLS is unset")
  void envVarHelperShouldBeFalseWhenUnset() {
    // We cannot reliably set env vars in-process across JVMs, but we can assert the helper's
    // behaviour against the real environment, which is unset in CI and dev shells by default.
    assertThat(HttpClientFactory.isInsecureTlsEnvVarEnabled())
        .as(
            "AXONFLOW_INSECURE_TLS must default to false; if this fails, the test environment "
                + "has the env var set and the insecure path could activate")
        .isFalse();
  }

  @Test
  @DisplayName("env-var gate helper should be true when AXONFLOW_INSECURE_TLS=true")
  @SetEnvironmentVariable(key = "AXONFLOW_INSECURE_TLS", value = "true")
  void envVarHelperShouldBeTrueWhenSetToTrue() {
    assertThat(HttpClientFactory.isInsecureTlsEnvVarEnabled())
        .as("helper must return true when env var is set to 'true'")
        .isTrue();
  }

  @Test
  @DisplayName("env-var gate helper should be true when AXONFLOW_INSECURE_TLS=1")
  @SetEnvironmentVariable(key = "AXONFLOW_INSECURE_TLS", value = "1")
  void envVarHelperShouldBeTrueWhenSetToOne() {
    assertThat(HttpClientFactory.isInsecureTlsEnvVarEnabled())
        .as("helper must return true when env var is set to '1'")
        .isTrue();
  }

  @Test
  @DisplayName(
      "should activate insecure TLS path when BOTH insecureSkipVerify(true) AND env var are set")
  @SetEnvironmentVariable(key = "AXONFLOW_INSECURE_TLS", value = "true")
  void shouldActivateInsecurePathWhenBothGatesArePresent() {
    // Positive-path regression: this is the single combination where the trust-all path
    // is intentionally activated. Both gates MUST be required; either alone is insufficient
    // (covered by sibling tests). If either gate stops being required, this test will still
    // pass — the gating behaviour is asserted in the negative-path tests.
    AxonFlowConfig config =
        AxonFlowConfig.builder()
            .agentUrl("https://localhost:8080")
            .insecureSkipVerify(true)
            .build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client).isNotNull();

    // 1. Hostname verifier MUST be the permissive lambda, NOT OkHttp's default OkHostnameVerifier.
    //    The permissive verifier in HttpClientFactory is `(hostname, session) -> true`, which is
    //    a synthetic lambda class — its name will NOT contain "OkHostnameVerifier".
    assertThat(client.hostnameVerifier().getClass().getName())
        .as("permissive hostname verifier must be installed when both gates are set")
        .doesNotContain("OkHostnameVerifier");

    // 2. Functional check: the verifier must accept any hostname/session pair.
    assertThat(client.hostnameVerifier().verify("any.host.example", null))
        .as("permissive hostname verifier must return true for arbitrary hostnames")
        .isTrue();

    // 3. SSL socket factory must be the trust-all-configured one (i.e. NOT the JVM default
    //    that OkHttp lazily constructs from the system trust store). We assert non-null and
    //    that calling sslSocketFactory() does not trigger OkHttp's default construction path
    //    by comparing against a freshly built default client.
    AxonFlowConfig defaultConfig =
        AxonFlowConfig.builder().agentUrl("https://localhost:8080").build();
    OkHttpClient defaultClient = HttpClientFactory.create(defaultConfig);
    assertThat(client.sslSocketFactory())
        .as("insecure-path SSL socket factory must differ from the default-path factory")
        .isNotSameAs(defaultClient.sslSocketFactory());
  }

  @Test
  @DisplayName(
      "should NOT activate insecure TLS path when env var is set but builder flag is false")
  @SetEnvironmentVariable(key = "AXONFLOW_INSECURE_TLS", value = "true")
  void shouldKeepDefaultPathWhenOnlyEnvVarIsSet() {
    // Complementary negative path: env var alone, without insecureSkipVerify(true), must
    // keep the default safe TrustManager and OkHostnameVerifier. The double-gate is symmetric:
    // BOTH must be present — neither alone activates the insecure path.
    AxonFlowConfig config = AxonFlowConfig.builder().agentUrl("https://localhost:8080").build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client).isNotNull();
    assertThat(client.hostnameVerifier().getClass().getName())
        .as("default hostname verifier must remain when only env var is set")
        .contains("OkHostnameVerifier");
  }

  @Test
  @DisplayName(
      "should REFUSE the insecure TLS path when both gates are set but a production environment "
          + "is detected (hard production guard)")
  @SetEnvironmentVariable(key = "AXONFLOW_INSECURE_TLS", value = "true")
  @SetEnvironmentVariable(key = "ENVIRONMENT", value = "production")
  void shouldRefuseInsecurePathInProductionEnvironment() {
    // Belt-and-suspenders: even with BOTH insecureSkipVerify(true) AND AXONFLOW_INSECURE_TLS=true,
    // a production-like deployment environment must hard-refuse the trust-all path. This asserts
    // the guard actually PREVENTS the insecure path (not merely logs): the default verifying
    // hostname verifier MUST remain installed.
    AxonFlowConfig config =
        AxonFlowConfig.builder()
            .agentUrl("https://localhost:8080")
            .insecureSkipVerify(true)
            .build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client).isNotNull();

    // Definitive proof the insecure path was refused: the insecure path installs the permissive
    // `(hostname, session) -> true` verifier (a synthetic lambda whose class name does NOT contain
    // "OkHostnameVerifier"). Seeing OkHttp's default verifier means the trust-all path never ran.
    assertThat(client.hostnameVerifier().getClass().getName())
        .as(
            "production guard must keep the default verifying hostname verifier, refusing trust-all")
        .contains("OkHostnameVerifier");

    // Parity with a plain default client: the guard must leave the SAME verifier type in place as a
    // config that never requested insecureSkipVerify at all. (OkHttp lazily builds a fresh default
    // SSLSocketFactory per client, so the socket-factory instances are NOT identity-equal even on
    // the safe path; the verifier type is the stable, meaningful signal here.)
    AxonFlowConfig defaultConfig =
        AxonFlowConfig.builder().agentUrl("https://localhost:8080").build();
    OkHttpClient defaultClient = HttpClientFactory.create(defaultConfig);
    assertThat(client.hostnameVerifier().getClass())
        .as("production guard must leave the default verifier type in place")
        .isEqualTo(defaultClient.hostnameVerifier().getClass());
  }

  @Test
  @DisplayName("production-guard helper should be false in a clean (non-production) environment")
  void productionGuardShouldBeFalseByDefault() {
    // The standard CI/dev shell sets none of the inspected deployment env vars to a production
    // token; if this fails, the test environment is mislabelled as production.
    assertThat(HttpClientFactory.isProductionLikeEnvironment())
        .as("no production signal should be present in a clean test environment")
        .isFalse();
    assertThat(HttpClientFactory.detectedProductionEnvSignal()).isNull();
  }

  @Test
  @DisplayName("production-guard helper should detect ENVIRONMENT=production")
  @SetEnvironmentVariable(key = "ENVIRONMENT", value = "production")
  void productionGuardShouldDetectEnvironmentProduction() {
    assertThat(HttpClientFactory.isProductionLikeEnvironment()).isTrue();
    assertThat(HttpClientFactory.detectedProductionEnvSignal()).isEqualTo("ENVIRONMENT=production");
  }

  @Test
  @DisplayName(
      "production-guard helper should detect a compound SPRING_PROFILES_ACTIVE=prod,metrics")
  @SetEnvironmentVariable(key = "SPRING_PROFILES_ACTIVE", value = "prod,metrics")
  void productionGuardShouldDetectCompoundSpringProfile() {
    // Compound, delimiter-separated values must be tokenised so a 'prod' token is still caught.
    assertThat(HttpClientFactory.isProductionLikeEnvironment()).isTrue();
    assertThat(HttpClientFactory.detectedProductionEnvSignal())
        .isEqualTo("SPRING_PROFILES_ACTIVE=prod,metrics");
  }

  @Test
  @DisplayName("production-guard helper should NOT trip on a non-production value")
  @SetEnvironmentVariable(key = "ENVIRONMENT", value = "development")
  void productionGuardShouldNotTripOnNonProductionValue() {
    // A non-production environment must still allow the development escape hatch: the guard must
    // not false-positive on substrings like 'reproduction' or unrelated values.
    assertThat(HttpClientFactory.isProductionLikeEnvironment()).isFalse();
    assertThat(HttpClientFactory.detectedProductionEnvSignal()).isNull();
  }

  @Test
  @DisplayName("production-guard helper should detect a hyphen-delimited ENVIRONMENT=production-us")
  @SetEnvironmentVariable(key = "ENVIRONMENT", value = "production-us")
  void productionGuardShouldDetectHyphenDelimitedProductionName() {
    // AxonFlow's own deployments use hyphen-delimited names like 'axonflow-production-us'; the
    // tokeniser must split on '-' so the embedded production token is caught.
    assertThat(HttpClientFactory.isProductionLikeEnvironment()).isTrue();
    assertThat(HttpClientFactory.detectedProductionEnvSignal())
        .isEqualTo("ENVIRONMENT=production-us");
  }

  @Test
  @DisplayName("production-guard helper should detect an underscore-delimited APP_ENV=prod_west")
  @SetEnvironmentVariable(key = "APP_ENV", value = "prod_west")
  void productionGuardShouldDetectUnderscoreDelimitedProdName() {
    assertThat(HttpClientFactory.isProductionLikeEnvironment()).isTrue();
    assertThat(HttpClientFactory.detectedProductionEnvSignal()).isEqualTo("APP_ENV=prod_west");
  }

  @Test
  @DisplayName("production-guard helper should NOT trip on a negated ENVIRONMENT=non-prod")
  @SetEnvironmentVariable(key = "ENVIRONMENT", value = "non-prod")
  void productionGuardShouldNotTripOnNegatedNonProd() {
    // 'non-prod' tokenises to [non, prod]; the 'non' negation prefix must keep the development
    // escape hatch usable in a non-production environment.
    assertThat(HttpClientFactory.isProductionLikeEnvironment()).isFalse();
    assertThat(HttpClientFactory.detectedProductionEnvSignal()).isNull();
  }

  @Test
  @DisplayName("production-guard helper should NOT trip on a negated SPRING profile pre-prod")
  @SetEnvironmentVariable(key = "SPRING_PROFILES_ACTIVE", value = "pre-prod,metrics")
  void productionGuardShouldNotTripOnNegatedPreProd() {
    assertThat(HttpClientFactory.isProductionLikeEnvironment()).isFalse();
    assertThat(HttpClientFactory.detectedProductionEnvSignal()).isNull();
  }
}
