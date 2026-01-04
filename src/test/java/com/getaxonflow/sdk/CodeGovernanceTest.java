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
package com.getaxonflow.sdk;

import com.getaxonflow.sdk.types.codegovernance.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Code Governance Types")
class CodeGovernanceTest {

    // ========================================================================
    // GitProviderType Enum
    // ========================================================================

    @Nested
    @DisplayName("GitProviderType")
    class GitProviderTypeTests {

        @Test
        @DisplayName("getValue should return correct string")
        void getValueShouldReturnCorrectString() {
            assertThat(GitProviderType.GITHUB.getValue()).isEqualTo("github");
            assertThat(GitProviderType.GITLAB.getValue()).isEqualTo("gitlab");
            assertThat(GitProviderType.BITBUCKET.getValue()).isEqualTo("bitbucket");
        }

        @Test
        @DisplayName("fromValue should return correct enum")
        void fromValueShouldReturnCorrectEnum() {
            assertThat(GitProviderType.fromValue("github")).isEqualTo(GitProviderType.GITHUB);
            assertThat(GitProviderType.fromValue("gitlab")).isEqualTo(GitProviderType.GITLAB);
            assertThat(GitProviderType.fromValue("bitbucket")).isEqualTo(GitProviderType.BITBUCKET);
        }

        @Test
        @DisplayName("fromValue should be case insensitive")
        void fromValueShouldBeCaseInsensitive() {
            assertThat(GitProviderType.fromValue("GITHUB")).isEqualTo(GitProviderType.GITHUB);
            assertThat(GitProviderType.fromValue("GitHub")).isEqualTo(GitProviderType.GITHUB);
        }

        @Test
        @DisplayName("fromValue should throw for invalid value")
        void fromValueShouldThrowForInvalid() {
            assertThatThrownBy(() -> GitProviderType.fromValue("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Git provider type");
        }
    }

    // ========================================================================
    // FileAction Enum
    // ========================================================================

    @Nested
    @DisplayName("FileAction")
    class FileActionTests {

        @Test
        @DisplayName("getValue should return correct string")
        void getValueShouldReturnCorrectString() {
            assertThat(FileAction.CREATE.getValue()).isEqualTo("create");
            assertThat(FileAction.UPDATE.getValue()).isEqualTo("update");
            assertThat(FileAction.DELETE.getValue()).isEqualTo("delete");
        }

        @Test
        @DisplayName("fromValue should return correct enum")
        void fromValueShouldReturnCorrectEnum() {
            assertThat(FileAction.fromValue("create")).isEqualTo(FileAction.CREATE);
            assertThat(FileAction.fromValue("update")).isEqualTo(FileAction.UPDATE);
            assertThat(FileAction.fromValue("delete")).isEqualTo(FileAction.DELETE);
        }

        @Test
        @DisplayName("fromValue should be case insensitive")
        void fromValueShouldBeCaseInsensitive() {
            assertThat(FileAction.fromValue("CREATE")).isEqualTo(FileAction.CREATE);
            assertThat(FileAction.fromValue("Update")).isEqualTo(FileAction.UPDATE);
        }

        @Test
        @DisplayName("fromValue should throw for invalid value")
        void fromValueShouldThrowForInvalid() {
            assertThatThrownBy(() -> FileAction.fromValue("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown file action");
        }
    }

    // ========================================================================
    // CodeFile
    // ========================================================================

    @Nested
    @DisplayName("CodeFile")
    class CodeFileTests {

        @Test
        @DisplayName("builder should create CodeFile with all fields")
        void builderShouldCreateCodeFile() {
            CodeFile file = CodeFile.builder()
                .path("src/main/java/App.java")
                .content("public class App {}")
                .action(FileAction.CREATE)
                .language("java")
                .build();

            assertThat(file.getPath()).isEqualTo("src/main/java/App.java");
            assertThat(file.getContent()).isEqualTo("public class App {}");
            assertThat(file.getAction()).isEqualTo(FileAction.CREATE);
            assertThat(file.getLanguage()).isEqualTo("java");
        }

        @Test
        @DisplayName("builder should create CodeFile with minimal fields")
        void builderShouldCreateMinimalCodeFile() {
            CodeFile file = CodeFile.builder()
                .path("src/test.py")
                .content("print('hello')")
                .action(FileAction.UPDATE)
                .build();

            assertThat(file.getPath()).isEqualTo("src/test.py");
            assertThat(file.getLanguage()).isNull();
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void equalsAndHashCodeShouldWork() {
            CodeFile file1 = CodeFile.builder()
                .path("src/App.java")
                .content("code")
                .action(FileAction.CREATE)
                .build();

            CodeFile file2 = CodeFile.builder()
                .path("src/App.java")
                .content("code")
                .action(FileAction.CREATE)
                .build();

            assertThat(file1).isEqualTo(file2);
            assertThat(file1.hashCode()).isEqualTo(file2.hashCode());
        }

        @Test
        @DisplayName("toString should return non-empty string")
        void toStringShouldWork() {
            CodeFile file = CodeFile.builder()
                .path("src/App.java")
                .content("code")
                .action(FileAction.CREATE)
                .build();

            assertThat(file.toString()).contains("src/App.java");
        }
    }

    // ========================================================================
    // CreatePRRequest
    // ========================================================================

    @Nested
    @DisplayName("CreatePRRequest")
    class CreatePRRequestTests {

