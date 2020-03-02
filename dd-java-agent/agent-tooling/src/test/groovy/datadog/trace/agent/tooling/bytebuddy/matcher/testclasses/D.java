package datadog.trace.agent.tooling.bytebuddy.matcher.testclasses;

import datadog.trace.api.Trace;

public interface D extends A, B, C {
  @Trace
  void d();
}
