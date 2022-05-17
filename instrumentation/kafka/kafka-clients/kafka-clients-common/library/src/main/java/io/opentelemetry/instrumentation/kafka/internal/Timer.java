/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Timer {

  public static Timer start() {
    return new Timer(Instant.now(), System.nanoTime());
  }

  private final Instant startTime;
  private final long startNanoTime;

  private Timer(Instant startTime, long startNanoTime) {
    this.startTime = startTime;
    this.startNanoTime = startNanoTime;
  }

  public long startTimeNanos() {
    return toNanos(startTime);
  }

  public long nowNanos() {
    long durationNanos = System.nanoTime() - startNanoTime;
    return toNanos(startTime.plusNanos(durationNanos));
  }

  private static long toNanos(Instant time) {
    return TimeUnit.SECONDS.toNanos(time.getEpochSecond()) + time.getNano();
  }
}
