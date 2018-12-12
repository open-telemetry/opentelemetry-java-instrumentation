package datadog.trace.tracer;

/** Concrete implementation of a continuation */
class ContinuationImpl implements Continuation {

  private final TraceInternal trace;
  private final Span span;
  private volatile boolean closed = false;

  ContinuationImpl(final TraceInternal trace, final Span span) {
    this.trace = trace;
    this.span = span;
  }

  @Override
  public Trace getTrace() {
    return trace;
  }

  @Override
  public Span getSpan() {
    return span;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public synchronized void close() {
    if (closed) {
      reportUsageError("Attempted to close continuation that is already closed: %s", this);
    } else {
      closeContinuation(false);
    }
  }

  // TODO: we may want to reconsider usage of 'finalize'. One of the problems seems to be that
  // exceptions thrown in finalizer are eaten up and ignored, and may not even be logged by default.
  // This may lead to fun debugging sessions.
  @Override
  protected synchronized void finalize() {
    if (!closed) {
      trace
          .getTracer()
          .reportWarning(
              "Closing continuation due to GC, this will prevent trace from being reported: %s",
              this);
      closeContinuation(true);
    }
  }

  /**
   * Helper method to perform operations needed to close the continuation.
   *
   * <p>Note: This has to be called under object lock.
   *
   * @param invalid true iff continuation is being closed due to GC, this will make trace invalid.
   */
  private void closeContinuation(final boolean invalid) {
    closed = true;
    trace.closeContinuation(this, invalid);
  }

  private void reportUsageError(final String message, final Object... args) {
    trace.getTracer().reportError(message, args);
  }
}
