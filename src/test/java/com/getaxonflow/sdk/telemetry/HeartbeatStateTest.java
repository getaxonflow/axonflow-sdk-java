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

    h.maybeSendHeartbeat(true, null, () -> {
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
    h.maybeSendHeartbeat(true, null, () -> {
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
    h.maybeSendHeartbeat(true, null, () -> {
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
      h.maybeSendHeartbeat(true, null, () -> {
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

    h.maybeSendHeartbeat(true, null, () -> {
      pings.incrementAndGet();
      return true;
    });
    assertThat(pings.get()).isEqualTo(1);

    // Backdate cache (2h ago) AND stamp file (8d ago).
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    Files.setLastModifiedTime(stamp, FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));

    h.maybeSendHeartbeat(true, null, () -> {
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

    h.maybeSendHeartbeat(true, null, () -> {
      pings.incrementAndGet();
      return true;
    });
    assertThat(pings.get()).isEqualTo(1);

    // Disable telemetry, force gates open, snapshot mtime AFTER manipulation.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    Files.setLastModifiedTime(stamp, FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));
    long mtimeBefore = Files.getLastModifiedTime(stamp).toMillis();

    h.maybeSendHeartbeat(false, null, () -> {
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
      new Thread(() -> {
        try {
          start.await();
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
        h.maybeSendHeartbeat(true, null, () -> {
          // Slow ping to encourage stampede behavior.
          try { Thread.sleep(10); } catch (InterruptedException ignored) {}
          pings.incrementAndGet();
          return true;
        });
        done.countDown();
      }, "heartbeat-test-" + i).start();
    }

    start.countDown();
    done.await();

    assertThat(pings.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Case 8: no cache dir (stampPath=null) → ping per process, no crash")
  void noCacheDir_pingsButNoStamp() {
    HeartbeatState h = new HeartbeatState((Path) null);
    AtomicInteger pings = new AtomicInteger(0);

    h.maybeSendHeartbeat(true, null, () -> {
      pings.incrementAndGet();
      return true;
    });
    assertThat(pings.get()).isEqualTo(1);

    // 1h cache holds within the same process even without a stamp file.
    h.maybeSendHeartbeat(true, null, () -> {
      pings.incrementAndGet();
      return true;
    });
    assertThat(pings.get()).isEqualTo(1);

    // Backdate cache, call again — fires because no stamp gate exists.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
    h.maybeSendHeartbeat(true, null, () -> {
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
        new Thread(() -> {
          try {
            start.await();
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
          // Each "client" calls the shared gate. The static singleton coalesces
          // them onto a single ping per heartbeatInterval.
          HeartbeatState.shared().maybeSendHeartbeat(true, null, () -> {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            pings.incrementAndGet();
            return true;
          });
          done.countDown();
        }, "multi-client-test-" + i).start();
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

    h.maybeSendHeartbeat(true, null, () -> {
      fails.incrementAndGet();
      return false;
    });
    assertThat(fails.get()).isEqualTo(1);
    assertThat(Files.exists(stamp)).isFalse();

    // Backdate cache, retry against success.
    h.setLastCheckedMillisForTest(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);

    h.maybeSendHeartbeat(true, null, () -> {
      successes.incrementAndGet();
      return true;
    });
    assertThat(successes.get()).isEqualTo(1);
    assertThat(Files.exists(stamp)).isTrue();
  }
}
