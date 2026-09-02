/* Copyright 2026 AxonFlow */
package com.getaxonflow.sdk.identity;

import java.util.Locale;
import java.util.Objects;

/**
 * The scope the platform computed a role-scoped read under, taken from the {@code
 * X-Axonflow-Read-Scope} response header.
 *
 * <p>A value type wrapping a string rather than an {@code enum}, for one deliberate reason: a scope
 * a newer platform names and this build does not recognise must round-trip verbatim instead of
 * failing to construct or being folded into a neighbour.
 *
 * <p>Three named values are the platform's closed set. Two states are NOT in it and are
 * deliberately distinct from each other and from the three:
 *
 * <ul>
 *   <li>{@link #ABSENT} — the response carried no such header. That is what a pre-#2922 platform, a
 *       non-scoped route, or a proxy that dropped the header looks like. It means "not stated",
 *       never "none": treating an absent header as a scope of {@code none} would turn every older
 *       stack's perfectly good read into a refusal.
 *   <li>any other non-empty string — preserved verbatim so a caller can see what it was, and never
 *       a trigger for a refusal: this header is the platform's account of a decision it has ALREADY
 *       made and applied, so an unrecognised value is a reporting gap on our side, not a licence to
 *       invent an outcome.
 * </ul>
 */
public final class ReadScope {

  /** No {@code X-Axonflow-Read-Scope} header at all. Distinct from {@link #NONE}. */
  public static final ReadScope ABSENT = new ReadScope("");

  /**
   * Tenant-wide: a tenant-wide role (admin / owner / policy_admin), or a Community / Community-SaaS
   * deployment where the whole tenant is the one operator.
   */
  public static final ReadScope TENANT = new ReadScope("tenant");

  /**
   * Narrowed to the rows attributed to the identity presented. A miss under this scope means "not
   * among yours", which is NOT the same statement as "not there" — see {@link ReadScopeException}.
   */
  public static final ReadScope OWN_ROWS = new ReadScope("own-rows");

  /**
   * The platform RESOLVED no per-user identity and the caller holds no tenant-wide authority, so it
   * returned zero rows by construction. Under this scope a read CANNOT have returned data, so its
   * empty answer says nothing about what exists.
   *
   * <p>"Resolved none" is wider than "presented none", and the difference is worth knowing before
   * you go looking in the wrong place. A token that validates perfectly still resolves to no
   * identity when its address is one the platform reserves for SHARED, non-personal identities —
   * the whole of {@code @axonflow.local} and {@code @axonflow.internal}, plus the community and
   * evaluator addresses. Those name a pool of callers rather than a person, and scoping a read to
   * one would return the pool, so the platform deliberately censuses them to nothing. A per-user
   * token minted with an address in one of those domains therefore reads exactly like no token at
   * all. (Easy to hit: the platform's own {@code generate-jwt.sh} defaults to {@code
   * demo-user@axonflow.local}.)
   */
  public static final ReadScope NONE = new ReadScope("none");

  private final String value;

  private ReadScope(String value) {
    this.value = value;
  }

  /**
   * The scope a response header names.
   *
   * <p>Trimmed and lower-cased, for the same reason the platform's own header helpers are: a proxy
   * that normalises header casing or appends whitespace must not silently change the answer. The
   * cost of getting that wrong is one-sided and quiet — a scope spelled {@code None} would fall to
   * the unrecognised branch and the vacuous empty page it describes would come back as data again.
   * An unrecognised value is otherwise unchanged, so it still round-trips to the caller.
   *
   * @param headerValue the raw header value, or {@code null} when the header was absent
   * @return the scope; {@link #ABSENT} for a null or blank header
   */
  public static ReadScope of(String headerValue) {
    if (headerValue == null) {
      return ABSENT;
    }
    String normalized = headerValue.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return ABSENT;
    }
    if (normalized.equals(TENANT.value)) {
      return TENANT;
    }
    if (normalized.equals(OWN_ROWS.value)) {
      return OWN_ROWS;
    }
    if (normalized.equals(NONE.value)) {
      return NONE;
    }
    return new ReadScope(normalized);
  }

  /** The scope's wire value; empty for {@link #ABSENT}. */
  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ReadScope && ((ReadScope) other).value.equals(value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
