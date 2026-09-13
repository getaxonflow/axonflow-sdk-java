// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.getaxonflow.sdk.authzen.AuthZENObligationType;
import com.getaxonflow.sdk.exceptions.PEPHandshakeException;
import java.util.Objects;

/**
 * One obligation type, at one schema version, that an enforcement point can discharge: a member of
 * a {@link PEPHandshake} (read by the platform from v10.4.0). The platform matches both members
 * exactly.
 *
 * <p>Built only through {@link #of}, which admits the obligation types the SDK's AuthZEN contract
 * declares ({@link AuthZENObligationType#KNOWN_WIRE_VALUES}) at a positive version. The platform
 * refuses any other capability, so none is ever built.
 */
@JsonPropertyOrder({"type", "version"})
public final class PEPCapability implements Comparable<PEPCapability> {

  @JsonProperty("type")
  private final AuthZENObligationType type;

  @JsonProperty("version")
  private final int version;

  private PEPCapability(AuthZENObligationType type, int version) {
    this.type = type;
    this.version = version;
  }

  /**
   * Returns the capability to discharge {@code type} at {@code version}.
   *
   * @param type one of the obligation types the platform declares
   * @param version the obligation's schema version, a positive integer
   * @return the capability
   * @throws PEPHandshakeException naming {@code /capabilities} when the platform would refuse it
   */
  public static PEPCapability of(AuthZENObligationType type, int version) {
    if (type == null || !type.isKnown()) {
      throw new PEPHandshakeException(
          "/capabilities",
          "names obligation type "
              + (type == null ? "null" : "\"" + type + "\"")
              + ", which is not one of "
              + AuthZENObligationType.KNOWN_WIRE_VALUES);
    }
    // A version of 0 would match only an obligation whose version was never set.
    if (version <= 0) {
      throw new PEPHandshakeException(
          "/capabilities",
          "declares \"" + type + "\" at version " + version + "; a version is a positive integer");
    }
    return new PEPCapability(type, version);
  }

  /** Returns the obligation type. */
  public AuthZENObligationType getType() {
    return type;
  }

  /** Returns the obligation's schema version. */
  public int getVersion() {
    return version;
  }

  /** Orders capabilities as the platform does: by type, then by version. */
  @Override
  public int compareTo(PEPCapability other) {
    int byType = type.value().compareTo(other.type.value());
    return byType != 0 ? byType : Integer.compare(version, other.version);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PEPCapability)) return false;
    PEPCapability that = (PEPCapability) o;
    return version == that.version && type.equals(that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, version);
  }

  @Override
  public String toString() {
    return type + "@" + version;
  }
}
