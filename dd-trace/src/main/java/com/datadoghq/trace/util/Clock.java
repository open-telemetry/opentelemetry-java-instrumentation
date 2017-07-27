package com.datadoghq.trace.util;

import java.util.concurrent.TimeUnit;

/**
 * A simple wrapper for system clock that aims to provide the current time
 *
 * <p>
 *
 * <p>The JDK provides two clocks:
 * <li>one in nanoseconds, for precision, but it can only use to measure durations
 * <li>one in milliseconds, for accuracy, useful to provide epoch time
 *     <p>
 *     <p>At this time, we are using a millis precision (converted to micros) in order to guarantee
 *     consistency between the span start times and the durations
 */
public class Clock {

  /**
   * Get the current nanos ticks, this method can't be use for date accuracy (only duration
   * calculations)
   *
   * @return The current nanos ticks
   */
  public static synchronized long currentNanoTicks() {
    return System.nanoTime();
  }

  /**
   * Get the current time in micros. The actual precision is the millis
   *
   * @return the current epoch time in micros
   */
  public static synchronized long currentMicroTime() {
    return TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
  }
}
