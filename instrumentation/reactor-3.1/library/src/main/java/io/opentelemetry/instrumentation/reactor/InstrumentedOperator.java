/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Operators;

final class InstrumentedOperator<REQUEST, RESPONSE, T>
    implements BiFunction<Scannable, CoreSubscriber<? super T>, CoreSubscriber<? super T>> {

  private final Instrumenter<REQUEST, RESPONSE> instrumenter;
  private final Context context;
  private final REQUEST request;
  private final Class<RESPONSE> responseType;
  private final ReactorAsyncOperationOptions options;
  private final AtomicBoolean firstSubscriber = new AtomicBoolean(true);

  static <REQUEST, RESPONSE, T>
      Function<? super Publisher<T>, ? extends Publisher<T>> instrumentedLift(
          Instrumenter<REQUEST, RESPONSE> instrumenter,
          Context context,
          REQUEST request,
          Class<RESPONSE> responseType,
          ReactorAsyncOperationOptions options) {

    return Operators.lift(
        new InstrumentedOperator<>(instrumenter, context, request, responseType, options));
  }

  private InstrumentedOperator(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Class<RESPONSE> responseType,
      ReactorAsyncOperationOptions options) {
    this.instrumenter = instrumenter;
    this.context = context;
    this.request = request;
    this.responseType = responseType;
    this.options = options;
  }

  @Override
  public CoreSubscriber<? super T> apply(
      Scannable scannable, CoreSubscriber<? super T> coreSubscriber) {

    if (isFirstSubscriber()) {
      return new InstrumentedSubscriber<>(
          instrumenter, context, request, responseType, options, coreSubscriber);
    }

    if (options.traceMultipleSubscribers()) {
      Context parentContext = Context.current();
      if (instrumenter.shouldStart(parentContext, request)) {
        Context context = instrumenter.start(parentContext, request);
        return new InstrumentedSubscriber<>(
            instrumenter, context, request, responseType, options, coreSubscriber);
      }
    }
    return coreSubscriber;
  }

  private boolean isFirstSubscriber() {
    return firstSubscriber.compareAndSet(true, false);
  }
}
