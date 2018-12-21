package datadog.trace.tracer;

/**
 * An Interceptor allows adding hooks to particular events of a span starting and finishing and also
 * trace being written to backend.
 */
public interface Interceptor {
  /**
   * Called after a span is started.
   *
   * @param span the started span.
   */
  void afterSpanStarted(Span span);

  /**
   * Called before a span is finished.
   *
   * @param span the span to be finished.
   */
  void beforeSpanFinished(Span span);

  /**
   * Invoked when a trace is eligible for writing but hasn't been handed off to its writer yet.
   *
   * @param trace The intercepted trace.
   * @return modified trace. Null if trace is to be dropped.
   */
  Trace beforeTraceWritten(Trace trace);
}
