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

/** Request to configure a Git provider. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConfigureGitProviderRequest {

  @JsonProperty("type")
  private final GitProviderType type;

  @JsonProperty("token")
  private final String token;

  @JsonProperty("base_url")
  private final String baseUrl;

  @JsonProperty("app_id")
  private final Integer appId;

  @JsonProperty("installation_id")
  private final Integer installationId;

  @JsonProperty("private_key")
  private final String privateKey;

  public ConfigureGitProviderRequest(
      @JsonProperty("type") GitProviderType type,
      @JsonProperty("token") String token,
      @JsonProperty("base_url") String baseUrl,
      @JsonProperty("app_id") Integer appId,
      @JsonProperty("installation_id") Integer installationId,
      @JsonProperty("private_key") String privateKey) {
    this.type = Objects.requireNonNull(type, "type is required");
    this.token = token;
    this.baseUrl = baseUrl;
    this.appId = appId;
    this.installationId = installationId;
    this.privateKey = privateKey;
  }

  public GitProviderType getType() {
    return type;
  }

  public String getToken() {
    return token;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public Integer getAppId() {
    return appId;
  }

  public Integer getInstallationId() {
    return installationId;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private GitProviderType type;
    private String token;
    private String baseUrl;
    private Integer appId;
    private Integer installationId;
    private String privateKey;

    public Builder type(GitProviderType type) {
      this.type = type;
      return this;
    }

    public Builder token(String token) {
      this.token = token;
      return this;
    }

    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public Builder appId(Integer appId) {
      this.appId = appId;
      return this;
    }

    public Builder installationId(Integer installationId) {
      this.installationId = installationId;
      return this;
    }

    public Builder privateKey(String privateKey) {
      this.privateKey = privateKey;
      return this;
    }

    public ConfigureGitProviderRequest build() {
      return new ConfigureGitProviderRequest(
          type, token, baseUrl, appId, installationId, privateKey);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConfigureGitProviderRequest that = (ConfigureGitProviderRequest) o;
    return type == that.type
        && Objects.equals(token, that.token)
        && Objects.equals(baseUrl, that.baseUrl)
        && Objects.equals(appId, that.appId)
        && Objects.equals(installationId, that.installationId)
        && Objects.equals(privateKey, that.privateKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, token, baseUrl, appId, installationId, privateKey);
  }

  @Override
  public String toString() {
    return "ConfigureGitProviderRequest{"
        + "type="
        + type
        + ", baseUrl='"
        + baseUrl
        + '\''
        + ", appId="
        + appId
        + ", installationId="
        + installationId
        + '}';
  }
}
