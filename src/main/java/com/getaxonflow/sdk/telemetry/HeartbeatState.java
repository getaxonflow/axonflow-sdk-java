/*
 * Copyright 2026 AxonFlow
 * Licensed under the Business Source License 1.1.
 */
package com.getaxonflow.sdk.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 7-day delivered-heartbeat state for the AxonFlow Java SDK telemetry gate.
 *
 * <p>Implements the cross-SDK contract:
 *
 * <pre>
 *   AxonFlow emits at most one heartbeat per environment every
 *   7 days during SDK activity.
 * </pre>
 *
 * <p>The gate is consulted at client construction and at every public HTTP
 * request site (via the {@code executeHttp} wrapper in {@code AxonFlow}).
 * Each gate run:
 *
 * <ol>
 *   <li>Re-evaluates {@code AXONFLOW_TELEMETRY=off} cheaply (lock-free) so
 *       a mid-process opt-out toggle takes effect immediately.
 *   <li>Checks an in-memory 1-hour cache to bound stamp-file stat
 *       frequency on hot request paths.
 *   <li>Reads the stamp file mtime as the source of truth for last
 *       successful delivery across process restarts.
 *   <li>Sends the ping and writes the stamp ONLY on success — stamp-on-
 *       DELIVERY semantics. Failed POSTs leave the stamp unchanged so
 *       the next call after the 1-hour cache expires retries.
 *   <li>Coalesces concurrent callers via an in-flight flag so a stampede
 *       across the boundary fires exactly one POST.
 * </ol>
 *
 * <p>Cross-platform stamp file location (no external deps):
 *
 * <ul>
 *   <li>macOS: {@code ~/Library/Caches/axonflow/java-telemetry-last-sent}
 *   <li>Linux: {@code $XDG_CACHE_HOME/axonflow/...} or {@code ~/.cache/axonflow/...}
 *   <li>Windows: {@code %LOCALAPPDATA%/axonflow/...}
 * </ul>
 *
 * <p>If no cache directory is available (restricted env), the stamp path
 * is {@code null} and the SDK falls back to "one ping per process" — same
 * as today's pre-heartbeat behavior. No regression for that runtime.
 */
public class HeartbeatState {
  private static final Logger logger = LoggerFactory.getLogger(HeartbeatState.class);

  /** 7 days in milliseconds. */
  public static final long HEARTBEAT_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L;

  /** 1 hour in milliseconds — bounds how often we stat() the stamp file. */
  public static final long HEARTBEAT_GUARD_INTERVAL_MS = 60L * 60L * 1000L;

  private final ReentrantLock lock = new ReentrantLock();
  private long lastCheckedMillis = -1L;
  private boolean inFlight = false;
  private final Path stampPath;

  /**
   * Default constructor: stamp path resolved via the OS-native cache dir.
   */
  public HeartbeatState() {
    this(resolveStampPath());
  }

  /**
   * Test-friendly constructor. Pass {@code null} to test the "no
   * persistence" path (Lambda-like restricted env).
   */
  public HeartbeatState(Path stampPath) {
    this.stampPath = stampPath;
  }

