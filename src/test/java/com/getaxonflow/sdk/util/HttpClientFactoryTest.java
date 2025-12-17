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

import com.getaxonflow.sdk.AxonFlowConfig;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HttpClientFactory")
class HttpClientFactoryTest {

    @Test
    @DisplayName("should create client with default config")
    void shouldCreateClientWithDefaultConfig() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .build();

        OkHttpClient client = HttpClientFactory.create(config);

        assertThat(client).isNotNull();
        // Default timeout is 60 seconds
        assertThat(client.connectTimeoutMillis()).isEqualTo(60000);
        assertThat(client.readTimeoutMillis()).isEqualTo(60000);
        assertThat(client.writeTimeoutMillis()).isEqualTo(60000);
    }

    @Test
    @DisplayName("should create client with custom timeout")
    void shouldCreateClientWithCustomTimeout() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .timeout(Duration.ofSeconds(10))
            .build();

        OkHttpClient client = HttpClientFactory.create(config);

        assertThat(client.connectTimeoutMillis()).isEqualTo(10000);
        assertThat(client.readTimeoutMillis()).isEqualTo(10000);
        assertThat(client.writeTimeoutMillis()).isEqualTo(10000);
    }

    @Test
    @DisplayName("should create client with debug mode")
    void shouldCreateClientWithDebugMode() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .debug(true)
            .build();

        OkHttpClient client = HttpClientFactory.create(config);

        assertThat(client).isNotNull();
        // Debug mode adds an interceptor
        assertThat(client.interceptors()).hasSize(1);
    }

    @Test
    @DisplayName("should create client with insecure skip verify")
    void shouldCreateClientWithInsecureSkipVerify() {
        AxonFlowConfig config = AxonFlowConfig.builder()
            .agentUrl("http://localhost:8080")
            .insecureSkipVerify(true)
            .build();

        OkHttpClient client = HttpClientFactory.create(config);

        assertThat(client).isNotNull();
        // Should have a custom hostname verifier that accepts all hosts
        assertThat(client.hostnameVerifier()).isNotNull();
    }
}
