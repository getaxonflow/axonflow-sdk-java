// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Names the engine call a PEP makes to discharge an {@link Obligation} (ADR-056, epic #2563).
 *
 * <p>Fulfillment is a property of the contract, not of PEP-author discipline: a conforming PEP
 * POSTs the obligation's source content to {@code endpoint} and forwards the engine-redacted
 * content the endpoint returns. There is no client-side redaction.
 *
 * <p>{@code contentTypes} advertises the mime-types the endpoint's detectors can handle today. The
 * contract is content-type-agnostic: a PEP holding content of a type NOT in this list must fail
 * closed rather than forward it unredacted. Mirrors the platform {@code ObligationFulfillment}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ObligationFulfillment {

  @JsonProperty("endpoint")
  private final String endpoint;

  @JsonProperty("method")
  private final String method;

  @JsonProperty("phase")
  private final String phase;

  @JsonProperty("content_types")
  private final List<String> contentTypes;

  /**
   * Creates a fulfillment descriptor.
   *
   * @param endpoint the engine path, e.g. {@code /api/v1/mcp/check-input}
   * @param method the HTTP method, e.g. {@code POST}
   * @param phase {@code "request"} or {@code "response"}
   * @param contentTypes mime-types the endpoint can redact today (may be null/empty)
   */
  @JsonCreator
  public ObligationFulfillment(
      @JsonProperty("endpoint") String endpoint,
      @JsonProperty("method") String method,
      @JsonProperty("phase") String phase,
      @JsonProperty("content_types") List<String> contentTypes) {
    this.endpoint = endpoint;
    this.method = method;
    this.phase = phase;
    this.contentTypes = contentTypes;
  }

  /** Returns the engine path the PEP POSTs content to, e.g. {@code /api/v1/mcp/check-input}. */
  public String getEndpoint() {
    return endpoint;
  }

  /** Returns the HTTP method, e.g. {@code POST}. */
  public String getMethod() {
    return method;
  }

  /** Returns the fulfillment phase: {@code "request"} or {@code "response"}. */
  public String getPhase() {
    return phase;
  }

  /** Returns the mime-types the endpoint can redact today, or null when unspecified. */
  public List<String> getContentTypes() {
    return contentTypes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ObligationFulfillment that = (ObligationFulfillment) o;
    return Objects.equals(endpoint, that.endpoint)
        && Objects.equals(method, that.method)
        && Objects.equals(phase, that.phase)
        && Objects.equals(contentTypes, that.contentTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(endpoint, method, phase, contentTypes);
  }

  @Override
  public String toString() {
    return "ObligationFulfillment{"
        + "endpoint='"
        + endpoint
        + '\''
        + ", method='"
        + method
        + '\''
        + ", phase='"
        + phase
        + '\''
        + ", contentTypes="
        + contentTypes
        + '}';
  }
}
