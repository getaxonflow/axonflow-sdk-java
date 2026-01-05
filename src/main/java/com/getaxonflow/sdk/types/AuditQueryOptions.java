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

import java.util.Objects;

/**
 * Options for querying audit logs by tenant.
 *
 * <p>Example usage:
 * <pre>{@code
 * AuditQueryOptions options = AuditQueryOptions.builder()
 *     .limit(100)
 *     .offset(50)
 *     .build();
 *
 * AuditSearchResponse response = axonflow.getAuditLogsByTenant("tenant-abc", options);
 * }</pre>
 */
public final class AuditQueryOptions {

    private final int limit;
    private final int offset;

    private AuditQueryOptions(Builder builder) {
        this.limit = Math.min(builder.limit != null ? builder.limit : 50, 1000);
        this.offset = builder.offset != null ? builder.offset : 0;
    }

    /**
     * Returns the maximum number of results to return.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Returns the pagination offset.
     */
    public int getOffset() {
        return offset;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates default options with limit=50, offset=0.
     */
    public static AuditQueryOptions defaults() {
        return builder().build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditQueryOptions that = (AuditQueryOptions) o;
        return limit == that.limit && offset == that.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit, offset);
    }

    @Override
    public String toString() {
        return "AuditQueryOptions{limit=" + limit + ", offset=" + offset + '}';
    }

    /**
     * Builder for AuditQueryOptions.
     */
    public static final class Builder {
        private Integer limit;
        private Integer offset;

        private Builder() {}

        /**
         * Maximum results to return (default: 50, max: 1000).
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Pagination offset (default: 0).
         */
        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public AuditQueryOptions build() {
            return new AuditQueryOptions(this);
        }
    }
}
