package datadog.trace.tracer;

import java.util.concurrent.TimeUnit;
import lombok.EqualsAndHashCode;

/**
 * This is a wrapper to System clock that provides an easier way to get nanosecond precision.
 *
 * <p>The JDK provides two clocks:
 * <li>one in nanoseconds, for precision, but it can only use to measure durations
 * <li>one in milliseconds, for accuracy, useful to provide epoch time
 *
 *     <p>Once created this class captures current time with millisecond presition and current
 *     nanosecond counter.
 *
 *     <p>It provides an API to create {@link Timestamp} that can be used to measure durations with
 *     nanosecond precision.
 */
@EqualsAndHashCode
class Clock {

  /** Tracer that created this clock */
  private final Tracer tracer;

  /** Trace start time in nano seconds measured up to a millisecond accuracy */
  private final long startTimeNano;
  /** Nano ticks counter when clock is created */
  private final long startNanoTicks;

  Clock(final Tracer tracer) {
    this.tracer = tracer;
    startTimeNano = epochTimeNano();
    startNanoTicks = nanoTicks();
  }

  /** @return {@link Tracer} that created this clock. */
  public Tracer getTracer() {
    return tracer;
  }

  /**
   * Create new timestamp instance for current time.
   *
   * @return new timestamp capturing current time.
   */
  public Timestamp createCurrentTimestamp() {
    return new Timestamp(this);
  }

  /**
   * Create new timestamp instance for current time.
   *
   * @return new timestamp capturing current time.
   */
  public Timestamp createTimestampForTime(final long time, final TimeUnit unit) {
    return new Timestamp(this, time, unit);
  }

  /**
   * Get the current nanos ticks (i.e. System.nanoTime()), this method can't be use for date
   * accuracy (only duration calculations).
   *
   * @return The current nanos ticks.
   */
  long nanoTicks() {
    return System.nanoTime();
  }

  /**
   * Get the current epoch time in micros.
   *
   * <p>Note: The actual precision is the millis.
   *
   * @return the current epoch time in micros.
   */
  long epochTimeMicro() {
    return TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
  }

  /**
   * Get the current epoch time in nanos.
   *
   * <p>Note: The actual precision is the millis. This will overflow ~290 years after epoch.
   *
   * @return the current epoch time in nanos.
   */
  long epochTimeNano() {
    return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
  }

  /**
   * Get time this clock instance was created in nanos.
   *
   * @return the time this clock instance was created in nanos.
   */
  long getStartTimeNano() {
    return startTimeNano;
  }

  /**
   * Get nano ticks counter value when this clock instance was created.
   *
   * @return nano ticks counter value when this clock instance was created.
   */
  long getStartNanoTicks() {
    return startNanoTicks;
  }
}
