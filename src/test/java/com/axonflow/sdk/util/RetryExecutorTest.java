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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RetryExecutor")
class RetryExecutorTest {

    @Test
    @DisplayName("should execute without retry on success")
    void shouldExecuteWithoutRetryOnSuccess() {
        RetryExecutor executor = new RetryExecutor(RetryConfig.defaults());
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.execute(() -> {
            attempts.incrementAndGet();
            return "success";
        }, "test");

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("should retry on IOException")
    void shouldRetryOnIOException() {
        RetryConfig config = RetryConfig.builder()
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(10))
            .build();
        RetryExecutor executor = new RetryExecutor(config);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("Connection failed");
            }
            return "success";
        }, "test");

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("should retry on SocketTimeoutException")
    void shouldRetryOnSocketTimeout() {
        RetryConfig config = RetryConfig.builder()
            .maxAttempts(2)
            .initialDelay(Duration.ofMillis(10))
            .build();
        RetryExecutor executor = new RetryExecutor(config);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new SocketTimeoutException("Read timed out");
            }
            return "success";
        }, "test");

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("should not retry on AuthenticationException")
    void shouldNotRetryOnAuthenticationException() {
        RetryConfig config = RetryConfig.builder()
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(10))
            .build();
        RetryExecutor executor = new RetryExecutor(config);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new AuthenticationException("Invalid credentials");
        }, "test"))
            .isInstanceOf(AuthenticationException.class);

        assertThat(attempts.get()).isEqualTo(1); // No retry
    }

    @Test
    @DisplayName("should not retry on PolicyViolationException")
    void shouldNotRetryOnPolicyViolationException() {
        RetryConfig config = RetryConfig.builder()
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(10))
            .build();
        RetryExecutor executor = new RetryExecutor(config);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new PolicyViolationException("Blocked by policy");
        }, "test"))
            .isInstanceOf(PolicyViolationException.class);

        assertThat(attempts.get()).isEqualTo(1); // No retry
    }

    @Test
    @DisplayName("should throw after max attempts")
    void shouldThrowAfterMaxAttempts() {
        RetryConfig config = RetryConfig.builder()
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(10))
            .build();
        RetryExecutor executor = new RetryExecutor(config);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new IOException("Always fails");
        }, "test"))
            .isInstanceOf(ConnectionException.class);

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("should not retry when disabled")
    void shouldNotRetryWhenDisabled() {
        RetryExecutor executor = new RetryExecutor(RetryConfig.disabled());
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new IOException("Connection failed");
        }, "test"))
            .isInstanceOf(ConnectionException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("should retry on RateLimitException")
    void shouldRetryOnRateLimitException() {
        RetryConfig config = RetryConfig.builder()
            .maxAttempts(2)
            .initialDelay(Duration.ofMillis(10))
            .build();
        RetryExecutor executor = new RetryExecutor(config);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RateLimitException("Rate limit exceeded");
            }
            return "success";
        }, "test");

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("should wrap generic exceptions")
    void shouldWrapGenericExceptions() {
        RetryExecutor executor = new RetryExecutor(RetryConfig.disabled());

        assertThatThrownBy(() -> executor.execute(() -> {
            throw new RuntimeException("Unexpected error");
        }, "test"))
            .isInstanceOf(AxonFlowException.class)
            .hasMessageContaining("test");
    }

    @Test
    @DisplayName("should handle null config")
    void shouldHandleNullConfig() {
        RetryExecutor executor = new RetryExecutor(null);

        String result = executor.execute(() -> "success", "test");

        assertThat(result).isEqualTo("success");
    }
}
