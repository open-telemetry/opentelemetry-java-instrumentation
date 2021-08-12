/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ClassNames;
import java.util.Map;
import org.springframework.web.reactive.function.server.ServerRequest;
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

  public static <T> Mono<T> setPublisherSpan(Mono<T> mono, Context context) {
    return mono.doOnError(t -> finishSpanIfPresent(context, t))
        .doOnSuccess(x -> finishSpanIfPresent(context, null))
        .doOnCancel(() -> finishSpanIfPresent(context, null));
  }

  public static void finishSpanIfPresent(ServerWebExchange exchange, Throwable throwable) {
    if (exchange != null) {
      finishSpanIfPresentInAttributes(exchange.getAttributes(), throwable);
    }
  }

  public static void finishSpanIfPresent(ServerRequest serverRequest, Throwable throwable) {
    if (serverRequest != null) {
      finishSpanIfPresentInAttributes(serverRequest.attributes(), throwable);
    }
  }

  static void finishSpanIfPresent(Context context, Throwable throwable) {
    if (context != null) {
      Span span = Span.fromContext(context);
      if (throwable != null) {
        span.setStatus(StatusCode.ERROR);
        span.recordException(throwable);
      }
      span.end();
    }
  }

  private static void finishSpanIfPresentInAttributes(
      Map<String, Object> attributes, Throwable throwable) {
    Context context = (Context) attributes.remove(CONTEXT_ATTRIBUTE);
    finishSpanIfPresent(context, throwable);
  }
}
