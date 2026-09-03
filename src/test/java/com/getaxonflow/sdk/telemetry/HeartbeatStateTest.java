/*
 * Copyright 2026 AxonFlow
 * Licensed under the Business Source License 1.1.
 */
package com.getaxonflow.sdk.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 9-case matrix mirroring the Go SDK reference (see heartbeat_test.go).
 *
 * <pre>
 *   1. cold start, no stamp           → 1 ping fires, stamp written
 *   2. fresh stamp (1d old)           → 0 pings
 *   3. stale stamp (8d old)           → 1 ping, stamp updated
 *   4. 5 calls within 1h cache        → exactly 1 ping
 *   5. cache expired + stale stamp    → 2nd ping fires
 *   6. AXONFLOW_TELEMETRY=off mid-run → covered by Java tests via mode/config gating;
 *      the env-var path requires process-level env mutation which JUnit cannot
 *      portably mutate, so we exercise the equivalent code path by passing
 *      isTelemetryEnabled=false on the second call.
 *   7. 100 concurrent threads         → exactly 1 ping (stampede coalesced)
 *   8. no cache dir (stampPath=null)  → ping per process, no crash
 *   9. ping returns false             → stamp NOT written; retry on success works
 * </pre>
 */
@DisplayName("HeartbeatState — 9-case matrix")
class HeartbeatStateTest {

