package datadog.trace.tracer;

import static java.lang.Math.max;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.concurrent.TimeUnit;
import lombok.EqualsAndHashCode;

/**
 * Class that encapsulations notion of a given timestamp, or instant in time.
 *
 * <p>Timestamps are created by a [@link Clock} instance.
 */
@EqualsAndHashCode
public class Timestamp {

  private final Clock clock;
  private final long nanoTicks;

  /**
   * Create timestamp for a given clock and given nanoTicks state
   *
   * @param clock clock instance
   */
  Timestamp(final Clock clock) {
    this.clock = clock;
    nanoTicks = clock.nanoTicks();
  }

  /**
   * Create timestamp for a given clock and given start time and precision
   *
   * @param clock clock instance
   */
  Timestamp(final Clock clock, final long time, final TimeUnit unit) {
    this.clock = clock;
    final long currentTime = clock.epochTimeNano();
    final long currentTick = clock.nanoTicks();
    final long desiredTime = unit.toNanos(time);
    final long offset = currentTime - desiredTime;
    nanoTicks = currentTick - offset;
  }

  /** @return clock instance used by this timestamp */
  Clock getClock() {
    return clock;
  }

  /** @return time since epoch in nanoseconds */
  @JsonValue
  public long getTime() {
    return clock.getStartTimeNano() + startTicksOffset();
  }

  /** @return duration in nanoseconds from this time stamp to current time. */
  public long getDuration() {
    return getDuration(clock.createCurrentTimestamp());
  }

  /**
   * Get duration in nanoseconds from this time stamp to provided finish timestamp.
   *
   * @param finishTimestamp finish timestamp to use as period's end.
   * @return duration in nanoseconds.
   */
  public long getDuration(final Timestamp finishTimestamp) {
    if (clock != finishTimestamp.clock) {
      clock
          .getTracer()
          .reportError(
              "Trying to find duration between two timestamps created by different clocks. Current clock: %s, finish timestamp clock: %s",
              clock, finishTimestamp.clock);
      // Do our best to try to calculate nano-second time using millisecond clock start time and
      // nanosecond offset.
      return max(0, finishTimestamp.getTime() - getTime());
    }
    return max(0, finishTimestamp.nanoTicks - nanoTicks);
  }

  /**
   * Duration in nanoseconds for external finish time.
   *
   * <p>Note: since we can only get time with millisecond precision in Java this ends up being
   * effectively millisecond precision duration converted to nanoseconds.
   *
   * @param finishTimeNanoseconds
   * @return duration in nanoseconds (with millisecond precision).
   */
  public long getDuration(final long finishTimeNanoseconds) {
    // This is calculated as the difference between finish time and clock start time and then
    // subtracting difference between timestamp nanoticks and clock start nanoticks to account for
    // time after clock has been created and before timestamp has been created.
    return max(0, finishTimeNanoseconds - clock.getStartTimeNano() - startTicksOffset());
  }

  private long startTicksOffset() {
    return nanoTicks - clock.getStartNanoTicks();
  }
}
