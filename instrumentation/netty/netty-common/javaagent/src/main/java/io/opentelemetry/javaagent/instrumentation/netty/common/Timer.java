/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import java.time.Instant;

public final class Timer implements ImplicitContextKeyed {

  private static final ContextKey<Timer> KEY = ContextKey.named("opentelemetry-timer-key");

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

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }

  public static Timer get(Context context) {
    return context.get(KEY);
  }
}
