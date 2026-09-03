// Copyright 2025 AxonFlow
// SPDX-License-Identifier: MIT
package com.getaxonflow.sdk.exceptions;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a request is blocked by a policy.
 *
 * <p>This exception provides details about which policy blocked the request and what the violation
 * was.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try {
 *     axonflow.proxyLLMCall(request);
 * } catch (PolicyViolationException e) {
 *     System.out.println("Blocked by policy: " + e.getPolicyName());
 *     System.out.println("Reason: " + e.getBlockReason());
 *     System.out.println("All policies checked: " + e.getPoliciesEvaluated());
 * }
 * }</pre>
 */
public class PolicyViolationException extends AxonFlowException {

  private static final long serialVersionUID = 1L;

  private final String policyName;
  private final String blockReason;
  private final List<String> policiesEvaluated;

  /**
   * Creates a new PolicyViolationException.
   *
   * @param blockReason the reason the request was blocked
   */
  public PolicyViolationException(String blockReason) {
    super("Request blocked by policy: " + blockReason, 403, "POLICY_VIOLATION");
    this.blockReason = blockReason;
    this.policyName = extractPolicyName(blockReason);
    this.policiesEvaluated = Collections.emptyList();
  }

  /**
   * Creates a new PolicyViolationException with full details.
   *
   * @param blockReason the reason the request was blocked
   * @param policyName the name of the policy that blocked the request
   * @param policiesEvaluated the list of policies that were evaluated
   */
  public PolicyViolationException(
      String blockReason, String policyName, List<String> policiesEvaluated) {
    super(
        "Request blocked by policy: " + (policyName != null ? policyName : blockReason),
        403,
        "POLICY_VIOLATION");
    this.blockReason = blockReason;
    this.policyName = policyName != null ? policyName : extractPolicyName(blockReason);
    this.policiesEvaluated =
        policiesEvaluated != null
            ? Collections.unmodifiableList(policiesEvaluated)
            : Collections.emptyList();
  }

  /**
   * Returns the name of the policy that blocked the request.
   *
   * @return the policy name
   */
  public String getPolicyName() {
    return policyName;
  }

  /**
   * Returns the detailed reason the request was blocked.
   *
   * @return the block reason
   */
  public String getBlockReason() {
    return blockReason;
  }

  /**
   * Returns the list of policies that were evaluated.
   *
   * @return immutable list of policy names
   */
  public List<String> getPoliciesEvaluated() {
    return policiesEvaluated;
  }

  /**
   * Extracts the policy name from a block reason string.
   *
   * <p>Handles common formats:
   *
   * <ul>
   *   <li>"Request blocked by policy: policy_name"
   *   <li>"Blocked by policy: policy_name"
   *   <li>"[policy_name] description"
   * </ul>
   *
   * @param blockReason the block reason string
   * @return the extracted policy name
   */
  private static String extractPolicyName(String blockReason) {
    if (blockReason == null || blockReason.isEmpty()) {
      return "unknown";
    }

    // Handle format: "Request blocked by policy: policy_name"
    String prefix = "Request blocked by policy: ";
    if (blockReason.startsWith(prefix)) {
      return blockReason.substring(prefix.length()).trim();
    }

    // Handle format: "Blocked by policy: policy_name"
    prefix = "Blocked by policy: ";
    if (blockReason.startsWith(prefix)) {
      return blockReason.substring(prefix.length()).trim();
    }

    // Handle format: "[policy_name] description"
    if (blockReason.startsWith("[")) {
      int endBracket = blockReason.indexOf(']');
      if (endBracket > 1) {
        return blockReason.substring(1, endBracket).trim();
      }
    }

    return blockReason;
  }
}
