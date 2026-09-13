// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

import com.getaxonflow.sdk.types.PEPHandshake;

/**
 * A PEP capability declaration the platform would refuse (platform v11.0.0).
 *
 * <p>{@link PEPHandshake#of} and {@link com.getaxonflow.sdk.types.PEPCapability#of} apply the
 * platform's own rules, so a declaration its decoder would answer with a 400 fails where it is
 * built rather than on the first governed call. {@link #getPointer()} names the member at fault as
 * the platform does ({@code /pep_id}, {@code /audience} or {@code /capabilities}), or is empty when
 * the whole document encodes past the header's size limit.
 */
public class PEPHandshakeException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final String pointer;

  /**
   * Creates a refusal.
   *
   * @param pointer the JSON Pointer of the member at fault, or the empty string for the whole
   *     document
   * @param detail what is wrong with it
   */
  public PEPHandshakeException(String pointer, String detail) {
    super(PEPHandshake.HEADER + ": " + (pointer.isEmpty() ? "" : pointer + ": ") + detail);
    this.pointer = pointer;
  }

  /**
   * Returns the member at fault.
   *
   * @return {@code /pep_id}, {@code /audience} or {@code /capabilities}, or the empty string when
   *     the whole document is refused
   */
  public String getPointer() {
    return pointer;
  }
}
