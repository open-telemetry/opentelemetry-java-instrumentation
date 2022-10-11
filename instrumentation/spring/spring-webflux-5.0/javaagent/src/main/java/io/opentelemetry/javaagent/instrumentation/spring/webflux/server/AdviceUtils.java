/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.server.WebfluxSingletons.instrumenter;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class AdviceUtils {

  public static final String ON_SPAN_END = AdviceUtils.class.getName() + ".Context";

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

  @FunctionalInterface
  interface OnSpanEnd {
    void end(Throwable throwable);
  }

  private AdviceUtils() {}
}