  /**
   * Resolve the OS-native stamp file path, or {@code null} if no
   * user-writable cache directory is available. Hand-rolled rather than
   * via a third-party platform-paths library to keep the SDK
   * dependency-free.
   */
  public static Path resolveStampPath() {
    String osName = System.getProperty("os.name", "").toLowerCase();
    String userHome = System.getProperty("user.home");
    if (osName.contains("mac")) {
      if (userHome == null || userHome.isEmpty()) {
        return null;
      }
      return Paths.get(userHome, "Library", "Caches", "axonflow", "java-telemetry-last-sent");
    }
    if (osName.contains("win")) {
      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData == null || localAppData.isEmpty()) {
        return null;
      }
      return Paths.get(localAppData, "axonflow", "java-telemetry-last-sent");
    }
    // Linux / *BSD / others — XDG.
    String xdg = System.getenv("XDG_CACHE_HOME");
    if (xdg != null && !xdg.isEmpty()) {
      return Paths.get(xdg, "axonflow", "java-telemetry-last-sent");
    }
    if (userHome == null || userHome.isEmpty()) {
      return null;
    }
    return Paths.get(userHome, ".cache", "axonflow", "java-telemetry-last-sent");
  }

  /** Read accessor for tests. */
  public Path getStampPath() {
    return stampPath;
  }

  /**
   * Returns the stamp file's mtime in ms-since-epoch, or {@code -1L} if
   * absent / unreadable / no path. Tolerant of every failure mode — a
   * corrupted or missing stamp is treated as "never sent" so we re-attempt.
   */
  public long readStampMtimeMillis() {
    if (stampPath == null) {
      return -1L;
    }
    try {
      return Files.getLastModifiedTime(stampPath).toMillis();
    } catch (IOException e) {
      return -1L;
    }
  }

  /**
   * Atomically write a fresh timestamp file via tmp+rename. Contents are
   * advisory (a single human-readable line); the SDK uses mtime as the
   * source of truth, never the contents.
   *
   * <p>Errors are non-fatal — a failed write means the next process
   * retries on schedule, which is preferable to silent dropping.
   */
  public void writeStampAtomic() {
    if (stampPath == null) {
      return;
    }
    try {
      Files.createDirectories(stampPath.getParent());
    } catch (IOException e) {
      return;
    }
    Path tmp;
    try {
      tmp = Files.createTempFile(
          stampPath.getParent(),
          "telemetry-last-sent-",
          ".tmp");
    } catch (IOException e) {
      return;
    }
    try {
      String line = "last_sent=" + DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
      Files.writeString(tmp, line + System.lineSeparator());
      Files.move(tmp, stampPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
        /* best-effort cleanup */
      }
    }
  }

  /**
   * Functional interface for the ping operation that returns delivery
   * success. Used by {@link #maybeSendHeartbeat} so the heartbeat module
   * doesn't depend directly on TelemetryReporter (avoids a circular
   * dependency through the gate).
   */
  @FunctionalInterface
  public interface PingFn {
    boolean send();
  }

  /**
   * Central gate for telemetry pings. Called from {@code AxonFlow}'s
   * constructor and {@code executeHttp} wrapper. Implements the
   * delivered-heartbeat contract documented at the top of this class.
   *
   * <p>Never throws — heartbeat failures must not surface to the caller.
   *
   * @param isTelemetryEnabled gating decision from the caller's
   *     mode/config check; if {@code false} the gate short-circuits.
   * @param pingFn callback that performs the actual ping. Returns
   *     {@code true} only on successful delivery; the stamp is written
   *     ONLY when this returns {@code true}.
   */
  public void maybeSendHeartbeat(boolean isTelemetryEnabled, PingFn pingFn) {
    maybeSendHeartbeat(isTelemetryEnabled, System.getenv("AXONFLOW_TELEMETRY"), pingFn);
  }

  /**
   * Package-private overload that accepts the {@code AXONFLOW_TELEMETRY}
   * env-var value as a parameter. Used by tests to avoid being suppressed
   * by the surefire/failsafe pom.xml env injection that pins the variable
   * to {@code off} during automated runs.
   */
  void maybeSendHeartbeat(boolean isTelemetryEnabled, String envOptOut, PingFn pingFn) {
    if (envOptOut != null && "off".equalsIgnoreCase(envOptOut.trim())) {
      return;
    }
    if (!isTelemetryEnabled) {
      return;
    }

    long now = System.currentTimeMillis();

    boolean shouldPing;
    lock.lock();
    try {
      if (inFlight) {
        return;
      }
      if (lastCheckedMillis >= 0 && (now - lastCheckedMillis) < HEARTBEAT_GUARD_INTERVAL_MS) {
        return;
      }
      lastCheckedMillis = now;

      long mtime = readStampMtimeMillis();
      if (mtime > 0 && (now - mtime) < HEARTBEAT_INTERVAL_MS) {
        return;
      }

      inFlight = true;
      shouldPing = true;
    } finally {
      lock.unlock();
    }

    if (!shouldPing) {
      return;
    }

    boolean ok;
    try {
      ok = pingFn.send();
    } catch (RuntimeException e) {
      logger.debug("heartbeat ping threw, treating as failure", e);
      ok = false;
    }

    // Stamp write happens OUTSIDE the lock — Files.* syscalls (mkdir +
    // createTempFile + writeString + move) are blocking, and holding the
    // lock through them serializes any concurrent gate run on the same
    // singleton through file IO. Clear inFlight first so other callers
    // can fast-path through; the stamp write is independent.
    lock.lock();
    try {
      inFlight = false;
    } finally {
      lock.unlock();
    }
    if (ok) {
      writeStampAtomic();
    }
  }

  // ---- test helpers ----

  /** Test-only: force {@code lastCheckedMillis}. */
  void setLastCheckedMillisForTest(long value) {
    lock.lock();
    try {
      lastCheckedMillis = value;
    } finally {
      lock.unlock();
    }
  }

  // ----------------------------------------------------------------
  // Process-global singleton — concurrent AxonFlow constructions on
  // the same JVM coalesce onto a single ping per heartbeatInterval.
  // The singleton must be shared across all AxonFlow instances or the
  // gate's in-flight + 1-hour cache offer no protection in the
  // multi-client startup pattern (a deployment that constructs N
  // clients concurrently before any stamp exists fires N pings
  // pre-fix; this singleton brings it to 1).
  //
  // Volatile read on access; the swap path takes the same lock as
  // gate operations to keep ordering simple. Production code goes
  // through {@link #shared()}; tests override via
  // {@link #replaceForTest} and {@link #restoreForTest}.
  // ----------------------------------------------------------------

  private static volatile HeartbeatState SHARED = new HeartbeatState();

  /** Returns the process-global heartbeat singleton. */
  public static HeartbeatState shared() {
    return SHARED;
  }

  /**
   * Test-only: install a fresh singleton at the given stamp path
   * (or {@code null} for "no persistence"), returning the previous
   * instance so the caller can restore it on cleanup.
   *
   * <p>{@code synchronized} on the class so parallel JUnit suites that
   * both call {@code replaceForTest} cannot race on the swap and lose
   * one another's restore handle.
   */
  public static synchronized HeartbeatState replaceForTest(Path stampPath) {
    HeartbeatState previous = SHARED;
    SHARED = new HeartbeatState(stampPath);
    return previous;
  }

  /** Test-only: restore a previously-saved singleton. */
  public static synchronized void restoreForTest(HeartbeatState state) {
    SHARED = state;
  }
}
