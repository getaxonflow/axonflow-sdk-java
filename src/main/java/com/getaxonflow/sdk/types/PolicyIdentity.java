// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * One policy a decision matched, as {@link DecideResponse#getPolicyIdentities()} names it (platform
 * v11.0.0, PRD v11 §1.14).
 *
 * <p>Entries follow {@link DecideResponse#getEvaluatedPolicies()} one for one, in order. {@code
 * name} is the policy's own display name and is null when it declares none: the platform never
 * presents an identifier as a name. {@code source} says whose the policy is ({@code shipped},
 * {@code organization} or {@code pack}), and {@code version} is the published version of an
 * organization's or an installed pack's policy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyIdentity {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("name")
  private final String name;

  @JsonProperty("source")
  private final String source;

  @JsonProperty("version")
  private final Integer version;

  /**
   * Creates a policy identity.
   *
   * @param id the policy id, as {@code evaluated_policies} carries it
   * @param name the policy's own display name, or null
   * @param source whose the policy is: {@code shipped}, {@code organization} or {@code pack}
   * @param version the published version, or null
   */
  @JsonCreator
  public PolicyIdentity(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("source") String source,
      @JsonProperty("version") Integer version) {
    this.id = id;
    this.name = name;
    this.source = source;
    this.version = version;
  }

  /** Returns the policy id, as {@code evaluated_policies} carries it. */
  public String getId() {
    return id;
  }

  /** Returns the policy's own display name, or null when it declares none. */
  public String getName() {
    return name;
  }

  /** Returns whose the policy is: {@code shipped}, {@code organization} or {@code pack}. */
  public String getSource() {
    return source;
  }

  /** Returns the published version of an organization's or a pack's policy, or null. */
  public Integer getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PolicyIdentity that = (PolicyIdentity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(source, that.source)
        && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, source, version);
  }

  @Override
  public String toString() {
    return "PolicyIdentity{id='"
        + id
        + "', name='"
        + name
        + "', source='"
        + source
        + "', version="
        + version
        + '}';
  }
}
