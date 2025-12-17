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

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for retry behavior.
 */
public final class RetryConfig {

    /** Default maximum retry attempts. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** Default initial delay between retries. */
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);

    /** Default maximum delay between retries. */
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(30);

    /** Default exponential backoff multiplier. */
    public static final double DEFAULT_MULTIPLIER = 2.0;

    private final boolean enabled;
    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;

    private RetryConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.multiplier = builder.multiplier;
    }

    /**
     * Returns default retry configuration.
     *
     * @return default configuration with retries enabled
     */
    public static RetryConfig defaults() {
        return builder().build();
    }

    /**
     * Returns configuration with retries disabled.
     *
     * @return configuration with retries disabled
     */
    public static RetryConfig disabled() {
        return builder().enabled(false).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Calculates the delay for a given attempt number using exponential backoff.
     *
     * @param attempt the attempt number (1-based)
     * @return the delay duration
     */
    public Duration getDelayForAttempt(int attempt) {
        if (attempt <= 1) {
            return initialDelay;
        }
        long delayMs = (long) (initialDelay.toMillis() * Math.pow(multiplier, attempt - 1));
        return Duration.ofMillis(Math.min(delayMs, maxDelay.toMillis()));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetryConfig that = (RetryConfig) o;
        return enabled == that.enabled &&
               maxAttempts == that.maxAttempts &&
               Double.compare(that.multiplier, multiplier) == 0 &&
               Objects.equals(initialDelay, that.initialDelay) &&
               Objects.equals(maxDelay, that.maxDelay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, maxAttempts, initialDelay, maxDelay, multiplier);
    }

    @Override
    public String toString() {
        return "RetryConfig{" +
               "enabled=" + enabled +
               ", maxAttempts=" + maxAttempts +
               ", initialDelay=" + initialDelay +
               ", maxDelay=" + maxDelay +
               ", multiplier=" + multiplier +
               '}';
    }

    /**
     * Builder for RetryConfig.
     */
    public static final class Builder {
        private boolean enabled = true;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration initialDelay = DEFAULT_INITIAL_DELAY;
        private Duration maxDelay = DEFAULT_MAX_DELAY;
        private double multiplier = DEFAULT_MULTIPLIER;

        private Builder() {}

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1");
            }
            if (maxAttempts > 10) {
                throw new IllegalArgumentException("maxAttempts cannot exceed 10");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException("initialDelay must be non-negative");
            }
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            if (maxDelay == null || maxDelay.isNegative()) {
                throw new IllegalArgumentException("maxDelay must be non-negative");
            }
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("multiplier must be at least 1.0");
            }
            this.multiplier = multiplier;
            return this;
        }

        public RetryConfig build() {
            return new RetryConfig(this);
        }
    }
}
