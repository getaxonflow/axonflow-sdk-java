# Changelog

All notable changes to the AxonFlow Java SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.3.0]

### Added

- **WCP Approval Gates** (Issue #1169): HITL approval and rejection for workflow steps
  - `approveStep(workflowId, stepId)` - Approve a pending workflow step
  - `rejectStep(workflowId, stepId, reason)` - Reject a step with reason (backward-compatible overload without reason preserved)
  - `rejectStepAsync(workflowId, stepId, reason)` - Async variant with reason
  - `getPendingApprovals(limit)` - List steps awaiting human approval

- **MAP Plan Cancellation** (Issue #1072): Cancel running multi-agent plans
  - `cancelPlan(planId, reason)` - Cancel a plan with optional reason

- **MAP Plan Update** (Issue #1072): Modify plan configuration before or during execution
  - `updatePlan(planId, request)` - Update execution mode, domain, or version

- **MAP Plan Versioning and Rollback** (Issue #1072): Version history and rollback support
  - `getPlanVersions(planId)` - List plan version history
  - `rollbackPlan(planId, version)` - Rollback to a previous version (throws on 409 conflict)
  - New types: `RollbackPlanResponse`, `PlanVersion`

- **Webhook Subscriptions** (Issue #1169): Event notification management
  - `createWebhook(request)` - Create a webhook subscription
  - `listWebhooks()` - List active webhook subscriptions
  - `getWebhook(webhookId)` - Get webhook details
  - `updateWebhook(webhookId, request)` - Update webhook configuration
  - `deleteWebhook(webhookId)` - Delete a webhook subscription
  - New type: `WebhookSubscription`

- **Unified Execution Cancellation** (EPIC #1074): Cancel running executions across both MAP and WCP subsystems
  - `cancelExecution(executionId, reason)` - Cancel a unified execution via `POST /api/v1/unified/executions/{id}/cancel`
  - Overloaded `cancelExecution(executionId)` variant without reason parameter
  - Propagates to MAP `cancelPlan()` or WCP `abortWorkflow()` based on execution type

### Fixed

- **Unified execution API URLs** (EPIC #1074): `getExecutionStatus()` and `listUnifiedExecutions()` now use correct `/api/v1/unified/executions` path (was incorrectly pointing to `/api/v1/executions` which is the Execution Replay API)
- **`rejectStep` reason parameter**: Added `reason` parameter to `rejectStep()` and `rejectStepAsync()` with backward-compatible 2-arg overloads

---

## [3.2.0] - 2026-02-05

### Added

- **Dynamic policy tier support**: `tier` (`PolicyTier`) and `organizationId` fields on `CreateDynamicPolicyRequest`, `UpdateDynamicPolicyRequest`, and `DynamicPolicy` response. Defaults to `PolicyTier.TENANT` when not specified. Builder: `.tier(PolicyTier.ORGANIZATION).organizationId("org-123")`.
- **`ListDynamicPoliciesOptions` filters**: Filter dynamic policies by `tier` and `organizationId`, matching `ListStaticPoliciesOptions` parity.

---

## [3.1.0] - 2026-02-04

### Changed

- Simplified internal endpoint handling by removing legacy helper names `getPortalUrl()` and `getOrchestratorUrl()`.
- Internal request URL construction is now standardized on `config.getEndpoint()`.
- No public API change.

## [3.0.0] - 2026-02-03

### Breaking Changes

- **Removed `executeQuery()`**: Use `proxyLLMCall()` instead (deprecated since v2.7.0). Removed both sync and async (`executeQueryAsync`) variants.

### Added

- **`isRedacted()` verification**: Verified `MCPExecuteResponse.isRedacted()` works correctly for execute responses with PII redaction

### Changed

- Updated all internal references, Javadoc examples, and tests from `executeQuery` to `proxyLLMCall`

---

## [2.7.1] - 2026-01-25

### Changed

- **Gateway Mode smart defaults**: `getPolicyApprovedContext()` and `auditLLMCall()` now use `"community"` as default clientId when not configured, enabling zero-config usage for community/self-hosted deployments

### Fixed

- **PolicyCategory enum**: Added `PII_SINGAPORE("pii-singapore")` value for Singapore PII detection policies (NRIC, FIN, UEN patterns)
- **proxyLLMCall clientId auto-injection**: Auto-populate `clientId` from config when not explicitly set in `ClientRequest`, matching Go/Python/TypeScript SDK behavior

---

## [2.7.0] - 2026-01-25

### Added

- **Unified Execution Tracking** (Issue #1075 - EPIC #1074): Consistent status tracking for MAP plans and WCP workflows
  - `getExecutionStatus(executionId)` - Get unified execution status by ID
  - `listUnifiedExecutions(request)` - List executions with type/status filters
  - `ExecutionTypes.ExecutionStatus` class with unified fields for both MAP and WCP executions
  - `ExecutionTypes.ExecutionType` enum: `MAP_PLAN`, `WCP_WORKFLOW`
  - `ExecutionTypes.ExecutionStatusValue` enum: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `ABORTED`, `EXPIRED`
  - `ExecutionTypes.StepStatusValue` enum: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED`, `BLOCKED`, `APPROVAL`
  - `ExecutionTypes.UnifiedStepType` enum: `LLM_CALL`, `TOOL_CALL`, `CONNECTOR_CALL`, `HUMAN_TASK`, `SYNTHESIS`, `ACTION`, `GATE`
  - `ExecutionTypes.UnifiedStepStatus` class with step-level details (duration, cost, policy decisions)
  - Helper methods: `isTerminal()`, `isStepTerminal()`, `isStepBlocking()`, `calculateTotalCost()`, `getCurrentStep()`
  - Consistent response format across MAP Multi-Agent Planning and WCP Workflow Control Plane

- **MAS FEAT Compliance Module** (Enterprise): Singapore financial services AI governance
  - AI System Registry: `masfeat().registerSystem()`, `masfeat().getSystem()`, `masfeat().updateSystem()`, `masfeat().listSystems()`, `masfeat().activateSystem()`, `masfeat().retireSystem()`, `masfeat().getRegistrySummary()`
  - 3-Dimensional Risk Rating: Customer Impact × Model Complexity × Human Reliance
  - Materiality Classification: High (sum≥12), Medium (sum≥8), Low (sum<8)
  - FEAT Assessments: `masfeat().createAssessment()`, `masfeat().getAssessment()`, `masfeat().updateAssessment()`, `masfeat().listAssessments()`, `masfeat().submitAssessment()`, `masfeat().approveAssessment()`, `masfeat().rejectAssessment()`
  - Assessment Lifecycle: pending → in_progress → completed → approved/rejected
  - Kill Switch: `masfeat().getKillSwitch()`, `masfeat().configureKillSwitch()`, `masfeat().checkKillSwitch()`, `masfeat().triggerKillSwitch()`, `masfeat().restoreKillSwitch()`, `masfeat().enableKillSwitch()`, `masfeat().disableKillSwitch()`, `masfeat().getKillSwitchHistory()`
  - Automatic model shutdown based on accuracy, bias, and error rate thresholds
  - New types: `AISystemRegistry`, `AISystemUseCase`, `MaterialityClassification`, `SystemStatus`, `FEATAssessment`, `FEATAssessmentStatus`, `FEATPillar`, `KillSwitch`, `KillSwitchStatus`, `KillSwitchEvent`, `KillSwitchEventType`, `RegistrySummary`

- **proxyLLMCall()**: New primary method for Proxy Mode with improved documentation
  - Clearly describes Proxy Mode behavior (AxonFlow makes the LLM call on your behalf)
  - Documents when to use Proxy Mode vs Gateway Mode
  - Both sync (`proxyLLMCall`) and async (`proxyLLMCallAsync`) variants

- **BudgetInfo**: `QueryResponse.getBudgetInfo()` for budget enforcement (HTTP 402)

### Deprecated

- **executeQuery()**: Deprecated in favor of proxyLLMCall()
  - Marked with `@Deprecated` annotation
  - Will be removed in v3.0.0
  - Logs deprecation warning in debug mode
  - Remains functional as a wrapper around proxyLLMCall()

---

## [2.6.0] - 2026-01-18

### Added

- **Workflow Policy Enforcement** (Issues #1019, #1020, #1021): Policy transparency for workflow operations
  - `StepGateResponse` now includes `getPoliciesEvaluated()` and `getPoliciesMatched()` methods with `PolicyMatch` type
  - `PolicyMatch` class with `getPolicyId()`, `getPolicyName()`, `getAction()`, `getReason()` for policy transparency
  - `PolicyEvaluationResult` class for MAP execution with `isAllowed()`, `getAppliedPolicies()`, `getRiskScore()`
  - Workflow operations (`workflow_created`, `workflow_step_gate`, `workflow_completed`) logged to audit trail

---

## [2.5.0] - 2026-01-17

### Added

- **Workflow Control Plane** (Issue #834): Governance gates for external orchestrators
  - "LangChain runs the workflow. AxonFlow decides when it's allowed to move forward."
  - `createWorkflow()` - Register workflows from LangChain/LangGraph/CrewAI/external
  - `stepGate()` - Check if step is allowed to proceed (allow/block/require_approval)
  - `markStepCompleted()` - Mark a step as completed with optional output data
  - `getWorkflow()` - Get workflow status and step history
  - `listWorkflows()` - List workflows with filters (status, source, pagination)
  - `completeWorkflow()` - Mark workflow as completed
  - `abortWorkflow()` - Abort workflow with reason
  - `resumeWorkflow()` - Resume after approval
  - New types: `WorkflowStatus`, `WorkflowSource`, `GateDecision`, `StepType`, `ApprovalStatus`, `MarkStepCompletedRequest`
  - Helper methods on `StepGateResponse`: `isAllowed()`, `isBlocked()`, `requiresApproval()`
  - Helper methods on `WorkflowStatus` and `WorkflowStatusResponse`: `isTerminal()`

---

## [2.4.0] - 2026-01-14

### Added

- **MCP Exfiltration Detection** (Issue #966): `ConnectorPolicyInfo` now includes `getExfiltrationCheck()` with row/volume limit information
  - `ExfiltrationCheckInfo` type with `getRowsReturned()`, `getRowLimit()`, `getBytesReturned()`, `getByteLimit()`, `isWithinLimits()` methods
  - Prevents large-scale data extraction via MCP queries
  - Configurable via `MCP_MAX_ROWS_PER_QUERY` and `MCP_MAX_BYTES_PER_QUERY` environment variables

- **MCP Dynamic Policies** (Issue #968): `ConnectorPolicyInfo` now includes `getDynamicPolicyInfo()` for Orchestrator-evaluated policies
  - `DynamicPolicyInfo` type with `getPoliciesEvaluated()`, `getMatchedPolicies()`, `isOrchestratorReachable()`, `getProcessingTimeMs()`
  - `DynamicPolicyMatch` type with `getPolicyId()`, `getPolicyName()`, `getPolicyType()`, `getAction()`, `getReason()`
  - Supports rate limiting, budget controls, time-based access, and role-based access policies
  - Optional feature - enable via `MCP_DYNAMIC_POLICIES_ENABLED=true`

---

## [2.3.0] - 2026-01-09

### Added

- **MCP Policy Enforcement Response Fields**: `mcpQuery()` and `mcpExecute()` now return policy enforcement metadata
  - `isRedacted()` - Whether any fields were redacted by PII policies
  - `getRedactedFields()` - JSON paths of redacted fields (e.g., `rows[0].ssn`)
  - `getPolicyInfo()` - Full policy evaluation metadata

- **PolicyInfo types**: New types for policy enforcement metadata
  - `ConnectorPolicyInfo` - Contains `getPoliciesEvaluated()`, `isBlocked()`, `getBlockReason()`, `getRedactionsApplied()`, `getProcessingTimeMs()`, `getMatchedPolicies()`
  - `PolicyMatchInfo` - Details of matched policies including `getPolicyId()`, `getPolicyName()`, `getCategory()`, `getSeverity()`, `getAction()`

---

## [2.2.0] - 2026-01-08

### Added

- **OAuth2-style client credentials**: New `clientId()` and `clientSecret()` builder methods following OAuth2 client credentials pattern.
  - `clientId` is used for request identification (required for most API calls)
  - `clientSecret` is optional - community/self-hosted deployments work without it

- **Enterprise: Close PR** (`closePR`): Close a PR without merging and optionally delete the branch
  - Useful for cleaning up test/demo PRs created by code governance examples
  - Supports all Git providers: GitHub, GitLab, Bitbucket
  - Requires enterprise portal authentication

### Changed

- **Simplified authentication**: For community mode, simply provide `clientId` for request identification. No `clientSecret` needed.

```java
// Community mode - no secret needed
AxonFlowClient client = AxonFlowClient.builder()
    .endpoint("http://localhost:8080")
    .clientId("my-app")  // Used for request identification
    .build();
```

### Fixed

- **getPlanStatus endpoint**: Fixed endpoint path from `/api/v1/orchestrator/plan/{id}` to `/api/v1/plan/{id}` to match agent proxy routes

### Enterprise

- OAuth2 Basic auth: `Authorization: Basic base64(clientId:clientSecret)` replaces `X-License-Key` header
- Removed `licenseKey()` builder method (use `clientId()`/`clientSecret()`)

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
