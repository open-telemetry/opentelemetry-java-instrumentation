/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.server.WebfluxSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ClassNames;
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
    return mono.doOnError(throwable -> instrumenter().end(context, null, null, throwable))
        .doOnSuccess(t -> instrumenter().end(context, null, null, null))
        .doOnCancel(() -> instrumenter().end(context, null, null, null));
  }
}
