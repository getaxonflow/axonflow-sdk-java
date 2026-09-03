// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

import com.getaxonflow.sdk.identity.ReadScope;

/**
 * A role-scoped read whose answer was decided by the caller's identity scope rather than by the
 * data (platform #2922).
 *
 * <p>It exists because "no rows" and "no identity" are the same bytes on the wire. The platform
 * distinguishes them in the {@code X-Axonflow-Read-Scope} header; this exception is that
 * distinction made visible, so a read that could not have succeeded reports a cause instead of a
 * confident nothing.
 *
 * <p>Two shapes, told apart by {@link #isIdentityMissing()}:
 *
 * <ul>
 *   <li>{@link ReadScope#NONE} — no identity was RESOLVED; the read returned zero rows by
 *       construction and says nothing about what exists. Remedy: present an identity whose address
 *       is a real person's — see {@link ReadScope#NONE} for why a valid token can still resolve to
 *       nothing.
 *   <li>{@link ReadScope#OWN_ROWS} — an identity WAS resolved, and the row is not among the ones
 *       attributed to it. That does NOT mean the row exists and belongs to somebody else: the
 *       platform answers "not attributed to you" and "not there at all" with the identical 404,
 *       deliberately, so that a miss cannot be used to probe for another user's rows. This
 *       exception therefore reports the scope, not a claim about what exists.
 * </ul>
 *
 * <p>The presented token is never included in the message: it is safe to log, which is the point of
 * putting the diagnosis in a type rather than in a string the caller assembles from the credential.
 *
 * <p>It extends {@link AxonFlowException}, so callers that already catch that keep working — the
 * refusal is a more specific answer to the same question, not a new failure mode to route
 * separately.
 */
public class ReadScopeException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  /**
   * NOT {@code transient}: an exception that loses its diagnosis when serialized reports {@code
   * isIdentityMissing() == false} and {@code getScope() == null} at the far end — a confidently
   * wrong answer, and the one this type exists to prevent. {@link ReadScope} is {@link
   * java.io.Serializable} for the same reason.
   */
  private final ReadScope scope;

  private final int statusCode;
  private final String resource;
  private final String identifier;

  /**
   * @param scope the scope the platform reported
   * @param statusCode the HTTP status the platform answered with (404 for a scoped miss, 200 for a
   *     scoped-empty page)
   * @param resource what was read, e.g. {@code "decision"}
   * @param identifier the identifier that was read, or {@code null} for a list read
   */
  public ReadScopeException(ReadScope scope, int statusCode, String resource, String identifier) {
    super(buildMessage(scope, statusCode, resource, identifier));
    this.scope = scope;
    this.statusCode = statusCode;
    this.resource = resource;
    this.identifier = identifier;
  }

  private static String buildMessage(
      ReadScope scope, int statusCode, String resource, String identifier) {
    String named = resource == null ? "read" : resource;
    String subject = identifier == null ? named : named + " \"" + identifier + "\"";
    if (ReadScope.NONE.equals(scope)) {
      return "HTTP "
          + statusCode
          + ": "
          + subject
          + ": the platform resolved no per-user identity for this read"
          + " (X-Axonflow-Read-Scope: "
          + scope
          + "), so it returned zero rows by construction and the empty answer says nothing about"
          + " what exists. Either no identity was presented — set userToken on the config, pass it"
          + " to this call, or use client.asUser(...) — or the one presented carries an address the"
          + " platform reserves for shared identities (@axonflow.local, @axonflow.internal), which"
          + " resolves to nobody. (platform #2922)";
    }
    return "HTTP "
        + statusCode
        + ": "
        + subject
        + " was not found among the rows this identity can see: the platform reports"
        + " X-Axonflow-Read-Scope: "
        + scope
        + ", so the read was narrowed to the identity's own rows. It is either not attributed to"
        + " this identity or not there at all — the platform answers both the same way ON PURPOSE,"
        + " so that a miss cannot be used to probe for the existence of another user's rows, and"
        + " this SDK cannot tell them apart either. A tenant-wide role (admin, owner or"
        + " policy_admin) reads the whole tenant. (platform #2922)";
  }

  /**
   * Whether the read failed because no per-user identity was resolved, as opposed to one being
   * resolved and not matching.
   *
   * @return true when the platform reported {@link ReadScope#NONE}
   */
  public boolean isIdentityMissing() {
    return ReadScope.NONE.equals(scope);
  }

  /** The scope the platform reported. */
  public ReadScope getScope() {
    return scope;
  }

  /** The HTTP status the platform answered with. */
  public int getStatusCode() {
    return statusCode;
  }

  /** What was read, e.g. {@code "decision"}. */
  public String getResource() {
    return resource;
  }

  /** The identifier that was read, or {@code null} for a list read. */
  public String getIdentifier() {
    return identifier;
  }
}
