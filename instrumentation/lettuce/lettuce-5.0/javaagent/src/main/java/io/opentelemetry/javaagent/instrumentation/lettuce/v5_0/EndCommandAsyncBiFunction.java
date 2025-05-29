/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.util.concurrent.CancellationException;
import java.util.function.BiFunction;

/**
 * Callback class to close the command span on an error or a success in the RedisFuture returned by
 * the lettuce async API.
 *
 * @param <T> the normal completion result
 * @param <U> the error
 * @param <R> the return type, should be null since nothing else should happen from tracing
 *     standpoint after the span is closed
 */
public class EndCommandAsyncBiFunction<T, U extends Throwable, R>
    implements BiFunction<T, Throwable, R> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.lettuce.experimental-span-attributes", false);

  private final Context context;
  private final RedisCommand<?, ?, ?> command;

  public EndCommandAsyncBiFunction(Context context, RedisCommand<?, ?, ?> command) {
    this.context = context;
    this.command = command;
  }

  @Override
  public R apply(T t, Throwable throwable) {
    end(context, command, throwable);
    return null;
  }

  public static void end(Context context, RedisCommand<?, ?, ?> command, Throwable throwable) {
    if (throwable instanceof CancellationException) {
      if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        Span.fromContext(context).setAttribute("lettuce.command.cancelled", true);
      }
      // and don't report this as an error
      throwable = null;
    }
    instrumenter().end(context, command, null, throwable);
  }
}
