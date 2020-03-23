/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.traceannotation;

import static io.opentelemetry.auto.instrumentation.traceannotation.TraceDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.traceannotation.TraceDecorator.TRACER;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Origin final Method method) {
    final Span span = TRACER.spanBuilder(DECORATE.spanNameForMethod(method)).startSpan();
    DECORATE.afterStart(span);
    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
    final Span span = spanWithScope.getSpan();
    DECORATE.onError(span, throwable);
    DECORATE.beforeFinish(span);
    span.end();
    spanWithScope.closeScope();
  }
}
