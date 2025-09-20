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
 *   class MySmokeTest {
 *     {@literal @}RegisterExtension
 *     static final SmokeTestTarget target = SmokeTestTarget.springBoot("version").build();
 *     static final InstrumentationExtension testing = target.testing();
 *
 *     {@literal @}ParameterizedTest
 *     {@literal @}ValueSource(ints = {8, 11, 17})
 *     void test(int jdk) throws Exception {
 *     SmokeTestOutput output = target.start(jdk);
 *       // test code ...
 *
 *       var spans = testing.spans();
 *       // assertions on collected spans ...
 *     }
 *   }
 * </pre>
 */
public class SmokeTestInstrumentationExtension extends InstrumentationExtension {

  private final TelemetryRetrieverProvider telemetryRetrieverProvider;

  SmokeTestInstrumentationExtension(TelemetryRetrieverProvider telemetryRetrieverProvider) {
    super(new SmokeTestRunner());
    this.telemetryRetrieverProvider = telemetryRetrieverProvider;
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    SmokeTestRunner smokeTestRunner = (SmokeTestRunner) getTestRunner();
    smokeTestRunner.setTelemetryRetriever(telemetryRetrieverProvider.getTelemetryRetriever());
    super.beforeEach(extensionContext);
  }
}
