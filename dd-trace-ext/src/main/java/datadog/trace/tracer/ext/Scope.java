package datadog.trace.tracer.ext;

import datadog.trace.tracer.Continuation;
import datadog.trace.tracer.Span;

/**
 * A scope holds a single span or trace continuation and may optionally finish its span or
 * continuation.
 *
 * <p>To create a scope, see {@link TracerContext#pushScope(Span)} and {@link
 * TracerContext#pushScope(Continuation)}.
 *
 * <p>All created scopes must be closed with {@link Scope#close()}
 */
public interface Scope extends AutoCloseable {
  /** Get the span held by this scope. */
  Span span();

  /**
   * Close this scope. This method must be invoked on all created scopes.
   *
   * <p>Attempting to close a scope which is not on the top of its TracerContext's scope-stack is an
   * error. See {@link TracerContext#peekScope()}.
   */
  @Override
  void close();
}
