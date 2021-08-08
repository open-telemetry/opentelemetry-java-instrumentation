/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.server.WebfluxSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ClassNames;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class AdviceUtils {

  public static final String CONTEXT_ATTRIBUTE = AdviceUtils.class.getName() + ".Context";

  public static String spanNameForHandler(Object handler) {
    String className = ClassNames.simpleName(handler.getClass());
    int lambdaIdx = className.indexOf("$$Lambda$");

    if (lambdaIdx > -1) {
      return className.substring(0, lambdaIdx) + ".lambda";
    }
    return className + ".handle";
  }

  public static <T> Mono<T> end(Mono<T> mono, ServerWebExchange exchange) {
    return mono.doOnError(throwable -> end(exchange, throwable))
        .doOnSuccess(t -> end(exchange, null))
        .doOnCancel(() -> end(exchange, null));
  }

  private static void end(ServerWebExchange exchange, @Nullable Throwable throwable) {
    Context context = (Context) exchange.getAttributes().get(AdviceUtils.CONTEXT_ATTRIBUTE);
    if (context != null) {
      instrumenter().end(context, null, null, throwable);
    }
  }
}
