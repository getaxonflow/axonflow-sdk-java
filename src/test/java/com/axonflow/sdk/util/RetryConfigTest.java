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
package com.axonflow.sdk.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RetryConfig")
class RetryConfigTest {

    @Test
    @DisplayName("should create defaults")
    void shouldCreateDefaults() {
        RetryConfig config = RetryConfig.defaults();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getMaxAttempts()).isEqualTo(3);
        assertThat(config.getInitialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.getMaxDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getMultiplier()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should create disabled config")
    void shouldCreateDisabled() {
        RetryConfig config = RetryConfig.disabled();

        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should build with custom values")
    void shouldBuildWithCustomValues() {
        RetryConfig config = RetryConfig.builder()
            .enabled(true)
            .maxAttempts(5)
            .initialDelay(Duration.ofMillis(500))
            .maxDelay(Duration.ofSeconds(10))
            .multiplier(1.5)
            .build();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getMaxAttempts()).isEqualTo(5);
        assertThat(config.getInitialDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(config.getMaxDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.getMultiplier()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("should calculate delay for attempts")
    void shouldCalculateDelayForAttempts() {
        RetryConfig config = RetryConfig.builder()
            .initialDelay(Duration.ofSeconds(1))
            .multiplier(2.0)
            .maxDelay(Duration.ofSeconds(30))
            .build();

        assertThat(config.getDelayForAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.getDelayForAttempt(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.getDelayForAttempt(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(config.getDelayForAttempt(4)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    @DisplayName("should cap delay at max")
    void shouldCapDelayAtMax() {
        RetryConfig config = RetryConfig.builder()
            .initialDelay(Duration.ofSeconds(10))
            .multiplier(2.0)
            .maxDelay(Duration.ofSeconds(15))
            .build();

        assertThat(config.getDelayForAttempt(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.getDelayForAttempt(2)).isEqualTo(Duration.ofSeconds(15)); // Capped
        assertThat(config.getDelayForAttempt(3)).isEqualTo(Duration.ofSeconds(15)); // Capped
    }

    @Test
    @DisplayName("should validate max attempts range")
    void shouldValidateMaxAttempts() {
        assertThatThrownBy(() -> RetryConfig.builder().maxAttempts(0).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts");

        assertThatThrownBy(() -> RetryConfig.builder().maxAttempts(11).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts");
    }

    @Test
    @DisplayName("should validate initial delay")
    void shouldValidateInitialDelay() {
        assertThatThrownBy(() -> RetryConfig.builder().initialDelay(null).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("initialDelay");

        assertThatThrownBy(() -> RetryConfig.builder().initialDelay(Duration.ofSeconds(-1)).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("initialDelay");
    }

    @Test
    @DisplayName("should validate multiplier")
    void shouldValidateMultiplier() {
        assertThatThrownBy(() -> RetryConfig.builder().multiplier(0.5).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multiplier");
    }

    @Test
    @DisplayName("should implement equals and hashCode")
    void shouldImplementEqualsAndHashCode() {
        RetryConfig config1 = RetryConfig.builder().maxAttempts(3).build();
        RetryConfig config2 = RetryConfig.builder().maxAttempts(3).build();
        RetryConfig config3 = RetryConfig.builder().maxAttempts(5).build();

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        assertThat(config1).isNotEqualTo(config3);
    }
}
