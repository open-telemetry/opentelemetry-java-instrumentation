/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceDatabaseClientTracer.tracer;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
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
    context = tracer().startOperation(Context.current(), null, command);
    if (finishSpanOnClose) {
      tracer().end(context);
    }
  }

  @Override
  public void accept(T t, Throwable throwable) {
    if (context != null) {
      if (throwable == null) {
        tracer().end(context);
      } else {
        tracer().endExceptionally(context, throwable);
      }
    } else {
      LoggerFactory.getLogger(Mono.class)
          .error(
              "Failed to finish this.span, BiConsumer cannot find this.span because "
                  + "it probably wasn't started.");
    }
  }
}
