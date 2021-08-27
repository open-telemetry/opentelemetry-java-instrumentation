/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import com.google.auto.value.AutoValue;
import java.time.Instant;

@AutoValue
public abstract class Timer {

  public static Timer start() {
    return new AutoValue_Timer(Instant.now(), System.nanoTime());
  }

  Timer() {}

  public abstract Instant startTime();

  abstract long startNanoTime();

  public Instant endTime() {
    long durationNanos = System.nanoTime() - startNanoTime();
    return startTime().plusNanos(durationNanos);
  }
}
