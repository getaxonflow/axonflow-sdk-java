/*
 * Copyright 2026 AxonFlow
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
package com.getaxonflow.sdk.telemetry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test for axonflow-enterprise#1706: the telemetry ping must be delivered synchronously
 * before {@code sendPing} returns, so that short-lived JVMs (CLI binaries, AWS Lambda handlers,
 * serverless cold-starts, quickstart scripts) don't drop the ping on JVM exit.
 *
 * <p>Root cause of the original bug: {@code CompletableFuture.runAsync(lambda)} submits to {@code
 * ForkJoinPool.commonPool()}, whose threads are daemon by default since Java 8. When the main
 * thread exits, the daemon pool is killed mid-flight and the in-flight HTTP POST is abandoned —
 * silently, with no error visible to the caller.
 *
 * <p>The key invariant: once {@code sendPing} returns, the HTTP round-trip must have already
 * completed (or timed out cleanly). We verify this by pointing the reporter at a WireMock server
 * that responds with a fixed delay; if {@code sendPing} is synchronous, it blocks until the
 * response, and elapsed time reflects the delay. If anyone regresses the code back to {@code
 * runAsync}, the call returns immediately and this test fails.
 */
@WireMockTest
@DisplayName("TelemetryReporter — short-lived-process regression")
class TelemetryReporterShortLivedTest {

  @Test
  @DisplayName("sendPing blocks until the HTTP round-trip completes (no fire-and-forget drop)")
  void sendPingBlocksUntilRoundTripCompletes(WireMockRuntimeInfo info) {
    // Mock checkpoint returns 200 only after a 200ms delay. If sendPing is
    // synchronous, the caller blocks for at least that long. If sendPing
    // regresses to fire-and-forget (daemon-thread), the caller returns
    // almost immediately (<50ms) and the assertion below fails.
    info.getWireMock()
        .register(
            post(urlEqualTo("/v1/ping"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(200).withBody("{}")));

    String checkpointUrl = info.getHttpBaseUrl() + "/v1/ping";

    long startNs = System.nanoTime();
    TelemetryReporter.sendPing(
        "production",
        "", // empty SDK endpoint: skip /health probe so we measure only the POST
        false,
        null, // AXONFLOW_TELEMETRY
        checkpointUrl);
    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

    // The fixed-delay mock forces a ~200ms round-trip. A synchronous implementation
    // must have waited for it; a fire-and-forget implementation would have returned
    // in well under 50ms. 150ms is the floor: generous slack for JVM scheduling /
    // network setup on slow CI machines without losing the regression signal.
    assertThat(elapsedMs)
        .as(
            "sendPing should have blocked long enough for the 200ms fixed-delay response "
                + "to complete. An elapsed time under ~150ms strongly suggests a regression "
                + "to the CompletableFuture.runAsync fire-and-forget pattern, which would "
                + "drop the ping on JVM exit in short-lived processes (see #1706).")
        .isGreaterThanOrEqualTo(150L);

    // And: the ping actually landed on the mock.
    info.getWireMock().verifyThat(postRequestedFor(urlEqualTo("/v1/ping")));
  }
}
