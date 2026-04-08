/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for writing smoke tests that use a {@link TelemetryRetriever} to retrieve
 * telemetry data from the fake backend.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MySmokeTest implements TelemetryRetrieverProvider {
 *     {@literal @}RegisterExtension
 *     static final InstrumentationExtension testing = SmokeTestInstrumentationExtension.create();
 *
 *     {@literal @}Test
 *     void test() {
 *       // test code ...
 *
 *       var spans = testing.spans();
 *       // assertions on collected spans ...
 *     }
 *   }
 * </pre>
 */
public class SmokeTestInstrumentationExtension extends InstrumentationExtension {
  private SmokeTestInstrumentationExtension() {
    super(SmokeTestRunner.instance());
  }

  public static SmokeTestInstrumentationExtension create() {
    return new SmokeTestInstrumentationExtension();
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    Object testInstance = extensionContext.getRequiredTestInstance();

    if (!(testInstance instanceof TelemetryRetrieverProvider)) {
      throw new AssertionError(
          "SmokeTestInstrumentationExtension can only be applied to a subclass of "
              + "TelemetryRetrieverProvider");
    }

    SmokeTestRunner smokeTestRunner = (SmokeTestRunner) getTestRunner();
    smokeTestRunner.setTelemetryRetriever(
        ((TelemetryRetrieverProvider) testInstance).getTelemetryRetriever());

    super.beforeEach(extensionContext);
  }
}
