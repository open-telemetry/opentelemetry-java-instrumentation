package io.opentelemetry.smoketest;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

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

    if (!(testInstance instanceof AbstractSmokeTest)) {
      throw new AssertionError(
          "SmokeTestInstrumentationExtension can only be applied to a subclass of "
              + "AbstractSmokeTest");
    }

    SmokeTestRunner smokeTestRunner = (SmokeTestRunner) getTestRunner();
    ((AbstractSmokeTest) testInstance).configureTelemetryRetriever(
        smokeTestRunner::setTelemetryRetriever);

    super.beforeEach(extensionContext);
  }
}

