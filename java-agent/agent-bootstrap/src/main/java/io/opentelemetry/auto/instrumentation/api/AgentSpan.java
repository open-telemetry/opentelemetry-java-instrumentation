package io.opentelemetry.auto.instrumentation.api;

import io.opentelemetry.trace.Span;

@Deprecated
public interface AgentSpan {
  AgentSpan setAttribute(String key, boolean value);

  AgentSpan setAttribute(String key, int value);

  AgentSpan setAttribute(String key, long value);

  AgentSpan setAttribute(String key, double value);

  AgentSpan setAttribute(String key, String value);

  AgentSpan setError(boolean error);

  AgentSpan setErrorMessage(String errorMessage);

  AgentSpan addThrowable(Throwable throwable);

  Context context();

  void finish();

  String getSpanName();

  void setSpanName(String spanName);

  Span getSpan();

  interface Context {}
}
