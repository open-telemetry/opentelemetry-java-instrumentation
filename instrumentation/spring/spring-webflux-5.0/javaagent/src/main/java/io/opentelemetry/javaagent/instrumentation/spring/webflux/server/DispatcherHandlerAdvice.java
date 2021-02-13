/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.server.SpringWebfluxHttpServerTracer.tracer;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * This is 'top level' advice for Webflux instrumentation. This handles creating and finishing
 * Webflux span.
 */
public class DispatcherHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void methodEnter(
      @Advice.Argument(0) ServerWebExchange exchange,
      @Advice.Local("otelScope") Scope otelScope,
      @Advice.Local("otelContext") Context otelContext) {

    otelContext = tracer().startSpan("DispatcherHandler.handle", SpanKind.INTERNAL);
    // Unfortunately Netty EventLoop is not instrumented well enough to attribute all work to the
    // right things so we have to store the context in request itself.
    exchange.getAttributes().put(AdviceUtils.CONTEXT_ATTRIBUTE, otelContext);

    otelScope = otelContext.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Thrown Throwable throwable,
      @Advice.Argument(0) ServerWebExchange exchange,
      @Advice.Return(readOnly = false) Mono<Void> mono,
      @Advice.Local("otelScope") Scope otelScope,
      @Advice.Local("otelContext") Context otelContext) {
    if (throwable == null && mono != null) {
      mono = AdviceUtils.setPublisherSpan(mono, otelContext);
    } else if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    otelScope.close();
    // span finished in SpanFinishingSubscriber
  }
}
