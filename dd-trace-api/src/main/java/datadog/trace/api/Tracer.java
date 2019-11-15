package datadog.trace.api;

import datadog.trace.context.ScopeListener;

/** A class with Datadog tracer features. */
public interface Tracer {

  /** Get the trace id of the active trace. Returns 0 if there is no active trace. */
  String getTraceId();

  /**
   * Get the span id of the active span of the active trace. Returns 0 if there is no active trace.
   */
  String getSpanId();

  /**
   * Attach a scope listener to the global scope manager
   *
   * @param listener listener to attach
   */
  void addScopeListener(ScopeListener listener);
}
