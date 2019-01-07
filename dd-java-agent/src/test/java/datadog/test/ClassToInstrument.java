package datadog.test;

import datadog.trace.api.Trace;

/** Note: this has to stay in 'datadog.test' package to be considered for instrumentation */
public class ClassToInstrument {
  @Trace
  public static void someMethod() {}
}
