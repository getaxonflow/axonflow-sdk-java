// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * A checksum validator that acted before the policy engine decided (platform v11.0.0, #4122).
 *
 * <p>Under an organization's recorded {@code pii=block} or {@code pii=redact} detection override,
 * the Indonesian or Indian identifier validator blocks or masks on its own, and a response's {@code
 * legacyValidators} names each one that did, so the verdict's provenance is complete.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LegacyValidatorAction {

  @JsonProperty("validator")
  private final String validator;

  @JsonProperty("action")
  private final String action;

  /**
   * Creates a validator action.
   *
   * @param validator the validator that acted: {@code indonesia_pii} or {@code india_pii}
   * @param action what it did: {@code blocked} or {@code masked}
   */
  @JsonCreator
  public LegacyValidatorAction(
      @JsonProperty("validator") String validator, @JsonProperty("action") String action) {
    this.validator = validator;
    this.action = action;
  }

  /** Returns the validator that acted: {@code indonesia_pii} or {@code india_pii}. */
  public String getValidator() {
    return validator;
  }

  /** Returns what the validator did: {@code blocked} or {@code masked}. */
  public String getAction() {
    return action;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LegacyValidatorAction that = (LegacyValidatorAction) o;
    return Objects.equals(validator, that.validator) && Objects.equals(action, that.action);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validator, action);
  }

  @Override
  public String toString() {
    return "LegacyValidatorAction{validator='" + validator + "', action='" + action + "'}";
  }
}
