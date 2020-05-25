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
package io.opentelemetry.auto.instrumentation.springwebflux.server;

import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * This is 'top level' advice for Webflux instrumentation. This handles creating and finishing
 * Webflux span.
 */
public class DispatcherHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope methodEnter(@Advice.Argument(0) final ServerWebExchange exchange) {
    // Unfortunately Netty EventLoop is not instrumented well enough to attribute all work to the
    // right things so we have to store span in request itself. We also store parent (netty's) span
    // so we could update resource name.
    final Span parentSpan = TRACER.getCurrentSpan();
    if (parentSpan.getContext().isValid()) {
      exchange.getAttributes().put(AdviceUtils.PARENT_SPAN_ATTRIBUTE, parentSpan);
    }

    final Span span = TRACER.spanBuilder("DispatcherHandler.handle").startSpan();
    DECORATE.afterStart(span);
    exchange.getAttributes().put(AdviceUtils.SPAN_ATTRIBUTE, span);

    return new SpanWithScope(span, currentContextWith(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Argument(0) final ServerWebExchange exchange,
      @Advice.Return(readOnly = false) Mono<Void> mono) {
    if (throwable == null && mono != null) {
      mono = AdviceUtils.setPublisherSpan(mono, spanWithScope.getSpan());
    } else if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    spanWithScope.closeScope();
  }
}
