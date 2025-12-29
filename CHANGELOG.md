# Changelog

All notable changes to the AxonFlow Java SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.0] - 2025-12-29

### Added

- **Code Governance Metrics & Export APIs** (Enterprise): Compliance reporting for AI-generated code
  - `getCodeGovernanceMetrics()` - Returns aggregated statistics (PR counts, file totals, security findings)
  - `exportCodeGovernanceData()` - Exports PR records as JSON for auditors
  - `exportCodeGovernanceDataCSV()` - Exports PR records as CSV

- **New Types**: `CodeGovernanceMetrics`, `ExportOptions`, `ExportResponse`

---

## [1.4.0] - 2025-12-29

### Added

- **Code Governance Git Provider APIs** (Enterprise): Create PRs from LLM-generated code
  - `validateGitProvider()` - Validate credentials before saving
  - `configureGitProvider()` - Configure GitHub, GitLab, or Bitbucket
  - `listGitProviders()` - List configured providers
  - `deleteGitProvider()` - Remove a provider
  - `createPR()` - Create PR from generated code with audit trail
  - `listPRs()` - List PRs with filtering
  - `getPR()` - Get PR details
  - `syncPRStatus()` - Sync status from Git provider

- **New Types**: `GitProviderType`, `FileAction`, `CodeFile`, `CreatePRRequest`, `CreatePRResponse`, `PRRecord`, `ListPRsOptions`, `ListPRsResponse`

- **Supported Git Providers**:
  - GitHub (Cloud and Enterprise Server)
  - GitLab (Cloud and Self-Managed)
  - Bitbucket (Cloud and Server/Data Center)

---

## [1.3.1] - 2025-12-29

### Fixed

- **MCP Connector Queries**: Fixed endpoint mismatch causing 404 errors
  - Changed `queryConnector()` to use `/api/request` with `request_type: "mcp-query"` (matches Go and TypeScript SDKs)
  - Previously used non-existent `/api/v1/connectors/query` endpoint
  - MCP connector examples now work correctly with configured connectors

---

## [1.3.0] - 2025-12-28

### Added

- **HITL Support**: `PolicyAction.REQUIRE_APPROVAL` for human oversight policies
  - Use with `createStaticPolicy()` to trigger approval workflows
  - Enterprise: Full HITL queue integration
  - Community: Auto-approves immediately

- **Code Governance**: `CodeArtifact` type for LLM-generated code detection
  - Language and code type identification
  - Potential secrets and unsafe pattern detection

---

## [1.2.0] - 2025-12-25

### Added

- **Policy CRUD Methods**: Full policy management support for Unified Policy Architecture v2.0.0
  - `listStaticPolicies()` - List policies with filtering
  - `getStaticPolicy()` - Get single policy by ID
  - `createStaticPolicy()` - Create custom policy
  - `updateStaticPolicy()` - Update existing policy
  - `deleteStaticPolicy()` - Delete policy
  - `toggleStaticPolicy()` - Enable/disable policy
  - `getEffectiveStaticPolicies()` - Get merged hierarchy
  - `testPattern()` - Test regex pattern

- **Policy Override Methods** (Enterprise)
- **Dynamic Policy Methods**
- **New Types**: `StaticPolicy`, `DynamicPolicy`, `PolicyOverride`

## [1.1.2] - 2025-12-23

### Fixed

- **Java 11 Compatibility** - Fixed compilation error on Java 11
  - Replaced `Stream.toList()` (Java 16+) with `Collectors.toList()` (Java 8+)
  - MAP plan parsing now works correctly on all supported Java versions (11, 17, 21)

## [1.1.1] - 2025-12-23

### Fixed

- **MAP Endpoint** - Fixed `generatePlan()` to use correct Agent API endpoint
  - Changed from `/api/v1/orchestrator/plan` to `/api/request` with `request_type: "multi-agent-plan"`
  - Added proper response parsing for Agent API format
  - Fixed null-safety issues with request context

## [1.1.0] - 2025-12-19

### Added

- **LLM Interceptors** - Transparent governance for LLM API calls (#1)
  - `OpenAIInterceptor` for OpenAI API interception
  - `AnthropicInterceptor` for Anthropic API interception
  - `GeminiInterceptor` for Google Generative AI interception
  - Policy enforcement and audit logging for all providers
- Full feature parity with other SDKs for LLM interceptors
- **Self-Hosted Zero-Config Tests** - Auth header verification for localhost (#2)
  - Tests verify auth headers are skipped for localhost endpoints

## [1.0.0] - 2025-12-04

### Added

- Initial release of AxonFlow Java SDK
- Core client with `executeQuery()` for governed AI calls
- Policy enforcement with `PolicyViolationException`
- **Gateway Mode** support
  - `getPolicyApprovedContext()` for pre-checks
  - `auditLLMCall()` for compliance logging
- **Multi-Agent Planning**
  - `generatePlan()` for creating execution plans
  - `executePlan()` for running plans
  - `getPlanStatus()` for checking plan status
- **MCP Connectors**
  - `listConnectors()` for available connectors
  - `installConnector()` for connector installation
  - `queryConnector()` for connector queries
- Comprehensive type definitions with Jackson
- Retry logic with exponential backoff (OkHttp)
- Response caching with Caffeine
- Self-hosted mode for localhost deployments
- Java 11+ compatibility
- Maven Central publishing support