        @Test
        @DisplayName("builder should create request with all fields")
        void builderShouldCreateRequest() {
            CodeFile file = CodeFile.builder()
                .path("src/App.java")
                .content("code")
                .action(FileAction.CREATE)
                .build();

            CreatePRRequest request = CreatePRRequest.builder()
                .owner("owner")
                .repo("repo")
                .title("Add feature")
                .description("Feature description")
                .baseBranch("main")
                .branchName("feature-branch")
                .draft(false)
                .files(List.of(file))
                .agentRequestId("req-123")
                .model("gpt-4")
                .build();

            assertThat(request.getOwner()).isEqualTo("owner");
            assertThat(request.getRepo()).isEqualTo("repo");
            assertThat(request.getTitle()).isEqualTo("Add feature");
            assertThat(request.getDescription()).isEqualTo("Feature description");
            assertThat(request.getBaseBranch()).isEqualTo("main");
            assertThat(request.getBranchName()).isEqualTo("feature-branch");
            assertThat(request.isDraft()).isFalse();
            assertThat(request.getFiles()).hasSize(1);
            assertThat(request.getAgentRequestId()).isEqualTo("req-123");
            assertThat(request.getModel()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("equals and hashCode should work")
        void equalsAndHashCodeShouldWork() {
            CreatePRRequest r1 = CreatePRRequest.builder()
                .owner("owner").repo("repo").title("title").build();
            CreatePRRequest r2 = CreatePRRequest.builder()
                .owner("owner").repo("repo").title("title").build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("toString should work")
        void toStringShouldWork() {
            CreatePRRequest request = CreatePRRequest.builder()
                .owner("owner").repo("repo").title("title").build();

            assertThat(request.toString()).contains("owner");
        }
    }

    // ========================================================================
    // CreatePRResponse
    // ========================================================================

    @Nested
    @DisplayName("CreatePRResponse")
    class CreatePRResponseTests {

        @Test
        @DisplayName("constructor should create response with all fields")
        void constructorShouldCreateResponse() {
            Instant now = Instant.now();
            CreatePRResponse response = new CreatePRResponse(
                "pr-123", 42, "https://github.com/owner/repo/pull/42",
                "open", "feature-branch", now);

            assertThat(response.getPrId()).isEqualTo("pr-123");
            assertThat(response.getPrNumber()).isEqualTo(42);
            assertThat(response.getPrUrl()).isEqualTo("https://github.com/owner/repo/pull/42");
            assertThat(response.getState()).isEqualTo("open");
            assertThat(response.getHeadBranch()).isEqualTo("feature-branch");
            assertThat(response.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("equals and hashCode should work correctly")
        void equalsAndHashCodeShouldWork() {
            Instant now = Instant.now();
            CreatePRResponse r1 = new CreatePRResponse("pr-123", 42, "url", "open", "branch", now);
            CreatePRResponse r2 = new CreatePRResponse("pr-123", 42, "url", "open", "branch", now);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("toString should return non-empty string")
        void toStringShouldWork() {
            CreatePRResponse response = new CreatePRResponse(
                "pr-123", 42, "url", "open", "branch", Instant.now());

            assertThat(response.toString()).contains("pr-123");
        }
    }

    // ========================================================================
    // ValidateGitProviderRequest
    // ========================================================================

    @Nested
    @DisplayName("ValidateGitProviderRequest")
    class ValidateGitProviderRequestTests {

        @Test
        @DisplayName("builder should create request")
        void builderShouldCreateRequest() {
            ValidateGitProviderRequest request = ValidateGitProviderRequest.builder()
                .type(GitProviderType.GITHUB)
                .token("ghp_xxx")
                .baseUrl("https://github.com")
                .build();

            assertThat(request.getType()).isEqualTo(GitProviderType.GITHUB);
            assertThat(request.getToken()).isEqualTo("ghp_xxx");
            assertThat(request.getBaseUrl()).isEqualTo("https://github.com");
        }
    }

    // ========================================================================
    // ValidateGitProviderResponse
    // ========================================================================

    @Nested
    @DisplayName("ValidateGitProviderResponse")
    class ValidateGitProviderResponseTests {

        @Test
        @DisplayName("constructor should create response with all fields")
        void constructorShouldCreateResponse() {
            ValidateGitProviderResponse response = new ValidateGitProviderResponse(true, "Validation successful");

            assertThat(response.isValid()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Validation successful");
        }

        @Test
        @DisplayName("equals and hashCode should work")
        void equalsAndHashCodeShouldWork() {
            ValidateGitProviderResponse r1 = new ValidateGitProviderResponse(true, "ok");
            ValidateGitProviderResponse r2 = new ValidateGitProviderResponse(true, "ok");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }
    }

    // ========================================================================
    // ConfigureGitProviderRequest
    // ========================================================================

    @Nested
    @DisplayName("ConfigureGitProviderRequest")
    class ConfigureGitProviderRequestTests {

        @Test
        @DisplayName("builder should create request")
        void builderShouldCreateRequest() {
            ConfigureGitProviderRequest request = ConfigureGitProviderRequest.builder()
                .type(GitProviderType.GITHUB)
                .token("ghp_xxx")
                .baseUrl("https://github.com")
                .build();

            assertThat(request.getType()).isEqualTo(GitProviderType.GITHUB);
            assertThat(request.getToken()).isEqualTo("ghp_xxx");
            assertThat(request.getBaseUrl()).isEqualTo("https://github.com");
        }
    }

}
