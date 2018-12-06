package datadog.trace.tracer;

/**
 * A tree of {@link Span}s with a single root node plus logic to determine when to report said tree
 * to the backend.
 *
 * <p>A trace will be written when all of its spans are finished and all trace continuations are
 * closed.
 *
 * <p>To create a Trace, see {@link Tracer#buildTrace()}
 */
public interface Trace {
  /** Get the tracer which created this trace. */
  Tracer getTracer();

  /** Get the root span for this trace. This will never be null. */
  Span getRootSpan();

  /**
   * Create a new span in this trace as a child of the given parentSpan.
   *
   * @param parentSpan the parent to use. Must be a span in this trace.
   * @return the new span. It is the caller's responsibility to ensure {@link Span#finish()} is
   *     eventually invoked on this span.
   */
  Span createSpan(Span parentSpan);

  /**
   * Create a new continuation for this trace
   *
   * @param parentSpan the parent to use. Must be a span in this trace.
   * @return the new continuation. It is the caller's responsibility to ensure {@link
   *     Continuation#close()} is eventually invoked on this continuation.
   */
  Continuation createContinuation(Span parentSpan);

  interface LifecycleInterceptor {
    /**
     * Invoked when a trace is eligible for writing but hasn't been handed off to its writer yet.
     *
     * @param trace The intercepted trace.
     */
    void beforeTraceWritten(Trace trace);
  }

  /** A way to prevent a trace from reporting without creating a span. */
  interface Continuation {
    /**
     * Close the continuation. Continuation's trace will not block reporting on account of this
     * continuation.
     *
     * <p>Has no effect after the first invocation.
     */
    void close();
  }
}
