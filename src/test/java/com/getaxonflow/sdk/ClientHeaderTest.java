// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.getaxonflow.sdk.types.*;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * X-Axonflow-Client header injection — ADR-050 §4.
 *
 * <p>Asserts every governed request forwards {@code X-Axonflow-Client: sdk-java/<SDK_VERSION>} so
 * the agent can derive request scope (sdk) and validate against the token's aud.scope via
 * HasScope().
 *
 * <p>Header value is sourced from the bundled {@link AxonFlowConfig#SDK_VERSION}; the consumer
 * cannot spoof its own client identity through config (intentional — honest-99% header injection
 * per ADR-050 §4).
 */
@WireMockTest
@DisplayName("X-Axonflow-Client header injection")
class ClientHeaderTest {

  private static final String EXPECTED_CLIENT = "sdk-java/" + AxonFlowConfig.SDK_VERSION;

  @Test
  @DisplayName("should send X-Axonflow-Client on proxyLLMCall")
  void shouldSendClientHeaderOnProxy(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api/request"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{" + "\"success\": true," + "\"data\": {\"answer\": \"ok\"}" + "}")));

    AxonFlow client =
        AxonFlow.create(
            AxonFlowConfig.builder()
                .agentUrl(wmRuntimeInfo.getHttpBaseUrl())
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());

    client.proxyLLMCall(ClientRequest.builder().userToken("").query("ping").build());

    verify(
        postRequestedFor(urlEqualTo("/api/request"))
            .withHeader("X-Axonflow-Client", equalTo(EXPECTED_CLIENT)));
  }

  @Test
  @DisplayName("getClientHeader returns sdk-java/<semver>")
  void getClientHeaderShouldMatchExpectedFormat() {
    AxonFlowConfig config = AxonFlowConfig.builder().agentUrl("http://localhost:8080").build();

    String header = config.getClientHeader();
    assertThat(header).startsWith("sdk-java/");
    // Sanity: agent's deriveScopeFromClientHeader splits on '/' and maps
    // "sdk-*" prefixes to scope=sdk. Lock down the shape.
    assertThat(header.split("/")).hasSize(2);
    assertThat(header.split("/")[0]).isEqualTo("sdk-java");
  }
}
