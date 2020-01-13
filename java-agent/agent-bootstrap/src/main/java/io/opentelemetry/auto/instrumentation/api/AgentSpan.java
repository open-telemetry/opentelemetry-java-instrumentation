package io.opentelemetry.auto.instrumentation.api;

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

  interface Context {}
}
