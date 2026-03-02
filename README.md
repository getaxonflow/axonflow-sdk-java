# AxonFlow Java SDK

Official Java SDK for [AxonFlow](https://getaxonflow.com) - AI Governance Platform for Enterprise LLM Applications.

[![CI](https://github.com/getaxonflow/axonflow-sdk-java/actions/workflows/ci.yml/badge.svg)](https://github.com/getaxonflow/axonflow-sdk-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.getaxonflow/axonflow-sdk.svg)](https://search.maven.org/artifact/com.getaxonflow/axonflow-sdk)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **Evaluating AxonFlow in production?** We're opening limited Design Partner slots.
>
> Free 30-minute architecture and incident-readiness review, priority issue triage, roadmap input, and early feature access.
>
> [Apply here](https://getaxonflow.com/design-partner?utm_source=readme_sdk_java) or email [design-partners@getaxonflow.com](mailto:design-partners@getaxonflow.com).
>
> No commitment required. We reply within 48 hours.

> **Questions or feedback?**
>
> Comment in [GitHub Discussions](https://github.com/getaxonflow/axonflow/discussions/239) or email [hello@getaxonflow.com](mailto:hello@getaxonflow.com) for private feedback.

## How This SDK Fits with AxonFlow

This SDK is a client library for interacting with a running AxonFlow control plane. It is used from application or agent code to send execution context, policies, and requests at runtime.

A deployed AxonFlow platform (self-hosted or cloud) is required for end-to-end AI governance. SDKs alone are not sufficient—the platform and SDKs are designed to be used together.

### Architecture Overview (2 min)

If you're new to AxonFlow, this short video shows how the control plane and SDKs work together in a real production setup:

[![AxonFlow Architecture Overview](https://img.youtube.com/vi/WwQXHKuZhxc/maxresdefault.jpg)](https://youtu.be/WwQXHKuZhxc)

▶️ [Watch on YouTube](https://youtu.be/WwQXHKuZhxc)

## Requirements

- Java 11 or higher
- Maven 3.6+ or Gradle 6.0+

## Installation

### Maven

```xml
<dependency>
    <groupId>com.getaxonflow</groupId>
    <artifactId>axonflow-sdk</artifactId>
    <version>3.2.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.getaxonflow:axonflow-sdk:3.2.0'
```

## Evaluation Tier (Free License)

Need more capacity than Community without moving to Enterprise? Evaluation uses the same core features with higher limits:

| Limit | Community | Evaluation (Free) | Enterprise |
|-------|-----------|-------------------|------------|
| Tenant policies | 20 | 50 | Unlimited |
| Org-wide policies | 0 | 5 | Unlimited |
| Audit retention | 3 days | 14 days | 3650 days |
| Concurrent executions | 5 | 25 | Unlimited |
| Pending execution approvals | 5 | 25 | Unlimited |

Concurrent executions applies to MAP and WCP executions per tenant. Pending execution approvals applies to MAP confirm/step mode and WCP approval queues.

[Get a free Evaluation license](https://getaxonflow.com/evaluation-license?utm_source=readme_sdk_java_eval) · [Full feature matrix](https://docs.getaxonflow.com/docs/features/community-vs-enterprise?utm_source=readme_sdk_java_eval)

## Quick Start

### Gateway Mode (Recommended)

Gateway mode provides the most control, allowing you to pre-check requests before making LLM calls:

```java
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.*;

public class GatewayExample {
    public static void main(String[] args) {
        // Initialize client
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint("https://agent.getaxonflow.com")
            .clientId("your-client-id")
            .clientSecret("your-client-secret")
            .build());

        // Step 1: Pre-check the request
        PolicyApproval approval = client.getPolicyApprovedContext(
            ClientRequest.builder()
                .userPrompt("What are the latest AI regulations?")
                .userId("user-123")
                .sessionId("session-456")
                .metadata(Map.of("source", "web-app"))
                .build()
        );

        // Step 2: Check if request is allowed
        if (approval.isAllowed()) {
            // Make your LLM call here
            String llmResponse = callYourLLM(approval.getModifiedPrompt());

            // Step 3: Audit the response
            ClientResponse response = client.auditLLMCall(
                AuditRequest.builder()
                    .requestId(approval.getRequestId())
                    .llmResponse(llmResponse)
                    .model("gpt-4")
                    .tokenUsage(TokenUsage.builder()
                        .promptTokens(150)
                        .completionTokens(200)
                        .totalTokens(350)
                        .build())
                    .latencyMs(450)
                    .build()
            );

            System.out.println("Response: " + response.getLlmResponse());
        } else {
            System.out.println("Request blocked: " + approval.getBlockedReason());
        }
    }
}
```

### Proxy Mode

Proxy mode is simpler but provides less control - AxonFlow handles the LLM call:

```java
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.types.*;

public class ProxyExample {
    public static void main(String[] args) {
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
            .endpoint("https://agent.getaxonflow.com")
            .clientId("your-client-id")
            .clientSecret("your-client-secret")
            .build());

        ClientResponse response = client.proxyLLMCall(
            ClientRequest.builder()
                .userPrompt("Summarize the key points of GDPR compliance")
                .userId("user-123")
                .model("gpt-4")
                .build()
        );

        if (response.isAllowed()) {
            System.out.println(response.getLlmResponse());
        } else {
            System.out.println("Blocked: " + response.getBlockedPolicyName());
        }
    }
}
```

## Configuration

### Builder Pattern

```java
AxonFlowConfig config = AxonFlowConfig.builder()
    .endpoint("https://agent.getaxonflow.com")  // Required
    .clientId("your-client-id")
            .clientSecret("your-client-secret")             // Required for cloud
    .timeout(Duration.ofSeconds(30))            // Default: 60s
    .debug(true)                                // Enable request logging
    .insecureSkipVerify(false)                  // SSL verification (default: false)
    .build();

AxonFlow client = AxonFlow.create(config);
```

### Environment Variables

The SDK supports configuration via environment variables:

| Variable | Description |
|----------|-------------|
| `AXONFLOW_AGENT_URL` | AxonFlow agent URL |
| `AXONFLOW_CLIENT_ID` | OAuth2 client ID for authentication |
| `AXONFLOW_CLIENT_SECRET` | OAuth2 client secret for authentication |
| `AXONFLOW_DEBUG` | Enable debug logging (`true`/`false`) |

## API Reference

### Core Methods

| Method | Description |
|--------|-------------|
| `getPolicyApprovedContext(request)` | Pre-check request against policies (Gateway Mode step 1) |
| `auditLLMCall(request)` | Audit LLM response (Gateway Mode step 2) |
| `proxyLLMCall(request)` | Execute query through proxy (Proxy Mode) |
| `healthCheck()` | Check agent health status |

### Multi-Agent Planning (MAP)

```java
// Generate a plan
PlanRequest planRequest = PlanRequest.builder()
    .goal("Research and summarize AI regulations")
    .domain("legal")
    .userId("user-123")
    .maxSteps(5)
    .build();

PlanResponse plan = client.generatePlan(planRequest);

// Execute a plan step
StepExecutionRequest stepRequest = StepExecutionRequest.builder()
    .planId(plan.getPlanId())
    .stepIndex(0)
    .build();

StepExecutionResponse result = client.executeStep(stepRequest);

// Get plan status
PlanStatusResponse status = client.getPlanStatus(plan.getPlanId());
```

### MCP Connectors

```java
// Query an MCP connector
MCPQueryRequest query = MCPQueryRequest.builder()
    .connectorName("amadeus-flights")
    .operation("search")
    .parameters(Map.of(
        "origin", "JFK",
        "destination", "LAX",
        "date", "2024-03-15"
    ))
    .build();

MCPQueryResponse response = client.queryConnector(query);
```

### MCP Policy Features (v3.2.0)

**Exfiltration Detection** - Prevent large-scale data extraction:

```java
// Query with exfiltration limits (default: 10K rows, 10MB)
MCPQueryResponse response = client.queryConnector(query);

// Check exfiltration info
PolicyInfo.ExfiltrationCheck exCheck = response.getPolicyInfo().getExfiltrationCheck();
if (exCheck.isExceeded()) {
    System.out.println("Limit exceeded: " + exCheck.getLimitType());
    // LimitType: "rows" or "bytes"
}

// Configure: MCP_MAX_ROWS_PER_QUERY=1000, MCP_MAX_BYTES_PER_QUERY=5242880
```

**Dynamic Policy Evaluation** - Orchestrator-based rate limiting, budget controls:

```java
// Response includes dynamic policy info when enabled
PolicyInfo.DynamicPolicyInfo dynamicInfo = response.getPolicyInfo().getDynamicPolicyInfo();
if (dynamicInfo.isOrchestratorReachable()) {
    System.out.println("Policies evaluated: " + dynamicInfo.getPoliciesEvaluated());
    for (PolicyMatch match : dynamicInfo.getMatchedPolicies()) {
        System.out.println("  " + match.getPolicyName() + ": " + match.getAction());
    }
}

// Enable: MCP_DYNAMIC_POLICIES_ENABLED=true
```

### Policy Management

```java
// List policies
List<Policy> policies = client.listPolicies();

// Get specific policy
Policy policy = client.getPolicy("sql-injection-prevention");
```

## Error Handling

The SDK provides typed exceptions for different error scenarios:

```java
try {
    PolicyApproval approval = client.getPolicyApprovedContext(request);
} catch (AxonFlowAuthenticationException e) {
    // Invalid or missing credentials
    System.err.println("Authentication failed: " + e.getMessage());
} catch (AxonFlowRateLimitException e) {
    // Rate limit exceeded
    System.err.println("Rate limited. Retry after: " + e.getRetryAfterSeconds() + "s");
} catch (AxonFlowValidationException e) {
    // Invalid request parameters
    System.err.println("Validation error: " + e.getMessage());
} catch (AxonFlowNetworkException e) {
    // Network/connectivity issues
    System.err.println("Network error: " + e.getMessage());
} catch (AxonFlowException e) {
    // Other SDK errors
    System.err.println("Error: " + e.getMessage());
}
```

## Retry Configuration

The SDK includes automatic retry with exponential backoff:

```java
AxonFlowConfig config = AxonFlowConfig.builder()
    .endpoint("https://agent.getaxonflow.com")
    .clientId("your-client-id")
            .clientSecret("your-client-secret")
    .retryConfig(RetryConfig.builder()
        .maxAttempts(3)
        .initialDelayMs(100)
        .maxDelayMs(5000)
        .multiplier(2.0)
        .retryableStatusCodes(Set.of(429, 500, 502, 503, 504))
        .build())
    .build();
```

## Response Caching

Enable caching for repeated policy checks:

```java
AxonFlowConfig config = AxonFlowConfig.builder()
    .endpoint("https://agent.getaxonflow.com")
    .clientId("your-client-id")
            .clientSecret("your-client-secret")
    .cacheEnabled(true)
    .cacheTtl(Duration.ofMinutes(5))
    .cacheMaxSize(1000)
    .build();
```

## LLM Interceptors

The SDK provides interceptors for wrapping OpenAI and Anthropic API calls with automatic governance, enabling transparent policy enforcement without changing your application code.

### OpenAI Interceptor

```java
import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;
import com.getaxonflow.sdk.interceptors.*;

// Initialize AxonFlow client
AxonFlow axonflow = AxonFlow.create(AxonFlowConfig.builder()
    .endpoint("https://agent.getaxonflow.com")
    .clientId("your-client-id")
            .clientSecret("your-client-secret")
    .build());

// Create interceptor
OpenAIInterceptor interceptor = OpenAIInterceptor.builder()
    .axonflow(axonflow)
    .userToken("user-123")
    .asyncAudit(true)  // Fire-and-forget audit logging
    .build();

// Wrap your OpenAI call
ChatCompletionResponse response = interceptor.wrap(request -> {
    // Your actual OpenAI SDK call here
    return yourOpenAIClient.createChatCompletion(request);
}).apply(ChatCompletionRequest.builder()
    .model("gpt-4")
    .addUserMessage("Hello, world!")
    .temperature(0.7)
    .maxTokens(1024)
    .build());

// Or use the static wrapper for one-off calls
ChatCompletionResponse response = OpenAIInterceptor.wrapChatCompletion(
    axonflow,
    "user-123",
    request -> yourOpenAIClient.createChatCompletion(request)
).apply(request);
```

### Anthropic Interceptor

```java
import com.getaxonflow.sdk.interceptors.AnthropicInterceptor;
import com.getaxonflow.sdk.interceptors.AnthropicInterceptor.*;

// Create interceptor
AnthropicInterceptor interceptor = AnthropicInterceptor.builder()
    .axonflow(axonflow)
    .userToken("user-123")
    .build();

// Wrap your Anthropic call
AnthropicResponse response = interceptor.wrap(request -> {
    // Your actual Anthropic SDK call here
    return yourAnthropicClient.createMessage(request);
}).apply(AnthropicRequest.builder()
    .model("claude-3-sonnet-20240229")
    .maxTokens(1024)
    .system("You are a helpful assistant.")
    .addUserMessage("Hello, Claude!")
    .temperature(0.7)
    .build());
```

### Async Support

Both interceptors support async operations with `CompletableFuture`:

```java
// Async OpenAI call
CompletableFuture<ChatCompletionResponse> future = interceptor.wrapAsync(
    request -> yourOpenAIClient.createChatCompletionAsync(request)
).apply(request);

future.thenAccept(response -> {
    System.out.println("Response: " + response.getContent());
});
```

### Policy Violations

When a request is blocked by policy, the interceptor throws a `PolicyViolationException`:

```java
try {
    ChatCompletionResponse response = interceptor.wrap(openaiCall).apply(request);
} catch (PolicyViolationException e) {
    System.err.println("Blocked by policy: " + e.getPolicyName());
    System.err.println("Reason: " + e.getBlockReason());
}
```

## Thread Safety

The `AxonFlow` client is thread-safe and designed for reuse. Create a single instance and share it across your application:

```java
// Create once at application startup
AxonFlow client = AxonFlow.create(config);

// Reuse across threads
executorService.submit(() -> client.proxyLLMCall(request1));
executorService.submit(() -> client.proxyLLMCall(request2));
```

## Logging

The SDK uses SLF4J for logging. Add your preferred logging implementation:

```xml
<!-- Logback -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.14</version>
</dependency>
```

Enable debug logging for request/response details:

```java
AxonFlowConfig config = AxonFlowConfig.builder()
    .endpoint("https://agent.getaxonflow.com")
    .debug(true)
    .build();
```

## Spring Boot Integration

See our [Spring Boot Integration Guide](https://docs.getaxonflow.com/sdks/java/spring-boot) for:

- Auto-configuration
- Spring Security integration
- Request interceptors
- Health indicators

## Examples

Complete working examples for all features are available in the [examples folder](https://github.com/getaxonflow/axonflow/tree/main/examples).

### Community Features

```java
// PII Detection - Automatically detect sensitive data
PolicyApproval result = client.getPolicyApprovedContext(
    ClientRequest.builder()
        .userPrompt("My SSN is 123-45-6789")
        .userId("user-123")
        .build()
);
// result.isAllowed() = true, result.requiresRedaction() = true (SSN detected)

// SQL Injection Detection - Block malicious queries
PolicyApproval result = client.getPolicyApprovedContext(
    ClientRequest.builder()
        .userPrompt("SELECT * FROM users; DROP TABLE users;")
        .userId("user-123")
        .build()
);
// result.isAllowed() = false, result.getBlockedReason() = "SQL injection detected"

// Static Policies - List and manage built-in policies
List<Policy> policies = client.listPolicies();
// Returns: [Policy{name="pii-detection", enabled=true}, ...]

// Dynamic Policies - Create runtime policies
client.createDynamicPolicy(DynamicPolicyRequest.builder()
    .name("block-competitor-queries")
    .conditions(Map.of("contains", List.of("competitor", "pricing")))
    .action("block")
    .build());

// MCP Connectors - Query external data sources
MCPQueryResponse resp = client.queryConnector(MCPQueryRequest.builder()
    .connectorName("postgres-db")
    .operation("query")
    .parameters(Map.of("sql", "SELECT name FROM customers"))
    .build());

// Multi-Agent Planning - Orchestrate complex workflows
PlanResponse plan = client.generatePlan(PlanRequest.builder()
    .goal("Research AI governance regulations")
    .domain("legal")
    .build());
StepExecutionResponse result = client.executePlan(plan.getPlanId());

// Audit Logging - Track all LLM interactions
client.auditLLMCall(AuditRequest.builder()
    .requestId(approval.getRequestId())
    .llmResponse(llmResponse)
    .model("gpt-4")
    .tokenUsage(TokenUsage.builder()
        .promptTokens(100)
        .completionTokens(200)
        .totalTokens(300)
        .build())
    .latencyMs(450)
    .build());
```

### Enterprise Features

These features require an AxonFlow Enterprise license:

```java
// Code Governance - Automated PR reviews with AI
PRReviewResponse prResult = client.reviewPullRequest(PRReviewRequest.builder()
    .repoOwner("your-org")
    .repoName("your-repo")
    .prNumber(123)
    .checkTypes(List.of("security", "style", "performance"))
    .build());

// Cost Controls - Budget management for LLM usage
Budget budget = client.getBudget("team-engineering");
// Returns: Budget{limit=1000.00, used=234.56, remaining=765.44}

// MCP Policy Enforcement - Automatic PII redaction in connector responses
MCPQueryResponse resp = client.queryConnector(query);
// resp.getPolicyInfo().isRedacted() = true
// resp.getPolicyInfo().getRedactedFields() = ["ssn", "credit_card"]
```

For enterprise features, contact [sales@getaxonflow.com](mailto:sales@getaxonflow.com).

## Telemetry

This SDK sends anonymous usage telemetry (SDK version, OS, enabled features) to help improve AxonFlow.
No prompts, payloads, or PII are ever collected. Opt out: `AXONFLOW_TELEMETRY=off` or `DO_NOT_TRACK=1`.
See [Telemetry Documentation](https://getaxonflow.com/docs/telemetry) for full details.

## Contributing

We welcome contributions. Please see our [Contributing Guide](CONTRIBUTING.md) for details.

## License

This SDK is licensed under the [Apache License 2.0](LICENSE).

## Support

- **Documentation**: https://docs.getaxonflow.com
- **Issues**: https://github.com/getaxonflow/axonflow-sdk-java/issues
- **Email**: dev@getaxonflow.com

If you are evaluating AxonFlow in a company setting and cannot open a public issue, you can share feedback or blockers confidentially here:
[Anonymous evaluation feedback form](https://getaxonflow.com/feedback)

No email required. Optional contact if you want a response.

<img referrerpolicy="no-referrer-when-downgrade" src="https://static.scarf.sh/a.png?x-pxid=ebd08248-7b5b-408f-8f54-cc062bd41c72" />
