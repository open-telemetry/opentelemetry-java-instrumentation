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

  private Context context;
  private final RedisCommand<?, ?, ?> command;
  private final boolean finishSpanOnClose;

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
}
