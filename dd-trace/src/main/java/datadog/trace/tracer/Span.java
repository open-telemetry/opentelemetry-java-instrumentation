package datadog.trace.tracer;

/**
 * A single measurement of time with arbitrary key-value attributes.
 *
 * <p>All spans are thread safe.
 *
 * <p>Spans may not be constructed individually, but created through a {@link Trace}
 */
public interface Span {

  /** @return The trace this span is associated with. */
  Trace getTrace();

  /** Stop the span's timer. Has no effect if the span is already finished. */
  void finish();

  /**
   * Stop the span's timer. Has no effect if the span is already finished.
   *
   * <p>It's undefined behavior to specify a finish timestamp which occurred before this span's
   * start timestamp.
   *
   * @param finishTimestampNanoseconds Epoch time in nanoseconds.
   */
  void finish(long finishTimestampNanoseconds);

  /** Returns true if a finish method has been invoked on this span. */
  boolean isFinished();

  /**
   * Attach a {@link LifecycleInterceptor} to this span.
   *
   * @param interceptor the interceptor to attach.
   */
  void addInterceptor(LifecycleInterceptor interceptor);

  /**
   * Get the span context for this span.
   *
   * @return the span context.
   */
  SpanContext getContext();

  /**
   * Get a meta value on a span.
   *
   * @param key The meta key
   * @return The value currently associated with the given key. Null if no associated. TODO: can
   *     return null
   */
  Object getMeta(String key);

  /**
   * Set key-value metadata on the span.
   *
   * <p>TODO: Forbid setting null?
   */
  void setMeta(String key, String value);

  /** {@link Span#setMeta(String, String)} for boolean values */
  void setMeta(String key, boolean value);

  /** {@link Span#setMeta(String, String)} for number values */
  void setMeta(String key, Number value);

  /** Get the span's name */
  String getName();
  /**
   * Set the span's name.
   *
   * @param newName the new name for the span.
   */
  void setName(String newName);

  String getResource();

  void setResource(String newResource);

  String getService();

  void setService(String newService);

  String getType();

  void setType(String newType);

  boolean isErrored();

  /** Attach a throwable to this span. */
  void attachThrowable();

  /**
   * Mark the span as having an error.
   *
   * @param isErrored true if the span has an error.
   */
  void setError(boolean isErrored);

  // TODO: OpenTracing Span#log methods. Do we need something here to support them? Current DDSpan
  // does not implement.

  /**
   * A LifecycleInterceptor allows adding hooks to particular events between a span starting and
   * finishing.
   */
  interface LifecycleInterceptor {
    /**
     * Called after a span is started.
     *
     * @param span the started span
     */
    void spanStarted(Span span);

    /** Called after a span's metadata is updated. */
    void afterMetadataSet(Span span, Object key, Object value);

    /**
     * Called after a span is finished.
     *
     * @param span the started span
     */
    void spanFinished(Span span);
  }
}
