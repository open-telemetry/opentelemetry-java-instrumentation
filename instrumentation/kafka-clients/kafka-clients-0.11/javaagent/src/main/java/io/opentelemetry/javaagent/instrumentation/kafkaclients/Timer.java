/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import java.time.Instant;

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

  public Instant startTime() {
    return startTime;
  }

  public Instant now() {
    long durationNanos = System.nanoTime() - startNanoTime;
    return startTime().plusNanos(durationNanos);
  }
}
