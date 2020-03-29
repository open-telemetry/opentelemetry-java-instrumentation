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
package io.opentelemetry.auto.instrumentation.springwebflux.server;

import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.DECORATE;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

public class HandlerAdapterAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope methodEnter(
      @Advice.Argument(0) final ServerWebExchange exchange,
      @Advice.Argument(1) final Object handler) {

    SpanWithScope spanWithScope = null;
    final Span span = exchange.getAttribute(AdviceUtils.SPAN_ATTRIBUTE);
    if (handler != null && span != null) {
      final String handlerType;
      final String operationName;

      if (handler instanceof HandlerMethod) {
        // Special case for requests mapped with annotations
        final HandlerMethod handlerMethod = (HandlerMethod) handler;
        operationName = DECORATE.spanNameForMethod(handlerMethod.getMethod());
        handlerType = handlerMethod.getMethod().getDeclaringClass().getName();
      } else {
        operationName = AdviceUtils.parseOperationName(handler);
        handlerType = handler.getClass().getName();
      }

      span.updateName(operationName);
      span.setAttribute("handler.type", handlerType);

      spanWithScope = new SpanWithScope(span, currentContextWith(span));
    }

    final Span parentSpan = exchange.getAttribute(AdviceUtils.PARENT_SPAN_ATTRIBUTE);
    final PathPattern bestPattern =
        exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (parentSpan != null && bestPattern != null) {
      final String resourceName = bestPattern.getPatternString();
      parentSpan.updateName(resourceName);
    }

    return spanWithScope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Argument(0) final ServerWebExchange exchange,
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable) {
    if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    if (spanWithScope != null) {
      spanWithScope.closeScope();
    }
  }
}
