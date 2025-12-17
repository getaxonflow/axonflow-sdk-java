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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResponseCache")
class ResponseCacheTest {

    @Test
    @DisplayName("should store and retrieve values")
    void shouldStoreAndRetrieveValues() {
        ResponseCache cache = new ResponseCache(CacheConfig.defaults());

        String key = "test-key";
        String value = "test-value";

        cache.put(key, value);
        Optional<String> retrieved = cache.get(key, String.class);

        assertThat(retrieved).isPresent().contains(value);
    }

    @Test
    @DisplayName("should return empty for missing keys")
    void shouldReturnEmptyForMissingKeys() {
        ResponseCache cache = new ResponseCache(CacheConfig.defaults());

        Optional<String> result = cache.get("nonexistent", String.class);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty for wrong type")
    void shouldReturnEmptyForWrongType() {
        ResponseCache cache = new ResponseCache(CacheConfig.defaults());

        cache.put("key", "string-value");
        Optional<Integer> result = cache.get("key", Integer.class);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should not cache when disabled")
    void shouldNotCacheWhenDisabled() {
        ResponseCache cache = new ResponseCache(CacheConfig.disabled());

        cache.put("key", "value");
        Optional<String> result = cache.get("key", String.class);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should invalidate specific key")
    void shouldInvalidateSpecificKey() {
        ResponseCache cache = new ResponseCache(CacheConfig.defaults());

        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.invalidate("key1");

        assertThat(cache.get("key1", String.class)).isEmpty();
        assertThat(cache.get("key2", String.class)).isPresent();
    }

    @Test
    @DisplayName("should clear all entries")
    void shouldClearAllEntries() {
        ResponseCache cache = new ResponseCache(CacheConfig.defaults());

        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.clear();

        assertThat(cache.get("key1", String.class)).isEmpty();
        assertThat(cache.get("key2", String.class)).isEmpty();
    }

    @Test
    @DisplayName("should generate consistent cache keys")
    void shouldGenerateConsistentKeys() {
        String key1 = ResponseCache.generateKey("chat", "hello", "user-123");
        String key2 = ResponseCache.generateKey("chat", "hello", "user-123");
        String key3 = ResponseCache.generateKey("chat", "hello", "user-456");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1).isNotEqualTo(key3);
    }

    @Test
    @DisplayName("should handle null values in key generation")
    void shouldHandleNullsInKeyGeneration() {
        String key1 = ResponseCache.generateKey(null, "query", "user");
        String key2 = ResponseCache.generateKey("", "query", "user");
        String key3 = ResponseCache.generateKey("type", null, null);

        assertThat(key1).isNotEmpty();
        assertThat(key2).isNotEmpty();
        assertThat(key3).isNotEmpty();
    }

    @Test
    @DisplayName("should provide stats")
    void shouldProvideStats() {
        ResponseCache cache = new ResponseCache(CacheConfig.defaults());

        String stats = cache.getStats();

        assertThat(stats).isNotEmpty();
    }

    @Test
    @DisplayName("should provide stats for disabled cache")
    void shouldProvideStatsForDisabledCache() {
        ResponseCache cache = new ResponseCache(CacheConfig.disabled());

        String stats = cache.getStats();

        assertThat(stats).isEqualTo("Cache disabled");
    }

    @Test
    @DisplayName("should not cache null values")
    void shouldNotCacheNullValues() {
        ResponseCache cache = new ResponseCache(CacheConfig.defaults());

        cache.put("key", null);
        Optional<String> result = cache.get("key", String.class);

        assertThat(result).isEmpty();
    }
}
