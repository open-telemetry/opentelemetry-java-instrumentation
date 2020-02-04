package io.opentelemetry.auto.instrumentation.springwebflux.server;

import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.TRACER;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.reactor.core.ReactorCoreAdviceUtils;
import io.opentelemetry.trace.Span;
import java.util.function.Function;
import net.bytebuddy.asm.Advice;
import org.reactivestreams.Publisher;
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

    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Argument(0) final ServerWebExchange exchange,
      @Advice.Return(readOnly = false) Mono<Object> mono) {
    if (throwable == null && mono != null) {
      final Function<? super Mono<Object>, ? extends Publisher<Object>> function =
          ReactorCoreAdviceUtils.finishSpanNextOrError();
      mono = ReactorCoreAdviceUtils.setPublisherSpan(mono, spanWithScope.getSpan());
    } else if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    if (spanWithScope != null) {
      spanWithScope.closeScope();
    }
  }
}
