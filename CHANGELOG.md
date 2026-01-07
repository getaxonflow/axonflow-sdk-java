# Changelog

All notable changes to the AxonFlow Java SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2.0] - 2026-01-07

### Added

- **Enterprise: Close PR** (`closePR`): Close a PR without merging and optionally delete the branch
  - Useful for cleaning up test/demo PRs created by code governance examples
  - Supports all Git providers: GitHub, GitLab, Bitbucket
  - Requires enterprise portal authentication
- **PRRecord.closedAt**: Added optional `closedAt` field to track when a PR was closed

## [2.1.2] - 2026-01-07

### Fixed

- **Gateway Mode clientId not sent in request body**: Fixed `getPolicyApprovedContext()` to auto-populate `client_id` in request body from config when not explicitly provided
  - Server requires `client_id` in JSON body for `/api/policy/pre-check` endpoint
  - Previously only sent as header (X-Client-ID), causing "client_id field is required" errors
  - Now matches Go SDK behavior which auto-populates from `config.ClientID`
  - Affects all Gateway Mode pre-check calls

- **executePlan() using non-existent endpoint**: Fixed `executePlan()` to use correct Agent API endpoint
  - Changed from `/api/v1/orchestrator/plan/{planId}/execute` (404) to `/api/request` with `request_type: "execute-plan"`
  - Now matches Go SDK pattern for plan execution
  - Fixes MAP (Multi-Agent Planning) two-step execution flow

## [2.1.1] - 2026-01-06

### Fixed

- **Null Policies List Handling**: Fixed `NullPointerException` in list-returning policy methods when API returns null instead of empty array
  - Affected methods: `listDynamicPolicies()`, `getEffectiveDynamicPolicies()`, `listStaticPolicies()`, `getEffectiveStaticPolicies()`
  - Added explicit null check for wrapper and list fields before returning
  - Returns empty list when wrapper or list field is null

## [2.1.0] - 2026-01-05

### Added

- **Sensitive Data Category**: Added `SENSITIVE_DATA` to `PolicyCategory` enum for policies that return `sensitive-data` category
- **Provider Restrictions for Compliance**: Support for `allowed_providers` in dynamic policy action config
  - Specify allowed providers via `DynamicPolicyAction` with `config.put("allowed_providers", List.of(...))`
  - Enables GDPR, HIPAA, and RBI compliance by restricting LLM routing to specific providers
  - Example: `new DynamicPolicyAction("route", Map.of("allowed_providers", List.of("ollama", "azure-eu")))`
- **Category field**: Added `category` field to `CreateDynamicPolicyRequest` and `UpdateDynamicPolicyRequest`
- **Dynamic Policy Response Wrappers**: Added `DynamicPoliciesResponse` and `DynamicPolicyResponse` wrapper types

### Fixed

