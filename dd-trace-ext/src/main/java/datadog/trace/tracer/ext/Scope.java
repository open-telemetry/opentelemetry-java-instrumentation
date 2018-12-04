package datadog.trace.tracer.ext;

import datadog.trace.tracer.Span;
import datadog.trace.tracer.Trace;

/**
 * A scope holds a single span or trace continuation and may optionally close out its span or
 * continuation.
 *
 * <p>To create a scope, see {@link TracerContext#pushScope(Span, boolean)} and {@link
 * TracerContext#pushScope(Trace.Continuation, boolean)}.
 *
 * <p>All created scopes must be closed with {@link }
 */
public interface Scope {
  /** Get the span held by this scope. */
  Span span();

  /**
   * Close this scope. This method must be invoked on all created scopes.
   *
   * <p>Attempting to close a scope which is not on the top of its TracerContext's scope-stack is an
   * error. See {@link TracerContext#topOfScopeStack()}.
   */
  void close();
}
