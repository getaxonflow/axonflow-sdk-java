# AxonFlow Java SDK

Official Java SDK for [AxonFlow](https://getaxonflow.com) - AI Governance Platform for Enterprise LLM Applications.

[![CI](https://github.com/getaxonflow/axonflow-sdk-java/actions/workflows/ci.yml/badge.svg)](https://github.com/getaxonflow/axonflow-sdk-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.getaxonflow/axonflow-sdk.svg)](https://search.maven.org/artifact/com.getaxonflow/axonflow-sdk)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **Upgrade strongly recommended.** AxonFlow ships substantial monthly security and quality hardening; staying on the latest major is the security-supported release line. [Latest release](https://github.com/getaxonflow/axonflow-sdk-java/releases/latest) · [Security advisories](https://github.com/getaxonflow/axonflow-sdk-java/security/advisories)

> **Taking a sponsored workflow to production?**
>
> Choose the path that fits:
> - **Self-serve:** free 90-day [Evaluation License](https://getaxonflow.com/evaluation-license?utm_source=readme_sdk_java_eval)
> - **Paid production program:** [Design Partner or Confidential Pilot](https://getaxonflow.com/design-partner?utm_source=readme_sdk_java)  -  one scoped workflow over 60 or 75 days, founder-led rollout support, upfront conversion pricing, and a fixed decision date; public track from $2,000 or confidential track from $4,000
>
> The paid program requires a dated forcing event, written controls, an executive sponsor, and a technical owner. Prices are subject to eligibility and a signed agreement.

> **Questions or feedback?**
>
> Comment in [GitHub Discussions](https://github.com/getaxonflow/axonflow/discussions/239) or email [hello@getaxonflow.com](mailto:hello@getaxonflow.com) for private feedback.

## How This SDK Fits with AxonFlow

This SDK is a client library for interacting with a running AxonFlow control plane. It is used from application or agent code to send execution context, policies, and requests at runtime.

A deployed AxonFlow platform (self-hosted or cloud) is required for end-to-end AI governance. SDKs alone are not sufficient—the platform and SDKs are designed to be used together.

### See AxonFlow in Action

Videos covering different angles of the platform:

- **[Product demos: Platform + Fraud & Risk](https://getaxonflow.com/demo/?utm_source=github&utm_medium=readme&utm_campaign=product_demo&utm_content=axonflow-sdk-java)** - runtime enforcement, HITL approvals, audit evidence, cost visibility, and agentic payment controls
- **[Community Quickstart walkthrough (2 min)](https://youtu.be/BSqU1z0xxCo)** - governed calls, PII blocking, Gateway Mode with LangChain/CrewAI, and MAP from YAML
- **[Architecture deep dive (12 min)](https://youtu.be/Q2CZ1qnquhg)** - how the control plane works, policy enforcement flow, and multi-agent planning

## Requirements

- Java 11 or higher
- Maven 3.6+ or Gradle 6.0+

## Installation

### Maven

```xml
<dependency>
    <groupId>com.getaxonflow</groupId>
    <artifactId>axonflow-sdk</artifactId>
    <version>8.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.getaxonflow:axonflow-sdk:8.0.0'
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
| Evidence export (CSV / JSON) | — | 5,000 records · 14d window · 3/day | Unlimited |
| Policy simulation | — | 300 / day | Unlimited |

Concurrent executions applies to MAP and WCP executions per tenant. Pending execution approvals applies to MAP confirm/step mode and WCP approval queues.

> **Note:** Evidence export and policy simulation are licensed AxonFlow platform capabilities available alongside the SDK on your deployed platform — not language-specific SDK helpers. Access them via the platform API or customer portal. The SDK row is included to show what your licensed deployment unlocks at each tier.

[Get a free Evaluation license](https://getaxonflow.com/evaluation-license?utm_source=readme_sdk_java_eval) · [Run a paid production program](https://getaxonflow.com/design-partner?utm_source=readme_sdk_java_eval) · [Full feature matrix](https://docs.getaxonflow.com/docs/features/community-vs-enterprise?utm_source=readme_sdk_java_eval)

## Try Without Installing

Skip local setup entirely — try AxonFlow instantly at [**try.getaxonflow.com**](https://docs.getaxonflow.com/docs/deployment/community-saas):

```bash
# 1. Register (30 seconds)
curl -X POST https://try.getaxonflow.com/api/v1/register \
  -H "Content-Type: application/json" -d '{"label":"my-trial"}'

# 2. Set credentials and auto-connect
export AXONFLOW_TRY=1
export AXONFLOW_CLIENT_ID=cs_your-tenant-id
export AXONFLOW_CLIENT_SECRET=your-secret
```

No Docker, no license, no installation. Rate-limited to 20 req/min. [Learn more](https://docs.getaxonflow.com/docs/deployment/community-saas).

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

## AuthZEN-native authorization (ADR-065)

`POST /api/v1/access/evaluation` is the AuthZEN-shaped authorization surface. It is the surface to write **new** integrations against: at v11 the engine behind it becomes the ADR-065 Policy Decision Point with no wire change, so an integration written against it migrates once rather than twice. Nothing here is deprecated — the existing decision surface stays wire-stable through all of v11. See `docs/AUTHZEN_MIGRATION_DRAFT.md`.

```java
import com.getaxonflow.sdk.authzen.*;

AuthZENDecision decision =
    client.evaluate(
        AuthZENEvaluation.of(
                new AuthZENSubject("gateway", "llm-gateway-01"),
                new AuthZENAction("llm.completion"),
                new AuthZENResource("llm", "llm"))
            .query(Attribute.known(userPrompt))
            .correlation("x-session-id", Attribute.known(sessionId))
            .build());

if (!decision.isAllowed()) {
    throw new IllegalStateException("blocked: " + decision.getState() + " (" + decision.getCategory() + ")");
}
for (AuthZENObligation obligation : decision.getMandatoryObligations()) {
    // An allow with an undischarged mandatory obligation is NOT an allow.
    discharge(obligation);
}
```

`evaluateAll` takes several preconditions of **one** operation and returns **one** decision: the entries combine to the least permissive outcome, so one denied entry denies the operation. An API returning a list would invite a caller to act on the entry it liked.

### Known gotchas

**A resolved attribute has three states, and `Optional` carries two.** Every attribute bag — `subject.properties`, `action.properties`, `resource.properties`, and `context` — holds `Attribute<T>` values, not nullable ones:

| | meaning | wire | outcome |
|---|---|---|---|
| `Attribute.known(v)` | the source answered with `v` | the member, with its value | evaluated |
| `Attribute.absent()` | the source answered: there is no value | the MEMBER is omitted from the bag; the bag itself is still sent, so `properties` arrives as `{}` | evaluated; a fact with no value changes nothing |
| `Attribute.unknown(why)` | the source **could not answer** | never reaches the wire | `AuthZENUnresolvedException`, before the round trip, NOT retryable |

Absent and unknown are not the same event. Dropping an unknown attribute from the request would obtain a decision that weighed every attribute except the one nobody could read — and report it as complete. That is the exact failure the server refuses on its side of the wire ("accepting it would report that it was considered when it was not"); `Attribute` is the same refusal on yours. Read a value with `Attribute.fold`, which does not compile until you have said what all three states mean; `asKnown()` collapses two of them and is for logging.

**A later write never overwrites an unresolved attribute.** `query(...)` and `correlation(...)` decline a write over an `Attribute.unknown`, at both the bag and the leaf, so a caller that recorded "nobody could read the request body" and then wrote a recovered partial query does not end up sending a complete-looking envelope. The declined write is not silent: the surviving unknown refuses the envelope at its own pointer, carrying the reason. `AttributeMap.put` is the ordinary map write and DOES replace - use `record` if you want the rule. Replacing the whole bag with `setContext(...)` replaces its contents, as any assignment does.

**Only one refusal code is worth retrying.** `AuthZENEvaluationException.isRetryable()` is the whole set in one place: a refusal only when its code is `evaluation_unavailable`; a transport failure with a `5xx`, a `429`, or no response at all; never an unreadable profile (retrying cannot make an older SDK able to read a newer one), never an unusable response, and never a `4xx` naming the caller's credentials. Every other refusal code names something about the request, which will not change on a retry.

This surface does **not** go through the client's `RetryConfig`. That executor is wired to the proxy path's request type, and retrying an authorization decision on your behalf is a policy decision this SDK does not make for you - a retried allow is a second evaluation the audit trail did not ask for. Retry is yours, guided by `isRetryable()`.

**A refusal is not a denial.** `decision: false` says the request was evaluated and denied. A refusal says it was never evaluated. They arrive as different things — a returned `AuthZENDecision` versus a thrown `AuthZENRefusedException` — so no caller branch can conflate an auth failure, a malformed envelope or an outage with a policy denial.

**A local refusal names the same MEMBER the server would.** The SDK validates before sending, and a local refusal carries the JSON Pointer the server would have sent for the same bytes - verified against a live server by `runtime-e2e/authzen_evaluation`. The CODE may be narrower on the server side, and that is not a defect in either: this client knows only that a required member is missing and says `incomplete_evaluation`, while the server additionally knows which values it can evaluate and narrows the same condition to `unsupported_subject` with a `supported` list. Branch on `getPointer()` for "which member"; read the code as the server's more specific reading when there is one.

**`isAllowed()` requires the state, not just the boolean.** It is true only when the collapsed boolean *and* the four-valued operational state both say `ALLOW`. A body where they disagree, one carrying no profile payload at all, or one written in a profile this build cannot read never becomes a decision — it becomes an exception. There is no path that returns an allow the SDK could not fully read.

**The WIRE TYPES are generated, never hand-written.** Every file in `com.getaxonflow.sdk.authzen` carrying the `Code generated by` header - the 13 wire types, the 6 enum classes and `AuthZENContract` - is emitted from `testdata/authzen-surface.json`, the platform's canonical contract artifact. Regenerate with `./scripts/gen-authzen-types.sh`; `mvn test` fails if a committed one is not what the artifact generates, and fails again if one is left behind after the contract stops describing it.

The rest of the package is hand-written and is meant to be edited: `Attribute`, `AttributeValue`, `AttributeMap`, `AuthZENDecision`, `AuthZENEvaluation`, `AuthZENRefusals`, and the six exception types (`AuthZENEvaluationException` and its five subclasses).

## Reading decisions: who is asking decides what comes back

`explainDecision` and `listDecisions` — and the audit reads — are scoped to the
**per-user identity** you present, not to the tenant credential. Since platform
#2922:

| What you present | What an enterprise stack returns |
|---|---|
| a tenant-wide role (`admin`, `owner`, `policy_admin`) | the whole tenant |
| any other identity (`developer`, `viewer`) | only the rows attributed to it |
| **no identity** | **nothing at all** — every list is empty, every explain is not-found |

`clientId`/`clientSecret` authenticate the **organization**. They do not say who
is asking, so on their own they land in the third row. Community and
Community-SaaS deployments are single-operator and read tenant-wide with no
identity needed.

```java
AxonFlow client = AxonFlow.create(
    AxonFlowConfig.builder()
        .endpoint("http://localhost:8080")
        .clientId(System.getenv("AXONFLOW_CLIENT_ID"))
        .clientSecret(System.getenv("AXONFLOW_CLIENT_SECRET"))
        .userToken(System.getenv("AXONFLOW_USER_TOKEN"))   // the per-user identity
        .build());

// Per call:
DecisionExplanation exp = client.explainDecision(decisionId, usersToken);

// Or, for a process acting on behalf of several people, derive a client bound
// to one person. Unlike the per-call overload, which only the read methods
// have, this reaches EVERY method.
List<DecisionSummary> rows = client.asUser(alicesToken).listDecisions(null);
```

The token is a per-user JWT — minted by the customer portal's user-token API, or
for local testing by `scripts/generate-jwt.sh --kind user`. It is **not** the
tenant JWT and not `clientSecret`. It is sent as `X-User-Token`, is never
logged, never reaches telemetry, and is never sent to any origin but the
configured endpoint.

### Telling the outcomes apart

"Not found", "not yours" and "no identity resolved" used to arrive as the same
`404`, and an unscoped list arrived as an ordinary empty page. Both now carry a
cause:

```java
try {
    List<DecisionSummary> decisions = client.listDecisions(null);
} catch (ReadScopeException e) {
    if (e.isIdentityMissing()) {
        // The platform resolved no identity, so it returned zero rows by
        // construction. The empty answer was never evidence about your data.
    }
}
```

`explainDecision` is where the other scope shows up. Under `own-rows` the
platform answers "not attributed to you" and "not there at all" with the **same
404**, deliberately, so that a miss cannot be used to probe for another user's
rows — the exception reports the scope the read ran under, never a claim about
what exists.

> **A valid token can still resolve to nobody.** The platform reserves the whole
> of `@axonflow.local` and `@axonflow.internal` for *shared* identities and
> censuses them to nothing before scoping. A correctly-signed developer token
> minted at `demo-user@axonflow.local` — which is `generate-jwt.sh`'s own
> default — reads zero rows and reports `isIdentityMissing()`, exactly like no
> token at all. Mint per-user identities at a real domain.

> **Setting `userToken` affects more than reads.** The header rides every
> request and the agent validates it on every route it proxies — not just the
> scoped reads. A stale or rotated token therefore turns `listConnectors`,
> `installConnector` and policy CRUD into `401`s rather than merely unscoping a
> read. That is the correct, fail-closed direction, but it puts this value in
> the same rotation story as `clientSecret`.

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

The SDK includes automatic retry with exponential backoff. The retry policy
itself is not configurable per HTTP status — retries fire on connect/timeout
errors, `5xx` server errors, and `429 Too Many Requests`. Authentication
failures (`401`/`403`), policy violations, and other `4xx` client errors are
always terminal and never retried (see `RetryExecutor.isRetryable`). This
contract is locked in as a regression test for
[getaxonflow/axonflow-enterprise#2275](https://github.com/getaxonflow/axonflow-enterprise/issues/2275)
— a retry storm on `401` against a misconfigured agent.

The `RetryConfig.Builder` exposes the following knobs:

- `enabled(boolean)` — turn retries off entirely (defaults to `true`).
- `maxAttempts(int)` — total attempts including the first, `1`–`10` (default `3`).
- `initialDelay(Duration)` — base delay before the second attempt (default `1s`).
- `maxDelay(Duration)` — cap on the exponential backoff (default `30s`).
- `multiplier(double)` — backoff multiplier, ≥ `1.0` (default `2.0`).

```java
import java.time.Duration;

AxonFlowConfig config = AxonFlowConfig.builder()
    .endpoint("https://agent.getaxonflow.com")
    .clientId("your-client-id")
    .clientSecret("your-client-secret")
    .retryConfig(RetryConfig.builder()
        .maxAttempts(3)
        .initialDelay(Duration.ofMillis(100))
        .maxDelay(Duration.ofSeconds(5))
        .multiplier(2.0)
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

See our [Spring Boot Integration Guide](https://docs.getaxonflow.com/docs/sdk/java-getting-started) for:

- Auto-configuration
- Spring Security integration
- Request interceptors
- Health indicators

## Examples

Complete working examples for all features are available in the [examples folder](https://github.com/getaxonflow/axonflow/tree/main/examples).

Runnable in this repository, against a live agent:

```bash
mvn -q -DskipTests install                     # put the SDK on the local classpath
AXONFLOW_AGENT_URL=http://localhost:8080 \
  mvn -q -f examples/authzen/pom.xml compile exec:java  # AuthZEN: 9 steps, 4 of them refusals
```

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
No prompts, payloads, or PII are ever collected. Opt out: `AXONFLOW_TELEMETRY=off`.

`AXONFLOW_TELEMETRY=off` is the **sole opt-out lever** as of v8.0. The
v7.x `telemetry(Boolean)` config-builder method has been removed; the
previous silent suppression of sandbox-mode pings has also been removed
(sandbox-mode pings now fire and are tagged `stream="sandbox"` so
they're distinguishable from production heartbeat).

### Scope of `AXONFLOW_TELEMETRY=off`

`AXONFLOW_TELEMETRY=off` disables the anonymous SDK heartbeat (version, OS, architecture). On **self-hosted** and **in-VPC** deployments, that heartbeat is the only data the SDK sends to AxonFlow, so setting `=off` means we receive nothing. On **Community SaaS** (`try.getaxonflow.com`) the hosted service also processes operational data — registrations, audit logs, policy enforcement records, workflow state, plan data, and request-header metadata aggregated for usage analytics — as part of running the platform; that operational data flow is governed by the [Privacy Policy](https://getaxonflow.com/privacy/), not by `AXONFLOW_TELEMETRY`.

### Platform licence tier (`license_tier`)

Each heartbeat also reports the licence tier of the AxonFlow platform the SDK is configured to talk to — for example `community`, `evaluation`, `Enterprise`, or the transient `starting` while a platform is still booting. This lets us tell an enterprise-licensed deployment apart from an unlicensed community one in aggregate adoption figures, which the heartbeat previously could not distinguish.

What is and is not collected:

- **Collected:** the coarse tier string only.
- **Not collected:** your licence key, its expiry, its seat or node count, your organisation's name, and any other licence detail. The SDK never reads your licence key.

The value is read from the `tier` field of the platform's own `/health` response — the same response the heartbeat already fetches to report the platform version, and an endpoint that returns this field to any caller without authentication. **No additional network request is made, and the SDK gains no access to anything `/health` does not already return.**

**This is an adoption-analytics signal, not an entitlement one.** The value is whatever the platform at your configured endpoint reported about itself, relayed unchanged: the SDK derives nothing and verifies nothing, and the receiver cannot verify the relay either. Whoever operates that endpoint controls the value completely, so it must never gate entitlement, unlock a feature, or enter any authorization or billing decision. It is used only for aggregate adoption figures.

The field is **omitted entirely** whenever the tier could not be determined — the platform is unreachable, returns an error, returns an unparseable body, or returns no `tier` field. It is never defaulted to a guessed value, so an absent field means "not known", never "community".

`AXONFLOW_TELEMETRY=off` suppresses this field along with the rest of the heartbeat.

`DO_NOT_TRACK` is **not** honored as an opt-out for AxonFlow telemetry. It is commonly inherited from host tools and developer environments, which makes it an unreliable expression of user intent.

See [Telemetry Documentation](https://docs.getaxonflow.com/docs/telemetry) for full details.

## Contributing

We welcome contributions. Please see our [Contributing Guide](CONTRIBUTING.md) for details.

## License

This SDK is licensed under the [Apache License 2.0](LICENSE).

## Support

- **Documentation**: https://docs.getaxonflow.com
- **Issues**: https://github.com/getaxonflow/axonflow-sdk-java/issues
- **Email**: hello@getaxonflow.com

If you are evaluating AxonFlow in a company setting and cannot open a public issue, you can share feedback or blockers confidentially here:
[Anonymous evaluation feedback form](https://getaxonflow.com/feedback)

No email required. Optional contact if you want a response.
