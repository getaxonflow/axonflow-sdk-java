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
package com.getaxonflow.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Registration helper for try.getaxonflow.com shared evaluation server. */
public class AxonFlowTry {

  public static final String TRY_ENDPOINT = "https://try.getaxonflow.com";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /** Register for a free evaluation tenant. Store the secret securely — it is shown only once. */
  public static TryRegistration register() throws IOException, InterruptedException {
    return register("", TRY_ENDPOINT);
  }

  /** Register with an optional label. */
  public static TryRegistration register(String label) throws IOException, InterruptedException {
    return register(label, TRY_ENDPOINT);
  }

  /** Register with a custom endpoint (for local testing). */
  public static TryRegistration register(String label, String endpoint)
      throws IOException, InterruptedException {
    String body =
        label != null && !label.isEmpty() ? String.format("{\"label\":\"%s\"}", label) : "{}";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "/api/v1/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build();

    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 201) {
      throw new IOException(
          "Registration failed (" + response.statusCode() + "): " + response.body());
    }

    return MAPPER.readValue(response.body(), TryRegistration.class);
  }

  /** Registration response from try.getaxonflow.com. */
  public static class TryRegistration {
    private String tenant_id;
    private String secret;
    private String secret_prefix;
    private String expires_at;
    private String endpoint;
    private String note;

    // Getters
    public String getTenantId() {
      return tenant_id;
    }

    public String getSecret() {
      return secret;
    }

    public String getSecretPrefix() {
      return secret_prefix;
    }

    public String getExpiresAt() {
      return expires_at;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public String getNote() {
      return note;
    }

    // Setters for Jackson
    public void setTenant_id(String v) {
      this.tenant_id = v;
    }

    public void setSecret(String v) {
      this.secret = v;
    }

    public void setSecret_prefix(String v) {
      this.secret_prefix = v;
    }

    public void setExpires_at(String v) {
      this.expires_at = v;
    }

    public void setEndpoint(String v) {
      this.endpoint = v;
    }

    public void setNote(String v) {
      this.note = v;
    }
  }
}
