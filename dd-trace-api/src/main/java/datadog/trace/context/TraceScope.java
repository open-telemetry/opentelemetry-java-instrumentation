package datadog.trace.context;

import java.io.Closeable;

/** An object which can propagate a datadog trace across multiple threads. */
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

  /** Used to pass async context between workers. */
  interface Continuation {
    /**
     * Activate the continuation.
     *
     * <p>Should be called on the child thread.
     */
    TraceScope activate();

    /** Cancel the continuation. */
    void cancel();
  }
}
