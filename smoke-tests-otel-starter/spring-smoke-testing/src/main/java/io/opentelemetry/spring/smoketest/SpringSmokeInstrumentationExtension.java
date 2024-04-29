/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.LibraryTestRunner;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for writing library instrumentation tests.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MyLibraryInstrumentationTest {
 *     {@literal @}RegisterExtension
 *     static final SpringSmokeInstrumentationExtension instrTesting = SpringSmokeInstrumentationExtension.create();
 *
 *     {@literal @}Test
 *     void test() {
 *       // test code ...
 *
 *       var spans = instrTesting.spans();
 *       // assertions on collected spans ...
 *     }
 *   }
 * </pre>
 */
public final class SpringSmokeInstrumentationExtension extends InstrumentationExtension {
  private final boolean resetBeforeTest;
  private static SpringSmokeInstrumentationExtension instance;

  private SpringSmokeInstrumentationExtension(boolean resetBeforeTest) {
    super(LibraryTestRunner.instance()); // we don't have the OpenTelemetry instance yet
    this.resetBeforeTest = resetBeforeTest;
    instance = this;
  }

  public static SpringSmokeInstrumentationExtension create() {
    return new SpringSmokeInstrumentationExtension(true);
  }

  public static SpringSmokeInstrumentationExtension createWithoutAutomaticReset() {
    return new SpringSmokeInstrumentationExtension(false);
  }

  public static void init(OpenTelemetry openTelemetry) {
    SpringSmokeTestRunner.openTelemetry = openTelemetry;
    instance.setTestRunner(new SpringSmokeTestRunner(openTelemetry));
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    if (resetBeforeTest) {
      super.beforeEach(extensionContext);
    }
  }
}
