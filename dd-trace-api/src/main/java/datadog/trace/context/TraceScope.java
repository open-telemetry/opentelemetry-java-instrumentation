package datadog.trace.context;

/** An object when can propagate a datadog trace across multiple threads. */
public interface TraceScope {
  /**
   * Prevent the trace attached to this TraceScope from reporting until the returned Continuation
   * finishes.
   *
   * <p>Should be called on the parent thread.
   */
  Continuation capture();

  /** Close the activated context and allow any underlying spans to finish. */
  void close();

  /** If true, this context will propagate across async boundaries. */
  boolean isAsyncLinking();

  /** Set context's async propagation value. */
  void setAsyncLinking(boolean value);

  /** Used to pass async context between workers. */
  interface Continuation {
    /**
     * Activate the continuation.
     *
     * <p>Should be called on the child thread.
     */
    TraceScope activate();

    /** Cancel the continuation. */
    void close();
  }
}
