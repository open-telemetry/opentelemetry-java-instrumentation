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

import static io.opentelemetry.auto.instrumentation.lettuce.v5_0.LettuceDatabaseClientTracer.TRACER;
import static io.opentelemetry.auto.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) final RedisCommand<?, ?, ?> command,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {

    span = TRACER.startSpan(null, command);
    scope = TRACER.startScope(span);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final RedisCommand<?, ?, ?> command,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    scope.close();

    if (throwable != null) {
      TRACER.endExceptionally(span, throwable);
      return;
    }

    // close spans on error or normal completion
    if (expectsResponse(command)) {
      asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(span));
    } else {
      TRACER.end(span);
    }
  }
}
