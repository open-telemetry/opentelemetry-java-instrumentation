/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;
import java.util.logging.Logger;

public final class Timer implements ImplicitContextKeyed {

  private static final ContextKey<Timer> KEY = ContextKey.named("opentelemetry-timer-key");

  private static final Logger logger = Logger.getLogger(Timer.class.getName());

  @SuppressWarnings("unchecked")
  public static <REQUEST, RESPONSE> TimeExtractor<REQUEST, RESPONSE> timeExtractor() {
    return (TimeExtractor<REQUEST, RESPONSE>) TimeExtractorImpl.INSTANCE;
  }

  public static Timer start() {
    return new Timer(Instant.now(), System.nanoTime());
  }

  private final Instant startTime;
  private final long startNanoTime;

  private Timer(Instant startTime, long startNanoTime) {
    this.startTime = startTime;
    this.startNanoTime = startNanoTime;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }

  private Instant startTime() {
    return startTime;
  }

  private Instant now() {
    long durationNanos = System.nanoTime() - startNanoTime;
    return startTime().plusNanos(durationNanos);
  }

  private enum TimeExtractorImpl implements TimeExtractor<Object, Object> {
    INSTANCE;

    @Override
    public Instant extractStartTime(Context parentContext, Object request) {
      Timer timer = parentContext.get(KEY);
      if (timer == null) {
        logger.warning(
            "Timer is missing from the passed context: this is most likely a programmer error."
                + " Using the current time instead.");
        return Instant.now();
      }
      return timer.startTime();
    }

    @Override
    public Instant extractEndTime(Context context, Object request) {
      Timer timer = context.get(KEY);
      if (timer == null) {
        logger.warning(
            "Timer is missing from the passed context: this is most likely a programmer error."
                + " Using the current time instead.");
        return Instant.now();
      }
      return timer.now();
    }
  }
}
