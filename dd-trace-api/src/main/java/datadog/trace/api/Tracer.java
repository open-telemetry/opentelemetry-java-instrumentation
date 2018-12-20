package datadog.trace.api;

import datadog.trace.api.interceptor.TraceInterceptor;
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
   * Add a new interceptor to the tracer. Interceptors with duplicate priority to existing ones are
   * ignored.
   *
   * @param traceInterceptor
   * @return false if an interceptor with same priority exists.
   */
  boolean addTraceInterceptor(TraceInterceptor traceInterceptor);

  /**
   * Attach a scope listener to the global scope manager
   *
   * @param listener listener to attach
   */
  void addScopeListener(ScopeListener listener);
}
