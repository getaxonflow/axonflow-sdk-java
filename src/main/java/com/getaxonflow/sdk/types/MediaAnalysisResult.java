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
 * Analysis results for a single media item.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MediaAnalysisResult {

    @JsonProperty("media_index")
    private final int mediaIndex;

    @JsonProperty("sha256_hash")
    private final String sha256Hash;

    @JsonProperty("has_faces")
    private final boolean hasFaces;

    @JsonProperty("face_count")
    private final int faceCount;

    @JsonProperty("has_biometric_data")
    private final boolean hasBiometricData;

    @JsonProperty("nsfw_score")
    private final double nsfwScore;

    @JsonProperty("violence_score")
    private final double violenceScore;

    @JsonProperty("content_safe")
    private final boolean contentSafe;

    @JsonProperty("document_type")
    private final String documentType;

    @JsonProperty("is_sensitive_document")
    private final boolean isSensitiveDocument;

    @JsonProperty("has_pii")
    private final boolean hasPII;

    @JsonProperty("pii_types")
    private final List<String> piiTypes;

    @JsonProperty("has_extracted_text")
    private final boolean hasExtractedText;

    @JsonProperty("extracted_text_length")
    private final int extractedTextLength;

    @JsonProperty("estimated_cost_usd")
    private final double estimatedCostUsd;

    @JsonProperty("warnings")
    private final List<String> warnings;

    public MediaAnalysisResult(
            @JsonProperty("media_index") int mediaIndex,
            @JsonProperty("sha256_hash") String sha256Hash,
            @JsonProperty("has_faces") boolean hasFaces,
            @JsonProperty("face_count") int faceCount,
            @JsonProperty("has_biometric_data") boolean hasBiometricData,
            @JsonProperty("nsfw_score") double nsfwScore,
            @JsonProperty("violence_score") double violenceScore,
            @JsonProperty("content_safe") boolean contentSafe,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("is_sensitive_document") boolean isSensitiveDocument,
            @JsonProperty("has_pii") boolean hasPII,
            @JsonProperty("pii_types") List<String> piiTypes,
            @JsonProperty("has_extracted_text") boolean hasExtractedText,
            @JsonProperty("extracted_text_length") int extractedTextLength,
            @JsonProperty("estimated_cost_usd") double estimatedCostUsd,
            @JsonProperty("warnings") List<String> warnings) {
        this.mediaIndex = mediaIndex;
        this.sha256Hash = sha256Hash;
        this.hasFaces = hasFaces;
        this.faceCount = faceCount;
        this.hasBiometricData = hasBiometricData;
        this.nsfwScore = nsfwScore;
        this.violenceScore = violenceScore;
        this.contentSafe = contentSafe;
        this.documentType = documentType;
        this.isSensitiveDocument = isSensitiveDocument;
        this.hasPII = hasPII;
        this.piiTypes = piiTypes != null ? Collections.unmodifiableList(piiTypes) : Collections.emptyList();
        this.hasExtractedText = hasExtractedText;
        this.extractedTextLength = extractedTextLength;
        this.estimatedCostUsd = estimatedCostUsd;
        this.warnings = warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
    }

    public int getMediaIndex() { return mediaIndex; }
    public String getSha256Hash() { return sha256Hash; }
    public boolean isHasFaces() { return hasFaces; }
    public int getFaceCount() { return faceCount; }
    public boolean isHasBiometricData() { return hasBiometricData; }
    public double getNsfwScore() { return nsfwScore; }
    public double getViolenceScore() { return violenceScore; }
    public boolean isContentSafe() { return contentSafe; }
    public String getDocumentType() { return documentType; }
    public boolean isSensitiveDocument() { return isSensitiveDocument; }
    public boolean isHasPII() { return hasPII; }
    public List<String> getPiiTypes() { return piiTypes; }
    public boolean isHasExtractedText() { return hasExtractedText; }
    public int getExtractedTextLength() { return extractedTextLength; }
    public double getEstimatedCostUsd() { return estimatedCostUsd; }
    public List<String> getWarnings() { return warnings; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaAnalysisResult that = (MediaAnalysisResult) o;
        return mediaIndex == that.mediaIndex &&
               hasFaces == that.hasFaces &&
               faceCount == that.faceCount &&
               hasBiometricData == that.hasBiometricData &&
               Double.compare(nsfwScore, that.nsfwScore) == 0 &&
               Double.compare(violenceScore, that.violenceScore) == 0 &&
               contentSafe == that.contentSafe &&
               isSensitiveDocument == that.isSensitiveDocument &&
               hasPII == that.hasPII &&
               hasExtractedText == that.hasExtractedText &&
               extractedTextLength == that.extractedTextLength &&
               Double.compare(estimatedCostUsd, that.estimatedCostUsd) == 0 &&
               Objects.equals(sha256Hash, that.sha256Hash) &&
               Objects.equals(documentType, that.documentType) &&
               Objects.equals(piiTypes, that.piiTypes) &&
               Objects.equals(warnings, that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaIndex, sha256Hash, hasFaces, faceCount,
            hasBiometricData, nsfwScore, violenceScore, contentSafe,
            documentType, isSensitiveDocument, hasPII, piiTypes,
            hasExtractedText, extractedTextLength, estimatedCostUsd, warnings);
    }

    @Override
    public String toString() {
        return "MediaAnalysisResult{mediaIndex=" + mediaIndex +
               ", contentSafe=" + contentSafe +
               ", hasPII=" + hasPII +
               ", hasFaces=" + hasFaces +
               ", hasExtractedText=" + hasExtractedText +
               ", extractedTextLength=" + extractedTextLength + '}';
    }
}
