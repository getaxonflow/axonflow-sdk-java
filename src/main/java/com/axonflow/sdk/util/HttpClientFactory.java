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

import com.axonflow.sdk.AxonFlowConfig;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating configured HTTP clients.
 */
public final class HttpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);

    private HttpClientFactory() {
        // Utility class
    }

    /**
     * Creates an OkHttpClient configured according to the SDK configuration.
     *
     * @param config the SDK configuration
     * @return a configured OkHttpClient
     */
    public static OkHttpClient create(AxonFlowConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .callTimeout(config.getTimeout().toMillis() * 2, TimeUnit.MILLISECONDS);

        if (config.isInsecureSkipVerify()) {
            configureInsecureSsl(builder);
        }

        if (config.isDebug()) {
            builder.addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                logger.debug("Request: {} {}", request.method(), request.url());
                okhttp3.Response response = chain.proceed(request);
                logger.debug("Response: {} {} ({}ms)",
                    response.code(), response.message(),
                    response.receivedResponseAtMillis() - response.sentRequestAtMillis());
                return response;
            });
        }

        return builder.build();
    }

    @SuppressWarnings("java:S4830") // Intentionally trusting all certificates when insecureSkipVerify is enabled
    private static void configureInsecureSsl(OkHttpClient.Builder builder) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Trust all clients
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Trust all servers
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            logger.warn("SSL certificate verification is disabled. This should only be used in development.");
        } catch (Exception e) {
            logger.error("Failed to configure insecure SSL", e);
        }
    }
}
