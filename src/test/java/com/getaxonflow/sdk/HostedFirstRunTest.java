// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.PolicyViolationException;
import com.getaxonflow.sdk.exceptions.RateLimitException;
import com.getaxonflow.sdk.types.ClientRequest;
import com.getaxonflow.sdk.types.ClientResponse;
import com.getaxonflow.sdk.util.CacheConfig;
import com.getaxonflow.sdk.util.RetryConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

@WireMockTest
class HostedFirstRunTest {
  private static final String PAYMENT =
      "{\"error\":\"hosted_payment_required\",\"message\":\"Hosted governance requires an active paid plan.\",\"checkout_method\":\"POST\",\"checkout_url\":\"https://try-staging.getaxonflow.com/api/v1/billing/checkout\"}";
  private static final String MONTHLY =
      "{\"error\":\"hosted_monthly_limit_reached\",\"message\":\"Monthly hosted governance allowance reached.\",\"limit_type\":\"monthly_governed_events\",\"higher_usage_path\":\"paid_enterprise_poc\"}";

  private AxonFlow client(String endpoint, String clientId, String secret) {
    return AxonFlow.create(
        AxonFlowConfig.builder()
            .endpoint(endpoint)
            .clientId(clientId)
            .clientSecret(secret)
            .retryConfig(
                RetryConfig.builder().maxAttempts(3).initialDelay(Duration.ofMillis(1)).build())
            .cacheConfig(CacheConfig.disabled())
            .build());
  }

  private ClientRequest request() {
    return ClientRequest.builder().query("harmless governed probe").build();
  }

