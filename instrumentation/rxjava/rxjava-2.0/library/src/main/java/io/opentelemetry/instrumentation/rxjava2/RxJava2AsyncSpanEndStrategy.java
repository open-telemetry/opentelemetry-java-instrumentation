/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.parallel.ParallelFlowable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;

public final class RxJava2AsyncSpanEndStrategy implements AsyncSpanEndStrategy {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("rxjava.canceled");

  public static RxJava2AsyncSpanEndStrategy create() {
    return newBuilder().build();
  }

  public static RxJava2AsyncSpanEndStrategyBuilder newBuilder() {
    return new RxJava2AsyncSpanEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  RxJava2AsyncSpanEndStrategy(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public boolean supports(Class<?> returnType) {
    return returnType == Publisher.class
        || returnType == Completable.class
        || returnType == Maybe.class
        || returnType == Single.class
        || returnType == Observable.class
        || returnType == Flowable.class
        || returnType == ParallelFlowable.class;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {

    EndOnFirstNotificationConsumer<?> notificationConsumer =
        new EndOnFirstNotificationConsumer<>(tracer, context);
    if (returnValue instanceof Completable) {
      return endWhenComplete((Completable) returnValue, notificationConsumer);
    } else if (returnValue instanceof Maybe) {
      return endWhenMaybeComplete((Maybe<?>) returnValue, notificationConsumer);
    } else if (returnValue instanceof Single) {
      return endWhenSingleComplete((Single<?>) returnValue, notificationConsumer);
    } else if (returnValue instanceof Observable) {
      return endWhenObservableComplete((Observable<?>) returnValue, notificationConsumer);
    } else if (returnValue instanceof ParallelFlowable) {
      return endWhenFirstComplete((ParallelFlowable<?>) returnValue, notificationConsumer);
    }
    return endWhenPublisherComplete((Publisher<?>) returnValue, notificationConsumer);
  }

  private static Completable endWhenComplete(
      Completable completable, EndOnFirstNotificationConsumer<?> notificationConsumer) {
    return completable
        .doOnEvent(notificationConsumer)
        .doOnDispose(notificationConsumer::onCancelOrDispose);
  }

  private static <T> Maybe<T> endWhenMaybeComplete(
      Maybe<T> maybe, EndOnFirstNotificationConsumer<?> notificationConsumer) {
    @SuppressWarnings("unchecked")
    EndOnFirstNotificationConsumer<T> typedConsumer =
        (EndOnFirstNotificationConsumer<T>) notificationConsumer;
    return maybe.doOnEvent(typedConsumer).doOnDispose(notificationConsumer::onCancelOrDispose);
  }

  private static <T> Single<T> endWhenSingleComplete(
      Single<T> single, EndOnFirstNotificationConsumer<?> notificationConsumer) {
    @SuppressWarnings("unchecked")
    EndOnFirstNotificationConsumer<T> typedConsumer =
        (EndOnFirstNotificationConsumer<T>) notificationConsumer;
    return single.doOnEvent(typedConsumer).doOnDispose(notificationConsumer::onCancelOrDispose);
  }

  private static Observable<?> endWhenObservableComplete(
      Observable<?> observable, EndOnFirstNotificationConsumer<?> notificationConsumer) {
    return observable
        .doOnComplete(notificationConsumer)
        .doOnError(notificationConsumer)
        .doOnDispose(notificationConsumer::onCancelOrDispose);
  }

  private static ParallelFlowable<?> endWhenFirstComplete(
      ParallelFlowable<?> parallelFlowable,
      EndOnFirstNotificationConsumer<?> notificationConsumer) {
    return parallelFlowable
        .doOnComplete(notificationConsumer)
        .doOnError(notificationConsumer)
        .doOnCancel(notificationConsumer::onCancelOrDispose);
  }

  private static Flowable<?> endWhenPublisherComplete(
      Publisher<?> publisher, EndOnFirstNotificationConsumer<?> notificationConsumer) {
    return Flowable.fromPublisher(publisher)
        .doOnComplete(notificationConsumer)
        .doOnError(notificationConsumer)
        .doOnCancel(notificationConsumer::onCancelOrDispose);
  }

  /**
   * Helper class to ensure that the span is ended exactly once regardless of how many OnComplete or
   * OnError notifications are received. Multiple notifications can happen anytime multiple
   * subscribers subscribe to the same publisher.
   */
  private final class EndOnFirstNotificationConsumer<T> extends AtomicBoolean
      implements Action, Consumer<Throwable>, BiConsumer<T, Throwable> {

    private final BaseTracer tracer;
    private final Context context;

    public EndOnFirstNotificationConsumer(BaseTracer tracer, Context context) {
      super(false);
      this.tracer = tracer;
      this.context = context;
    }

    @Override
    public void run() {
      accept(null);
    }

    public void onCancelOrDispose() {
      if (compareAndSet(false, true)) {
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
        }
        tracer.end(context);
      }
    }

    @Override
    public void accept(Throwable exception) {
      if (compareAndSet(false, true)) {
        if (exception != null) {
          tracer.endExceptionally(context, exception);
        } else {
          tracer.end(context);
        }
      }
    }

    @Override
    public void accept(T value, Throwable exception) {
      accept(exception);
    }
  }
}
