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

/**
 * Thrown when a request times out.
 */
public class TimeoutException extends AxonFlowException {

    private static final long serialVersionUID = 1L;

    private final Duration timeout;

    /**
     * Creates a new TimeoutException.
     *
     * @param message the error message
     */
    public TimeoutException(String message) {
        super(message, 0, "TIMEOUT");
        this.timeout = null;
    }

    /**
     * Creates a new TimeoutException with timeout duration.
     *
     * @param message the error message
     * @param timeout the configured timeout duration
     */
    public TimeoutException(String message, Duration timeout) {
        super(message, 0, "TIMEOUT");
        this.timeout = timeout;
    }

    /**
     * Creates a new TimeoutException with cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public TimeoutException(String message, Throwable cause) {
        super(message, 0, "TIMEOUT", cause);
        this.timeout = null;
    }

    /**
     * Creates a new TimeoutException with timeout and cause.
     *
     * @param message the error message
     * @param timeout the configured timeout duration
     * @param cause   the underlying cause
     */
    public TimeoutException(String message, Duration timeout, Throwable cause) {
        super(message, 0, "TIMEOUT", cause);
        this.timeout = timeout;
    }

    /**
     * Returns the configured timeout duration.
     *
     * @return the timeout duration, or null if not specified
     */
    public Duration getTimeout() {
        return timeout;
    }
}
