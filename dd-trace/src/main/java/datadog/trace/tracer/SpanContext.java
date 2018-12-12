package datadog.trace.tracer;

/**
 * All attributes of a {@link Span} which propagate for distributed tracing.
 *
 * <p>All Spans must have a SpanContext, but not all SpanContexts require a span.
 *
 * <p>All SpanContexts are thread safe.
 */
public interface SpanContext {

  /**
   * Get this context's trace id.
   *
   * @return 64 bit unsigned integer in String format.
   */
  String getTraceId();

  /**
   * Get this context's parent span id.
   *
   * @return 64 bit unsigned integer in String format.
   */
  String getParentId();

  /**
   * Get this context's span id.
   *
   * @return 64 bit unsigned integer in String format.
   */
  String getSpanId();

  /**
   * Get the sampling flag for this context.
   *
   * @return sampling flag for null if no sampling flags are set.
   */
  // TODO: should we add a @Nullable annotation to our project?
  Integer getSamplingFlags();
}
