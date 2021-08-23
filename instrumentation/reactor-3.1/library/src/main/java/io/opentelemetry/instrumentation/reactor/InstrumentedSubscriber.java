/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport.tryToGetResponse;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;

final class InstrumentedSubscriber<REQUEST, RESPONSE, T>
    implements CoreSubscriber<T>, Subscription {

  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("reactor.canceled");

  private final Instrumenter<REQUEST, RESPONSE> instrumenter;
  private final Context context;
  private final REQUEST request;
  private final Class<RESPONSE> responseType;
  private final ReactorAsyncOperationOptions options;
  private final CoreSubscriber<T> actual;
  private Subscription subscription;
  private T value;

  InstrumentedSubscriber(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Class<RESPONSE> responseType,
      ReactorAsyncOperationOptions options,
      CoreSubscriber<T> actual) {

    this.instrumenter = instrumenter;
    this.context = context;
    this.request = request;
    this.responseType = responseType;
    this.options = options;
    this.actual = actual;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    if (Operators.validate(this.subscription, subscription)) {
      this.subscription = subscription;
      actual.onSubscribe(this);
    }
  }

  @Override
  public void request(long count) {
    if (subscription != null) {
      subscription.request(count);
    }
  }

  @Override
  public void cancel() {
    if (subscription != null) {
      if (options.captureExperimentalSpanAttributes()) {
        Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      }
      instrumenter.end(context, request, null, null);
      subscription.cancel();
    }
  }

  @Override
  public void onNext(T value) {
    this.value = value;
    actual.onNext(value);
  }

  @Override
  public void onError(Throwable error) {
    instrumenter.end(context, request, null, error);
    actual.onError(error);
  }

  @Override
  public void onComplete() {
    instrumenter.end(context, request, tryToGetResponse(responseType, value), null);
    actual.onComplete();
  }

  @Override
  public reactor.util.context.Context currentContext() {
    return actual.currentContext();
  }
}
