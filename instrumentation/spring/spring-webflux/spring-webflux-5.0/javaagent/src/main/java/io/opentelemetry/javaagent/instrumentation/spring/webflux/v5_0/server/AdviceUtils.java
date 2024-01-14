/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.WebfluxSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public final class AdviceUtils {

  public static final String ON_SPAN_END = AdviceUtils.class.getName() + ".OnSpanEnd";
  public static final String CONTEXT = AdviceUtils.class.getName() + ".Context";

  public static void registerOnSpanEnd(
      ServerWebExchange exchange, Context context, Object handler) {
    exchange
        .getAttributes()
        .put(
            AdviceUtils.ON_SPAN_END,
            (AdviceUtils.OnSpanEnd) t -> instrumenter().end(context, handler, null, t));
  }

  public static <T> Mono<T> end(Mono<T> mono, ServerWebExchange exchange) {
    return mono.doOnError(throwable -> end(exchange, throwable))
        .doOnSuccess(t -> end(exchange, null))
        .doOnCancel(() -> end(exchange, null));
  }

  private static void end(ServerWebExchange exchange, @Nullable Throwable throwable) {
    OnSpanEnd onSpanEnd = (OnSpanEnd) exchange.getAttributes().get(AdviceUtils.ON_SPAN_END);
    if (onSpanEnd != null) {
      onSpanEnd.end(throwable);
    }
  }

  public static <T> Mono<T> wrapMono(Mono<T> mono, Context context) {
    if (context == null) {
      return mono;
    }
    return new ContextMono<>(mono, context);
  }

  @FunctionalInterface
  interface OnSpanEnd {
    void end(Throwable throwable);
  }

  private static class ContextMono<T> extends Mono<T> {

    private final Mono<T> delegate;
    private final Context parentContext;

    ContextMono(Mono<T> delegate, Context parentContext) {
      this.delegate = delegate;
      this.parentContext = parentContext;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.subscribe(actual);
      }
    }
  }

  private AdviceUtils() {}
}