  @Test
  @DisplayName("Case 1: cold start, no stamp → 1 ping, stamp written")
  void coldStart_noStamp_firesOnce(@TempDir Path tmp) {
    Path stamp = tmp.resolve("stamp");
    HeartbeatState h = new HeartbeatState(stamp);
    AtomicInteger pings = new AtomicInteger(0);

    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });

    assertThat(pings.get()).isEqualTo(1);
    assertThat(Files.exists(stamp)).isTrue();
  }

  @Test
  @DisplayName("Case 2: fresh stamp (1d old) → 0 pings")
  void freshStamp_doesNotFire(@TempDir Path tmp) throws IOException {
    Path stamp = tmp.resolve("stamp");
    Files.createDirectories(stamp.getParent());
    Files.writeString(stamp, "last_sent=test\n");
    Files.setLastModifiedTime(stamp, FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS)));

    HeartbeatState h = new HeartbeatState(stamp);
    AtomicInteger pings = new AtomicInteger(0);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });

    assertThat(pings.get()).isEqualTo(0);
  }

  @Test
  @DisplayName("Case 3: stale stamp (8d old) → 1 ping, stamp updated")
  void staleStamp_firesAndUpdates(@TempDir Path tmp) throws IOException {
    Path stamp = tmp.resolve("stamp");
    Files.createDirectories(stamp.getParent());
    Files.writeString(stamp, "last_sent=test\n");
    Files.setLastModifiedTime(stamp, FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));

    HeartbeatState h = new HeartbeatState(stamp);
    AtomicInteger pings = new AtomicInteger(0);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });

    assertThat(pings.get()).isEqualTo(1);
    long mtime = Files.getLastModifiedTime(stamp).toMillis();
    assertThat(System.currentTimeMillis() - mtime).isLessThan(5000L);
  }

  @Test
  @DisplayName("Case 4: 5 calls within 1h cache → exactly 1 ping")
  void within1hCache_firesOnce(@TempDir Path tmp) {
    HeartbeatState h = new HeartbeatState(tmp.resolve("stamp"));
    AtomicInteger pings = new AtomicInteger(0);

    for (int i = 0; i < 5; i++) {
      h.maybeSendHeartbeat(
          true,
          null,
          () -> {
            pings.incrementAndGet();
            return true;
          });
    }

    assertThat(pings.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Case 5: cache expired + stale stamp → 2nd ping fires")
  void afterCacheExpiry_firesAgain(@TempDir Path tmp) throws IOException {
    Path stamp = tmp.resolve("stamp");
    HeartbeatState h = new HeartbeatState(stamp);
    AtomicInteger pings = new AtomicInteger(0);

    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });
    assertThat(pings.get()).isEqualTo(1);

    // Backdate the cache (2h ago), the in-memory DELIVERY record (8d ago) AND the
    // stamp file (8d ago).
    //
    // lastDeliveredMillis joined this list with the in-memory 7-day cadence
    // (axonflow-enterprise#3682). It is not an extra knob for the test's convenience:
    // "eight days have passed" is a statement about all three records, and a fixture
    // that moved only two of them was modelling a state the process cannot be in.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    h.setLastDeliveredMillisForTest(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000L);
    Files.setLastModifiedTime(stamp, FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));

    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });
    assertThat(pings.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("Case 6: telemetry disabled mid-run → 0 further pings, stamp unchanged")
  void disabledMidProcess_stopsPings(@TempDir Path tmp) throws IOException {
    Path stamp = tmp.resolve("stamp");
    HeartbeatState h = new HeartbeatState(stamp);
    AtomicInteger pings = new AtomicInteger(0);

    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });
    assertThat(pings.get()).isEqualTo(1);

    // Disable telemetry, force gates open, snapshot mtime AFTER manipulation.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    Files.setLastModifiedTime(stamp, FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));
    long mtimeBefore = Files.getLastModifiedTime(stamp).toMillis();

    h.maybeSendHeartbeat(
        false,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });

    assertThat(pings.get()).isEqualTo(1);
    long mtimeAfter = Files.getLastModifiedTime(stamp).toMillis();
    assertThat(mtimeAfter).isEqualTo(mtimeBefore);
  }

  @Test
  @DisplayName("Case 7: 100 concurrent threads → exactly 1 ping")
  void concurrentCallers_coalesceToOnePing(@TempDir Path tmp) throws InterruptedException {
    HeartbeatState h = new HeartbeatState(tmp.resolve("stamp"));
    AtomicInteger pings = new AtomicInteger(0);

    int threadCount = 100;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      new Thread(
              () -> {
                try {
                  start.await();
                } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
                }
                h.maybeSendHeartbeat(
                    true,
                    null,
                    () -> {
                      // Slow ping to encourage stampede behavior.
                      try {
                        Thread.sleep(10);
                      } catch (InterruptedException ignored) {
                      }
                      pings.incrementAndGet();
                      return true;
                    });
                done.countDown();
              },
              "heartbeat-test-" + i)
          .start();
    }

    start.countDown();
    done.await();

    assertThat(pings.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Case 8: no cache dir (stampPath=null) → ping ONCE, then bounded in memory")
  void noCacheDir_pingsButNoStamp() {
    HeartbeatState h = new HeartbeatState((Path) null);
    AtomicInteger pings = new AtomicInteger(0);

    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });
    assertThat(pings.get()).isEqualTo(1);

    // 1h cache holds within the same process even without a stamp file.
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });
    assertThat(pings.get()).isEqualTo(1);

    // ASSERTION INVERTED IN #3682, AND THE OLD ONE WAS THE DEFECT.
    //
    // This block used to backdate the cache and assert a SECOND ping "because no stamp
    // gate exists". That is precisely the bug: in a runtime with no usable cache dir —
    // distroless and scratch containers, Lambda custom runtimes, a read-only root
    // filesystem — the stamp can never be written, so nothing bounded the cadence and a
    // SUCCESSFUL ping recurred every hour, forever. 168 pings a week against a contract
    // that discloses one, in exactly the environments least able to notice, and the
    // failure backoff cannot help because these deliveries succeed.
    //
    // The in-memory lastDeliveredMillis record is the bound. An hour after a delivery
    // the gate must now REFUSE.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });
    assertThat(pings.get()).isEqualTo(1);

    // And the other direction, so the bound cannot pass as a permanent mute: past the
    // 7-day interval it must fire again.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    h.setLastDeliveredMillisForTest(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000L);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          pings.incrementAndGet();
          return true;
        });
    assertThat(pings.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("Case 7b: 50 concurrent callers via shared() → exactly 1 ping (singleton gate)")
  void multiClientConcurrent_coalesceToOnePing(@TempDir Path tmp) throws InterruptedException {
    // Verifies the SINGLETON gate (HeartbeatState.shared()) coalesces
    // concurrent callers to a single ping. End-to-end coverage that the
    // AxonFlow constructor actually routes through shared() (the P1 fix
    // for per-instance gates) lives in HeartbeatE2ETest, which constructs
    // real AxonFlow instances against an httptest checkpoint.
    HeartbeatState previous = HeartbeatState.replaceForTest(tmp.resolve("stamp"));
    try {
      AtomicInteger pings = new AtomicInteger(0);
      int clientCount = 50;
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(clientCount);

      for (int i = 0; i < clientCount; i++) {
        new Thread(
                () -> {
                  try {
                    start.await();
                  } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                  }
                  // Each "client" calls the shared gate. The static singleton coalesces
                  // them onto a single ping per heartbeatInterval.
                  HeartbeatState.shared()
                      .maybeSendHeartbeat(
                          true,
                          null,
                          () -> {
                            try {
                              Thread.sleep(10);
                            } catch (InterruptedException ignored) {
                            }
                            pings.incrementAndGet();
                            return true;
                          });
                  done.countDown();
                },
                "multi-client-test-" + i)
            .start();
      }

      start.countDown();
      done.await();

      assertThat(pings.get()).isEqualTo(1);
    } finally {
      HeartbeatState.restoreForTest(previous);
    }
  }

  @Test
  @DisplayName("Case 9: ping returns false → stamp NOT written; retry on success works")
  void pingFailure_stampNotWritten_retrySucceeds(@TempDir Path tmp) throws IOException {
    Path stamp = tmp.resolve("stamp");
    HeartbeatState h = new HeartbeatState(stamp);
    AtomicInteger fails = new AtomicInteger(0);
    AtomicInteger successes = new AtomicInteger(0);

    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          fails.incrementAndGet();
          return false;
        });
    assertThat(fails.get()).isEqualTo(1);
    assertThat(Files.exists(stamp)).isFalse();

    // Backdate cache, retry against success.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);

    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          successes.incrementAndGet();
          return true;
        });
    assertThat(successes.get()).isEqualTo(1);
    assertThat(Files.exists(stamp)).isTrue();
  }

  @Test
  @DisplayName("the failure backoff doubles and caps at the 7-day interval")
  void guardIntervalDoublesAndCaps() {
    // NO TEST COVERED THIS AT ALL until the mutation run said so: removing the doubling
    // entirely left the whole suite green. A backoff nothing asserts is a backoff that
    // can be deleted in a refactor.
    //
    // Literals rather than the constants, for the same reason: comparing
    // guardIntervalFor(1) against 2 * HEARTBEAT_GUARD_INTERVAL_MS moves both sides under
    // a mutant of the constant.
    long hour = 60L * 60L * 1000L;
    long sevenDays = 7L * 24L * 60L * 60L * 1000L;

    assertThat(HeartbeatState.HEARTBEAT_GUARD_INTERVAL_MS).isEqualTo(hour);
    assertThat(HeartbeatState.HEARTBEAT_INTERVAL_MS).isEqualTo(sevenDays);

    assertThat(HeartbeatState.guardIntervalFor(0)).isEqualTo(hour);
    assertThat(HeartbeatState.guardIntervalFor(1)).isEqualTo(2 * hour);
    assertThat(HeartbeatState.guardIntervalFor(2)).isEqualTo(4 * hour);
    assertThat(HeartbeatState.guardIntervalFor(7)).isEqualTo(128 * hour);
    // 256h would exceed 7 days, so it caps.
    assertThat(HeartbeatState.guardIntervalFor(8)).isEqualTo(sevenDays);
    // An unbounded counter must cap rather than shift into an absurd or negative value.
    assertThat(HeartbeatState.guardIntervalFor(Integer.MAX_VALUE)).isEqualTo(sevenDays);
    // A negative counter cannot produce a negative interval either.
    assertThat(HeartbeatState.guardIntervalFor(-5)).isEqualTo(hour);

    // EXACTLY 64, AND THE REASON IS THE JVM RATHER THAN THE ARITHMETIC. Java masks a
    // long shift distance to its low 6 bits, so `x << 64` is `x << 0` — the value comes
    // back UNSHIFTED. Without the `Math.min(..., MAX_BACKOFF_DOUBLINGS)` clamp, a process
    // on its 64th consecutive failure would compute a one-HOUR interval: the backoff
    // silently inverts into its most aggressive setting at the point it should be at its
    // most patient, and an air-gapped deployment goes back to retrying hourly forever.
    //
    // Every other assertion in this test survives deleting that clamp. Integer.MAX_VALUE
    // masks to 31, which shifts to a large positive value that trips the `> 7 days` cap
    // and returns the right answer for the wrong reason. 64 is the value that exposes it,
    // and nothing here used it until the reviewer asked what pinned the ceiling.
    assertThat(HeartbeatState.guardIntervalFor(64)).isEqualTo(sevenDays);
    assertThat(HeartbeatState.guardIntervalFor(65)).isEqualTo(sevenDays);
    assertThat(HeartbeatState.guardIntervalFor(128)).isEqualTo(sevenDays);
  }

  @Test
  @DisplayName("the guard-warm probe is what routes a cold call to the SYNCHRONOUS path")
  void guardWarmProbe(@TempDir Path tmp) throws IOException {
    // The heartbeat moved from the constructor to the first outbound request
    // (axonflow-enterprise#3682). The constructor's ping was SYNCHRONOUS on purpose — a
    // JVM that exits promptly would otherwise drop a POST left on the daemon executor —
    // so AxonFlow.invokeHeartbeatOnRequest keeps that property by running the gate
    // synchronously when it is COLD and dispatching only when WARM.
    //
    // isGuardWarm is the predicate that routes between them, so it is pinned here: a
    // predicate that always returned true would silently put every first request back on
    // the async path and re-open the short-lived-process drop.
    HeartbeatState h = new HeartbeatState(tmp.resolve("stamp"));

    assertThat(h.isGuardWarm())
        .as("a fresh gate is COLD, so the first request runs the heartbeat synchronously")
        .isFalse();

    h.maybeSendHeartbeat(true, null, () -> true);

    assertThat(h.isGuardWarm())
        .as("after a gate run the guard is warm, so later requests dispatch asynchronously")
        .isTrue();

    // And it goes cold again once the guard interval has elapsed.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    assertThat(h.isGuardWarm()).isFalse();
  }

  @Test
  @DisplayName("the backoff is enforced BY THE GATE, not just by the pure function")
  void theBackoffIsEnforcedByTheGate(@TempDir Path tmp) {
    // R3 round 1: guardIntervalFor was pinned only as a pure function, so THREE
    // separate mutants left the suite green — deleting the failure-counter
    // increment, deleting its reset on delivery, and reverting the gate's call
    // site to the base interval. Testing the arithmetic is not testing the
    // gate; only counting what the gate ADMITS can tell the two intervals
    // apart.
    //
    // The stamp path is a real temp file so the 7-day file gate is inert (it is
    // never written on a failed ping), leaving the guard interval as the only
    // thing deciding each attempt.
    HeartbeatState h = new HeartbeatState(tmp.resolve("stamp"));
    AtomicInteger attempts = new AtomicInteger(0);

    // Two consecutive failures. The first is admitted because the gate is cold;
    // the second needs the guard re-opened, which models an hour passing.
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          attempts.incrementAndGet();
          return false;
        });
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          attempts.incrementAndGet();
          return false;
        });
    assertThat(attempts.get()).as("two attempts were made, both undelivered").isEqualTo(2);
    assertThat(h.getConsecutiveFailuresForTest())
        .as("MUTATION GATE: delete the increment and this is 0")
        .isEqualTo(2);

    // 90 minutes later. PAST the base 1-hour guard, INSIDE the 4-hour interval
    // two failures earn — which is the only span that can distinguish them.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 90 * 60 * 1000L);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          attempts.incrementAndGet();
          return false;
        });
    assertThat(attempts.get())
        .as(
            "MUTATION GATE: revert the gate to HEARTBEAT_GUARD_INTERVAL_MS and this is 3. "
                + "90 minutes is inside the widened %d ms interval two failures earn; a gate "
                + "still using the base %d ms probes the customer's own platform hourly "
                + "forever when egress is blocked",
            HeartbeatState.guardIntervalFor(2), HeartbeatState.HEARTBEAT_GUARD_INTERVAL_MS)
        .isEqualTo(2);

    // FIVE hours later: past the widened interval, so it must retry. Without
    // this the "backoff" could be a permanent mute and still pass above.
    //
    // Five, not three, and the arithmetic is worth stating because I got it
    // wrong first: the interval DOUBLES, so two consecutive failures earn
    // 1h << 2 == 4 HOURS. Three hours is still inside it and is correctly
    // refused — a fixture using three would have failed for the right reason
    // while appearing to test the wrong one.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 5 * 60 * 60 * 1000L);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          attempts.incrementAndGet();
          return true; // this one DELIVERS
        });
    assertThat(attempts.get()).as("the widened interval must re-open, not mute").isEqualTo(3);
    assertThat(h.getConsecutiveFailuresForTest())
        .as("MUTATION GATE: delete the reset on delivery and this is 3")
        .isZero();

    // 61 minutes after the delivery. The counter is back to 0, so the base
    // 1-hour guard applies again — but the 7-day cadence now suppresses it,
    // which is the correct reason and a different one from the backoff.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 61 * 60 * 1000L);
    h.maybeSendHeartbeat(
        true,
        null,
        () -> {
          attempts.incrementAndGet();
          return true;
        });
    assertThat(attempts.get())
        .as("a delivered ping is bounded by the 7-day cadence, not by the (reset) backoff")
        .isEqualTo(3);
  }
}
