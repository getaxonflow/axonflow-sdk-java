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
package com.getaxonflow.sdk.simulation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Request to generate a policy impact report.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * ImpactReportRequest request = ImpactReportRequest.builder()
 *     .policyId("policy_block_pii")
 *     .inputs(List.of(
 *         ImpactReportInput.builder().query("My SSN is 123-45-6789").build(),
 *         ImpactReportInput.builder().query("What is the weather?").build()
 *     ))
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ImpactReportRequest {

  @JsonProperty("policy_id")
  private final String policyId;

  @JsonProperty("inputs")
  private final List<ImpactReportInput> inputs;

  private ImpactReportRequest(Builder builder) {
    this.policyId = Objects.requireNonNull(builder.policyId, "policyId cannot be null");
    if (this.policyId.isEmpty()) {
      throw new IllegalArgumentException("policyId cannot be empty");
    }
    this.inputs = Objects.requireNonNull(builder.inputs, "inputs cannot be null");
    if (this.inputs.isEmpty()) {
      throw new IllegalArgumentException("inputs cannot be empty");
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getPolicyId() {
    return policyId;
  }

  public List<ImpactReportInput> getInputs() {
    return inputs;
  }

  /** Builder for {@link ImpactReportRequest}. */
  public static final class Builder {
    private String policyId;
    private List<ImpactReportInput> inputs;

    public Builder policyId(String policyId) {
      this.policyId = policyId;
      return this;
    }

    public Builder inputs(List<ImpactReportInput> inputs) {
      this.inputs = inputs;
      return this;
    }

    /**
     * Builds the ImpactReportRequest.
     *
     * @return the request
     * @throws NullPointerException if policyId or inputs is null
     * @throws IllegalArgumentException if policyId is empty or inputs is empty
     */
    public ImpactReportRequest build() {
      return new ImpactReportRequest(this);
    }
  }
}
