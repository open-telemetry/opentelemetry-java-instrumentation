package datadog.trace.context;

import java.io.Closeable;

/** An object when can propagate a datadog trace across multiple threads. */
public interface TraceScope extends Closeable {
  /**
   * Prevent the trace attached to this TraceScope from reporting until the returned Continuation
   * finishes.
   *
   * <p>Should be called on the parent thread.
   */
  Continuation capture();

  /** Close the activated context and allow any underlying spans to finish. */
  @Override
  void close();

  /** If true, this context will propagate across async boundaries. */
  boolean isAsyncPropagating();

  /**
   * Enable or disable async propagation. Async propagation is initially set to false.
   *
   * @param value The new propagation value. True == propagate. False == don't propagate.
   */
  void setAsyncPropagation(boolean value);

  /** Used to pass async context between workers. */
  interface Continuation {
    /**
     * Activate the continuation.
     *
     * <p>Should be called on the child thread.
     */
    TraceScope activate();

    /**
     * Cancel the continuation. This also closes parent scope.
     *
     * <p>FIXME: the fact that this is closing parent scope is confusing, we should review this in
     * new API.
     */
    void close();

    /**
     * Close the continuation.
     *
     * @param closeContinuationScope true iff parent scope should also be closed
     */
    void close(boolean closeContinuationScope);
  }
}
