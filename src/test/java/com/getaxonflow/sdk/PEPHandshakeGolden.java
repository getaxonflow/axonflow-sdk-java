// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import com.getaxonflow.sdk.authzen.AuthZENObligationType;
import com.getaxonflow.sdk.types.PEPCapability;
import java.util.List;

/**
 * The PEP handshake's golden vectors. The platform's reference encoder ({@code
 * PEPHandshake.Encode}, platform/decision/contract at 857455033) produced them and its decoder
 * accepted them. They are extracted verbatim from axonflow-sdk-python's tests/test_pep_handshake.py
 * at ef8fd006d, so every SDK pins the same bytes. Capabilities are listed in the order GIVEN; for
 * "unsorted" that is not the canonical order.
 */
final class PEPHandshakeGolden {

  static final class Vector {
    final String name;
    final String pepId;
    final String audience;
    final List<PEPCapability> capabilities;
    final String header;

    Vector(
        String name,
        String pepId,
        String audience,
        List<PEPCapability> capabilities,
        String header) {
      this.name = name;
      this.pepId = pepId;
      this.audience = audience;
      this.capabilities = capabilities;
      this.header = header;
    }
  }

  static final List<Vector> VECTORS =
      List.of(
          new Vector(
              "empty",
              "sdk-python",
              "https://pep.example.test",
              List.of(),
              "eyJwcm9maWxlX3ZlcnNpb24iOjEsInBlcF9pZCI6InNkay1weXRob24iLCJhdWRpZW5jZSI6Imh0dHBzOi8vcGVwLmV4YW1wbGUudGVzdCIsImNhcGFiaWxpdGllcyI6W119"),
          new Vector(
              "unsorted",
              "gateway.request-1",
              "urn:example:aud",
              List.of(
                  cap("field_redact", 2),
                  cap("approval_challenge", 1),
                  cap("field_redact", 1),
                  cap("notification", 3)),
              "eyJwcm9maWxlX3ZlcnNpb24iOjEsInBlcF9pZCI6ImdhdGV3YXkucmVxdWVzdC0xIiwiYXVkaWVuY2UiOiJ1cm46ZXhhbXBsZTphdWQiLCJjYXBhYmlsaXRpZXMiOlt7InR5cGUiOiJhcHByb3ZhbF9jaGFsbGVuZ2UiLCJ2ZXJzaW9uIjoxfSx7InR5cGUiOiJmaWVsZF9yZWRhY3QiLCJ2ZXJzaW9uIjoxfSx7InR5cGUiOiJmaWVsZF9yZWRhY3QiLCJ2ZXJzaW9uIjoyfSx7InR5cGUiOiJub3RpZmljYXRpb24iLCJ2ZXJzaW9uIjozfV19"),
          new Vector(
              "minimal",
              "a",
              "A",
              List.of(cap("step_up_authentication", 1)),
              "eyJwcm9maWxlX3ZlcnNpb24iOjEsInBlcF9pZCI6ImEiLCJhdWRpZW5jZSI6IkEiLCJjYXBhYmlsaXRpZXMiOlt7InR5cGUiOiJzdGVwX3VwX2F1dGhlbnRpY2F0aW9uIiwidmVyc2lvbiI6MX1dfQ"));

  /**
   * The 64-capability vector, pinned by length and digest: every declared type in canonical order
   * at version 1, then 2, and so on, stopping at the count cap.
   */
  static final int SIXTY_FOUR_LENGTH = 3364;

  static final String SIXTY_FOUR_SHA256 =
      "cdb2b368348bceaed99ca92647afeecd70981604157b60ad66067953a371edbf";

  private static PEPCapability cap(String type, int version) {
    return PEPCapability.of(AuthZENObligationType.of(type), version);
  }

  private PEPHandshakeGolden() {}
}
