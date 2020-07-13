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

package io.opentelemetry.auto.instrumentation.lettuce.v5_0;

import io.opentelemetry.trace.Span;
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

  public LettuceAsyncBiFunction(final Span span) {
    this.span = span;
  }

  @Override
  public R apply(final T t, final Throwable throwable) {
    if (throwable instanceof CancellationException) {
      span.setAttribute("db.command.cancelled", true);
      LettuceDatabaseClientTracer.TRACER.end(span);
    } else {
      LettuceDatabaseClientTracer.TRACER.endExceptionally(span, throwable);
    }
    return null;
  }
}
