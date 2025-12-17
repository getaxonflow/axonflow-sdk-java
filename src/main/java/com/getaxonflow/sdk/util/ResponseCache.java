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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

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
            this.cache = Caffeine.newBuilder()
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
     * @param <T>      the response type
     * @param cacheKey the cache key
     * @param type     the expected response type
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

    /**
     * Clears all cached entries.
     */
    public void clear() {
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    /**
     * Generates a cache key from request parameters.
     *
     * @param requestType the type of request
     * @param query       the query string
     * @param userToken   the user token
     * @return a unique cache key
     */
    public static String generateKey(String requestType, String query, String userToken) {
        String input = String.format("%s:%s:%s",
            requestType != null ? requestType : "",
            query != null ? query : "",
            userToken != null ? userToken : "");

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

    /**
     * Wrapper for cached responses.
     */
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
