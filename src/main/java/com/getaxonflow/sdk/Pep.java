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
package com.getaxonflow.sdk;

import com.getaxonflow.sdk.types.Obligation;
import com.getaxonflow.sdk.types.ObligationFulfillment;
import java.util.List;

/**
 * Decision Mode PEP (Policy Enforcement Point) contract constants and helpers (ADR-056, epic
 * #2563).
 *
 * <p>A PEP follows one path: <b>decide → fulfill → forward</b>.
 *
 * <ul>
 *   <li>decide: ask the PDP ({@code POST /api/v1/decide}) for a verdict on a request.
 *   <li>fulfill: for every obligation the verdict carries, call the ENGINE endpoint named in the
 *       obligation's {@code fulfillment} block to obtain engine-redacted content.
 *   <li>forward: forward the (possibly redacted) content, or block, per verdict.
 * </ul>
 *
 * <p>The structural guarantee #2563 demands: a PEP built on this SDK contains NO redaction logic of
 * its own. The ONLY way it discharges a {@code redact_pii} obligation is by POSTing the source
 * content to the engine endpoint the obligation names ({@code AxonFlow.fulfillRequest} / {@code
 * AxonFlow.decideAndFulfill}) and forwarding what the engine returns. If an obligation arrives
 * without a fulfillable engine endpoint — or the engine reports the redactor did not run — the
 * helper throws {@code ObligationNotFulfillableException} and the caller MUST fail closed (block),
 * never forward unredacted. Mirrors {@code platform/shared/pep} (the Go reference PEP).
 */
public final class Pep {

  private Pep() {}

  // --- Obligation contract constants (mirror platform/agent/decision_handler.go) ---

  /**
   * The obligation a PEP discharges by replacing request content with engine-redacted content
   * before forwarding.
   */
  public static final String OBLIGATION_REDACT_PII = "redact_pii";

  /**
   * Request-phase fulfillment. {@code /decide} runs pre-call so it only emits request-phase
   * obligations.
   */
  public static final String PHASE_REQUEST = "request";

  /**
   * Response-phase fulfillment. Part of the contract for PEP helpers that fan out to the
   * response-redaction endpoint after the backend call.
   */
  public static final String PHASE_RESPONSE = "response";

  /**
   * The only redaction content-type wired today. The contract is content-type agnostic — a PEP
   * holding content of a type not advertised by an obligation's {@code contentTypes} must fail
   * closed rather than forward it unredacted.
   */
  public static final String CONTENT_TYPE_TEXT = "text/plain";

  // --- Verdict values returned by the PDP ---

  /** The PDP allows the request (possibly carrying obligations). */
  public static final String VERDICT_ALLOW = "allow";

  /** The PDP denies the request. */
  public static final String VERDICT_DENY = "deny";

  /** The PDP requires human approval before the request may proceed. */
  public static final String VERDICT_NEEDS_APPROVAL = "needs_approval";

  // --- Engine endpoints a PEP will POST content to for fulfillment ---
  // An obligation whose fulfillment endpoint is not one of these is rejected — a
  // PEP must not be steered into calling an arbitrary URL by a malformed verdict.

  /** The PDP verdict endpoint. */
  public static final String DECIDE_PATH = "/api/v1/decide";

  /** The request-phase redaction engine endpoint. */
  public static final String REQUEST_REDACTION_PATH = "/api/v1/mcp/check-input";

  /** The response-phase redaction engine endpoint. */
  public static final String RESPONSE_REDACTION_PATH = "/api/v1/mcp/check-output";

  /**
   * Reports whether any obligation requires request-phase PII redaction.
   *
   * <p>Exposed so a PEP can branch ("does this verdict carry work for me?") before calling {@code
   * AxonFlow.fulfillRequest}.
   *
   * @param obligations the obligations to scan (may be null)
   * @return {@code true} if any {@code redact_pii} obligation has a request-phase fulfillment
   */
  public static boolean hasRequestRedaction(List<Obligation> obligations) {
    if (obligations == null) {
      return false;
    }
    for (Obligation o : obligations) {
      if (o == null) {
        continue;
      }
      ObligationFulfillment f = o.getFulfillment();
      if (OBLIGATION_REDACT_PII.equals(o.getType())
          && f != null
          && PHASE_REQUEST.equals(f.getPhase())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reports whether {@code endpoint} is the expected engine path.
   *
   * <p>Tolerates an absolute URL whose path component matches (some PDPs return a fully-qualified
   * obligation endpoint); a blank endpoint never matches.
   *
   * @param endpoint the obligation's fulfillment endpoint
   * @param expected the expected engine path, e.g. {@link #REQUEST_REDACTION_PATH}
   * @return {@code true} when the endpoint's path equals {@code expected}
   */
  public static boolean endpointPathMatches(String endpoint, String expected) {
    String e = endpoint == null ? "" : endpoint.trim();
    if (e.equals(expected)) {
      return true;
    }
    int idx = e.indexOf("://");
    if (idx >= 0) {
      String rest = e.substring(idx + 3);
      int slash = rest.indexOf('/');
      if (slash >= 0) {
        String path = rest.substring(slash);
        int q = path.indexOf('?');
        if (q >= 0) {
          path = path.substring(0, q);
        }
        return path.equals(expected);
      }
    }
    return false;
  }
}
