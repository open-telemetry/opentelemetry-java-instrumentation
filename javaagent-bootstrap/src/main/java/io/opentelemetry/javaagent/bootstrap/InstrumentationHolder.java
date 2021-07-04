package io.opentelemetry.javaagent.bootstrap;

import java.lang.instrument.Instrumentation;

/**
 * This class serves as a "everywhere accessible" source of {@link Instrumentation} instance.
 */
public class InstrumentationHolder {
  private static volatile Instrumentation instrumentation;

  public static Instrumentation getInstrumentation() {
    return instrumentation;
  }

  public static void setInstrumentation(Instrumentation instrumentation) {
    InstrumentationHolder.instrumentation = instrumentation;
  }
}
