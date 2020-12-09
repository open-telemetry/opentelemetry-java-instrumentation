/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

public class HandlerAdapterAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void methodEnter(
      @Advice.Argument(0) ServerWebExchange exchange,
      @Advice.Argument(1) Object handler,
      @Advice.Local("otelScope") Scope scope) {

    Context context = exchange.getAttribute(AdviceUtils.CONTEXT_ATTRIBUTE);
    if (handler != null && context != null) {
      Span span =
          io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(
              context);
      String handlerType;
      String operationName;

      if (handler instanceof HandlerMethod) {
        // Special case for requests mapped with annotations
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        operationName = Tracer.spanNameForMethod(handlerMethod.getMethod());
        handlerType = handlerMethod.getMethod().getDeclaringClass().getName();
      } else {
        operationName = AdviceUtils.parseOperationName(handler);
        handlerType = handler.getClass().getName();
      }

      span.updateName(operationName);
      span.setAttribute("spring-webflux.handler.type", handlerType);

      scope = context.makeCurrent();
    }

    if (context != null) {
      Span serverSpan = context.get(Tracer.CONTEXT_SERVER_SPAN_KEY);
      PathPattern bestPattern =
          exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (serverSpan != null && bestPattern != null) {
        serverSpan.updateName(
            ServletContextPath.prepend(Context.current(), bestPattern.toString()));
      }
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Argument(0) ServerWebExchange exchange,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelScope") Scope scope) {
    if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    if (scope != null) {
      scope.close();
      // span finished in SpanFinishingSubscriber
    }
  }
}
