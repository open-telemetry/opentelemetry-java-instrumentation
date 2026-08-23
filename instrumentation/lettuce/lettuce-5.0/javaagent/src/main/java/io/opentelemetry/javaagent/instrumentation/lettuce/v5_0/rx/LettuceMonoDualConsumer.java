/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import reactor.core.publisher.Mono;

public class LettuceMonoDualConsumer<R, T> implements Consumer<R>, BiConsumer<T, Throwable> {

  private final RedisCommand<?, ?, ?> command;
  private final boolean finishSpanOnClose;
  private Context context;

  public LettuceMonoDualConsumer(RedisCommand<?, ?, ?> command, boolean finishSpanOnClose) {
    this.command = command;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override
  public void accept(R r) {
    context = instrumenter().start(Context.current(), command);
    if (finishSpanOnClose) {
      instrumenter().end(context, command, null, null);
    }
  }

  @Override
  public void accept(T t, Throwable throwable) {
    if (context != null) {
      instrumenter().end(context, command, null, throwable);
    } else {
      Logger.getLogger(Mono.class.getName())
          .severe(
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
  public Mono<T> finishSpanOnTerminal(Mono<T> publisher) {
    return publisher
        .doOnSuccess(value -> accept(value, (Throwable) null))
        .doOnError(error -> accept(null, error));
  }
}
