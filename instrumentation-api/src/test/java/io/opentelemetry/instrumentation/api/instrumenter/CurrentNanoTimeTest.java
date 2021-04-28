/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CurrentNanoTimeTest {
  @Test
  void shouldCreateAnInstantWithNanosecondPrecision() {
    // given
    long epochNanoseconds = TimeUnit.SECONDS.toNanos(10);
    AtomicLong nanoseconds = new AtomicLong(1_000);

    Clock java8Clock = new CurrentNanoTime.Java8NanoClock(epochNanoseconds, nanoseconds::get);

    nanoseconds.set(1_000_002_000);

    // when
    Instant result = java8Clock.instant();

    // then
    assertEquals(11, result.getEpochSecond());
    assertEquals(1_000, result.getNano());
  }
}
