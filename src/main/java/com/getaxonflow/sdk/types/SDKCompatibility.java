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
import java.util.Map;
import java.util.Objects;

/** SDK compatibility information returned by the AxonFlow platform health endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SDKCompatibility {

  @JsonProperty("min_sdk_version")
  private final Map<String, String> minSdkVersion;

  @JsonProperty("recommended_sdk_version")
  private final Map<String, String> recommendedSdkVersion;

  public SDKCompatibility(
      @JsonProperty("min_sdk_version") Map<String, String> minSdkVersion,
      @JsonProperty("recommended_sdk_version") Map<String, String> recommendedSdkVersion) {
    this.minSdkVersion = minSdkVersion;
    this.recommendedSdkVersion = recommendedSdkVersion;
  }

  /** Returns the per-language minimum SDK version map (e.g. {"java":"5.0.0","python":"6.0.0"}). */
  public Map<String, String> getMinSdkVersion() {
    return minSdkVersion;
  }

  /** Returns the minimum SDK version for a specific language, or null if not specified. */
  public String getMinSdkVersionFor(String language) {
    return minSdkVersion != null ? minSdkVersion.get(language) : null;
  }

  /** Returns the per-language recommended SDK version map. */
  public Map<String, String> getRecommendedSdkVersion() {
    return recommendedSdkVersion;
  }

  /** Returns the recommended SDK version for a specific language, or null if not specified. */
  public String getRecommendedSdkVersionFor(String language) {
    return recommendedSdkVersion != null ? recommendedSdkVersion.get(language) : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SDKCompatibility that = (SDKCompatibility) o;
    return Objects.equals(minSdkVersion, that.minSdkVersion)
        && Objects.equals(recommendedSdkVersion, that.recommendedSdkVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(minSdkVersion, recommendedSdkVersion);
  }

  @Override
  public String toString() {
    return "SDKCompatibility{minSdkVersion="
        + minSdkVersion
        + ", recommendedSdkVersion="
        + recommendedSdkVersion
        + "}";
  }
}
