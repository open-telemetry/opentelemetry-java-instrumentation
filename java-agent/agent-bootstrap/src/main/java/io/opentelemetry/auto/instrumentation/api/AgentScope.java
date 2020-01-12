package io.opentelemetry.auto.instrumentation.api;

import java.io.Closeable;

public interface AgentScope extends Closeable {
  AgentSpan span();

  @Override
  void close();
}
