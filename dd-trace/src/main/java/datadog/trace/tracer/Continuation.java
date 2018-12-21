package datadog.trace.tracer;

/**
 * Continuations are used to prevent a trace from reporting without creating a span.
 *
 * <p>All spans are thread safe.
 *
 * <p>To create a Span, see {@link Trace#createContinuation(Span parentSpan)}
 */
public interface Continuation {

  /** @return parent span used to create this continuation. */
  Span getSpan();

  /** @return trace used to create this continuation. */
  Trace getTrace();

  /** @return true iff continuation has been closed. */
  boolean isClosed();

  /**
   * Close the continuation. Continuation's trace will not block reporting on account of this
   * continuation.
   */
  void close();
}
