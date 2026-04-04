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
package com.getaxonflow.sdk.adapters;

import com.getaxonflow.sdk.types.workflow.WorkflowTypes.ToolContext;
import java.util.Map;

/** Options for {@link LangGraphAdapter#checkGate}. */
public final class CheckGateOptions {

  private final String stepId;
  private final Map<String, Object> stepInput;
  private final String model;
  private final String provider;
  private final ToolContext toolContext;

  private CheckGateOptions(Builder builder) {
    this.stepId = builder.stepId;
    this.stepInput = builder.stepInput;
    this.model = builder.model;
    this.provider = builder.provider;
    this.toolContext = builder.toolContext;
  }

  public String getStepId() {
    return stepId;
  }

  public Map<String, Object> getStepInput() {
    return stepInput;
  }

  public String getModel() {
    return model;
  }

  public String getProvider() {
    return provider;
  }

  public ToolContext getToolContext() {
    return toolContext;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String stepId;
    private Map<String, Object> stepInput;
    private String model;
    private String provider;
    private ToolContext toolContext;

    private Builder() {}

    public Builder stepId(String stepId) {
      this.stepId = stepId;
      return this;
    }

    public Builder stepInput(Map<String, Object> stepInput) {
      this.stepInput = stepInput;
      return this;
    }

    public Builder model(String model) {
      this.model = model;
      return this;
    }

    public Builder provider(String provider) {
      this.provider = provider;
      return this;
    }

    public Builder toolContext(ToolContext toolContext) {
      this.toolContext = toolContext;
      return this;
    }

    public CheckGateOptions build() {
      return new CheckGateOptions(this);
    }
  }
}
