/*
 * Copyright 2025 AxonFlow
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SDK compatibility information returned by the AxonFlow platform health endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SDKCompatibility {

    @JsonProperty("min_sdk_version")
    private final String minSdkVersion;

    @JsonProperty("recommended_sdk_version")
    private final String recommendedSdkVersion;

    public SDKCompatibility(
            @JsonProperty("min_sdk_version") String minSdkVersion,
            @JsonProperty("recommended_sdk_version") String recommendedSdkVersion) {
        this.minSdkVersion = minSdkVersion;
        this.recommendedSdkVersion = recommendedSdkVersion;
    }

    public String getMinSdkVersion() { return minSdkVersion; }
    public String getRecommendedSdkVersion() { return recommendedSdkVersion; }
}
