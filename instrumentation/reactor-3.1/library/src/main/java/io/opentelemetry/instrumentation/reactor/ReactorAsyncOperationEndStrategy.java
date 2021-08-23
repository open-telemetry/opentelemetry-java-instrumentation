/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport.tryToGetResponse;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Objects;
import java.util.function.BiFunction;
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

  private final ReactorAsyncOperationOptions options;

  ReactorAsyncOperationEndStrategy(ReactorAsyncOperationOptions options) {
    this.options = options;
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

    if (asyncValue instanceof Mono) {
      Mono<?> mono = (Mono<?>) asyncValue;
      return instrumentMono(mono, instrumenter, context, request, responseType);
    } else {
      Flux<?> flux = Flux.from((Publisher<?>) asyncValue);
      return instrumentFlux(flux, instrumenter, context, request, responseType);
    }
  }

  private <RESPONSE, REQUEST, T> Mono<T> instrumentMono(
      Mono<T> mono,
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Class<RESPONSE> responseType) {
    Mono<T> withCheckpoint = checkpoint(mono, context, Mono::checkpoint);
    if (tryEndSynchronously(mono, instrumenter, context, request, responseType)) {
      return withCheckpoint;
    }
    return withCheckpoint.transform(
        InstrumentedOperator.<REQUEST, RESPONSE, T>instrumentedLift(
            instrumenter, context, request, responseType, options));
  }

  private <RESPONSE, REQUEST, T> Flux<T> instrumentFlux(
      Flux<T> flux,
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Class<RESPONSE> responseType) {
    Flux<T> withCheckpoint = checkpoint(flux, context, Flux::checkpoint);
    if (tryEndSynchronously(flux, instrumenter, context, request, responseType)) {
      return withCheckpoint;
    }
    return withCheckpoint.transform(
        InstrumentedOperator.<REQUEST, RESPONSE, T>instrumentedLift(
            instrumenter, context, request, responseType, options));
  }

  private <T, P extends Publisher<T>> P checkpoint(
      P publisher, Context context, BiFunction<P, String, P> checkpoint) {
    if (options.emitCheckpoints()) {
      Span currentSpan = Span.fromContextOrNull(context);
      if (currentSpan != null) {
        return checkpoint.apply(publisher, Objects.toString(currentSpan));
      }
    }
    return publisher;
  }

  private static <REQUEST, RESPONSE> boolean tryEndSynchronously(
      Publisher<?> publisher,
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Class<RESPONSE> responseType) {

    if (publisher instanceof Fuseable.ScalarCallable) {
      Fuseable.ScalarCallable<?> scalarCallable = (Fuseable.ScalarCallable<?>) publisher;
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
