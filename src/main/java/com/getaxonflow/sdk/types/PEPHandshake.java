// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getaxonflow.sdk.exceptions.AxonFlowException;
import com.getaxonflow.sdk.exceptions.PEPHandshakeException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A PEP capability declaration (platform v11.0.0): the exact obligation types and schema versions
 * an enforcement point can discharge, sent on each governed call as the {@value #HEADER} header.
 *
 * <p>The header carries the unpadded base64url encoding of the JSON document {@code
 * {"profile_version":1,"pep_id":...,"audience":...,"capabilities":[{"type":...,"version":...}]}}.
 * On an Enterprise deployment, an allow verdict carrying a mandatory obligation the declared set
 * cannot discharge becomes a deny, so the enforcement point is never handed an instruction it would
 * drop. A Community deployment records the declaration and does not deny on it.
 *
 * <p><b>Where it is sent.</b> Present it with {@link
 * com.getaxonflow.sdk.AxonFlowConfig.Builder#pepHandshake} (every call) or {@link
 * com.getaxonflow.sdk.AxonFlow#withPEPHandshake} (a derived client). The client sends it on the
 * four planes that read it: {@code decide} (and {@code decideAndFulfill} and {@code
 * fulfillRequest}'s engine round-trip), AuthZEN {@code evaluate} and {@code evaluateAll}, {@code
 * mcpCheckInput} and {@code mcpCheckOutput} (and their {@code checkTool*} aliases), and the gateway
 * pre-check ({@code getPolicyApprovedContext}, {@code preCheck}). No other route reads it, and the
 * client never sends it anywhere else.
 *
 * <p><b>Absent is not empty.</b> A client with no declaration sends no header, and the platform
 * behaves as it did before the handshake existed; there is no default, because only the caller
 * knows what its enforcement point can discharge. An empty capability list declares that it
 * discharges nothing, which on Enterprise turns every allow carrying a mandatory obligation into a
 * deny.
 *
 * <p>Built only through {@link #of}, which applies the platform's own rules (its {@code
 * DecodePEPHandshake}) and computes the header once. An instance is therefore always a declaration
 * the platform accepts, and every request carries exactly that value.
 */
@JsonPropertyOrder({"profile_version", "pep_id", "audience", "capabilities"})
public final class PEPHandshake {

  /** The request header the declaration rides on. */
  public static final String HEADER = "X-Axonflow-PEP-Handshake";

  /** The only handshake profile the platform reads, matched exactly. */
  public static final int PROFILE_V1 = 1;

  /** The longest header value the platform reads, in bytes of base64. */
  public static final int MAX_HEADER_BYTES = 4096;

  /** The most capabilities one declaration may carry; a surplus is refused, not truncated. */
  public static final int MAX_CAPABILITIES = 64;

  private static final int MAX_IDENTIFIER_BYTES = 128;

  // The pep_id excludes ":" because the platform builds the enforcement point's identifier as
  // "client:<authenticated credential>:<pep_id>". The audience is composed into nothing, and admits
  // ":" and "/" so a URI is usable as one.
  private static final Pattern PEP_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");
  private static final Pattern AUDIENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]*");

  // A plain mapper, so the document's bytes never depend on how a caller configures its own.
  private static final ObjectMapper CANONICAL = new ObjectMapper();

  @JsonProperty("profile_version")
  private final int profileVersion;

  @JsonProperty("pep_id")
  private final String pepId;

  @JsonProperty("audience")
  private final String audience;

  @JsonProperty("capabilities")
  private final List<PEPCapability> capabilities;

  // The encoding of the members above, not a member: Jackson never maps it, and the annotation
  // tells the wire-shape contract's source scan the same.
  @JsonIgnore private final String headerValue;

  private PEPHandshake(String pepId, String audience, List<PEPCapability> capabilities) {
    this.profileVersion = PROFILE_V1;
    this.pepId = pepId;
    this.audience = audience;
    this.capabilities = capabilities;
    this.headerValue = encode();
  }

  /**
   * Returns a declaration for {@code pepId} and {@code audience}, with {@code capabilities} copied
   * into the platform's canonical order.
   *
   * @param pepId names this enforcement point within the client's credential: lower-case letters,
   *     digits, ".", "_" and "-", starting with a letter or digit, at most 128 bytes. The platform
   *     prefixes it with the authenticated credential, so it cannot name another client's
   *     enforcement point.
   * @param audience the audience this enforcement point expects a decision proof to be bound to, at
   *     most 128 bytes; a URI is the usual form. It is recorded and bound, and authorises nothing.
   * @param capabilities the exact set this enforcement point can discharge, at most 64. An empty
   *     list declares that it discharges nothing; null is refused.
   * @return the declaration
   * @throws PEPHandshakeException naming the member at fault when the platform would refuse it
   */
  public static PEPHandshake of(String pepId, String audience, List<PEPCapability> capabilities) {
    requireIdentifier(pepId, PEP_ID, "/pep_id");
    requireIdentifier(audience, AUDIENCE, "/audience");
    if (capabilities == null) {
      throw new PEPHandshakeException(
          "/capabilities",
          "is absent; a handshake exists to declare capabilities, and an enforcement point that"
              + " discharges nothing declares an empty list");
    }
    if (capabilities.size() > MAX_CAPABILITIES) {
      throw new PEPHandshakeException(
          "/capabilities",
          "declares "
              + capabilities.size()
              + " capabilities; the platform reads at most "
              + MAX_CAPABILITIES);
    }
    List<PEPCapability> canonical = new ArrayList<>(capabilities);
    if (canonical.contains(null)) {
      throw new PEPHandshakeException("/capabilities", "contains a null capability");
    }
    Collections.sort(canonical);
    for (int i = 1; i < canonical.size(); i++) {
      PEPCapability repeated = canonical.get(i);
      if (repeated.equals(canonical.get(i - 1))) {
        throw new PEPHandshakeException(
            "/capabilities",
            "declares \""
                + repeated.getType()
                + "\" at version "
                + repeated.getVersion()
                + " more than once; the platform refuses a repeated capability");
      }
    }
    return new PEPHandshake(pepId, audience, Collections.unmodifiableList(canonical));
  }

  private static void requireIdentifier(String value, Pattern pattern, String pointer) {
    if (value == null
        || value.isEmpty()
        || value.getBytes(StandardCharsets.UTF_8).length > MAX_IDENTIFIER_BYTES
        || !pattern.matcher(value).matches()) {
      throw new PEPHandshakeException(
          pointer,
          (value == null ? "null" : "\"" + value + "\"")
              + " is not of the form "
              + pattern.pattern()
              + " with at most "
              + MAX_IDENTIFIER_BYTES
              + " bytes");
    }
  }

  private String encode() {
    byte[] document;
    try {
      document = CANONICAL.writeValueAsBytes(this);
    } catch (JsonProcessingException e) {
      throw new AxonFlowException("Failed to encode the PEP capability declaration", e);
    }
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(document);
    if (encoded.length() > MAX_HEADER_BYTES) {
      throw new PEPHandshakeException(
          "",
          "encodes to "
              + encoded.length()
              + " bytes; the header carries at most "
              + MAX_HEADER_BYTES);
    }
    return encoded;
  }

  /** Returns the handshake profile, {@link #PROFILE_V1}. */
  public int getProfileVersion() {
    return profileVersion;
  }

  /** Returns the enforcement point's identifier within the client's credential. */
  public String getPepId() {
    return pepId;
  }

  /** Returns the audience a decision proof is bound to. */
  public String getAudience() {
    return audience;
  }

  /** Returns the declared capabilities in canonical order; the list is unmodifiable. */
  public List<PEPCapability> getCapabilities() {
    return capabilities;
  }

  /**
   * Returns the {@value #HEADER} value this declaration is sent as. Two declarations of the same
   * set in a different order return the same value.
   *
   * @return the unpadded base64url encoding of the canonical document
   */
  public String headerValue() {
    return headerValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PEPHandshake)) return false;
    return headerValue.equals(((PEPHandshake) o).headerValue);
  }

  @Override
  public int hashCode() {
    return headerValue.hashCode();
  }

  @Override
  public String toString() {
    return "PEPHandshake{pep_id="
        + pepId
        + ", audience="
        + audience
        + ", capabilities="
        + capabilities
        + '}';
  }
}
