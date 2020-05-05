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

import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(0) final RedisCommand command) {

    final Span span =
        LettuceClientDecorator.TRACER
            .spanBuilder(LettuceInstrumentationUtil.getCommandName(command))
            .setSpanKind(CLIENT)
            .startSpan();
    LettuceClientDecorator.DECORATE.afterStart(span);

    return new SpanWithScope(span, currentContextWith(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final RedisCommand command,
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {

    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      LettuceClientDecorator.DECORATE.onError(span, throwable);
      LettuceClientDecorator.DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
      return;
    }

    // close spans on error or normal completion
    if (LettuceInstrumentationUtil.doFinishSpanEarly(command)) {
      span.end();
    } else {
      asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(span));
    }
    spanWithScope.closeScope();
    // span finished by LettuceAsyncBiFunction
  }
}
