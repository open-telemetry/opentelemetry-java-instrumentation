/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;
import rx.subscriptions.Subscriptions;

@ExtendWith(MockitoExtension.class)
class TracedOnSubscribeTest {
  private static final ContextKey<String> CONTEXT_KEY = ContextKey.named("test-context");
  @Mock private Instrumenter<String, Void> instrumenter;
  @Mock private Observer<String> delegate;

  @ParameterizedTest
  @ValueSource(longs = {0, 1})
  void propagatesBoundedDemand(long initialRequest) {
    Context context = Context.root().with(CONTEXT_KEY, "operation");
    when(instrumenter.start(Context.root(), "request")).thenReturn(context);
    List<Long> requests = new ArrayList<>();
    Observable<Integer> source = Observable.range(1, 3).doOnRequest(requests::add);
    Observable<Integer> result =
        Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    TestSubscriber<Integer> observer =
        new TestSubscriber<Integer>() {
          @Override
          public void onNext(Integer value) {
            assertThat(Context.current()).isSameAs(context);
            super.onNext(value);
          }

          @Override
          public void onCompleted() {
            assertThat(Context.current()).isSameAs(context);
            super.onCompleted();
          }
        };
    observer.requestMore(initialRequest);

    result.subscribe(observer);

    assertThat(requests).containsExactly(initialRequest);
    assertThat(observer.getOnNextEvents()).hasSize((int) initialRequest);
    assertThat(observer.getOnCompletedEvents()).isEmpty();
    verify(instrumenter).start(Context.root(), "request");
    verifyNoMoreInteractions(instrumenter);

    observer.requestMore(1);

    assertThat(requests).containsExactly(initialRequest, 1L);
    assertThat(observer.getOnNextEvents()).hasSize((int) initialRequest + 1);
    assertThat(observer.getOnCompletedEvents()).isEmpty();
    verifyNoMoreInteractions(instrumenter);

    observer.requestMore(2 - initialRequest);

    assertThat(requests).containsExactly(initialRequest, 1L, 2 - initialRequest);
    assertThat(observer.getOnNextEvents()).containsExactly(1, 2, 3);
    assertThat(observer.getOnCompletedEvents()).hasSize(1);
    observer.assertNoErrors();
    observer.unsubscribe();
    assertThat(Context.current()).isSameAs(Context.root());
    verify(instrumenter).end(context, "request", null, null);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void propagatesCancellationToSource() {
    when(instrumenter.start(Context.root(), "request")).thenReturn(Context.root());
    AtomicReference<Subscriber<? super String>> sourceSubscriber = new AtomicReference<>();
    AtomicInteger cancellations = new AtomicInteger();
    Observable<String> source =
        Observable.create(
            subscriber -> {
              sourceSubscriber.set(subscriber);
              subscriber.add(Subscriptions.create(cancellations::incrementAndGet));
            });
    Observable<String> result =
        Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    TestSubscriber<String> observer = new TestSubscriber<>();
    result.subscribe(observer);

    assertThat(sourceSubscriber.get().isUnsubscribed()).isFalse();
    assertThat(cancellations.get()).isZero();
    verify(instrumenter).start(Context.root(), "request");
    verifyNoMoreInteractions(instrumenter);

    observer.unsubscribe();
    observer.unsubscribe();

    assertThat(sourceSubscriber.get().isUnsubscribed()).isTrue();
    assertThat(cancellations.get()).isEqualTo(1);
    assertThat(observer.getOnCompletedEvents()).isEmpty();
    observer.assertNoErrors();
    verify(instrumenter).end(Context.root(), "request", null, null);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void endsSpanOnAlreadyCompleted() {
    when(instrumenter.start(Context.root(), "request")).thenReturn(Context.root());

    Observable<String> result =
        Observable.create(
            new TracedOnSubscribe<>(Observable.just("response"), instrumenter, "request"));
    TestSubscriber<String> observer = new TestSubscriber<>();
    result.subscribe(observer);

    verify(instrumenter).end(Context.root(), "request", null, null);
    assertThat(observer.getOnCompletedEvents()).hasSize(1);
    observer.assertNoErrors();
  }

  @Test
  void endsSpanOnAlreadyErrored() {
    IllegalStateException exception = new IllegalStateException();
    when(instrumenter.start(Context.root(), "request")).thenReturn(Context.root());

    Observable<String> result =
        Observable.create(
            new TracedOnSubscribe<>(Observable.error(exception), instrumenter, "request"));
    TestSubscriber<String> observer = new TestSubscriber<>();
    result.subscribe(observer);

    verify(instrumenter).end(Context.root(), "request", null, exception);
    assertThat(observer.getOnErrorEvents()).containsExactly(exception);
  }

  @Test
  void endsSpanWhenCompleted() {
    ReplaySubject<String> source = ReplaySubject.create();
    when(instrumenter.start(Context.root(), "request")).thenReturn(Context.root());

    Observable<String> result =
        Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    TestSubscriber<String> observer = new TestSubscriber<>();
    result.subscribe(observer);

    verify(instrumenter).start(Context.root(), "request");
    verifyNoMoreInteractions(instrumenter);

    source.onCompleted();

    verify(instrumenter).end(Context.root(), "request", null, null);
    assertThat(observer.getOnCompletedEvents()).hasSize(1);
    observer.assertNoErrors();
  }

  @Test
  void endsSpanWhenErrored() {
    IllegalStateException exception = new IllegalStateException();
    ReplaySubject<String> source = ReplaySubject.create();
    when(instrumenter.start(Context.root(), "request")).thenReturn(Context.root());

    Observable<String> result =
        Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    TestSubscriber<String> observer = new TestSubscriber<>();
    result.subscribe(observer);

    verify(instrumenter).start(Context.root(), "request");
    verifyNoMoreInteractions(instrumenter);

    source.onError(exception);

    verify(instrumenter).end(Context.root(), "request", null, exception);
    assertThat(observer.getOnErrorEvents()).containsExactly(exception);
  }

  @Test
  void endsWhenCancelled() {
    ReplaySubject<String> source = ReplaySubject.create();
    when(instrumenter.start(Context.root(), "request")).thenReturn(Context.root());

    Observable<String> result =
        Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    TestSubscriber<String> observer = new TestSubscriber<>();
    result.subscribe(observer);

    verify(instrumenter).start(Context.root(), "request");
    verifyNoMoreInteractions(instrumenter);

    observer.unsubscribe();

    verify(instrumenter).end(Context.root(), "request", null, null);
  }

  @Test
  void endsSpanForEachSubscriber() {
    ReplaySubject<String> source = ReplaySubject.create();
    when(instrumenter.start(Context.root(), "request")).thenReturn(Context.root());

    Observable<String> result =
        Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    TestSubscriber<String> observer1 = new TestSubscriber<>();
    TestSubscriber<String> observer2 = new TestSubscriber<>();
    TestSubscriber<String> observer3 = new TestSubscriber<>();
    result.subscribe(observer1);
    result.subscribe(observer2);
    result.subscribe(observer3);

    verify(instrumenter, times(3)).start(Context.root(), "request");
    verifyNoMoreInteractions(instrumenter);

    source.onCompleted();

    assertThat(observer1.getOnCompletedEvents()).hasSize(1);
    assertThat(observer2.getOnCompletedEvents()).hasSize(1);
    assertThat(observer3.getOnCompletedEvents()).hasSize(1);
    observer1.assertNoErrors();
    observer2.assertNoErrors();
    observer3.assertNoErrors();
    verify(instrumenter, times(3)).end(Context.root(), "request", null, null);
  }

  @Test
  void restoresContextOnAlreadyErrored() {
    IllegalStateException exception = new IllegalStateException();
    Context parentContext = Context.root().with(CONTEXT_KEY, "parent");
    Context context = parentContext.with(CONTEXT_KEY, "operation");
    Context emitterContext = Context.root().with(CONTEXT_KEY, "emitter");
    when(instrumenter.start(parentContext, "request")).thenReturn(context);
    doAnswer(
            invocation -> {
              assertThat(Context.current()).isSameAs(context);
              verify(instrumenter).end(context, "request", null, exception);
              return null;
            })
        .when(delegate)
        .onError(exception);

    Observable<String> result;
    try (Scope ignored = parentContext.makeCurrent()) {
      result =
          Observable.create(
              new TracedOnSubscribe<>(Observable.error(exception), instrumenter, "request"));
    }
    TestSubscriber<String> observer = new TestSubscriber<>(delegate);
    try (Scope ignored = emitterContext.makeCurrent()) {
      result.subscribe(observer);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(observer.getOnErrorEvents()).containsExactly(exception);
    verify(instrumenter).start(parentContext, "request");
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void restoresContextWhenErrored() {
    IllegalStateException exception = new IllegalStateException();
    ReplaySubject<String> source = ReplaySubject.create();
    Context parentContext = Context.root().with(CONTEXT_KEY, "parent");
    Context context = parentContext.with(CONTEXT_KEY, "operation");
    Context emitterContext = Context.root().with(CONTEXT_KEY, "emitter");
    when(instrumenter.start(parentContext, "request")).thenReturn(context);
    doAnswer(
            invocation -> {
              assertThat(Context.current()).isSameAs(context);
              verify(instrumenter).end(context, "request", null, exception);
              return null;
            })
        .when(delegate)
        .onError(exception);

    Observable<String> result;
    try (Scope ignored = parentContext.makeCurrent()) {
      result = Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    }
    TestSubscriber<String> observer = new TestSubscriber<>(delegate);
    result.subscribe(observer);

    verify(instrumenter).start(parentContext, "request");
    verifyNoMoreInteractions(instrumenter);

    try (Scope ignored = emitterContext.makeCurrent()) {
      source.onError(exception);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(observer.getOnErrorEvents()).containsExactly(exception);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void restoresParentContextWhenErroredAfterCancellation() {
    IllegalStateException exception = new IllegalStateException();
    AtomicReference<Subscriber<? super String>> sourceSubscriber = new AtomicReference<>();
    Observable<String> source = Observable.<String>create(sourceSubscriber::set);
    Context parentContext = Context.root().with(CONTEXT_KEY, "parent");
    Context context = parentContext.with(CONTEXT_KEY, "operation");
    Context emitterContext = Context.root().with(CONTEXT_KEY, "emitter");
    when(instrumenter.start(parentContext, "request")).thenReturn(context);
    doAnswer(
            invocation -> {
              assertThat(Context.current()).isSameAs(parentContext);
              return null;
            })
        .when(delegate)
        .onError(exception);

    Observable<String> result;
    try (Scope ignored = parentContext.makeCurrent()) {
      result = Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    }
    TestSubscriber<String> observer = new TestSubscriber<>(delegate);
    result.unsafeSubscribe(observer);

    verify(instrumenter).start(parentContext, "request");
    verifyNoMoreInteractions(instrumenter);

    observer.unsubscribe();
    observer.unsubscribe();

    try (Scope ignored = emitterContext.makeCurrent()) {
      sourceSubscriber.get().onError(exception);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(observer.getOnErrorEvents()).containsExactly(exception);
    verify(instrumenter).end(context, "request", null, null);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void restoresContextWhenErrorCallbackThrows() {
    IllegalStateException exception = new IllegalStateException();
    IllegalArgumentException callbackException = new IllegalArgumentException();
    ReplaySubject<String> source = ReplaySubject.create();
    Context parentContext = Context.root().with(CONTEXT_KEY, "parent");
    Context context = parentContext.with(CONTEXT_KEY, "operation");
    Context emitterContext = Context.root().with(CONTEXT_KEY, "emitter");
    when(instrumenter.start(parentContext, "request")).thenReturn(context);
    doAnswer(
            invocation -> {
              assertThat(Context.current()).isSameAs(context);
              verify(instrumenter).end(context, "request", null, exception);
              throw callbackException;
            })
        .when(delegate)
        .onError(exception);

    Observable<String> result;
    try (Scope ignored = parentContext.makeCurrent()) {
      result = Observable.create(new TracedOnSubscribe<>(source, instrumenter, "request"));
    }
    TestSubscriber<String> observer = new TestSubscriber<>(delegate);
    result.unsafeSubscribe(observer);

    verify(instrumenter).start(parentContext, "request");
    verifyNoMoreInteractions(instrumenter);

    try (Scope ignored = emitterContext.makeCurrent()) {
      assertThatThrownBy(() -> source.onError(exception)).isSameAs(callbackException);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    verifyNoMoreInteractions(instrumenter);
  }
}
