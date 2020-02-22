package datadog.trace.agent.tooling.bytebuddy.matcher.testclasses;

import datadog.trace.api.Trace;

public interface B extends A {
  @Trace
  void b();
}
