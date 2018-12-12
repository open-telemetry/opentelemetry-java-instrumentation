package datadog.trace.tracer;

/** Trace interface that provides additional methods used internally */
interface TraceInternal extends Trace {

  /**
   * Called by the span to inform trace that span is finished.
   *
   * @param span span to finish.
   * @param invalid true iff span is 'invalid'.
   */
  void finishSpan(final Span span, final boolean invalid);

  /**
   * Called by the continuation to inform trace that span is closed.
   *
   * @param continuation continuation to close.
   * @param invalid true iff span is 'invalid'.
   */
  void closeContinuation(final Continuation continuation, final boolean invalid);
}
