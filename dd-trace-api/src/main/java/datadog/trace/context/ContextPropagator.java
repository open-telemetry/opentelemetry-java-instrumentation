package datadog.trace.context;

import io.opentracing.Scope;

/** An object when can propagate a datadog trace across multiple threads. */
public interface ContextPropagator {
  /**
   * Prevent the trace attached to this ContextPropagator from reporting until the returned
   * Continuation finishes.
   */
  public Continuation capture(boolean finishOnClose);

  /** Used to pass async context between workers. */
  public static interface Continuation {
    /**
     * Activate the continuation. When the returned scope is closed, the continuation will no longer
     * prevent the trace from reporting.
     */
    public Scope activate();
  }
}
