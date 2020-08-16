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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.anotations;

import static io.opentelemetry.instrumentation.auto.opentelemetryapi.anotations.TraceDecorator.DECORATE;
import static io.opentelemetry.instrumentation.auto.opentelemetryapi.anotations.TraceDecorator.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import application.io.opentelemetry.extensions.auto.annotations.WithSpan;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

/**
 * Instrumentation for methods annotated with {@link
 * io.opentelemetry.extensions.auto.annotations.WithSpan} annotation.
 *
 * @see WithSpanAnnotationInstrumentation
 */
public class WithSpanAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Origin Method method) {
    WithSpan applicationAnnotation = method.getAnnotation(WithSpan.class);

    Span span =
        TRACER
            .spanBuilder(DECORATE.spanNameForMethodWithAnnotation(applicationAnnotation, method))
            .setSpanKind(DECORATE.extractSpanKind(applicationAnnotation))
            .startSpan();
    DECORATE.afterStart(span);
    return new SpanWithScope(span, currentContextWith(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter SpanWithScope spanWithScope, @Advice.Thrown Throwable throwable) {
    Span span = spanWithScope.getSpan();
    DECORATE.onError(span, throwable);
    DECORATE.beforeFinish(span);
    span.end();
    spanWithScope.closeScope();
  }
}
