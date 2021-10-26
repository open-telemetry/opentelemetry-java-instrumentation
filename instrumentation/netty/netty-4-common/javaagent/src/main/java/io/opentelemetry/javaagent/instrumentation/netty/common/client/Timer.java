/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import java.time.Instant;

final class Timer {

  static Timer start() {
    return new Timer(Instant.now(), System.nanoTime());
  }

  private final Instant startTime;
  private final long startNanoTime;

  private Timer(Instant startTime, long startNanoTime) {
    this.startTime = startTime;
    this.startNanoTime = startNanoTime;
  }

  Instant startTime() {
    return startTime;
  }

  Instant now() {
    long durationNanos = System.nanoTime() - startNanoTime;
    return startTime().plusNanos(durationNanos);
  }
}
