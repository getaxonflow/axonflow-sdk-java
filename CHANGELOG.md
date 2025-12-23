# Changelog

All notable changes to the AxonFlow Java SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
