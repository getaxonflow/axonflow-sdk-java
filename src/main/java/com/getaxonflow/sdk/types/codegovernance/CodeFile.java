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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * A code file to include in a PR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CodeFile {

    @JsonProperty("path")
    private final String path;

    @JsonProperty("content")
    private final String content;

    @JsonProperty("language")
    private final String language;

    @JsonProperty("action")
    private final FileAction action;

    public CodeFile(
            @JsonProperty("path") String path,
            @JsonProperty("content") String content,
            @JsonProperty("language") String language,
            @JsonProperty("action") FileAction action) {
        this.path = Objects.requireNonNull(path, "path is required");
        this.content = Objects.requireNonNull(content, "content is required");
        this.language = language;
        this.action = Objects.requireNonNull(action, "action is required");
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public String getLanguage() {
        return language;
    }

    public FileAction getAction() {
        return action;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String path;
        private String content;
        private String language;
        private FileAction action;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder action(FileAction action) {
            this.action = action;
            return this;
        }

        public CodeFile build() {
            return new CodeFile(path, content, language, action);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeFile codeFile = (CodeFile) o;
        return Objects.equals(path, codeFile.path) &&
               Objects.equals(content, codeFile.content) &&
               Objects.equals(language, codeFile.language) &&
               action == codeFile.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, content, language, action);
    }

    @Override
    public String toString() {
        return "CodeFile{" +
               "path='" + path + '\'' +
               ", language='" + language + '\'' +
               ", action=" + action +
               '}';
    }
}
