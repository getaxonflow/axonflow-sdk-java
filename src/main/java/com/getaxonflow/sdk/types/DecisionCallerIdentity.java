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
package com.getaxonflow.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Gateway-asserted identity for a {@code POST /api/v1/decide} request (ADR-056, epic #2563).
 *
 * <p>{@code orgId} / {@code tenantId} are optional in the body — the auth-derived identity is
 * authoritative; body-supplied values are accepted only when they match. Mirrors the platform
 * {@code DecisionCallerIdentity}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DecisionCallerIdentity {

  @JsonProperty("gateway_id")
  private final String gatewayId;

  @JsonProperty("org_id")
  private final String orgId;

  @JsonProperty("tenant_id")
  private final String tenantId;

  /**
   * Creates a caller-identity descriptor. All fields are optional.
   *
   * @param gatewayId the asserting gateway's identifier (may be null)
   * @param orgId the organization identifier (may be null)
   * @param tenantId the tenant identifier (may be null)
   */
  @JsonCreator
  public DecisionCallerIdentity(
      @JsonProperty("gateway_id") String gatewayId,
      @JsonProperty("org_id") String orgId,
      @JsonProperty("tenant_id") String tenantId) {
    this.gatewayId = gatewayId;
    this.orgId = orgId;
    this.tenantId = tenantId;
  }

  /** Returns the asserting gateway's identifier, or null. */
  public String getGatewayId() {
    return gatewayId;
  }

  /** Returns the organization identifier, or null. */
  public String getOrgId() {
    return orgId;
  }

  /** Returns the tenant identifier, or null. */
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecisionCallerIdentity that = (DecisionCallerIdentity) o;
    return Objects.equals(gatewayId, that.gatewayId)
        && Objects.equals(orgId, that.orgId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gatewayId, orgId, tenantId);
  }

  @Override
  public String toString() {
    return "DecisionCallerIdentity{"
        + "gatewayId='"
        + gatewayId
        + '\''
        + ", orgId='"
        + orgId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
