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
package com.getaxonflow.sdk.telemetry;

import com.getaxonflow.sdk.AxonFlowConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Fire-and-forget telemetry reporter that sends anonymous usage pings
 * to the AxonFlow checkpoint endpoint.
 *
 * <p>Telemetry is completely anonymous and contains no user data, only
 * SDK version, runtime environment, and deployment mode information.
 *
 * <p>Telemetry can be disabled via:
 * <ul>
 *   <li>Setting environment variable {@code DO_NOT_TRACK=1}</li>
 *   <li>Setting environment variable {@code AXONFLOW_TELEMETRY=off}</li>
 *   <li>Setting {@code telemetry(false)} on the config builder</li>
 * </ul>
 *
 * <p>By default, telemetry is OFF in sandbox mode and ON in production mode.
 */
public class TelemetryReporter {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryReporter.class);

    static final String DEFAULT_ENDPOINT = "https://checkpoint.getaxonflow.com/v1/ping";
    private static final int TIMEOUT_SECONDS = 3;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * Sends an anonymous telemetry ping asynchronously (fire-and-forget).
     *
     * @param mode             the deployment mode (e.g. "production", "sandbox")
     * @param sdkEndpoint      the configured SDK endpoint (unused in payload, present for future use)
     * @param telemetryEnabled config override for telemetry (null = use default based on mode)
     * @param debug            whether debug logging is enabled
     */
    public static void sendPing(String mode, String sdkEndpoint, Boolean telemetryEnabled, boolean debug,
                               boolean hasCredentials) {
        sendPing(mode, sdkEndpoint, telemetryEnabled, debug, hasCredentials,
                System.getenv("DO_NOT_TRACK"),
                System.getenv("AXONFLOW_TELEMETRY"),
                System.getenv("AXONFLOW_CHECKPOINT_URL"));
    }

    /**
     * Package-private overload for testability, accepting env var values as parameters.
     */
    static void sendPing(String mode, String sdkEndpoint, Boolean telemetryEnabled, boolean debug,
                         boolean hasCredentials,
                         String doNotTrack, String axonflowTelemetry, String checkpointUrl) {
        if (!isEnabled(mode, telemetryEnabled, hasCredentials, doNotTrack, axonflowTelemetry)) {
            if (debug) {
                logger.debug("Telemetry is disabled, skipping ping");
            }
            return;
        }

        String endpoint = (checkpointUrl != null && !checkpointUrl.isEmpty())
                ? checkpointUrl
                : DEFAULT_ENDPOINT;

        CompletableFuture.runAsync(() -> {
            try {
                String payload = buildPayload(mode);

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build();

                RequestBody body = RequestBody.create(payload, JSON);
                Request request = new Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (debug) {
                        logger.debug("Telemetry ping sent, status={}", response.code());
                    }
                }
            } catch (Exception e) {
                // Silent failure - telemetry must never disrupt SDK operation
                if (debug) {
                    logger.debug("Telemetry ping failed (silent): {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Determines whether telemetry is enabled based on environment and config.
     *
     * <p>Priority order:
     * <ol>
     *   <li>{@code DO_NOT_TRACK=1} environment variable disables telemetry</li>
     *   <li>{@code AXONFLOW_TELEMETRY=off} environment variable disables telemetry</li>
     *   <li>Config override ({@code Boolean.TRUE} or {@code Boolean.FALSE}) takes precedence</li>
     *   <li>Default: ON for production/enterprise with credentials, OFF for sandbox or no credentials</li>
     * </ol>
     *
     * @param mode           the deployment mode
     * @param configOverride explicit config override (null = use default)
     * @param hasCredentials whether the client has credentials (clientId + clientSecret)
     * @return true if telemetry should be sent
     */
    static boolean isEnabled(String mode, Boolean configOverride, boolean hasCredentials) {
        return isEnabled(mode, configOverride, hasCredentials,
                System.getenv("DO_NOT_TRACK"), System.getenv("AXONFLOW_TELEMETRY"));
    }

    /**
     * Package-private for testing. Accepts env var values as parameters.
     */
    static boolean isEnabled(String mode, Boolean configOverride, boolean hasCredentials,
                             String doNotTrack, String axonflowTelemetry) {
        if ("1".equals(doNotTrack)) {
            return false;
        }
        if ("off".equalsIgnoreCase(axonflowTelemetry)) {
            return false;
        }
        if (configOverride != null) {
            return configOverride;
        }
        if ("sandbox".equals(mode)) {
            return false;
        }
        return hasCredentials;
    }

    /**
     * Builds the JSON payload for the telemetry ping.
     */
    static String buildPayload(String mode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("sdk", "java");
            root.put("sdk_version", AxonFlowConfig.SDK_VERSION);
            root.putNull("platform_version");
            root.put("os", System.getProperty("os.name"));
            root.put("arch", System.getProperty("os.arch"));
            root.put("runtime_version", System.getProperty("java.version"));
            root.put("deployment_mode", mode);

            ArrayNode features = mapper.createArrayNode();
            root.set("features", features);

            root.put("instance_id", UUID.randomUUID().toString());

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            // Fallback minimal payload
            return "{\"sdk\":\"java\",\"sdk_version\":\"" + AxonFlowConfig.SDK_VERSION + "\"}";
        }
    }

    private TelemetryReporter() {
        // Utility class
    }
}
