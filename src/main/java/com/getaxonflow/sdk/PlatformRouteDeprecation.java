// Copyright 2026 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk;

import java.util.Objects;

/**
 * What the platform declared about a route a call used.
 *
 * <p>A v11.0.0 platform stamps its legacy policy routes with {@code X-AxonFlow-Removed-In} and
 * {@code Link: <successor>; rel="successor-version"}, and adds an RFC 9745 {@code Deprecation}
 * header once the deprecating release is tagged. Either the first or the last marks the route
 * deprecated, so the SDK reports it before the tag as well as after it. See {@link
 * AxonFlowConfig.Builder#onRouteDeprecation}.
 */
public final class PlatformRouteDeprecation {

  private final String route;
  private final String successor;
  private final String removedIn;
  private final String deprecation;

  /**
   * Creates a route deprecation.
   *
   * @param route the method and the route: its path, or its template when the path carries an id,
   *     e.g. {@code GET /api/v1/static-policies} or {@code POST
   *     /api/v1/static-policies/{id}/override}
   * @param successor the route that replaces it, or null when the platform names none
   * @param removedIn the release that removes it, e.g. {@code v12.0}, or null
   * @param deprecation the RFC 9745 {@code Deprecation} value, or null when not sent
   */
  public PlatformRouteDeprecation(
      String route, String successor, String removedIn, String deprecation) {
    this.route = route;
    this.successor = successor;
    this.removedIn = removedIn;
    this.deprecation = deprecation;
  }

  /**
   * Returns the method and the route: its path, or its template when the path carries an id (a path
   * parameter reads {@code {id}}), e.g. {@code GET /api/v1/static-policies} or {@code POST
   * /api/v1/static-policies/{id}/override}.
   */
  public String getRoute() {
    return route;
  }

  /** Returns the route that replaces this one, or null when the platform names none. */
  public String getSuccessor() {
    return successor;
  }

  /** Returns the release that removes the route, e.g. {@code v12.0}, or null. */
  public String getRemovedIn() {
    return removedIn;
  }

  /** Returns the RFC 9745 {@code Deprecation} value, or null when the platform sent none. */
  public String getDeprecation() {
    return deprecation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlatformRouteDeprecation that = (PlatformRouteDeprecation) o;
    return Objects.equals(route, that.route)
        && Objects.equals(successor, that.successor)
        && Objects.equals(removedIn, that.removedIn)
        && Objects.equals(deprecation, that.deprecation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(route, successor, removedIn, deprecation);
  }

  /** Returns a one-line description, e.g. for a log line. */
  @Override
  public String toString() {
    StringBuilder s = new StringBuilder(route).append(" is deprecated by the AxonFlow platform");
    if (successor != null) {
      s.append("; use ").append(successor).append(" instead");
    }
    if (removedIn != null) {
      s.append("; it is removed in ").append(removedIn);
    }
    return s.append('.').toString();
  }
}
