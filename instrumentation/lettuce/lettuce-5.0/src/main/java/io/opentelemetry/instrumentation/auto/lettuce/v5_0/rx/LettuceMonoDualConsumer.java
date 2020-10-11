/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v5_0.rx;

import static io.opentelemetry.instrumentation.auto.lettuce.v5_0.LettuceDatabaseClientTracer.TRACER;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.trace.Span;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class LettuceMonoDualConsumer<R, T> implements Consumer<R>, BiConsumer<T, Throwable> {

  private Span span = null;
  private final RedisCommand<?, ?, ?> command;
  private final boolean finishSpanOnClose;

  public LettuceMonoDualConsumer(RedisCommand<?, ?, ?> command, boolean finishSpanOnClose) {
    this.command = command;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override
  public void accept(R r) {
    span = TRACER.startSpan(null, command);
    if (finishSpanOnClose) {
      TRACER.end(span);
    }
  }

  @Override
  public void accept(T t, Throwable throwable) {
    if (span != null) {
      if (throwable == null) {
        TRACER.end(span);
      } else {
        TRACER.endExceptionally(span, throwable);
      }
    } else {
      LoggerFactory.getLogger(Mono.class)
          .error(
              "Failed to finish this.span, BiConsumer cannot find this.span because "
                  + "it probably wasn't started.");
    }
  }
}
