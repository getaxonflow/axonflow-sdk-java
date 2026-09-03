// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * A self-describing, engine-fulfillable PEP requirement on an allow verdict (ADR-056, epic #2563).
 *
 * <p>Obligations are SELF-DESCRIBING and ENGINE-FULFILLABLE: {@code /decide} is a pure PDP and
 * never mutates content, so a {@code redact_pii} obligation is not "go redact this yourself with
 * your own patterns" — it is "call the AxonFlow engine endpoint named in {@code fulfillment} to
 * obtain engine-redacted content." There is no other blessed way to satisfy it; client-side
 * redaction is forbidden. Mirrors the platform {@code DecisionObligation}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Obligation {

  @JsonProperty("type")
  private final String type;

  @JsonProperty("detail")
  private final String detail;

  @JsonProperty("fulfillment")
  private final ObligationFulfillment fulfillment;

  /**
   * Creates an obligation.
   *
   * @param type the obligation type, e.g. {@code redact_pii}
   * @param detail human-readable detail for audit logs (may be null)
   * @param fulfillment how a PEP discharges this obligation via the engine (may be null)
   */
  @JsonCreator
  public Obligation(
      @JsonProperty("type") String type,
      @JsonProperty("detail") String detail,
      @JsonProperty("fulfillment") ObligationFulfillment fulfillment) {
    this.type = type;
    this.detail = detail;
    this.fulfillment = fulfillment;
  }

  /** Returns the obligation type, e.g. {@code redact_pii}. */
  public String getType() {
    return type;
  }

  /** Returns the human-readable detail for audit logs, or null. */
  public String getDetail() {
    return detail;
  }

  /** Returns the engine fulfillment descriptor, or null when the obligation names no endpoint. */
  public ObligationFulfillment getFulfillment() {
    return fulfillment;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Obligation that = (Obligation) o;
    return Objects.equals(type, that.type)
        && Objects.equals(detail, that.detail)
        && Objects.equals(fulfillment, that.fulfillment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, detail, fulfillment);
  }

  @Override
  public String toString() {
    return "Obligation{"
        + "type='"
        + type
        + '\''
        + ", detail='"
        + detail
        + '\''
        + ", fulfillment="
        + fulfillment
        + '}';
  }
}
