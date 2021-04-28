/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * This utility class allows to retrieve an {@link Instant} with the current time measured with
 * nanosecond precision.
 */
public final class CurrentNanoTime {
  private static final Clock CLOCK;

  static {
    Clock clock;
    try {
      // Runtime#version() was added in Java 9
      Runtime.class.getDeclaredMethod("version");
      // Java 9+ SystemClock has nano precision
      clock = Clock.systemUTC();
    } catch (NoSuchMethodException e) {
      // Java 8 doesn't, so we have to compute the correct timestamp ourselves
      clock = new Java8NanoClock();
    }
    CLOCK = clock;
  }

  /** Returns an {@link Instant} with the current time measured with nanosecond precision. */
  public static Instant get() {
    return CLOCK.instant();
  }

  // reused logic from the SDK MonotonicClock
  static final class Java8NanoClock extends Clock {
    private final long epochNanos;
    private final LongSupplier nanoTimeSupplier;
    private final long nanoTimeOffset;

    private Java8NanoClock() {
      this(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()), System::nanoTime);
    }

    // visible for tests
    Java8NanoClock(long epochNanos, LongSupplier nanoTimeSupplier) {
      this.epochNanos = epochNanos;
      this.nanoTimeSupplier = nanoTimeSupplier;
      this.nanoTimeOffset = nanoTimeSupplier.getAsLong();
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      long deltaNanos = nanoTimeSupplier.getAsLong() - this.nanoTimeOffset;
      long nowNanos = epochNanos + deltaNanos;
      long seconds = TimeUnit.NANOSECONDS.toSeconds(nowNanos);
      long nanoAdjustment = nowNanos - TimeUnit.SECONDS.toNanos(seconds);
      return Instant.ofEpochSecond(seconds, nanoAdjustment);
    }
  }

  private CurrentNanoTime() {}
}
