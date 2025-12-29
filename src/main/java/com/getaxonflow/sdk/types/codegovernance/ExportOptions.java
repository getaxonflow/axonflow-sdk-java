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
package com.getaxonflow.sdk.types.codegovernance;

import java.time.Instant;

/**
 * Options for exporting code governance data.
 */
public class ExportOptions {
    private String format = "json";
    private Instant startDate;
    private Instant endDate;
    private String state;

    public ExportOptions() {}

    public String getFormat() {
        return format;
    }

    public ExportOptions setFormat(String format) {
        this.format = format;
        return this;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public ExportOptions setStartDate(Instant startDate) {
        this.startDate = startDate;
        return this;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public ExportOptions setEndDate(Instant endDate) {
        this.endDate = endDate;
        return this;
    }

    public String getState() {
        return state;
    }

    public ExportOptions setState(String state) {
        this.state = state;
        return this;
    }
}
