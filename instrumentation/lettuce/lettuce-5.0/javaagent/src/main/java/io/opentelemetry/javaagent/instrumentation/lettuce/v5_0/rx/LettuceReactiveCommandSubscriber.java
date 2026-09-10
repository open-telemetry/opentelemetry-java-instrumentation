/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

public final class LettuceReactiveCommandSubscriber<T> implements CoreSubscriber<T> {

  private final CoreSubscriber<? super T> actual;
  private final LettuceReactiveCommandHandler handler;
  private final boolean cancelHandlerOnSubscribe;

  LettuceReactiveCommandSubscriber(
      CoreSubscriber<? super T> actual, LettuceReactiveCommandHandler handler) {
    this(actual, handler, false);
  }

  private LettuceReactiveCommandSubscriber(
      CoreSubscriber<? super T> actual,
      LettuceReactiveCommandHandler handler,
      boolean cancelHandlerOnSubscribe) {
    this.actual = actual;
    this.handler = handler;
    this.cancelHandlerOnSubscribe = cancelHandlerOnSubscribe;
  }

  public static Subscriber<?> withCancellation(
      CoreSubscriber<?> actual, LettuceReactiveCommandHandler handler) {
    return new LettuceReactiveCommandSubscriber<>(actual, handler, true);
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    if (cancelHandlerOnSubscribe) {
      Subscription upstream = subscription;
      subscription =
          new Subscription() {
            @Override
            public void request(long n) {
              upstream.request(n);
            }

            @Override
            public void cancel() {
              try {
                handler.onCancel();
              } catch (Throwable t) {
                Exceptions.throwIfFatal(t);
                Operators.onErrorDropped(t, LettuceReactiveCommandSubscriber.this.currentContext());
              } finally {
                upstream.cancel();
              }
            }
          };
    }
    actual.onSubscribe(subscription);
  }

  @Override
  public void onNext(T value) {
    actual.onNext(value);
  }

  @Override
  public void onError(Throwable throwable) {
    actual.onError(throwable);
  }

  @Override
  public void onComplete() {
    actual.onComplete();
  }

  @Override
  public Context currentContext() {
    return actual.currentContext().put(LettuceReactiveCommandContext.HANDLER_KEY, handler);
  }
}
