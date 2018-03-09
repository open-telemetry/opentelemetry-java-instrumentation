package datadog.trace.context;

/** An object when can propagate a datadog trace across multiple threads. */
public interface ContextPropagator {
  /**
   * Prevent the trace attached to this ContextPropagator from reporting until the returned
   * Continuation finishes.
   *
   * <p>Should be called on the parent thread.
   */
  Continuation capture(boolean finishOnClose);

  /** Close the activated context and allow any underlying spans to finish. */
  void close();

  /** Used to pass async context between workers. */
  interface Continuation {
    /**
     * Activate the continuation.
     *
     * <p>Should be called on the child thread.
     */
    ContextPropagator activate();
  }
}
