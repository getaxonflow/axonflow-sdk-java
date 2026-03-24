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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result for a single input in an impact report.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ImpactReportResult {

    @JsonProperty("query")
    private final String query;

    @JsonProperty("matched")
    private final boolean matched;

    @JsonProperty("blocked")
    private final boolean blocked;

    @JsonProperty("action")
    private final String action;

    public ImpactReportResult(
            @JsonProperty("query") String query,
            @JsonProperty("matched") boolean matched,
            @JsonProperty("blocked") boolean blocked,
            @JsonProperty("action") String action) {
        this.query = query;
        this.matched = matched;
        this.blocked = blocked;
        this.action = action;
    }

    public String getQuery() { return query; }
    public boolean isMatched() { return matched; }
    public boolean isBlocked() { return blocked; }
    public String getAction() { return action; }
}
