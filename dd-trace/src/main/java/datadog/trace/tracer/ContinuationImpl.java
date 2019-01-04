package datadog.trace.tracer;

import lombok.extern.slf4j.Slf4j;

/** Concrete implementation of a continuation */
@Slf4j
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

  @Override
  protected synchronized void finalize() {
    try {
      if (!closed) {
        log.debug(
            "Closing continuation due to GC, this will prevent trace from being reported: {}",
            this);
        closeContinuation(true);
      }
    } catch (final Throwable t) {
      // Exceptions thrown in finalizer are eaten up and ignored, so log them instead
      log.debug("Span finalizer had thrown an exception: ", t);
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
