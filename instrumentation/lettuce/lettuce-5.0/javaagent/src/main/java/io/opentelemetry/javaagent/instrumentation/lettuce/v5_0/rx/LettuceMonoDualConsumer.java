/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;

public class LettuceMonoDualConsumer<T>
    implements Consumer<Subscription>, BiConsumer<T, Throwable> {

  private static final Logger logger = Logger.getLogger(LettuceMonoDualConsumer.class.getName());

  private final StatefulConnection<?, ?> connection;
  private final boolean expectsResponse;
  @Nullable private RedisCommand<?, ?, ?> command;
  @Nullable private Context context;

  public static <T> Mono<T> monitor(
      Mono<T> publisher, StatefulConnection<?, ?> connection, boolean expectsResponse) {
    return Mono.defer(
        () -> {
          LettuceMonoDualConsumer<T> handler =
              new LettuceMonoDualConsumer<>(connection, expectsResponse);
          Mono<T> monitoredPublisher = publisher.doOnSubscribe(handler);
          return expectsResponse
              ? handler.finishSpanOnTerminal(monitoredPublisher)
              : monitoredPublisher;
        });
  }

  private LettuceMonoDualConsumer(StatefulConnection<?, ?> connection, boolean expectsResponse) {
    this.connection = connection;
    this.expectsResponse = expectsResponse;
  }

  @Override
  public void accept(Subscription subscription) {
    RedisCommand<?, ?, ?> subscriptionCommand = LettuceReactiveCommandContext.current();
    if (subscriptionCommand == null) {
      logger.fine("Failed to correlate a Lettuce reactive subscription with its command.");
      return;
    }
    command = subscriptionCommand;
    LettuceSingletons.attachAddress(subscriptionCommand, connection);
    context = instrumenter().start(Context.current(), subscriptionCommand);
    if (!expectsResponse) {
      instrumenter().end(context, subscriptionCommand, null, null);
    }
  }

  @Override
  public void accept(T t, Throwable throwable) {
    if (context != null && command != null) {
      instrumenter().end(context, command, null, throwable);
    } else {
      logger.fine(
          "Failed to finish this.span, BiConsumer cannot find this.span because "
              + "it probably wasn't started.");
    }
  }

  /**
   * Registers terminal callbacks that finish the span on completion or error. {@code
   * doOnSuccessOrError} was removed in reactor 3.5, so {@code doOnSuccess} + {@code doOnError}
   * (both available across the whole supported reactor range) are used instead. The wiring lives
   * here, on an injected helper class, rather than inline in the advice, so the lambdas do not
   * become private synthetic methods on the advice class (which the instrumented class cannot
   * access).
   */
  private Mono<T> finishSpanOnTerminal(Mono<T> publisher) {
    return publisher
        .doOnSuccess(value -> accept(value, (Throwable) null))
        .doOnError(error -> accept(null, error));
  }
}
