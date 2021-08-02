/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport.tryToGetResponse;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.reactivestreams.Publisher;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ReactorAsyncOperationEndStrategy implements AsyncOperationEndStrategy {
  public static ReactorAsyncOperationEndStrategy create() {
    return newBuilder().build();
  }

  public static ReactorAsyncOperationEndStrategyBuilder newBuilder() {
    return new ReactorAsyncOperationEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  ReactorAsyncOperationEndStrategy(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public boolean supports(Class<?> returnType) {
    return returnType == Publisher.class || returnType == Mono.class || returnType == Flux.class;
  }

  @Override
  public <REQUEST, RESPONSE> Object end(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Object asyncValue,
      Class<RESPONSE> responseType) {

    if (tryEndSynchronously(instrumenter, context, request, asyncValue, responseType)) {
      return asyncValue;
    }

    if (asyncValue instanceof Mono) {
      Mono<?> mono = (Mono<?>) asyncValue;
      return InstrumentedOperator.transformMono(
          mono, instrumenter, context, request, responseType, captureExperimentalSpanAttributes);
    } else {
      Flux<?> flux = Flux.from((Publisher<?>) asyncValue);
      return InstrumentedOperator.transformFlux(
          flux, instrumenter, context, request, responseType, captureExperimentalSpanAttributes);
    }
  }

  private static <REQUEST, RESPONSE> boolean tryEndSynchronously(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Object asyncValue,
      Class<RESPONSE> responseType) {

    if (asyncValue instanceof Fuseable.ScalarCallable) {
      Fuseable.ScalarCallable<?> scalarCallable = (Fuseable.ScalarCallable<?>) asyncValue;
      try {
        Object result = scalarCallable.call();
        instrumenter.end(context, request, tryToGetResponse(responseType, result), null);
      } catch (Throwable error) {
        instrumenter.end(context, request, null, error);
      }
      return true;
    }
    return false;
  }
}
