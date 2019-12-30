package datadog.trace.instrumentation.api;

import java.io.Closeable;

public interface AgentScope extends Closeable {
  AgentSpan span();

  AgentScope.Continuation capture();

  @Override
  void close();

  interface Continuation {
    AgentScope activate();
  }
}
