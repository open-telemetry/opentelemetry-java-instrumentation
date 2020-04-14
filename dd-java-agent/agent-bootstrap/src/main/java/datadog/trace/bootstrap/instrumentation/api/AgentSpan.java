package datadog.trace.bootstrap.instrumentation.api;

public interface AgentSpan {
  AgentSpan setTag(String key, boolean value);

  AgentSpan setTag(String key, int value);

  AgentSpan setTag(String key, long value);

  AgentSpan setTag(String key, double value);

  AgentSpan setTag(String key, String value);

  AgentSpan setError(boolean error);

  AgentSpan setErrorMessage(String errorMessage);

  AgentSpan addThrowable(Throwable throwable);

  AgentSpan getLocalRootSpan();

  boolean isSameTrace(AgentSpan otherSpan);

  Context context();

  void finish();

  String getSpanName();

  void setSpanName(String spanName);

  interface Context {}
}
