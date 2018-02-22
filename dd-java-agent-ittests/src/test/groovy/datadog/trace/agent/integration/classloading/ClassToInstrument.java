package datadog.trace.agent.integration.classloading;

import datadog.trace.api.Trace;

class ClassToInstrument {
  @Trace
  public static void someMethod() {}
}
