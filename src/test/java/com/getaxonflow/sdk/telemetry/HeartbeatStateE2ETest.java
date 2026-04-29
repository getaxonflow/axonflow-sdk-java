/*
 * Copyright 2026 AxonFlow
 * Licensed under the Business Source License 1.1.
 */
package com.getaxonflow.sdk.telemetry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 4-run cross-process E2E mirroring the Go reference (see
 * {@code heartbeat_e2e_test.go}). Validates the delivered-stamp contract
 * end-to-end against a real WireMock HTTP server:
 *
 * <pre>
 *   Run 1: cold start (no stamp)              → 1 ping;  stamp present
 *   Run 2: immediate re-run (fresh stamp)     → 0 pings; stamp unchanged
 *   Run 3: backdate stamp 8d                  → 1 ping;  stamp re-touched
 *   Run 4: backdate stamp 8d, mock returns 503 → ping attempted but stamp NOT advanced;
 *                                                retry against 200-mock fires + lands cleanly
 * </pre>
 *
 * <p>The Java equivalent of Go's {@code t.Setenv("AXONFLOW_CHECKPOINT_URL")}
 * + {@code replaceHeartbeatStateForTest(stampPath)} pattern is to drive
 * the gate directly via {@link HeartbeatState#replaceForTest(Path)} and
 * pass an inline {@link HeartbeatState.PingFn} that POSTs to WireMock —
 * Java's {@code System.getenv} is immutable post-launch, so we cannot
 * inject AXONFLOW_CHECKPOINT_URL on a per-test basis the way Go can.
 *
 * <p>Each run installs a fresh in-memory gate at the SAME stamp path —
 * exactly the cross-run invariant the Go E2E exercises (the stamp file
 * is the source of truth across simulated process restarts).
 */
@WireMockTest
@DisplayName("HeartbeatState — 4-run cross-process E2E")
class HeartbeatStateE2ETest {

  @Test
  @DisplayName("Cold → warm → stale → 503 → retry, with delivered-stamp semantics")
  void fourRunCycle(WireMockRuntimeInfo wm, @TempDir Path tmp) throws IOException {
    Path stampPath = tmp.resolve("java-telemetry-last-sent");

    // Always-200 endpoint for runs 1, 2, 3 and the retry leg of run 4.
    WireMock.stubFor(
        post(urlMatching("/v1/.*"))
            .withName("ok")
            .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));
    String okUrl = wm.getHttpBaseUrl() + "/v1/checkpoint";

    // Always-503 endpoint for run 4's failure leg.
    WireMock.stubFor(
        post(urlMatching("/v1/fail.*"))
            .withName("fail")
            .willReturn(aResponse().withStatus(503)));
    String failUrl = wm.getHttpBaseUrl() + "/v1/fail";

    // Save the process-global singleton and restore it at the end so we
    // don't leak gate state into other tests in the suite.
    HeartbeatState previous = HeartbeatState.replaceForTest(stampPath);
    try {
      // Each run mirrors a "new process": fresh in-memory gate at the
      // same stamp path. Mode/credentials don't matter for this test —
      // the gate's only contract is "fire the PingFn, write stamp on
      // success" — so we drive it directly with isTelemetryEnabled=true.

      // ----- Run 1: cold start, no stamp → 1 ping, stamp present ---------------
      WireMock.resetAllRequests();
      runOnceAt(stampPath, okUrl);
      assertThat(WireMock.getAllServeEvents())
          .as("Run 1 (cold): expected exactly 1 ping")
          .hasSize(1);
      assertThat(Files.exists(stampPath))
          .as("Run 1 (cold): expected stamp file present")
          .isTrue();
      long stampMtimeAfterRun1 = Files.getLastModifiedTime(stampPath).toMillis();

      // ----- Run 2: immediate re-run, fresh stamp → 0 pings -------------------
      WireMock.resetAllRequests();
      runOnceAt(stampPath, okUrl);
      WireMock.verify(0, postRequestedFor(urlMatching("/v1/.*")));
      assertThat(Files.getLastModifiedTime(stampPath).toMillis())
          .as("Run 2 (warm): stamp unchanged")
          .isEqualTo(stampMtimeAfterRun1);

      // ----- Run 3: backdate stamp 8d → 1 ping + stamp re-touched -------------
      Files.setLastModifiedTime(
          stampPath, FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));
      long stampMtimeBeforeRun3 = Files.getLastModifiedTime(stampPath).toMillis();

      WireMock.resetAllRequests();
      runOnceAt(stampPath, okUrl);
      assertThat(WireMock.getAllServeEvents())
          .as("Run 3 (stale): expected exactly 1 ping after backdating stamp")
          .hasSize(1);
      long stampMtimeAfterRun3 = Files.getLastModifiedTime(stampPath).toMillis();
      assertThat(stampMtimeAfterRun3)
          .as("Run 3 (stale): stamp mtime must advance after successful ping")
          .isGreaterThan(stampMtimeBeforeRun3);
      assertThat(System.currentTimeMillis() - stampMtimeAfterRun3)
          .as("Run 3 (stale): stamp mtime is recent")
          .isLessThan(5_000L);

      // ----- Run 4 (failure): backdate stamp 8d, point at 503 -----------------
      // Expectation: ping is attempted but stamp is NOT advanced.
      Files.setLastModifiedTime(
          stampPath, FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));
      long stampMtimeBeforeFail = Files.getLastModifiedTime(stampPath).toMillis();

      WireMock.resetAllRequests();
      runOnceAt(stampPath, failUrl);
      assertThat(WireMock.getAllServeEvents())
          .as("Run 4 (failure): expected exactly 1 attempt against 503 endpoint")
          .hasSize(1);
      long stampMtimeAfterFail = Files.getLastModifiedTime(stampPath).toMillis();
      assertThat(stampMtimeAfterFail)
          .as("Run 4 (failure): stamp mtime must NOT advance on failed POST")
          .isEqualTo(stampMtimeBeforeFail);

      // ----- Run 4 (retry): same stale stamp, point at 200 --------------------
      WireMock.resetAllRequests();
      runOnceAt(stampPath, okUrl);
      assertThat(WireMock.getAllServeEvents())
          .as("Run 4 (retry): expected exactly 1 ping when stamp still stale and server now 200")
          .hasSize(1);
      long stampMtimeAfterRetry = Files.getLastModifiedTime(stampPath).toMillis();
      assertThat(System.currentTimeMillis() - stampMtimeAfterRetry)
          .as("Run 4 (retry): stamp mtime advanced to ~now after successful retry")
          .isLessThan(5_000L);
    } finally {
      HeartbeatState.restoreForTest(previous);
    }
  }

  /**
   * Simulate a "new process": install a fresh in-memory gate at the
   * SAME stamp path (cross-run invariant) and run the gate once with
   * an inline ping that POSTs to {@code targetUrl}. Returns once the
   * gate's stamp write — if any — has settled, since
   * {@link HeartbeatState#maybeSendHeartbeat} is synchronous.
   */
  private static void runOnceAt(Path stampPath, String targetUrl) {
    HeartbeatState.replaceForTest(stampPath);
    HeartbeatState.shared()
        .maybeSendHeartbeat(
            true,
            null,
            () -> {
              try {
                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(targetUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(2_000);
                conn.setReadTimeout(2_000);
                conn.getOutputStream().write("{}".getBytes());
                conn.getOutputStream().close();
                int code = conn.getResponseCode();
                conn.disconnect();
                return code >= 200 && code < 300;
              } catch (IOException e) {
                return false;
              }
            });
  }
}
