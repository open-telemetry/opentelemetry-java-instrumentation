/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.connectInstrumenter;

import io.lettuce.core.RedisURI;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.concurrent.CancellationException;
import java.util.function.BiFunction;

/**
 * Callback class to close the connect span on an error or a success in the RedisFuture returned by
 * the lettuce async API.
 *
 * @param <T> the normal completion result
 * @param <U> the error
 * @param <R> the return type, should be null since nothing else should happen from tracing
 *     standpoint after the span is closed
 */
public class EndConnectAsyncBiFunction<T, U extends Throwable, R>
    implements BiFunction<T, Throwable, R> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.lettuce.experimental-span-attributes", false);

  private final Context context;
  private final RedisURI redisUri;

  public EndConnectAsyncBiFunction(Context context, RedisURI redisUri) {
    this.context = context;
    this.redisUri = redisUri;
  }

  @Override
  public R apply(T t, Throwable throwable) {
    if (throwable instanceof CancellationException) {
      if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        Span.fromContext(context).setAttribute("lettuce.command.cancelled", true);
      }
      // and don't report this as an error
      throwable = null;
    }
    connectInstrumenter().end(context, redisUri, null, throwable);
    return null;
  }
}
