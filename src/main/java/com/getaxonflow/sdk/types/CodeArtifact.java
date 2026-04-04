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
 * Represents metadata for LLM-generated code detection.
 *
 * <p>When an LLM generates code in its response, AxonFlow automatically detects and analyzes it.
 * This metadata is included in PolicyInfo for audit and compliance.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CodeArtifact {

  @JsonProperty("is_code_output")
  private final boolean isCodeOutput;

  @JsonProperty("language")
  private final String language;

  @JsonProperty("code_type")
  private final String codeType;

  @JsonProperty("size_bytes")
  private final int sizeBytes;

  @JsonProperty("line_count")
  private final int lineCount;

  @JsonProperty("secrets_detected")
  private final int secretsDetected;

  @JsonProperty("unsafe_patterns")
  private final int unsafePatterns;

  @JsonProperty("policies_checked")
  private final List<String> policiesChecked;

  /**
   * Creates a new CodeArtifact instance.
   *
   * @param isCodeOutput whether the response contains code
   * @param language detected programming language
   * @param codeType code category (function, class, script, etc.)
   * @param sizeBytes size of detected code in bytes
   * @param lineCount number of lines of code
   * @param secretsDetected count of potential secrets found
   * @param unsafePatterns count of unsafe code patterns
   * @param policiesChecked list of code governance policies evaluated
   */
  public CodeArtifact(
      @JsonProperty("is_code_output") boolean isCodeOutput,
      @JsonProperty("language") String language,
      @JsonProperty("code_type") String codeType,
      @JsonProperty("size_bytes") int sizeBytes,
      @JsonProperty("line_count") int lineCount,
      @JsonProperty("secrets_detected") int secretsDetected,
      @JsonProperty("unsafe_patterns") int unsafePatterns,
      @JsonProperty("policies_checked") List<String> policiesChecked) {
    this.isCodeOutput = isCodeOutput;
    this.language = language != null ? language : "";
    this.codeType = codeType != null ? codeType : "";
    this.sizeBytes = sizeBytes;
    this.lineCount = lineCount;
    this.secretsDetected = secretsDetected;
    this.unsafePatterns = unsafePatterns;
    this.policiesChecked =
        policiesChecked != null
            ? Collections.unmodifiableList(policiesChecked)
            : Collections.emptyList();
  }

  /**
   * Returns whether the response contains code.
   *
   * @return true if code was detected, false otherwise
   */
  public boolean isCodeOutput() {
    return isCodeOutput;
  }

  /**
   * Returns the detected programming language.
   *
   * @return the programming language (e.g., "python", "javascript", "go")
   */
  public String getLanguage() {
    return language;
  }

  /**
   * Returns the code category.
   *
   * @return the code type (e.g., "function", "class", "script", "config", "snippet")
   */
  public String getCodeType() {
    return codeType;
  }

  /**
   * Returns the size of detected code in bytes.
   *
   * @return code size in bytes
   */
  public int getSizeBytes() {
    return sizeBytes;
  }

  /**
   * Returns the number of lines of code.
   *
   * @return line count
   */
  public int getLineCount() {
    return lineCount;
  }

  /**
   * Returns the count of potential secrets found.
   *
   * @return number of secrets detected
   */
  public int getSecretsDetected() {
    return secretsDetected;
  }

  /**
   * Returns the count of unsafe code patterns.
   *
   * @return number of unsafe patterns detected
   */
  public int getUnsafePatterns() {
    return unsafePatterns;
  }

  /**
   * Returns the list of code governance policies that were evaluated.
   *
   * @return immutable list of policy names
   */
  public List<String> getPoliciesChecked() {
    return policiesChecked;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CodeArtifact that = (CodeArtifact) o;
    return isCodeOutput == that.isCodeOutput
        && sizeBytes == that.sizeBytes
        && lineCount == that.lineCount
        && secretsDetected == that.secretsDetected
        && unsafePatterns == that.unsafePatterns
        && Objects.equals(language, that.language)
        && Objects.equals(codeType, that.codeType)
        && Objects.equals(policiesChecked, that.policiesChecked);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        isCodeOutput,
        language,
        codeType,
        sizeBytes,
        lineCount,
        secretsDetected,
        unsafePatterns,
        policiesChecked);
  }

  @Override
  public String toString() {
    return "CodeArtifact{"
        + "isCodeOutput="
        + isCodeOutput
        + ", language='"
        + language
        + '\''
        + ", codeType='"
        + codeType
        + '\''
        + ", sizeBytes="
        + sizeBytes
        + ", lineCount="
        + lineCount
        + ", secretsDetected="
        + secretsDetected
        + ", unsafePatterns="
        + unsafePatterns
        + ", policiesChecked="
        + policiesChecked
        + '}';
  }
}
