# AxonFlow Java SDK

Official Java SDK for [AxonFlow](https://getaxonflow.com) - AI Governance Platform for Enterprise LLM Applications.

[![CI](https://github.com/getaxonflow/axonflow-sdk-java/actions/workflows/ci.yml/badge.svg)](https://github.com/getaxonflow/axonflow-sdk-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.getaxonflow/axonflow-sdk.svg)](https://search.maven.org/artifact/com.getaxonflow/axonflow-sdk)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Requirements

- Java 11 or higher
- Maven 3.6+ or Gradle 6.0+

## Installation

### Maven

```xml
<dependency>
    <groupId>com.getaxonflow</groupId>
    <artifactId>axonflow-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.getaxonflow:axonflow-sdk:1.0.0'
```

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
            .agentUrl("https://agent.getaxonflow.com")
            .licenseKey("your-license-key")
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
            .agentUrl("https://agent.getaxonflow.com")
            .licenseKey("your-license-key")
            .build());

        ClientResponse response = client.executeQuery(
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
    .agentUrl("https://agent.getaxonflow.com")  // Required
    .licenseKey("your-license-key")             // Required for cloud
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
| `AXONFLOW_LICENSE_KEY` | License key for authentication |
| `AXONFLOW_DEBUG` | Enable debug logging (`true`/`false`) |

## API Reference

### Core Methods

| Method | Description |
|--------|-------------|
| `getPolicyApprovedContext(request)` | Pre-check request against policies (Gateway Mode step 1) |
| `auditLLMCall(request)` | Audit LLM response (Gateway Mode step 2) |
| `executeQuery(request)` | Execute query through proxy (Proxy Mode) |
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
    // Invalid or missing license key
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
    .agentUrl("https://agent.getaxonflow.com")
    .licenseKey("your-license-key")
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
    .agentUrl("https://agent.getaxonflow.com")
    .licenseKey("your-license-key")
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
    .agentUrl("https://agent.getaxonflow.com")
    .licenseKey("your-license-key")
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
executorService.submit(() -> client.executeQuery(request1));
executorService.submit(() -> client.executeQuery(request2));
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
    .agentUrl("https://agent.getaxonflow.com")
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

- [Hello World](examples/hello-world) - Basic usage
- [Gateway Mode](examples/gateway-mode) - Pre-check and audit flow
- [Proxy Mode](examples/proxy-mode) - Simple proxy integration
- [Multi-Agent Planning](examples/map) - Orchestrated workflows
- [Error Handling](examples/error-handling) - Exception handling patterns

## Contributing

We welcome contributions. Please see our [Contributing Guide](CONTRIBUTING.md) for details.

## License

This SDK is licensed under the [Apache License 2.0](LICENSE).

## Support

- [Documentation](https://docs.getaxonflow.com)
- [GitHub Issues](https://github.com/getaxonflow/axonflow-sdk-java/issues)
- [Community Discord](https://discord.gg/axonflow)
