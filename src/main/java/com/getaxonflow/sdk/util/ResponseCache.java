// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe cache for API responses.
 *
 * <p>Uses Caffeine for high-performance caching with automatic expiration.
 */
public final class ResponseCache {

  private static final Logger logger = LoggerFactory.getLogger(ResponseCache.class);

  private final Cache<String, CachedResponse> cache;
  private final boolean enabled;

  /**
   * Creates a new response cache.
   *
   * @param config the cache configuration
   */
  public ResponseCache(CacheConfig config) {
    this.enabled = config.isEnabled();
    if (enabled) {
      this.cache =
          Caffeine.newBuilder()
              .maximumSize(config.getMaxSize())
              .expireAfterWrite(config.getTtl())
              .recordStats()
              .build();
    } else {
      this.cache = null;
    }
  }

  /**
   * Gets a cached response.
   *
   * @param <T> the response type
   * @param cacheKey the cache key
   * @param type the expected response type
   * @return the cached response, or empty if not found
   */
  @SuppressWarnings("unchecked")
  public <T> Optional<T> get(String cacheKey, Class<T> type) {
    if (!enabled || cache == null) {
      return Optional.empty();
    }

    CachedResponse cached = cache.getIfPresent(cacheKey);
    if (cached != null && type.isInstance(cached.getResponse())) {
      logger.debug("Cache hit for key: {}", cacheKey);
      return Optional.of((T) cached.getResponse());
    }

    logger.debug("Cache miss for key: {}", cacheKey);
    return Optional.empty();
  }

  /**
   * Stores a response in the cache.
   *
   * @param cacheKey the cache key
   * @param response the response to cache
   */
  public void put(String cacheKey, Object response) {
    if (!enabled || cache == null || response == null) {
      return;
    }

    cache.put(cacheKey, new CachedResponse(response));
    logger.debug("Cached response for key: {}", cacheKey);
  }

  /**
   * Invalidates a specific cache entry.
   *
   * @param cacheKey the cache key to invalidate
   */
  public void invalidate(String cacheKey) {
    if (cache != null) {
      cache.invalidate(cacheKey);
    }
  }

  /** Clears all cached entries. */
  public void clear() {
    if (cache != null) {
      cache.invalidateAll();
    }
  }

  /**
   * Generates a cache key from request parameters.
   *
   * @param requestType the type of request
   * @param query the query string
   * @param userToken the user token
   * @return a unique cache key
   */
  public static String generateKey(String requestType, String query, String userToken) {
    return generateKey(requestType, query, userToken, null);
  }

  /**
   * Generates a cache key that also distinguishes the caller's READ-PATH identity.
   *
   * <p>{@code readIdentity} is a different thing from {@code userToken}: that one is the write-path
   * body field the call was made with, and this one is the {@code X-User-Token} header the request
   * will carry. Both belong in the key, because both can change the answer.
   *
   * <p>It has to be here because a client derived with {@code asUser} SHARES the parent's cache by
   * design — deriving one per request must not cost a cache. Without this component, {@code
   * base.asUser(ALICE)} and {@code base.asUser(BOB)} making the same call hash to the same entry,
   * so one request is sent carrying ALICE and BOB is served ALICE's governed response from the
   * cache, with nothing evaluated on his behalf at all. Measured on the two-derived-clients test:
   * one request reached the server for two callers.
   *
   * <p>The identity is hashed, never stored, so a cache dump cannot yield the credential.
   *
   * @param requestType the type of request
   * @param query the query string
   * @param userToken the write-path user token carried in the request body
   * @param readIdentity the per-user identity the request will present, or {@code null} for none
   * @return a unique cache key
   */
  public static String generateKey(
      String requestType, String query, String userToken, String readIdentity) {
    String input =
        String.format(
            "%s:%s:%s:%s",
            requestType != null ? requestType : "",
            query != null ? query : "",
            userToken != null ? userToken : "",
            readIdentity != null ? readIdentity : "");

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      // Fall back to simple hash if SHA-256 not available
      return String.valueOf(input.hashCode());
    }
  }

  /**
   * Returns cache statistics.
   *
   * @return cache statistics string
   */
  public String getStats() {
    if (cache == null) {
      return "Cache disabled";
    }
    return cache.stats().toString();
  }

  /** Wrapper for cached responses. */
  private static final class CachedResponse {
    private final Object response;
    private final long cachedAt;

    CachedResponse(Object response) {
      this.response = response;
      this.cachedAt = System.currentTimeMillis();
    }

    Object getResponse() {
      return response;
    }

    long getCachedAt() {
      return cachedAt;
    }
  }
}
