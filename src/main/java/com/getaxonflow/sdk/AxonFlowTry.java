// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Legacy registration helper for self-hosted Community servers. */
public class AxonFlowTry {

  public static final String TRY_ENDPOINT = "https://try.getaxonflow.com";
  public static final String HOSTED_START_URL = "https://portal.try.getaxonflow.com/hosted/start";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /**
   * Direct new Hosted buyers to the verified Portal journey. Existing linked credentials can be
   * supplied through {@link AxonFlowConfig.Builder#clientId(String)} and {@link
   * AxonFlowConfig.Builder#clientSecret(String)}.
   */
  public static TryRegistration register() throws IOException, InterruptedException {
    return register("", TRY_ENDPOINT);
  }

  /** Legacy overload: directs new Hosted buyers to verified Portal signup. */
  public static TryRegistration register(String label) throws IOException, InterruptedException {
    return register(label, TRY_ENDPOINT);
  }

  /** Register with a custom self-hosted Community endpoint. */
  public static TryRegistration register(String label, String endpoint)
      throws IOException, InterruptedException {
    URI origin = URI.create(endpoint);
    String host = origin.getHost();
    if (host != null) {
      host = host.replaceFirst("\\.+$", "");
    }
    if ("try.getaxonflow.com".equalsIgnoreCase(host)
        || "try-staging.getaxonflow.com".equalsIgnoreCase(host)) {
      throw new IOException(
          "New Hosted accounts start at "
              + HOSTED_START_URL
              + ": verify your email, complete account and plan setup, then link an API client in the Portal. Configure the linked client ID and secret explicitly in AxonFlowConfig.");
    }
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

  /** Registration response from a custom self-hosted Community server. */
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
