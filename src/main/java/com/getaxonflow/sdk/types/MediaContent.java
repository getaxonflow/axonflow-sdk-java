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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Media content (image) to include with a request for governance analysis.
 *
 * <p>Supported formats: JPEG, PNG, GIF, WebP. Images can be provided as
 * base64-encoded data or referenced by URL.
 *
 * <p>Example usage:
 * <pre>{@code
 * MediaContent image = MediaContent.builder()
 *     .source("base64")
 *     .mimeType("image/jpeg")
 *     .base64Data(encodedImage)
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MediaContent {

    @JsonProperty("source")
    private final String source;

    @JsonProperty("base64_data")
    private final String base64Data;

    @JsonProperty("url")
    private final String url;

    @JsonProperty("mime_type")
    private final String mimeType;

    private MediaContent(Builder builder) {
        this.source = Objects.requireNonNull(builder.source, "source cannot be null");
        this.base64Data = builder.base64Data;
        this.url = builder.url;
        this.mimeType = Objects.requireNonNull(builder.mimeType, "mimeType cannot be null");
    }

    // Jackson deserialization constructor
    public MediaContent(
            @JsonProperty("source") String source,
            @JsonProperty("base64_data") String base64Data,
            @JsonProperty("url") String url,
            @JsonProperty("mime_type") String mimeType) {
        this.source = source;
        this.base64Data = base64Data;
        this.url = url;
        this.mimeType = mimeType;
    }

    public String getSource() { return source; }
    public String getBase64Data() { return base64Data; }
    public String getUrl() { return url; }
    public String getMimeType() { return mimeType; }

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaContent that = (MediaContent) o;
        return Objects.equals(source, that.source) &&
               Objects.equals(base64Data, that.base64Data) &&
               Objects.equals(url, that.url) &&
               Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, base64Data, url, mimeType);
    }

    @Override
    public String toString() {
        return "MediaContent{source='" + source + "', mimeType='" + mimeType + "'}";
    }

    public static final class Builder {
        private String source;
        private String base64Data;
        private String url;
        private String mimeType;

        private Builder() {}

        public Builder source(String source) { this.source = source; return this; }
        public Builder base64Data(String base64Data) { this.base64Data = base64Data; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }

        public MediaContent build() { return new MediaContent(this); }
    }
}
