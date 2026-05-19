/*
 * Copyright 2025 AxonFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * X-Client-ID header verification (v9 identity).
 *
 * <p>Every governed request carries {@code X-Client-ID} alongside Basic Auth. The agent's
 * apiAuthMiddleware overwrites the header with its own auth-derived value, so a missing or
 * wrong client-side header is harmless server-side. These tests pin SDK-emitted behaviour so
 * future regressions are caught early.
 */
@WireMockTest
@DisplayName("X-Client-ID header (v9)")
class XClientIdHeaderTest {

  @Test
  @DisplayName("emits X-Client-ID: community when no clientId configured")
  void communityDefault(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"data\":{\"answer\":\"ok\"}}")));

    AxonFlow client =
        AxonFlow.create(AxonFlowConfig.builder().agentUrl(wmRuntimeInfo.getHttpBaseUrl()).build());

    client.proxyLLMCall(ClientRequest.builder().userToken("").query("ping").build());

    verify(
        postRequestedFor(urlEqualTo("/api/request"))
            .withHeader("X-Client-ID", equalTo("community")));
  }

  @Test
  @DisplayName("emits X-Client-ID matching configured clientId")
  void configuredClient(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"data\":{\"answer\":\"ok\"}}")));

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("acme-corp")
                .clientSecret("secret")
                .build());

    client.proxyLLMCall(ClientRequest.builder().userToken("").query("ping").build());

    verify(
        postRequestedFor(urlEqualTo("/api/request"))
            .withHeader("X-Client-ID", equalTo("acme-corp")));
  }

  @Test
  @DisplayName("does NOT emit legacy X-Tenant-ID")
  void noLegacyTenantHeader(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":true,\"data\":{\"answer\":\"ok\"}}")));

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("acme-corp")
                .clientSecret("secret")
                .build());

    client.proxyLLMCall(ClientRequest.builder().userToken("").query("ping").build());

    verify(postRequestedFor(urlEqualTo("/api/request")).withoutHeader("X-Tenant-ID"));
  }
}
