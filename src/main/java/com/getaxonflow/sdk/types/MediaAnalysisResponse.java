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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated media analysis results in the response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MediaAnalysisResponse {

    @JsonProperty("results")
    private final List<MediaAnalysisResult> results;

    @JsonProperty("total_cost_usd")
    private final double totalCostUsd;

    @JsonProperty("analysis_time_ms")
    private final long analysisTimeMs;

    public MediaAnalysisResponse(
            @JsonProperty("results") List<MediaAnalysisResult> results,
            @JsonProperty("total_cost_usd") double totalCostUsd,
            @JsonProperty("analysis_time_ms") long analysisTimeMs) {
        this.results = results != null ? Collections.unmodifiableList(results) : Collections.emptyList();
        this.totalCostUsd = totalCostUsd;
        this.analysisTimeMs = analysisTimeMs;
    }

    public List<MediaAnalysisResult> getResults() { return results; }
    public double getTotalCostUsd() { return totalCostUsd; }
    public long getAnalysisTimeMs() { return analysisTimeMs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaAnalysisResponse that = (MediaAnalysisResponse) o;
        return Double.compare(totalCostUsd, that.totalCostUsd) == 0 &&
               analysisTimeMs == that.analysisTimeMs &&
               Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(results, totalCostUsd, analysisTimeMs);
    }

    @Override
    public String toString() {
        return "MediaAnalysisResponse{results=" + (results != null ? results.size() : 0) +
               ", totalCostUsd=" + totalCostUsd +
               ", analysisTimeMs=" + analysisTimeMs + '}';
    }
}
