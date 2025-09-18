package io.opentelemetry.smoketest;

import java.util.function.Consumer;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractSmokeTest {
  public abstract void configureTelemetryRetriever(Consumer<JavaTelemetryRetriever> action);
}
