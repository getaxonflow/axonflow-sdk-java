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

import java.util.Map;

/**
 * Options for {@link LangGraphAdapter#checkToolGate}.
 */
public final class CheckToolGateOptions {

    private final String stepName;
    private final String stepId;
    private final Map<String, Object> toolInput;
    private final String model;
    private final String provider;

    private CheckToolGateOptions(Builder builder) {
        this.stepName = builder.stepName;
        this.stepId = builder.stepId;
        this.toolInput = builder.toolInput;
        this.model = builder.model;
        this.provider = builder.provider;
    }

    public String getStepName() {
        return stepName;
    }

    public String getStepId() {
        return stepId;
    }

    public Map<String, Object> getToolInput() {
        return toolInput;
    }

    public String getModel() {
        return model;
    }

    public String getProvider() {
        return provider;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String stepName;
        private String stepId;
        private Map<String, Object> toolInput;
        private String model;
        private String provider;

        private Builder() {
        }

        public Builder stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder toolInput(Map<String, Object> toolInput) {
            this.toolInput = toolInput;
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

        public CheckToolGateOptions build() {
            return new CheckToolGateOptions(this);
        }
    }
}
