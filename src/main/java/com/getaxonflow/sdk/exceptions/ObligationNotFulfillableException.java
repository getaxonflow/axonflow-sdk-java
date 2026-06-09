/*
 * Copyright 2026 AxonFlow
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
