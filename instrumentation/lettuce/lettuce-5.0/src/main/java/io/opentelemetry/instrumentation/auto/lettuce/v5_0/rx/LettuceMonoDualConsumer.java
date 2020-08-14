/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  public LettuceMonoDualConsumer(
      final RedisCommand<?, ?, ?> command, final boolean finishSpanOnClose) {
    this.command = command;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override
  public void accept(final R r) {
    span = TRACER.startSpan(null, command);
    if (finishSpanOnClose) {
      TRACER.end(span);
    }
  }

  @Override
  public void accept(final T t, final Throwable throwable) {
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
