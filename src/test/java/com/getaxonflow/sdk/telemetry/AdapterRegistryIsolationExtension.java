/*
 * Copyright 2026 AxonFlow
 * Licensed under the Business Source License 1.1.
 */
package com.getaxonflow.sdk.telemetry;

import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Resets the process-global adapter registry around every test in the suite.
 *
 * <p>The registry is static BY DESIGN — an adapter constructed anywhere in the process is genuinely
 * in use, and that is exactly what the telemetry heartbeat should report. In a test run that same
 * property is cross-test pollution: {@code LangGraphAdapterTest} constructs adapters repeatedly,
 * each of which registers {@code langgraph}, and {@code TelemetryReporterTest.testPayloadFormat}
 * then fails on {@code features.size() == 0} depending on class execution order. That is how this
 * was found — a pre-existing test in an unrelated class, not one of the new ones.
 *
 * <p>AUTO-DETECTED AND GLOBAL, registered through {@code META-INF/services} rather than an
 * {@code @ExtendWith} on the classes that happen to care, for the same reason the Python SDK puts
 * its equivalent in an autouse conftest fixture: the isolation has to hold for tests that have
 * never heard of the registry, not only for the ones that use it deliberately. A test asserting an
 * empty {@code features} array should not need to know that another class registers adapters.
 */
public class AdapterRegistryIsolationExtension implements BeforeEachCallback, AfterEachCallback {

  private List<String> saved;

  @Override
  public void beforeEach(ExtensionContext context) {
    saved = TelemetryReporter.resetAdapterRegistryForTest();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (saved != null) {
      TelemetryReporter.restoreAdapterRegistryForTest(saved);
      saved = null;
    }
  }
}
