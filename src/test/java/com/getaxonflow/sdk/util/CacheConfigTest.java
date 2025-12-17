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

import static org.assertj.core.api.Assertions.*;

@DisplayName("CacheConfig")
class CacheConfigTest {

    @Test
    @DisplayName("should create defaults")
    void shouldCreateDefaults() {
        CacheConfig config = CacheConfig.defaults();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.getMaxSize()).isEqualTo(1000);
    }

    @Test
    @DisplayName("should create disabled config")
    void shouldCreateDisabled() {
        CacheConfig config = CacheConfig.disabled();

        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should build with custom values")
    void shouldBuildWithCustomValues() {
        CacheConfig config = CacheConfig.builder()
            .enabled(true)
            .ttl(Duration.ofMinutes(5))
            .maxSize(500)
            .build();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.getMaxSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("should validate TTL")
    void shouldValidateTtl() {
        assertThatThrownBy(() -> CacheConfig.builder().ttl(null).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ttl");

        assertThatThrownBy(() -> CacheConfig.builder().ttl(Duration.ofSeconds(-1)).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ttl");
    }

    @Test
    @DisplayName("should validate max size")
    void shouldValidateMaxSize() {
        assertThatThrownBy(() -> CacheConfig.builder().maxSize(0).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxSize");
    }

    @Test
    @DisplayName("should implement equals and hashCode")
    void shouldImplementEqualsAndHashCode() {
        CacheConfig config1 = CacheConfig.builder().maxSize(100).build();
        CacheConfig config2 = CacheConfig.builder().maxSize(100).build();
        CacheConfig config3 = CacheConfig.builder().maxSize(200).build();

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        assertThat(config1).isNotEqualTo(config3);
    }
}
