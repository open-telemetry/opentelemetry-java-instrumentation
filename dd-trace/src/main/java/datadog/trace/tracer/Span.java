package datadog.trace.tracer;

import java.util.Map;

/**
 * A single measurement of time with arbitrary key-value attributes.
 *
 * <p>All spans are thread safe.
 *
 * <p>To create a Span, see {@link Trace#createSpan(SpanContext parentContext, Timestamp
 * startTimestamp)}
 */
public interface Span {

  /** @return The trace this span is associated with. */
  Trace getTrace();

  /** @return start timestamp of this span */
  public Timestamp getStartTimestamp();

  /** @return duration of this span in nanoseconds or null if span is not finished */
  public Long getDuration();

  /** @return true if a finish method has been invoked on this span. */
  boolean isFinished();

  /**
   * Get the span context for this span - required attributes to report to datadog.
   *
   * <p>See https://docs.datadoghq.com/api/?lang=python#tracing
   *
   * @return the span context.
   */
  SpanContext getContext();

  /** @return the span's service. */
  String getService();

  /**
   * Set the span's service.
   *
   * <p>May not exceed 100 characters.
   *
   * @param service the new service for the span.
   */
  void setService(String service);

  /** @return the span's resource. */
  String getResource();

  /**
   * Span the span's resource (e.g. http endpoint).
   *
   * <p>May not exceed 5000 characters.
   *
   * @param resource the new resource for the span.
   */
  void setResource(String resource);

  /** @return the span's type. */
  String getType();

  /**
   * Set the span's type (web, db, etc). {@see DDSpanTypes}.
   *
   * @param type the new type of the span.
   */
  void setType(String type);

  /** @return the span's name. */
  String getName();

  /**
   * Set the span's name.
   *
   * <p>May not exceed 100 characters.
   *
   * @param name the new name for the span.
   */
  void setName(String name);

  /** @return true iff span was marked as error span. */
  boolean isErrored();

  /**
   * Mark the span as having an error.
   *
   * @param errored true if the span has an error.
   */
  void setErrored(boolean errored);

  /**
   * Attach a throwable to this span.
   *
   * @param throwable throwable to attach
   */
  void attachThrowable(Throwable throwable);

  /**
   * Get all the metadata attached to this span.
   *
   * @return immutable map of span metadata.
   */
  Map<String, Object> getMeta();

  /**
   * Get a meta value on a span.
   *
   * @param key The meta key
   * @return The value currently associated with the given key. Null if no associated.
   */
  Object getMeta(String key);

  /**
   * Set key-value metadata on the span.
   *
   * @param key to set
   * @param value to set
   */
  void setMeta(String key, String value);

  /** {@link Span#setMeta(String, String)} for boolean values */
  void setMeta(String key, Boolean value);

  /** {@link Span#setMeta(String, String)} for number values */
  void setMeta(String key, Number value);

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
  // FIXME: This should take a Timestamp object instead.
  void finish(long finishTimestampNanoseconds);
}
