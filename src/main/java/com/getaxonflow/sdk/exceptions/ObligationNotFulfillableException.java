// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

/**
 * Fail-closed signal: a {@code redact_pii} obligation could not be discharged through the engine
 * (ADR-056, epic #2563).
 *
 * <p>Thrown by {@code AxonFlow.fulfillRequest} / {@code AxonFlow.decideAndFulfill} when an
 * obligation named no request-phase fulfillment, advertised a content-type the PEP is not holding,
 * named an endpoint this client will not call, the engine call failed, or the engine reported the
 * redactor did not run ({@code redaction_evaluated=false}).
 *
 * <p>A caller catching this MUST fail closed (block) — it must NEVER forward the original,
 * unredacted statement. The SDK never returns the original content under any unfulfillable
 * condition; this exception is the only outcome.
 */
public class ObligationNotFulfillableException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new ObligationNotFulfillableException.
   *
   * @param message the error message
   */
  public ObligationNotFulfillableException(String message) {
    super(message, 0, "OBLIGATION_NOT_FULFILLABLE");
  }

  /**
   * Creates a new ObligationNotFulfillableException with a cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public ObligationNotFulfillableException(String message, Throwable cause) {
    super(message, 0, "OBLIGATION_NOT_FULFILLABLE", cause);
  }
}
