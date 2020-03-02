package datadog.trace.agent.tooling.bytebuddy.matcher.testclasses;

import datadog.trace.api.Trace;

public abstract class F implements E {
  @Trace
  public abstract void f();
}
