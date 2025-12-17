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

import com.axonflow.sdk.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Executes operations with retry logic and exponential backoff.
 */
public final class RetryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RetryExecutor.class);

    private final RetryConfig config;

    /**
     * Creates a new retry executor.
     *
     * @param config the retry configuration
     */
    public RetryExecutor(RetryConfig config) {
        this.config = config != null ? config : RetryConfig.defaults();
    }

    /**
     * Executes an operation with retry logic.
     *
     * @param <T>       the return type
     * @param operation the operation to execute
     * @param context   a description of the operation for logging
     * @return the operation result
     * @throws AxonFlowException if all retries fail
     */
    public <T> T execute(Callable<T> operation, String context) throws AxonFlowException {
        if (!config.isEnabled()) {
            return executeOnce(operation, context);
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= config.getMaxAttempts(); attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;

                if (!isRetryable(e)) {
                    throw wrapException(e, context);
                }

                if (attempt < config.getMaxAttempts()) {
                    Duration delay = config.getDelayForAttempt(attempt);
                    logger.warn("Attempt {}/{} failed for {}, retrying in {}ms: {}",
                        attempt, config.getMaxAttempts(), context, delay.toMillis(), e.getMessage());
                    sleep(delay);
                } else {
                    logger.error("All {} attempts failed for {}", config.getMaxAttempts(), context);
                }
            }
        }

        throw wrapException(lastException, context);
    }

    private <T> T executeOnce(Callable<T> operation, String context) throws AxonFlowException {
        try {
            return operation.call();
        } catch (Exception e) {
            throw wrapException(e, context);
        }
    }

    /**
     * Determines if an exception is retryable.
     *
     * <p>Retryable exceptions include:
     * <ul>
     *   <li>Connection/network errors</li>
     *   <li>Timeouts</li>
     *   <li>Server errors (5xx)</li>
     *   <li>Rate limiting (429)</li>
     * </ul>
     *
     * <p>Non-retryable exceptions include:
     * <ul>
     *   <li>Authentication errors (401, 403)</li>
     *   <li>Client errors (400, 404)</li>
     *   <li>Policy violations</li>
     * </ul>
     *
     * @param e the exception to check
     * @return true if the operation should be retried
     */
    private boolean isRetryable(Exception e) {
        // Don't retry authentication or policy errors
        if (e instanceof AuthenticationException ||
            e instanceof PolicyViolationException ||
            e instanceof ConfigurationException) {
            return false;
        }

        // Retry connection and timeout errors
        if (e instanceof ConnectionException ||
            e instanceof TimeoutException ||
            e instanceof SocketTimeoutException ||
            e instanceof IOException) {
            return true;
        }

        // Retry rate limit errors
        if (e instanceof RateLimitException) {
            return true;
        }

        // Retry server errors (5xx)
        if (e instanceof AxonFlowException) {
            int statusCode = ((AxonFlowException) e).getStatusCode();
            return statusCode >= 500 && statusCode < 600;
        }

        // Default to not retrying unknown errors
        return false;
    }

    private AxonFlowException wrapException(Exception e, String context) {
        if (e instanceof AxonFlowException) {
            return (AxonFlowException) e;
        }

        if (e instanceof SocketTimeoutException) {
            return new TimeoutException("Request timed out: " + context, e);
        }

        if (e instanceof IOException) {
            return new ConnectionException("Connection failed: " + context, e);
        }

        return new AxonFlowException("Operation failed: " + context, e);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AxonFlowException("Retry interrupted", e);
        }
    }
}
