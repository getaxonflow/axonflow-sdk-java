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
package com.axonflow.sdk.exceptions;

import java.time.Duration;
import java.time.Instant;

/**
 * Thrown when the rate limit has been exceeded.
 */
public class RateLimitException extends AxonFlowException {

    private static final long serialVersionUID = 1L;

    private final int limit;
    private final int remaining;
    private final Instant resetAt;

    /**
     * Creates a new RateLimitException.
     *
     * @param message the error message
     */
    public RateLimitException(String message) {
        super(message, 429, "RATE_LIMIT_EXCEEDED");
        this.limit = 0;
        this.remaining = 0;
        this.resetAt = null;
    }

    /**
     * Creates a new RateLimitException with rate limit details.
     *
     * @param message   the error message
     * @param limit     the maximum requests allowed
     * @param remaining the remaining requests in the current window
     * @param resetAt   when the rate limit resets
     */
    public RateLimitException(String message, int limit, int remaining, Instant resetAt) {
        super(message, 429, "RATE_LIMIT_EXCEEDED");
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
    }

    /**
     * Returns the maximum number of requests allowed.
     *
     * @return the rate limit
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Returns the remaining requests in the current window.
     *
     * @return the remaining count
     */
    public int getRemaining() {
        return remaining;
    }

    /**
     * Returns when the rate limit resets.
     *
     * @return the reset time
     */
    public Instant getResetAt() {
        return resetAt;
    }

    /**
     * Returns the duration until the rate limit resets.
     *
     * @return the duration until reset, or Duration.ZERO if already reset
     */
    public Duration getRetryAfter() {
        if (resetAt == null) {
            return Duration.ZERO;
        }
        Duration duration = Duration.between(Instant.now(), resetAt);
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
