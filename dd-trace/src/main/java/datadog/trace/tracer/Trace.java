package datadog.trace.tracer;

import java.util.List;

/**
 * A tree of {@link Span}s with a single root node plus logic to determine when to report said tree
 * to the backend.
 *
 * <p>A trace will be written when all of its spans are finished and all trace continuations are
 * closed.
 *
 * <p>To create a Trace, see {@link Tracer#buildTrace(SpanContext parentContext)}
 */
public interface Trace {
  /** @return the tracer which created this trace. */
  Tracer getTracer();

  /** @return the root span for this trace. This will never be null. */
  Span getRootSpan();

  /**
   * @return list of spans for this trace. Note: if trace is not finished this will report error.
   */
  List<Span> getSpans();

  /** @return true iff trace is valid (invalid traces should not be reported). */
  boolean isValid();

  /** @return current timestamp using this trace's clock */
  Timestamp createCurrentTimestamp();

  /**
   * Create a new span in this trace as a child of the given parent context.
   *
   * @param parentContext the parent to use. Must be a span in this trace.
   * @return the new span. It is the caller's responsibility to ensure {@link Span#finish()} is
   *     eventually invoked on this span.
   */
  Span createSpan(final SpanContext parentContext);

  /**
   * Create a new span in this trace as a child of the given parent context.
   *
   * @param parentContext the parent to use. Must be a span in this trace.
   * @param startTimestamp timestamp to use as start timestamp for a new span.
   * @return the new span. It is the caller's responsibility to ensure {@link Span#finish()} is
   *     eventually invoked on this span.
   */
  Span createSpan(final SpanContext parentContext, final Timestamp startTimestamp);

  /**
   * Create a new continuation for this trace
   *
   * @param parentSpan the parent to use. Must be a span in this trace.
   * @return the new continuation. It is the caller's responsibility to ensure {@link
   *     Continuation#close()} is eventually invoked on this continuation.
   */
  Continuation createContinuation(Span parentSpan);
}
