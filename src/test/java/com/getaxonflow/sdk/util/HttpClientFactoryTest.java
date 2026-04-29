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
        AxonFlowConfig.builder().agentUrl("https://localhost:8080").insecureSkipVerify(true).build();

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
    AxonFlowConfig config =
        AxonFlowConfig.builder().agentUrl("https://localhost:8080").build();

    OkHttpClient client = HttpClientFactory.create(config);

    assertThat(client).isNotNull();
    assertThat(client.hostnameVerifier().getClass().getName())
        .as("default hostname verifier must remain when only env var is set")
        .contains("OkHostnameVerifier");
  }
}
