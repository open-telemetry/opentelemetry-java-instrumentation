package datadog.trace.instrumentation.api;

import java.io.Closeable;

public interface AgentScope extends Closeable {
  AgentSpan span();

  AgentScope setAsyncPropagation(boolean value);

  @Override
  void close();
}
