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
package io.opentelemetry.auto.instrumentation.lettuce;

import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(1) final RedisURI redisURI) {
    final Span span = TRACER.spanBuilder("CONNECT").setSpanKind(CLIENT).startSpan();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return new SpanWithScope(span, currentContextWith(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture<?> connectionFuture) {
    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(span));
    spanWithScope.closeScope();
  }
}
