package io.opentelemetry.auto.instrumentation.springwebflux.server;

import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.TRACER;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
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

    SpanWithScope spanAndScope = null;
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

      spanAndScope = new SpanWithScope(span, TRACER.withSpan(span));
    }

    final Span parentSpan = exchange.getAttribute(AdviceUtils.PARENT_SPAN_ATTRIBUTE);
    final PathPattern bestPattern =
        exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (parentSpan != null && bestPattern != null) {
      parentSpan.setAttribute(
          MoreTags.RESOURCE_NAME,
          exchange.getRequest().getMethodValue() + " " + bestPattern.getPatternString());
    }

    return spanAndScope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Argument(0) final ServerWebExchange exchange,
      @Advice.Enter final SpanWithScope spanAndScope,
      @Advice.Thrown final Throwable throwable) {
    if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    if (spanAndScope != null) {
      spanAndScope.closeScope();
    }
  }
}
