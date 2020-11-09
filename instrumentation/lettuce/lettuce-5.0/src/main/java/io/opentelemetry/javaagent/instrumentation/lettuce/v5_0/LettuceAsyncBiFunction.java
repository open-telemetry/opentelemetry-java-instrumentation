/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceDatabaseClientTracer.tracer;

import io.opentelemetry.api.trace.Span;
import java.util.concurrent.CancellationException;
import java.util.function.BiFunction;

/**
 * Callback class to close the span on an error or a success in the RedisFuture returned by the
 * lettuce async API
 *
 * @param <T> the normal completion result
 * @param <U> the error
 * @param <R> the return type, should be null since nothing else should happen from tracing
 *     standpoint after the span is closed
 */
public class LettuceAsyncBiFunction<T, U extends Throwable, R>
    implements BiFunction<T, Throwable, R> {

  private final Span span;

  public LettuceAsyncBiFunction(Span span) {
    this.span = span;
  }

  @Override
  public R apply(T t, Throwable throwable) {
    if (throwable instanceof CancellationException) {
      span.setAttribute("db.command.cancelled", true);
      tracer().end(span);
    } else {
      tracer().endExceptionally(span, throwable);
    }
    return null;
  }
}