  @Test
  void defaultHostedRegistrationRequiresVerifiedPortal() {
    assertThatThrownBy(AxonFlowTry::register)
        .isInstanceOf(IOException.class)
        .hasMessageContaining(AxonFlowTry.HOSTED_START_URL)
        .hasMessageContaining("link an API client");
    assertThatThrownBy(() -> AxonFlowTry.register("trial", AxonFlowTry.TRY_ENDPOINT + "/"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining(AxonFlowTry.HOSTED_START_URL);
    assertThatThrownBy(() -> AxonFlowTry.register("trial", "https://try.getaxonflow.com."))
        .isInstanceOf(IOException.class)
        .hasMessageContaining(AxonFlowTry.HOSTED_START_URL);
    assertThatThrownBy(() -> AxonFlowTry.register("trial", "https://try-staging.getaxonflow.com.:443"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining(AxonFlowTry.HOSTED_START_URL);
  }

  @Test
  void explicitCommunityRegistrationStillWorks(WireMockRuntimeInfo server) throws Exception {
    stubFor(
        post(urlEqualTo("/api/v1/register"))
            .withRequestBody(containing("\"label\":\"local\""))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"tenant_id\":\"cs_local\",\"secret\":\"local-secret\"}")));
    AxonFlowTry.TryRegistration registration =
        AxonFlowTry.register("local", server.getHttpBaseUrl());
    assertThat(registration.getTenantId()).isEqualTo("cs_local");
    assertThat(registration.getSecret()).isEqualTo("local-secret");
    verify(1, postRequestedFor(urlEqualTo("/api/v1/register")));
  }

  @Test
  @SetEnvironmentVariable(key = "AXONFLOW_TRY", value = "1")
  void explicitEndpointAndCredentialsWinOverLegacyTrySwitch(WireMockRuntimeInfo server) {
    AxonFlowConfig config =
        AxonFlowConfig.builder()
            .endpoint(server.getHttpBaseUrl())
            .clientId("self-hosted")
            .clientSecret("configured-secret")
            .build();
    assertThat(config.getEndpoint()).isEqualTo(server.getHttpBaseUrl());
    assertThat(config.getClientId()).isEqualTo("self-hosted");
    assertThat(config.getClientSecret()).isEqualTo("configured-secret");
  }

  @Test
  void linkedCredentialAndOrdinary200StillWork(WireMockRuntimeInfo server) throws Exception {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"result\":\"governed\"}")));
    try (AxonFlow sdk = client(server.getHttpBaseUrl(), "cs_linked", "linked-secret")) {
      ClientResponse response = sdk.proxyLLMCall(request());
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getResult()).isEqualTo("governed");
    }
    String basic =
        Base64.getEncoder()
            .encodeToString("cs_linked:linked-secret".getBytes(StandardCharsets.UTF_8));
    verify(
        1,
        postRequestedFor(urlEqualTo("/api/request"))
            .withHeader("Authorization", equalTo("Basic " + basic)));
  }

  @Test
  void answeredHosted402IsTerminal(WireMockRuntimeInfo server) throws Exception {
    stubFor(
        post(urlEqualTo("/api/request")).willReturn(aResponse().withStatus(402).withBody(PAYMENT)));
    try (AxonFlow sdk = client(server.getHttpBaseUrl(), "cs_linked", "linked-secret")) {
      AxonFlowException refusal =
          catchThrowableOfType(() -> sdk.proxyLLMCall(request()), AxonFlowException.class);
      assertThat(refusal).isNotNull();
      assertThat(refusal.getStatusCode()).isEqualTo(402);
      assertThat(refusal.getErrorCode()).isEqualTo("hosted_payment_required");
    }
    verify(1, postRequestedFor(urlEqualTo("/api/request")));
  }

  @Test
  void answeredHosted429IsTerminalWithBodyOrHeader(WireMockRuntimeInfo server) throws Exception {
    for (String body : new String[] {MONTHLY, "{\"error\":\"hosted_monthly_limit_reached\""}) {
      resetAllRequests();
      stubFor(
          post(urlEqualTo("/api/request"))
              .willReturn(
                  aResponse()
                      .withStatus(429)
                      .withHeader("X-Axonflow-Tier-Limit", "monthly_governed_events")
                      .withBody(body)));
      try (AxonFlow sdk = client(server.getHttpBaseUrl(), "cs_linked", "linked-secret")) {
        RateLimitException refusal =
            catchThrowableOfType(() -> sdk.proxyLLMCall(request()), RateLimitException.class);
        assertThat(refusal).isNotNull();
        assertThat(refusal.getStatusCode()).isEqualTo(429);
        assertThat(refusal.getErrorCode()).isEqualTo("hosted_monthly_limit_reached");
        assertThat(refusal.getLimitType()).isEqualTo("monthly_governed_events");
      }
      verify(1, postRequestedFor(urlEqualTo("/api/request")));
    }
  }

  @Test
  void answeredHosted429BodyCodeIsTerminalWithoutHeader(WireMockRuntimeInfo server)
      throws Exception {
    stubFor(
        post(urlEqualTo("/api/request")).willReturn(aResponse().withStatus(429).withBody(MONTHLY)));
    try (AxonFlow sdk = client(server.getHttpBaseUrl(), "cs_linked", "linked-secret")) {
      RateLimitException refusal =
          catchThrowableOfType(() -> sdk.proxyLLMCall(request()), RateLimitException.class);
      assertThat(refusal).isNotNull();
      assertThat(refusal.getErrorCode()).isEqualTo("hosted_monthly_limit_reached");
    }
    verify(1, postRequestedFor(urlEqualTo("/api/request")));
  }

  @Test
  void ordinary429StillRetries(WireMockRuntimeInfo server) throws Exception {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(aResponse().withStatus(429).withBody("{\"error\":\"rate_limited\"}")));
    try (AxonFlow sdk = client(server.getHttpBaseUrl(), "self-hosted", "secret")) {
      RateLimitException refusal =
          catchThrowableOfType(() -> sdk.proxyLLMCall(request()), RateLimitException.class);
      assertThat(refusal).isNotNull();
      assertThat(refusal.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }
    verify(3, postRequestedFor(urlEqualTo("/api/request")));
  }

  @Test
  void legacySelfHostedBlocked402StillBlocks(WireMockRuntimeInfo server) throws Exception {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(402)
                    .withBody(
                        "{\"success\":false,\"blocked\":true,\"block_reason\":\"budget exceeded\"}")));
    try (AxonFlow sdk = client(server.getHttpBaseUrl(), "self-hosted", "secret")) {
      assertThatThrownBy(() -> sdk.proxyLLMCall(request()))
          .isInstanceOf(PolicyViolationException.class);
    }
    verify(1, postRequestedFor(urlEqualTo("/api/request")));
  }
}
