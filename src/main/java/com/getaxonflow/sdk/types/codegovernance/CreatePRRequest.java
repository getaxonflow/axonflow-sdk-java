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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Request to create a PR from LLM-generated code.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CreatePRRequest {

    @JsonProperty("owner")
    private final String owner;

    @JsonProperty("repo")
    private final String repo;

    @JsonProperty("title")
    private final String title;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("base_branch")
    private final String baseBranch;

    @JsonProperty("branch_name")
    private final String branchName;

    @JsonProperty("draft")
    private final boolean draft;

    @JsonProperty("files")
    private final List<CodeFile> files;

    @JsonProperty("agent_request_id")
    private final String agentRequestId;

    @JsonProperty("model")
    private final String model;

    @JsonProperty("policies_checked")
    private final List<String> policiesChecked;

    @JsonProperty("secrets_detected")
    private final Integer secretsDetected;

    @JsonProperty("unsafe_patterns")
    private final Integer unsafePatterns;

    public CreatePRRequest(
            @JsonProperty("owner") String owner,
            @JsonProperty("repo") String repo,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("base_branch") String baseBranch,
            @JsonProperty("branch_name") String branchName,
            @JsonProperty("draft") boolean draft,
            @JsonProperty("files") List<CodeFile> files,
            @JsonProperty("agent_request_id") String agentRequestId,
            @JsonProperty("model") String model,
            @JsonProperty("policies_checked") List<String> policiesChecked,
            @JsonProperty("secrets_detected") Integer secretsDetected,
            @JsonProperty("unsafe_patterns") Integer unsafePatterns) {
        this.owner = Objects.requireNonNull(owner, "owner is required");
        this.repo = Objects.requireNonNull(repo, "repo is required");
        this.title = Objects.requireNonNull(title, "title is required");
        this.description = description;
        this.baseBranch = baseBranch;
        this.branchName = branchName;
        this.draft = draft;
        this.files = files != null ? Collections.unmodifiableList(files) : Collections.emptyList();
        this.agentRequestId = agentRequestId;
        this.model = model;
        this.policiesChecked = policiesChecked != null ? Collections.unmodifiableList(policiesChecked) : null;
        this.secretsDetected = secretsDetected;
        this.unsafePatterns = unsafePatterns;
    }

    public String getOwner() { return owner; }
    public String getRepo() { return repo; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getBaseBranch() { return baseBranch; }
    public String getBranchName() { return branchName; }
    public boolean isDraft() { return draft; }
    public List<CodeFile> getFiles() { return files; }
    public String getAgentRequestId() { return agentRequestId; }
    public String getModel() { return model; }
    public List<String> getPoliciesChecked() { return policiesChecked; }
    public Integer getSecretsDetected() { return secretsDetected; }
    public Integer getUnsafePatterns() { return unsafePatterns; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String owner;
        private String repo;
        private String title;
        private String description;
        private String baseBranch;
        private String branchName;
        private boolean draft;
        private List<CodeFile> files;
        private String agentRequestId;
        private String model;
        private List<String> policiesChecked;
        private Integer secretsDetected;
        private Integer unsafePatterns;

        public Builder owner(String owner) { this.owner = owner; return this; }
        public Builder repo(String repo) { this.repo = repo; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder baseBranch(String baseBranch) { this.baseBranch = baseBranch; return this; }
        public Builder branchName(String branchName) { this.branchName = branchName; return this; }
        public Builder draft(boolean draft) { this.draft = draft; return this; }
        public Builder files(List<CodeFile> files) { this.files = files; return this; }
        public Builder agentRequestId(String agentRequestId) { this.agentRequestId = agentRequestId; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder policiesChecked(List<String> policiesChecked) { this.policiesChecked = policiesChecked; return this; }
        public Builder secretsDetected(Integer secretsDetected) { this.secretsDetected = secretsDetected; return this; }
        public Builder unsafePatterns(Integer unsafePatterns) { this.unsafePatterns = unsafePatterns; return this; }

        public CreatePRRequest build() {
            return new CreatePRRequest(owner, repo, title, description, baseBranch, branchName,
                    draft, files, agentRequestId, model, policiesChecked, secretsDetected, unsafePatterns);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreatePRRequest that = (CreatePRRequest) o;
        return draft == that.draft &&
               Objects.equals(owner, that.owner) &&
               Objects.equals(repo, that.repo) &&
               Objects.equals(title, that.title) &&
               Objects.equals(files, that.files);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, repo, title, draft, files);
    }

    @Override
    public String toString() {
        return "CreatePRRequest{" +
               "owner='" + owner + '\'' +
               ", repo='" + repo + '\'' +
               ", title='" + title + '\'' +
               ", draft=" + draft +
               ", filesCount=" + (files != null ? files.size() : 0) +
               '}';
    }
}
