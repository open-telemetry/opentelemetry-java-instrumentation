/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.parallel.ParallelFlowable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;

public enum RxJava2AsyncSpanEndStrategy implements AsyncSpanEndStrategy {
  INSTANCE;

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
    if (returnValue instanceof Completable) {
      return endWhenComplete(tracer, context, (Completable) returnValue);
    } else if (returnValue instanceof Maybe) {
      return endWhenMaybeComplete(tracer, context, (Maybe<?>) returnValue);
    } else if (returnValue instanceof Single) {
      return endWhenSingleComplete(tracer, context, (Single<?>) returnValue);
    } else if (returnValue instanceof Observable) {
      return endWhenObservableComplete(tracer, context, (Observable<?>) returnValue);
    } else if (returnValue instanceof ParallelFlowable) {
      return endWhenFirstComplete(tracer, context, (ParallelFlowable<?>) returnValue);
    }
    return endWhenPublisherComplete(tracer, context, (Publisher<?>) returnValue);
  }

  private Completable endWhenComplete(BaseTracer tracer, Context context, Completable completable) {
    return completable
        .doOnComplete(() -> tracer.end(context))
        .doOnError(exception -> tracer.endExceptionally(context, exception));
  }

  private Maybe<?> endWhenMaybeComplete(BaseTracer tracer, Context context, Maybe<?> maybe) {
    return maybe
        .doOnComplete(() -> tracer.end(context))
        .doOnSuccess(ignored -> tracer.end(context))
        .doOnError(exception -> tracer.endExceptionally(context, exception));
  }

  private Single<?> endWhenSingleComplete(BaseTracer tracer, Context context, Single<?> single) {
    return single
        .doOnSuccess(ignored -> tracer.end(context))
        .doOnError(exception -> tracer.endExceptionally(context, exception));
  }

  private Observable<?> endWhenObservableComplete(
      BaseTracer tracer, Context context, Observable<?> observable) {
    return observable
        .doOnComplete(() -> tracer.end(context))
        .doOnError(exception -> tracer.endExceptionally(context, exception));
  }

  /**
   * The {@link ParallelFlowable} class makes multiple parallel subscriptions which results in the
   * OnComplete and OnError signals being received multiple times, once for each subscriber. The
   * use of {@link AtomicBoolean} is to ensure that the span is only ended once for the first
   * signal.
   */
  private ParallelFlowable<?> endWhenFirstComplete(
      BaseTracer tracer, Context context, ParallelFlowable<?> parallelFlowable) {
    AtomicBoolean done = new AtomicBoolean(false);
    return parallelFlowable
        .doOnComplete(
            () -> {
              if (done.compareAndSet(false, true)) {
                tracer.end(context);
              }
            })
        .doOnError(
            exception -> {
              if (done.compareAndSet(false, true)) {
                tracer.endExceptionally(context, exception);
              }
            });
  }

  private Flowable<?> endWhenPublisherComplete(
      BaseTracer tracer, Context context, Publisher<?> publisher) {

    Flowable<?> flowable;
    if (publisher instanceof Flowable) {
      flowable = (Flowable<?>) publisher;
    } else {
      flowable = Flowable.fromPublisher(publisher);
    }
    return flowable
        .doOnComplete(() -> tracer.end(context))
        .doOnError(exception -> tracer.endExceptionally(context, exception));
  }
}
