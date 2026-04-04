/**
 * MAS FEAT Compliance module for Singapore regulatory compliance.
 *
 * <p>This package provides types and client for the MAS FEAT (Monetary Authority of Singapore -
 * Fairness, Ethics, Accountability, Transparency) compliance framework.
 *
 * <p><b>Enterprise Feature:</b> Requires AxonFlow Enterprise license.
 *
 * <h2>Features</h2>
 *
 * <ul>
 *   <li>AI System Registry with 3-dimensional materiality classification
 *   <li>FEAT Assessment lifecycle management
 *   <li>Kill Switch for emergency model shutdown
 *   <li>7-year audit retention
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * AxonFlowClient client = AxonFlowClient.builder()
 *     .apiKey("your-api-key")
 *     .endpoint("http://localhost:8081")
 *     .build();
 *
 * // Register an AI system
 * AISystemRegistry system = client.masfeat().registerSystem(
 *     RegisterSystemRequest.builder()
 *         .systemId("credit-scoring-ai-v1")
 *         .systemName("Credit Scoring AI")
 *         .useCase(AISystemUseCase.CREDIT_SCORING)
 *         .ownerTeam("Risk Management")
 *         .customerImpact(4)
 *         .modelComplexity(3)
 *         .humanReliance(5)
 *         .build()
 * );
 *
 * // Configure kill switch
 * KillSwitch killSwitch = client.masfeat().configureKillSwitch(
 *     "credit-scoring-ai-v1",
 *     ConfigureKillSwitchRequest.builder()
 *         .accuracyThreshold(0.85)
 *         .biasThreshold(0.15)
 *         .autoTriggerEnabled(true)
 *         .build()
 * );
 * }</pre>
 *
 * @see com.getaxonflow.sdk.masfeat.MASFEATTypes
 */
package com.getaxonflow.sdk.masfeat;