- **toggleDynamicPolicy HTTP Method**: Changed from PATCH to PUT to match API specification
- **Dynamic Policy Response Parsing**: Fixed all dynamic policy methods to correctly parse wrapped API responses (Issue #886)
  - Agent proxy returns `{"policies": [...]}` and `{"policy": {...}}` wrappers
  - Updated `listDynamicPolicies`, `getDynamicPolicy`, `createDynamicPolicy`, `updateDynamicPolicy`, `toggleDynamicPolicy`, `getEffectiveDynamicPolicies`
- **X-Tenant-ID Header for Orchestrator Requests**: Fixed missing X-Tenant-ID header in orchestrator API calls
  - Added `addTenantIdHeader()` call to `buildOrchestratorRequest()` method
  - Ensures tenant identification works in community/self-hosted mode without full credentials

## [2.0.0] - 2026-01-05

### Breaking Changes

- **BREAKING**: Renamed `agentUrl` to `endpoint` in `AxonFlowConfig.Builder`
- **BREAKING**: Removed `orchestratorUrl` and `portalUrl` config options (Agent now proxies all routes per ADR-026)
- **BREAKING**: Dynamic policy API path changed from `/api/v1/policies/dynamic` to `/api/v1/dynamic-policies`

### Added

- **Audit Log Reading**: Added `searchAuditLogs()` for searching audit logs with filters (user email, client ID, time range, request type)
- **Tenant Audit Logs**: Added `getAuditLogsByTenant()` for retrieving audit logs scoped to a specific tenant
- **Audit Types**: Added `AuditLogEntry`, `AuditSearchRequest`, `AuditQueryOptions`, and `AuditSearchResponse` types
- **PII Redaction Support**: Added `isRequiresRedaction()` method to `PolicyApprovalResult` (Issue #891)
  - When `true`, PII was detected with redact action and response should be processed for redaction
  - Supports new detection defaults: PII defaults to redact instead of block

### Changed

- All SDK methods now route through single Agent endpoint
- Simplified configuration - only `endpoint()` builder method needed
- Removed `getOrchestratorUrl()` and `getPortalUrl()` config methods (now return endpoint directly)
- Added `@Deprecated` annotation on `agentUrl()` builder method for backwards compatibility

### Migration Guide

**Before (v1.x):**
```java
AxonFlowConfig config = AxonFlowConfig.builder()
    .agentUrl("http://localhost:8080")
    .orchestratorUrl("http://localhost:8081")
    .portalUrl("http://localhost:8082")
    .clientId("my-client")
    .clientSecret("my-secret")
    .build();
```

**After (v2.x):**
```java
AxonFlowConfig config = AxonFlowConfig.builder()
    .endpoint("http://localhost:8080")
    .clientId("my-client")
    .clientSecret("my-secret")
    .build();
```

---

## [1.12.0] - 2026-01-04

### Added

- **Portal Authentication**: Added `loginToPortal()` and `logoutFromPortal()` for session-based authentication
- **Portal URL Configuration**: New `portalUrl` config option for Code Governance portal endpoints
- **CSV Export**: Added `exportCodeGovernanceDataCsv()` for CSV format exports

### Fixed

- **Code Governance Authentication**: Changed Code Governance methods to use portal session-based auth instead of API key auth

---

## [1.11.0] - 2026-01-04

### Added

- **Get Connector**: `getConnector(id)` to retrieve details for a specific connector
- **Connector Health Check**: `getConnectorHealth(id)` to check health status of an installed connector
- **ConnectorHealthStatus type**: New type for connector health responses
- **Orchestrator Health Check**: `orchestratorHealthCheck()` to verify Orchestrator service health
- **Uninstall Connector**: `uninstallConnector()` to remove installed MCP connectors

### Fixed

- **Connector API Endpoints**: Fixed endpoints to use Orchestrator (port 8081) instead of Agent
  - `listConnectors()` - Changed from Agent `/api/connectors` to Orchestrator `/api/v1/connectors`
  - `installConnector()` - Fixed path to `/api/v1/connectors/{id}/install`
- **Dynamic Policies Endpoint**: Changed from Agent `/api/v1/policies` to Orchestrator `/api/v1/policies/dynamic`

---

## [1.10.0] - 2026-01-04

### Added

- **Execution Replay API**: Debug governed workflows with step-by-step state capture
  - `listExecutions()` - List executions with filtering (status, time range)
  - `getExecution()` - Get execution with all step snapshots
  - `getExecutionSteps()` - Get individual step snapshots
  - `getExecutionTimeline()` - Timeline view for visualization
  - `exportExecution()` - Export for compliance/archival
  - `deleteExecution()` - Delete execution records

- **Cost Controls**: Budget management and LLM usage tracking
  - `createBudget()` / `getBudget()` / `listBudgets()` - Budget CRUD
  - `updateBudget()` / `deleteBudget()` - Budget management
  - `getBudgetStatus()` - Check current budget usage
  - `checkBudget()` - Pre-request budget validation
  - `recordUsage()` - Record LLM token usage
  - `getUsageSummary()` - Usage analytics and reporting

---

## [1.9.0] - 2025-12-31

### Fixed

- **Gateway Mode Community Fix**: Removed client-side credential validation from Gateway Mode methods
  - `getPolicyApprovedContext()` and `auditLLMCall()` now work without credentials in community/self-hosted deployments
  - Server decides auth requirements based on `DEPLOYMENT_MODE`
  - Matches TypeScript SDK v1.11.1 behavior

---

## [1.8.0] - 2025-12-30

### Changed

- **Community Mode**: Credentials are now optional for self-hosted/community deployments
  - SDK can be initialized without `licenseKey` or `clientId/clientSecret` for community features
  - `executeQuery()` and `healthCheck()` work without credentials
  - Auth headers are only sent when credentials are configured

### Added

- `hasCredentials()` method in `AxonFlowConfig` to check if credentials are configured
- `requireCredentials()` helper for enterprise feature validation

### Fixed

- Fixed `PolicyOverride` JSON field mappings (`action_override`, `override_reason`)
- Fixed `listPolicyOverrides()` endpoint path and response parsing
- Fixed `getStaticPolicyVersions()` response parsing

---

## [1.7.0] - 2025-12-29

_Note: v1.7.0 on Maven Central does not include community mode. Use v1.8.0 instead._

---

## [1.6.0] - 2025-12-29

### Added

- **Enterprise Policy Features**:
  - `organizationId()` builder method in `CreateStaticPolicyRequest` for organization-tier policies
  - `organizationId()` builder method in `ListStaticPoliciesOptions` for filtering by organization
  - `listPolicyOverrides()` method to list all active policy overrides

- **Convenience Methods**:
  - `listStaticPolicies(PolicyTier tier, String organizationId)` - filter by tier and organization
  - `listStaticPolicies(PolicyTier tier, PolicyCategory category)` - filter by tier and category
  - `listStaticPolicies(PolicyCategory category)` - filter by category
  - `getEffectiveStaticPolicies(PolicyCategory category)` - filter effective policies by category

---

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
