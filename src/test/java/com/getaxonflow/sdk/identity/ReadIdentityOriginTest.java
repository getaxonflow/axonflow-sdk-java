package com.getaxonflow.sdk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link ReadIdentity#sameOrigin} as a pure function.
 *
 * <p>In this package rather than beside the rest of ReadIdentityTest because the function is
 * package-private on purpose: it is an internal comparison, not API, and widening it to public to
 * make it testable would publish a surface the SDK would then owe compatibility on.
 */
class ReadIdentityOriginTest {

  /**
   * The origin comparison as the pure function it is, one axis varied per row.
   *
   * <p>This is not a lookalike standing in for the two-listener redirect tests. Those assert the
   * ALTITUDE property — that the check is re-run per hop by a network interceptor — and a local
   * listener can only vary the PORT. A comparison that dropped scheme and host entirely survives
   * both of them at 39/39, and {@code https://api.example.com:8443 ->
   * https://attacker.example:8443} would then forward all four credentials with no test moving.
   * Different properties; neither substitutes for the other.
   */
  @ParameterizedTest(name = "{2}")
  @CsvSource({
    "https://api.example.com:8443, https://api.example.com:8443, identical is same-origin, true",
    "https://api.example.com:8443, https://attacker.example:8443, HOST alone differs, false",
    "https://api.example.com:8443, http://api.example.com:8443, SCHEME alone differs, false",
    "https://api.example.com:8443, https://api.example.com:9443, PORT alone differs, false",
    "http://localhost:8080, http://127.0.0.1:8080, localhost vs its own loopback literal, false",
    "https://api.example.com:8443, https://evil.api.example.com:8443, a SUBDOMAIN is not the origin, false",
    "https://api.example.com, https://api.example.com:443, the default port is the explicit one, true",
    "http://api.example.com, http://api.example.com:80, the default http port likewise, true"
  })
  void sameOriginComparesEveryAxis(String a, String b, String description, boolean expected) {
    assertThat(ReadIdentity.sameOrigin(okhttp3.HttpUrl.get(a), okhttp3.HttpUrl.get(b)))
        .as("%s: %s vs %s", description, a, b)
        .isEqualTo(expected);
  }
}
