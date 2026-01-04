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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for code governance types.
 */
@DisplayName("Code Governance Types")
class CodeGovernanceTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("FileAction")
    class FileActionTests {

        @Test
        @DisplayName("should have correct values")
        void shouldHaveCorrectValues() {
            assertThat(FileAction.CREATE.getValue()).isEqualTo("create");
            assertThat(FileAction.UPDATE.getValue()).isEqualTo("update");
            assertThat(FileAction.DELETE.getValue()).isEqualTo("delete");
        }

        @Test
        @DisplayName("should parse from value")
        void shouldParseFromValue() {
            assertThat(FileAction.fromValue("create")).isEqualTo(FileAction.CREATE);
            assertThat(FileAction.fromValue("update")).isEqualTo(FileAction.UPDATE);
            assertThat(FileAction.fromValue("delete")).isEqualTo(FileAction.DELETE);
        }

        @Test
        @DisplayName("should parse case insensitively")
        void shouldParseCaseInsensitively() {
            assertThat(FileAction.fromValue("CREATE")).isEqualTo(FileAction.CREATE);
            assertThat(FileAction.fromValue("Update")).isEqualTo(FileAction.UPDATE);
        }

        @Test
        @DisplayName("should throw for unknown value")
        void shouldThrowForUnknownValue() {
            assertThatThrownBy(() -> FileAction.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown file action");
        }
    }

    @Nested
    @DisplayName("GitProviderType")
    class GitProviderTypeTests {

        @Test
        @DisplayName("should have correct values")
        void shouldHaveCorrectValues() {
            assertThat(GitProviderType.GITHUB.getValue()).isEqualTo("github");
            assertThat(GitProviderType.GITLAB.getValue()).isEqualTo("gitlab");
            assertThat(GitProviderType.BITBUCKET.getValue()).isEqualTo("bitbucket");
        }

        @Test
        @DisplayName("should parse from value")
        void shouldParseFromValue() {
            assertThat(GitProviderType.fromValue("github")).isEqualTo(GitProviderType.GITHUB);
            assertThat(GitProviderType.fromValue("gitlab")).isEqualTo(GitProviderType.GITLAB);
            assertThat(GitProviderType.fromValue("bitbucket")).isEqualTo(GitProviderType.BITBUCKET);
        }

        @Test
        @DisplayName("should parse case insensitively")
        void shouldParseCaseInsensitively() {
            assertThat(GitProviderType.fromValue("GITHUB")).isEqualTo(GitProviderType.GITHUB);
            assertThat(GitProviderType.fromValue("GitLab")).isEqualTo(GitProviderType.GITLAB);
        }

        @Test
        @DisplayName("should throw for unknown value")
        void shouldThrowForUnknownValue() {
            assertThatThrownBy(() -> GitProviderType.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Git provider type");
        }
    }

    @Nested
    @DisplayName("CodeFile")
    class CodeFileTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreateWithAllFields() {
            CodeFile file = new CodeFile(
                "src/main/java/Test.java",
                "public class Test {}",
                "java",
                FileAction.CREATE
            );

            assertThat(file.getPath()).isEqualTo("src/main/java/Test.java");
            assertThat(file.getContent()).isEqualTo("public class Test {}");
            assertThat(file.getLanguage()).isEqualTo("java");
            assertThat(file.getAction()).isEqualTo(FileAction.CREATE);
        }

        @Test
        @DisplayName("should build using builder")
        void shouldBuildUsingBuilder() {
            CodeFile file = CodeFile.builder()
                .path("src/test.py")
                .content("print('hello')")
                .language("python")
                .action(FileAction.UPDATE)
                .build();

            assertThat(file.getPath()).isEqualTo("src/test.py");
            assertThat(file.getContent()).isEqualTo("print('hello')");
            assertThat(file.getLanguage()).isEqualTo("python");
            assertThat(file.getAction()).isEqualTo(FileAction.UPDATE);
        }

        @Test
        @DisplayName("should fail when path is null")
        void shouldFailWhenPathIsNull() {
            assertThatThrownBy(() -> new CodeFile(null, "content", "java", FileAction.CREATE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("path is required");
        }

        @Test
        @DisplayName("should fail when content is null")
        void shouldFailWhenContentIsNull() {
            assertThatThrownBy(() -> new CodeFile("path", null, "java", FileAction.CREATE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("content is required");
        }

        @Test
        @DisplayName("should fail when action is null")
        void shouldFailWhenActionIsNull() {
            assertThatThrownBy(() -> new CodeFile("path", "content", "java", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("action is required");
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"path\":\"test.go\"," +
                "\"content\":\"package main\"," +
                "\"language\":\"go\"," +
                "\"action\":\"create\"" +
                "}";

            CodeFile file = objectMapper.readValue(json, CodeFile.class);

            assertThat(file.getPath()).isEqualTo("test.go");
            assertThat(file.getLanguage()).isEqualTo("go");
            assertThat(file.getAction()).isEqualTo(FileAction.CREATE);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            CodeFile f1 = new CodeFile("p", "c", "l", FileAction.CREATE);
            CodeFile f2 = new CodeFile("p", "c", "l", FileAction.CREATE);
            CodeFile f3 = new CodeFile("p2", "c", "l", FileAction.CREATE);

            assertThat(f1).isEqualTo(f2);
            assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
            assertThat(f1).isNotEqualTo(f3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            CodeFile file = new CodeFile("test.java", "content", "java", FileAction.CREATE);
            assertThat(file.toString()).contains("CodeFile").contains("test.java");
        }
    }

    @Nested
    @DisplayName("GitProviderInfo")
    class GitProviderInfoTests {

        @Test
        @DisplayName("should create with type")
        void shouldCreateWithType() {
            GitProviderInfo info = new GitProviderInfo(GitProviderType.GITHUB);
            assertThat(info.getType()).isEqualTo(GitProviderType.GITHUB);
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{\"type\":\"gitlab\"}";
            GitProviderInfo info = objectMapper.readValue(json, GitProviderInfo.class);
            assertThat(info.getType()).isEqualTo(GitProviderType.GITLAB);
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            GitProviderInfo i1 = new GitProviderInfo(GitProviderType.GITHUB);
            GitProviderInfo i2 = new GitProviderInfo(GitProviderType.GITHUB);
            GitProviderInfo i3 = new GitProviderInfo(GitProviderType.GITLAB);

            assertThat(i1).isEqualTo(i2);
            assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
            assertThat(i1).isNotEqualTo(i3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            GitProviderInfo info = new GitProviderInfo(GitProviderType.BITBUCKET);
            assertThat(info.toString()).contains("GitProviderInfo").contains("BITBUCKET");
        }
    }

    @Nested
    @DisplayName("ListPRsOptions")
    class ListPRsOptionsTests {

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            ListPRsOptions options = ListPRsOptions.builder()
                .limit(10)
                .offset(20)
                .state("open")
                .build();

            assertThat(options.getLimit()).isEqualTo(10);
            assertThat(options.getOffset()).isEqualTo(20);
            assertThat(options.getState()).isEqualTo("open");
        }

        @Test
        @DisplayName("should build with partial fields")
        void shouldBuildWithPartialFields() {
            ListPRsOptions options = ListPRsOptions.builder()
                .limit(5)
                .build();

            assertThat(options.getLimit()).isEqualTo(5);
            assertThat(options.getOffset()).isNull();
            assertThat(options.getState()).isNull();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ListPRsOptions o1 = ListPRsOptions.builder().limit(10).state("open").build();
            ListPRsOptions o2 = ListPRsOptions.builder().limit(10).state("open").build();
            ListPRsOptions o3 = ListPRsOptions.builder().limit(20).state("open").build();

            assertThat(o1).isEqualTo(o2);
            assertThat(o1.hashCode()).isEqualTo(o2.hashCode());
            assertThat(o1).isNotEqualTo(o3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ListPRsOptions options = ListPRsOptions.builder().limit(50).build();
            assertThat(options.toString()).contains("ListPRsOptions").contains("50");
        }
    }

    @Nested
    @DisplayName("PRRecord")
    class PRRecordTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            PRRecord record = new PRRecord(
                "pr-123", 42, "https://github.com/owner/repo/pull/42",
                "Add feature", "open", "owner", "repo",
                "feature-branch", "main", 5, 0, 1,
                now, "user@test.com", "github"
            );

            assertThat(record.getId()).isEqualTo("pr-123");
            assertThat(record.getPrNumber()).isEqualTo(42);
            assertThat(record.getPrUrl()).isEqualTo("https://github.com/owner/repo/pull/42");
            assertThat(record.getTitle()).isEqualTo("Add feature");
            assertThat(record.getState()).isEqualTo("open");
            assertThat(record.getOwner()).isEqualTo("owner");
            assertThat(record.getRepo()).isEqualTo("repo");
            assertThat(record.getHeadBranch()).isEqualTo("feature-branch");
            assertThat(record.getBaseBranch()).isEqualTo("main");
            assertThat(record.getFilesCount()).isEqualTo(5);
            assertThat(record.getSecretsDetected()).isEqualTo(0);
            assertThat(record.getUnsafePatterns()).isEqualTo(1);
            assertThat(record.getCreatedAt()).isEqualTo(now);
            assertThat(record.getCreatedBy()).isEqualTo("user@test.com");
            assertThat(record.getProviderType()).isEqualTo("github");
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"id\":\"pr-456\"," +
                "\"pr_number\":123," +
                "\"pr_url\":\"https://gitlab.com/owner/repo/-/merge_requests/123\"," +
                "\"title\":\"Fix bug\"," +
                "\"state\":\"merged\"," +
                "\"owner\":\"myorg\"," +
                "\"repo\":\"myrepo\"," +
                "\"head_branch\":\"fix/bug\"," +
                "\"base_branch\":\"develop\"," +
                "\"files_count\":3," +
                "\"secrets_detected\":0," +
                "\"unsafe_patterns\":0," +
                "\"provider_type\":\"gitlab\"" +
                "}";

            PRRecord record = objectMapper.readValue(json, PRRecord.class);

            assertThat(record.getId()).isEqualTo("pr-456");
            assertThat(record.getPrNumber()).isEqualTo(123);
            assertThat(record.getTitle()).isEqualTo("Fix bug");
            assertThat(record.getState()).isEqualTo("merged");
            assertThat(record.getProviderType()).isEqualTo("gitlab");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            PRRecord r1 = new PRRecord("id1", 1, "url", "t", "s", "o", "r", "h", "b", 1, 0, 0, null, "u", "g");
            PRRecord r2 = new PRRecord("id1", 1, "url", "t", "s", "o", "r", "h", "b", 1, 0, 0, null, "u", "g");
            PRRecord r3 = new PRRecord("id2", 1, "url", "t", "s", "o", "r", "h", "b", 1, 0, 0, null, "u", "g");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            PRRecord record = new PRRecord("id", 99, "url", "My PR", "open", "owner", "repo", "h", "b", 2, 0, 0, null, "u", "g");
            String str = record.toString();
            assertThat(str).contains("PRRecord");
            assertThat(str).contains("My PR");
            assertThat(str).contains("99");
        }
    }

    @Nested
    @DisplayName("CreatePRRequest")
    class CreatePRRequestTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() {
            CreatePRRequest request = CreatePRRequest.builder()
                .owner("owner")
                .repo("repo")
                .title("My PR")
                .build();

            assertThat(request.getOwner()).isEqualTo("owner");
            assertThat(request.getRepo()).isEqualTo("repo");
            assertThat(request.getTitle()).isEqualTo("My PR");
            assertThat(request.getFiles()).isEmpty();
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            List<CodeFile> files = Arrays.asList(
                new CodeFile("test.java", "content", "java", FileAction.CREATE)
            );
            List<String> policies = Arrays.asList("security", "style");

            CreatePRRequest request = CreatePRRequest.builder()
                .owner("myorg")
                .repo("myrepo")
                .title("Feature: Add login")
                .description("Adds login functionality")
                .baseBranch("main")
                .branchName("feature/login")
                .draft(true)
                .files(files)
                .agentRequestId("agent-123")
                .model("gpt-4")
                .policiesChecked(policies)
                .secretsDetected(0)
                .unsafePatterns(0)
                .build();

            assertThat(request.getOwner()).isEqualTo("myorg");
            assertThat(request.getRepo()).isEqualTo("myrepo");
            assertThat(request.getTitle()).isEqualTo("Feature: Add login");
            assertThat(request.getDescription()).isEqualTo("Adds login functionality");
            assertThat(request.getBaseBranch()).isEqualTo("main");
            assertThat(request.getBranchName()).isEqualTo("feature/login");
            assertThat(request.isDraft()).isTrue();
            assertThat(request.getFiles()).hasSize(1);
            assertThat(request.getAgentRequestId()).isEqualTo("agent-123");
            assertThat(request.getModel()).isEqualTo("gpt-4");
            assertThat(request.getPoliciesChecked()).containsExactly("security", "style");
            assertThat(request.getSecretsDetected()).isEqualTo(0);
            assertThat(request.getUnsafePatterns()).isEqualTo(0);
        }

        @Test
        @DisplayName("should fail when owner is null")
        void shouldFailWhenOwnerIsNull() {
            assertThatThrownBy(() -> CreatePRRequest.builder()
                .repo("repo")
                .title("title")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("owner is required");
        }

        @Test
        @DisplayName("should fail when repo is null")
        void shouldFailWhenRepoIsNull() {
            assertThatThrownBy(() -> CreatePRRequest.builder()
                .owner("owner")
                .title("title")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repo is required");
        }

        @Test
        @DisplayName("should fail when title is null")
        void shouldFailWhenTitleIsNull() {
            assertThatThrownBy(() -> CreatePRRequest.builder()
                .owner("owner")
                .repo("repo")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("title is required");
        }

        @Test
        @DisplayName("should handle null files list")
        void shouldHandleNullFilesList() {
            CreatePRRequest request = CreatePRRequest.builder()
                .owner("o")
                .repo("r")
                .title("t")
                .files(null)
                .build();

            assertThat(request.getFiles()).isEmpty();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            CreatePRRequest r1 = CreatePRRequest.builder().owner("o").repo("r").title("t").build();
            CreatePRRequest r2 = CreatePRRequest.builder().owner("o").repo("r").title("t").build();
            CreatePRRequest r3 = CreatePRRequest.builder().owner("o2").repo("r").title("t").build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            CreatePRRequest request = CreatePRRequest.builder()
                .owner("myowner")
                .repo("myrepo")
                .title("My title")
                .draft(true)
                .build();
            String str = request.toString();
            assertThat(str).contains("CreatePRRequest");
            assertThat(str).contains("myowner");
            assertThat(str).contains("myrepo");
        }
    }

    @Nested
    @DisplayName("CreatePRResponse")
    class CreatePRResponseTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            CreatePRResponse response = new CreatePRResponse(
                "pr-id-123", 99, "https://github.com/o/r/pull/99",
                "open", "feature-branch", now
            );

            assertThat(response.getPrId()).isEqualTo("pr-id-123");
            assertThat(response.getPrNumber()).isEqualTo(99);
            assertThat(response.getPrUrl()).isEqualTo("https://github.com/o/r/pull/99");
            assertThat(response.getState()).isEqualTo("open");
            assertThat(response.getHeadBranch()).isEqualTo("feature-branch");
            assertThat(response.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            String json = "{" +
                "\"pr_id\":\"abc123\"," +
                "\"pr_number\":42," +
                "\"pr_url\":\"https://gitlab.com/merge/42\"," +
                "\"state\":\"opened\"," +
                "\"head_branch\":\"my-branch\"" +
                "}";

            CreatePRResponse response = objectMapper.readValue(json, CreatePRResponse.class);

            assertThat(response.getPrId()).isEqualTo("abc123");
            assertThat(response.getPrNumber()).isEqualTo(42);
            assertThat(response.getState()).isEqualTo("opened");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            CreatePRResponse r1 = new CreatePRResponse("id1", 1, "url", "open", "b", null);
            CreatePRResponse r2 = new CreatePRResponse("id1", 1, "url", "open", "b", null);
            CreatePRResponse r3 = new CreatePRResponse("id2", 1, "url", "open", "b", null);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            CreatePRResponse response = new CreatePRResponse("id", 77, "url", "merged", "branch", null);
            String str = response.toString();
            assertThat(str).contains("CreatePRResponse");
            assertThat(str).contains("77");
        }
    }

    @Nested
    @DisplayName("ConfigureGitProviderRequest")
    class ConfigureGitProviderRequestTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() {
            ConfigureGitProviderRequest request = ConfigureGitProviderRequest.builder()
                .type(GitProviderType.GITHUB)
                .build();

            assertThat(request.getType()).isEqualTo(GitProviderType.GITHUB);
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            ConfigureGitProviderRequest request = ConfigureGitProviderRequest.builder()
                .type(GitProviderType.GITHUB)
                .token("ghp_token123")
                .baseUrl("https://github.example.com")
                .appId(12345)
                .installationId(67890)
                .privateKey("-----BEGIN PRIVATE KEY-----")
                .build();

            assertThat(request.getType()).isEqualTo(GitProviderType.GITHUB);
            assertThat(request.getToken()).isEqualTo("ghp_token123");
            assertThat(request.getBaseUrl()).isEqualTo("https://github.example.com");
            assertThat(request.getAppId()).isEqualTo(12345);
            assertThat(request.getInstallationId()).isEqualTo(67890);
            assertThat(request.getPrivateKey()).isEqualTo("-----BEGIN PRIVATE KEY-----");
        }

        @Test
        @DisplayName("should fail when type is null")
        void shouldFailWhenTypeIsNull() {
            assertThatThrownBy(() -> ConfigureGitProviderRequest.builder()
                .token("token")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type is required");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ConfigureGitProviderRequest r1 = ConfigureGitProviderRequest.builder()
                .type(GitProviderType.GITHUB)
                .token("t")
                .build();
            ConfigureGitProviderRequest r2 = ConfigureGitProviderRequest.builder()
                .type(GitProviderType.GITHUB)
                .token("t")
                .build();
            ConfigureGitProviderRequest r3 = ConfigureGitProviderRequest.builder()
                .type(GitProviderType.GITLAB)
                .token("t")
                .build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ConfigureGitProviderRequest request = ConfigureGitProviderRequest.builder()
                .type(GitProviderType.BITBUCKET)
                .baseUrl("https://bitbucket.org")
                .build();
            String str = request.toString();
            assertThat(str).contains("ConfigureGitProviderRequest");
            assertThat(str).contains("BITBUCKET");
        }
    }

    @Nested
    @DisplayName("ValidateGitProviderRequest")
    class ValidateGitProviderRequestTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() {
            ValidateGitProviderRequest request = ValidateGitProviderRequest.builder()
                .type(GitProviderType.GITLAB)
                .build();

            assertThat(request.getType()).isEqualTo(GitProviderType.GITLAB);
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            ValidateGitProviderRequest request = ValidateGitProviderRequest.builder()
                .type(GitProviderType.GITLAB)
                .token("glpat-xxx")
                .baseUrl("https://gitlab.example.com")
                .appId(111)
                .installationId(222)
                .privateKey("key")
                .build();

            assertThat(request.getType()).isEqualTo(GitProviderType.GITLAB);
            assertThat(request.getToken()).isEqualTo("glpat-xxx");
            assertThat(request.getBaseUrl()).isEqualTo("https://gitlab.example.com");
            assertThat(request.getAppId()).isEqualTo(111);
            assertThat(request.getInstallationId()).isEqualTo(222);
            assertThat(request.getPrivateKey()).isEqualTo("key");
        }

        @Test
        @DisplayName("should fail when type is null")
        void shouldFailWhenTypeIsNull() {
            assertThatThrownBy(() -> ValidateGitProviderRequest.builder()
                .token("token")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type is required");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ValidateGitProviderRequest r1 = ValidateGitProviderRequest.builder()
                .type(GitProviderType.GITLAB)
                .token("t")
                .build();
            ValidateGitProviderRequest r2 = ValidateGitProviderRequest.builder()
                .type(GitProviderType.GITLAB)
                .token("t")
                .build();
            ValidateGitProviderRequest r3 = ValidateGitProviderRequest.builder()
                .type(GitProviderType.GITHUB)
                .token("t")
                .build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ValidateGitProviderRequest request = ValidateGitProviderRequest.builder()
                .type(GitProviderType.GITLAB)
                .baseUrl("https://gitlab.com")
                .build();
            String str = request.toString();
            assertThat(str).contains("ValidateGitProviderRequest");
            assertThat(str).contains("GITLAB");
        }
    }

    @Nested
    @DisplayName("ExportOptions")
    class ExportOptionsTests {

        @Test
        @DisplayName("should create with defaults")
        void shouldCreateWithDefaults() {
            ExportOptions options = new ExportOptions();

            assertThat(options.getFormat()).isEqualTo("json");
            assertThat(options.getStartDate()).isNull();
            assertThat(options.getEndDate()).isNull();
            assertThat(options.getState()).isNull();
        }

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            Instant start = Instant.parse("2026-01-01T00:00:00Z");
            Instant end = Instant.parse("2026-01-31T23:59:59Z");

            ExportOptions options = new ExportOptions()
                .setFormat("csv")
                .setStartDate(start)
                .setEndDate(end)
                .setState("merged");

            assertThat(options.getFormat()).isEqualTo("csv");
            assertThat(options.getStartDate()).isEqualTo(start);
            assertThat(options.getEndDate()).isEqualTo(end);
            assertThat(options.getState()).isEqualTo("merged");
        }

        @Test
        @DisplayName("should support fluent API")
        void shouldSupportFluentApi() {
            ExportOptions options = new ExportOptions()
                .setFormat("json")
                .setState("open");

            assertThat(options.getFormat()).isEqualTo("json");
            assertThat(options.getState()).isEqualTo("open");
        }
    }

    @Nested
    @DisplayName("ListPRsResponse")
    class ListPRsResponseTests {

        @Test
        @DisplayName("should create with prs and count")
        void shouldCreateWithPrsAndCount() {
            PRRecord pr = new PRRecord("id", 1, "url", "t", "s", "o", "r", "h", "b", 1, 0, 0, null, "u", "g");
            List<PRRecord> prs = Arrays.asList(pr);

            ListPRsResponse response = new ListPRsResponse(prs, 1);

            assertThat(response.getPrs()).hasSize(1);
            assertThat(response.getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle null prs list")
        void shouldHandleNullPrsList() {
            ListPRsResponse response = new ListPRsResponse(null, 0);
            assertThat(response.getPrs()).isEmpty();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ListPRsResponse r1 = new ListPRsResponse(null, 5);
            ListPRsResponse r2 = new ListPRsResponse(null, 5);
            ListPRsResponse r3 = new ListPRsResponse(null, 10);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ListPRsResponse response = new ListPRsResponse(null, 3);
            assertThat(response.toString()).contains("ListPRsResponse").contains("3");
        }
    }

    @Nested
    @DisplayName("ListGitProvidersResponse")
    class ListGitProvidersResponseTests {

        @Test
        @DisplayName("should create with providers and count")
        void shouldCreateWithProvidersAndCount() {
            GitProviderInfo info = new GitProviderInfo(GitProviderType.GITHUB);
            List<GitProviderInfo> providers = Arrays.asList(info);

            ListGitProvidersResponse response = new ListGitProvidersResponse(providers, 1);

            assertThat(response.getProviders()).hasSize(1);
            assertThat(response.getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle null providers list")
        void shouldHandleNullProvidersList() {
            ListGitProvidersResponse response = new ListGitProvidersResponse(null, 0);
            assertThat(response.getProviders()).isEmpty();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ListGitProvidersResponse r1 = new ListGitProvidersResponse(null, 2);
            ListGitProvidersResponse r2 = new ListGitProvidersResponse(null, 2);
            ListGitProvidersResponse r3 = new ListGitProvidersResponse(null, 4);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ListGitProvidersResponse response = new ListGitProvidersResponse(null, 1);
            assertThat(response.toString()).contains("ListGitProvidersResponse");
        }
    }

    @Nested
    @DisplayName("ConfigureGitProviderResponse")
    class ConfigureGitProviderResponseTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreateWithAllFields() {
            ConfigureGitProviderResponse response = new ConfigureGitProviderResponse(
                "Provider configured successfully", "github"
            );

            assertThat(response.getMessage()).isEqualTo("Provider configured successfully");
            assertThat(response.getType()).isEqualTo("github");
        }

        @Test
        @DisplayName("should handle null values with defaults")
        void shouldHandleNullValues() {
            ConfigureGitProviderResponse response = new ConfigureGitProviderResponse(null, null);

            assertThat(response.getMessage()).isEmpty();
            assertThat(response.getType()).isEmpty();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ConfigureGitProviderResponse r1 = new ConfigureGitProviderResponse("msg", "type");
            ConfigureGitProviderResponse r2 = new ConfigureGitProviderResponse("msg", "type");
            ConfigureGitProviderResponse r3 = new ConfigureGitProviderResponse("msg2", "type");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ConfigureGitProviderResponse response = new ConfigureGitProviderResponse("OK", "gitlab");
            assertThat(response.toString()).contains("ConfigureGitProviderResponse");
        }
    }

    @Nested
    @DisplayName("ValidateGitProviderResponse")
    class ValidateGitProviderResponseTests {

        @Test
        @DisplayName("should create valid response")
        void shouldCreateValidResponse() {
            ValidateGitProviderResponse response = new ValidateGitProviderResponse(true, "Validation successful");

            assertThat(response.isValid()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Validation successful");
        }

        @Test
        @DisplayName("should create invalid response")
        void shouldCreateInvalidResponse() {
            ValidateGitProviderResponse response = new ValidateGitProviderResponse(false, "Invalid token");

            assertThat(response.isValid()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Invalid token");
        }

        @Test
        @DisplayName("should handle null message")
        void shouldHandleNullMessage() {
            ValidateGitProviderResponse response = new ValidateGitProviderResponse(true, null);
            assertThat(response.getMessage()).isEmpty();
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            ValidateGitProviderResponse r1 = new ValidateGitProviderResponse(true, "msg");
            ValidateGitProviderResponse r2 = new ValidateGitProviderResponse(true, "msg");
            ValidateGitProviderResponse r3 = new ValidateGitProviderResponse(false, "msg");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
        }

        @Test
        @DisplayName("should have toString")
        void shouldHaveToString() {
            ValidateGitProviderResponse response = new ValidateGitProviderResponse(true, "OK");
            assertThat(response.toString()).contains("ValidateGitProviderResponse").contains("true");
        }
    }
}
