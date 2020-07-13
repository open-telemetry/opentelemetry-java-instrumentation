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

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(1) final RedisURI redisURI,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span = TRACER.startSpan("CONNECT", redisURI, null);
    scope = TRACER.startScope(span);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture<?> connectionFuture,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (scope != null) {
      scope.close();
    }

    if (throwable != null) {
      TRACER.endExceptionally(span, throwable);
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(span));
  }
}
