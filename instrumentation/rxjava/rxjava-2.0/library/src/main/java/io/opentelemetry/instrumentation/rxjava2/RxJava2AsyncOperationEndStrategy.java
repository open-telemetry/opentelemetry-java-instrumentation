/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.reactivestreams.Publisher;

public final class RxJava2AsyncOperationEndStrategy
    implements AsyncOperationEndStrategy, AsyncSpanEndStrategy {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("rxjava.canceled");

  public static RxJava2AsyncOperationEndStrategy create() {
    return newBuilder().build();
  }

  public static RxJava2AsyncOperationEndStrategyBuilder newBuilder() {
    return new RxJava2AsyncOperationEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  RxJava2AsyncOperationEndStrategy(boolean captureExperimentalSpanAttributes) {
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
  public <REQUEST, RESPONSE> Object end(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Object asyncValue,
      Class<RESPONSE> responseType) {

    return end(
        asyncValue,
        new EndOnFirstNotificationConsumer<Object>(context) {
          @Override
          protected void end(Object response, Throwable error) {
            instrumenter.end(context, request, tryToGetResponse(responseType, response), error);
          }
        });
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {
    return end(
        returnValue,
        new EndOnFirstNotificationConsumer<Object>(context) {
          @Override
          protected void end(Object response, Throwable error) {
            if (error != null) {
              tracer.endExceptionally(context, error);
            } else {
              tracer.end(context);
            }
          }
        });
  }

  private static <T> Object end(
      Object asyncValue, EndOnFirstNotificationConsumer<T> notificationConsumer) {
    if (asyncValue instanceof Completable) {
      return endWhenComplete((Completable) asyncValue, notificationConsumer);
    } else if (asyncValue instanceof Maybe) {
      return endWhenMaybeComplete((Maybe<?>) asyncValue, notificationConsumer);
    } else if (asyncValue instanceof Single) {
      return endWhenSingleComplete((Single<?>) asyncValue, notificationConsumer);
    } else if (asyncValue instanceof Observable) {
      return endWhenObservableComplete((Observable<?>) asyncValue, notificationConsumer);
    } else if (asyncValue instanceof ParallelFlowable) {
      return endWhenFirstComplete((ParallelFlowable<?>) asyncValue, notificationConsumer);
    }
    return endWhenPublisherComplete((Publisher<?>) asyncValue, notificationConsumer);
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

  @Nullable
  private static <RESPONSE> RESPONSE tryToGetResponse(Class<RESPONSE> responseType, Object result) {
    if (responseType.isInstance(result)) {
      return responseType.cast(result);
    }
    return null;
  }

  /**
   * Helper class to ensure that the span is ended exactly once regardless of how many OnComplete or
   * OnError notifications are received. Multiple notifications can happen anytime multiple
   * subscribers subscribe to the same publisher.
   */
  private abstract class EndOnFirstNotificationConsumer<T> extends AtomicBoolean
      implements Action, Consumer<Throwable>, BiConsumer<T, Throwable> {

    private final Context context;

    protected EndOnFirstNotificationConsumer(Context context) {
      this.context = context;
    }

    @Override
    public void run() {
      accept(null, null);
    }

    public void onCancelOrDispose() {
      if (compareAndSet(false, true)) {
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
        }
        end(null, null);
      }
    }

    @Override
    public void accept(Throwable exception) {
      accept(null, exception);
    }

    @Override
    public void accept(T value, Throwable exception) {
      if (compareAndSet(false, true)) {
        end(value, exception);
      }
    }

    protected abstract void end(Object response, Throwable error);
  }
}
