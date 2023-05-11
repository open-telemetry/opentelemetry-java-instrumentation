/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.rxjava.v3.common.RxJava3AsyncOperationEndStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.parallel.ParallelFlowable;
import io.reactivex.rxjava3.processors.ReplayProcessor;
import io.reactivex.rxjava3.processors.UnicastProcessor;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subjects.MaybeSubject;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.SingleSubject;
import io.reactivex.rxjava3.subjects.UnicastSubject;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@ExtendWith(MockitoExtension.class)
public class RxJava3AsyncOperationEndStrategyTest {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("rxjava.canceled");
  @Mock Instrumenter<String, String> instrumenter;
  @Mock Span span;
  private final AsyncOperationEndStrategy underTest = RxJava3AsyncOperationEndStrategy.create();
  private final RxJava3AsyncOperationEndStrategy underTestWithExperimentalAttributes =
      RxJava3AsyncOperationEndStrategy.builder().setCaptureExperimentalSpanAttributes(true).build();

  @Nested
  class CompletableTest {
    @Test
    void supported() {
      assertThat(underTest.supports(Completable.class)).isTrue();
    }

    @Test
    void endsSpanOnAlreadyCompleted() {
      Completable result =
          (Completable)
              underTest.end(
                  instrumenter, Context.root(), "request", Completable.complete(), String.class);
      TestObserver<Void> observer = result.test();

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanOnAlreadyErrored() {
      IllegalStateException exception = new IllegalStateException();

      Completable result =
          (Completable)
              underTest.end(
                  instrumenter,
                  Context.root(),
                  "request",
                  Completable.error(exception),
                  String.class);
      TestObserver<Void> observer = result.test();

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCompleted() {
      CompletableSubject source = CompletableSubject.create();

      Completable result =
          (Completable)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<Void> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onComplete();
      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanWhenErrored() {
      IllegalStateException exception = new IllegalStateException();
      CompletableSubject source = CompletableSubject.create();

      Completable result =
          (Completable)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<Void> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onError(exception);
      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsWhenCancelled() {
      when(span.storeInContext(any())).thenCallRealMethod();
      CompletableSubject source = CompletableSubject.create();
      Context context = Context.root().with(span);

      Completable result =
          (Completable) underTest.end(instrumenter, context, "request", source, String.class);
      TestObserver<Void> observer = result.test();

      verifyNoInteractions(instrumenter);

      observer.dispose();
      verify(instrumenter).end(context, "request", null, null);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
    }

    @Test
    void endsSpanWhenCancelledExperimentalAttribute() {
      when(span.storeInContext(any())).thenCallRealMethod();
      CompletableSubject source = CompletableSubject.create();
      Context context = Context.root().with(span);

      Completable result =
          (Completable)
              underTestWithExperimentalAttributes.end(
                  instrumenter, context, "request", source, String.class);
      TestObserver<Void> observer = result.test();

      verifyNoInteractions(instrumenter);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);

      observer.dispose();

      verify(instrumenter).end(context, "request", null, null);
      verify(span).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
    }

    @Test
    void endsSpanOnceForMultipleSubscribers() {
      CompletableSubject source = CompletableSubject.create();
      TestObserver<String> observer1 = new TestObserver<>();
      TestObserver<String> observer2 = new TestObserver<>();
      TestObserver<String> observer3 = new TestObserver<>();

      Completable result =
          (Completable)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      result.subscribe(observer1);
      result.subscribe(observer2);
      result.subscribe(observer3);

      verifyNoInteractions(instrumenter);

      source.onComplete();
      observer1.assertComplete();
      observer2.assertComplete();
      observer3.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }
  }

  @Nested
  class MaybeTest {
    @Test
    void supported() {
      assertThat(underTest.supports(Maybe.class)).isTrue();
    }

    @Test
    void endsSpanOnAlreadyCompleted() {
      Maybe<?> result =
          (Maybe<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", Maybe.just("response"), String.class);
      TestObserver<?> observer = result.test();

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", "response", null);
    }

    @Test
    void endsSpanOnAlreadyEmpty() {
      Maybe<?> result =
          (Maybe<?>)
              underTest.end(instrumenter, Context.root(), "request", Maybe.empty(), String.class);
      TestObserver<?> observer = result.test();

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanOnAlreadyErrored() {
      IllegalStateException exception = new IllegalStateException();

      Maybe<?> result =
          (Maybe<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", Maybe.error(exception), String.class);
      TestObserver<?> observer = result.test();

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCompleted() {
      MaybeSubject<String> source = MaybeSubject.create();

      Maybe<?> result =
          (Maybe<?>) underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onSuccess("response");
      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", "response", null);
    }

    @Test
    void endsSpanWhenEmpty() {
      MaybeSubject<String> source = MaybeSubject.create();

      Maybe<?> result =
          (Maybe<?>) underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onComplete();
      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanWhenErrored() {
      IllegalStateException exception = new IllegalStateException();
      MaybeSubject<String> source = MaybeSubject.create();

      Maybe<?> result =
          (Maybe<?>) underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer = result.test();

      source.onError(exception);
      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCancelled() {
      when(span.storeInContext(any())).thenCallRealMethod();
      MaybeSubject<String> source = MaybeSubject.create();
      Context context = Context.root().with(span);

      Maybe<?> result =
          (Maybe<?>) underTest.end(instrumenter, context, "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      observer.dispose();

      verify(instrumenter).end(context, "request", null, null);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
    }

    @Test
    void endsSpanWhenCancelledExperimentalAttributes() {
      when(span.storeInContext(any())).thenCallRealMethod();
      MaybeSubject<String> source = MaybeSubject.create();
      Context context = Context.root().with(span);

      Maybe<?> result =
          (Maybe<?>)
              underTestWithExperimentalAttributes.end(
                  instrumenter, context, "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);

      observer.dispose();

      verify(instrumenter).end(context, "request", null, null);
      verify(span).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
    }

    @Test
    void endsSpanOnceForMultipleSubscribers() {
      MaybeSubject<String> source = MaybeSubject.create();

      Maybe<?> result =
          (Maybe<?>) underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer1 = result.test();
      TestObserver<?> observer2 = result.test();
      TestObserver<?> observer3 = result.test();

      verifyNoInteractions(instrumenter);

      source.onSuccess("response");

      observer1.assertComplete();
      observer1.assertValue(value -> value.equals("response"));
      observer2.assertComplete();
      observer2.assertValue(value -> value.equals("response"));
      observer3.assertComplete();
      observer3.assertValue(value -> value.equals("response"));
      verify(instrumenter).end(Context.root(), "request", "response", null);
    }
  }

  @Nested
  class SingleTest {
    @Test
    void supported() {
      assertThat(underTest.supports(Single.class)).isTrue();
    }

    @Test
    void endsSpanOnAlreadyCompleted() {
      Single<?> result =
          (Single<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", Single.just("response"), String.class);
      TestObserver<?> observer = result.test();

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", "response", null);
    }

    @Test
    void endsSpanOnAlreadyErrored() {
      IllegalStateException exception = new IllegalStateException();

      Single<?> result =
          (Single<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", Single.error(exception), String.class);
      TestObserver<?> observer = result.test();

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCompleted() {
      SingleSubject<String> source = SingleSubject.create();

      Single<?> result =
          (Single<?>) underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onSuccess("response");

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", "response", null);
    }

    @Test
    void endsSpanWhenErrored() {
      IllegalStateException exception = new IllegalStateException();
      SingleSubject<String> source = SingleSubject.create();

      Single<?> result =
          (Single<?>) underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onError(exception);

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCancelled() {
      when(span.storeInContext(any())).thenCallRealMethod();
      SingleSubject<String> source = SingleSubject.create();
      Context context = Context.root().with(span);

      Single<?> result =
          (Single<?>) underTest.end(instrumenter, context, "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      observer.dispose();
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    void endsSpanWhenCancelledExperimentalAttributes() {
      when(span.storeInContext(any())).thenCallRealMethod();
      SingleSubject<String> source = SingleSubject.create();
      Context context = Context.root().with(span);

      Single<?> result =
          (Single<?>)
              underTestWithExperimentalAttributes.end(
                  instrumenter, context, "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);

      observer.dispose();

      verify(instrumenter).end(context, "request", null, null);
      verify(span).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
    }

    @Test
    void endsSpanOnceForMultipleSubscribers() {
      SingleSubject<String> source = SingleSubject.create();

      Single<?> result =
          (Single<?>) underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer1 = result.test();
      TestObserver<?> observer2 = result.test();
      TestObserver<?> observer3 = result.test();

      verifyNoInteractions(instrumenter);

      source.onSuccess("response");

      observer1.assertValue(value -> value.equals("response"));
      observer1.assertComplete();
      observer2.assertValue(value -> value.equals("response"));
      observer2.assertComplete();
      observer3.assertValue(value -> value.equals("response"));
      observer3.assertComplete();
      verify(instrumenter).end(Context.root(), "request", "response", null);
    }
  }

  @Nested
  class ObservableTest {
    @Test
    void supported() {
      assertThat(underTest.supports(Observable.class)).isTrue();
    }

    @Test
    void endsSpanOnAlreadyCompleted() {
      Observable<?> result =
          (Observable<?>)
              underTest.end(
                  instrumenter,
                  Context.root(),
                  "request",
                  Observable.just("response"),
                  String.class);
      TestObserver<?> observer = result.test();

      verify(instrumenter).end(Context.root(), "request", null, null);
      observer.assertComplete();
    }

    @Test
    void endsSpanOnAlreadyErrored() {
      IllegalStateException exception = new IllegalStateException();

      Observable<?> result =
          (Observable<?>)
              underTest.end(
                  instrumenter,
                  Context.root(),
                  "request",
                  Observable.error(exception),
                  String.class);
      TestObserver<?> observer = result.test();

      verify(instrumenter).end(Context.root(), "request", null, exception);
      observer.assertError(exception);
    }

    @Test
    void endsSpanWhenCompleted() {
      UnicastSubject<String> source = UnicastSubject.create();

      Observable<?> result =
          (Observable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onComplete();

      verify(instrumenter).end(Context.root(), "request", null, null);
      observer.assertComplete();
    }

    @Test
    void endsSpanWhenErrored() {
      IllegalStateException exception = new IllegalStateException();
      UnicastSubject<String> source = UnicastSubject.create();

      Observable<?> result =
          (Observable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onError(exception);

      verify(instrumenter).end(Context.root(), "request", null, exception);
      observer.assertError(exception);
    }

    @Test
    void endsOnWhenCancelled() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastSubject<String> source = UnicastSubject.create();
      Context context = Context.root().with(span);

      Observable<?> result =
          (Observable<?>) underTest.end(instrumenter, context, "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      observer.dispose();

      verify(instrumenter).end(context, "request", null, null);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
    }

    @Test
    void endsSpanWhenCancelledExperimentalAttributes() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastSubject<String> source = UnicastSubject.create();
      Context context = Context.root().with(span);

      Observable<?> result =
          (Observable<?>)
              underTestWithExperimentalAttributes.end(
                  instrumenter, context, "request", source, String.class);
      TestObserver<?> observer = result.test();

      verifyNoInteractions(instrumenter);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);

      observer.dispose();

      verify(instrumenter).end(context, "request", null, null);
      verify(span).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
    }

    @Test
    void endsSpanOnceForMultipleSubscribers() {
      ReplaySubject<String> source = ReplaySubject.create();

      Observable<?> result =
          (Observable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestObserver<?> observer1 = result.test();
      TestObserver<?> observer2 = result.test();
      TestObserver<?> observer3 = result.test();

      verifyNoInteractions(instrumenter);

      source.onComplete();

      observer1.assertComplete();
      observer2.assertComplete();
      observer3.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }
  }

  @Nested
  class FlowableTest {
    @Test
    void supported() {
      assertThat(underTest.supports(Flowable.class)).isTrue();
    }

    @Test
    void endsSpanOnAlreadyCompleted() {
      Flowable<?> result =
          (Flowable<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", Flowable.just("response"), String.class);
      TestSubscriber<?> observer = result.test();

      verify(instrumenter).end(Context.root(), "request", null, null);
      observer.assertComplete();
    }

    @Test
    void endsOnAlreadyErrored() {
      IllegalStateException exception = new IllegalStateException();

      Flowable<?> result =
          (Flowable<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", Flowable.error(exception), String.class);
      TestSubscriber<?> observer = result.test();

      verify(instrumenter).end(Context.root(), "request", null, exception);
      observer.assertError(exception);
    }

    @Test
    void endsSpanWhenCompleted() {
      UnicastProcessor<String> source = UnicastProcessor.create();

      Flowable<?> result =
          (Flowable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onComplete();
      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsOnWhenErrored() {
      IllegalStateException exception = new IllegalStateException();
      UnicastProcessor<String> source = UnicastProcessor.create();

      Flowable<?> result =
          (Flowable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onError(exception);

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCancelled() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastProcessor<String> source = UnicastProcessor.create();
      Context context = Context.root().with(span);

      Flowable<?> result =
          (Flowable<?>) underTest.end(instrumenter, context, "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      observer.cancel();

      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    void endsSpanWhenCancelledExperimentalAttributes() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastProcessor<String> source = UnicastProcessor.create();
      Context context = Context.root().with(span);

      Flowable<?> result =
          (Flowable<?>)
              underTestWithExperimentalAttributes.end(
                  instrumenter, context, "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);

      observer.cancel();

      verify(span).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    void endsSpanOnceForMultipleSubscribers() {
      ReplayProcessor<String> source = ReplayProcessor.create();

      Flowable<?> result =
          (Flowable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestSubscriber<?> observer1 = result.test();
      TestSubscriber<?> observer2 = result.test();
      TestSubscriber<?> observer3 = result.test();

      verifyNoInteractions(instrumenter);

      source.onComplete();
      observer1.assertComplete();
      observer2.assertComplete();
      observer3.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }
  }

  @Nested
  class ParallelFlowableTest {
    @Test
    void supported() {
      assertThat(underTest.supports(ParallelFlowable.class)).isTrue();
    }

    @Test
    void endsSpanOnAlreadyCompleted() {
      ParallelFlowable<?> result =
          (ParallelFlowable<?>)
              underTest.end(
                  instrumenter,
                  Context.root(),
                  "request",
                  Flowable.just("response").parallel(),
                  String.class);
      TestSubscriber<?> observer = result.sequential().test();

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanOnAlreadyErrored() {
      IllegalStateException exception = new IllegalStateException();

      ParallelFlowable<?> result =
          (ParallelFlowable<?>)
              underTest.end(
                  instrumenter,
                  Context.root(),
                  "request",
                  Flowable.error(exception).parallel(),
                  String.class);
      TestSubscriber<?> observer = result.sequential().test();

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCompleted() {
      UnicastProcessor<String> source = UnicastProcessor.create();

      ParallelFlowable<?> result =
          (ParallelFlowable<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", source.parallel(), String.class);
      TestSubscriber<?> observer = result.sequential().test();

      verifyNoInteractions(instrumenter);

      source.onComplete();

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanWhenErrored() {
      IllegalStateException exception = new IllegalStateException();
      UnicastProcessor<String> source = UnicastProcessor.create();

      ParallelFlowable<?> result =
          (ParallelFlowable<?>)
              underTest.end(
                  instrumenter, Context.root(), "request", source.parallel(), String.class);
      TestSubscriber<?> observer = result.sequential().test();

      verifyNoInteractions(instrumenter);

      source.onError(exception);

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCancelled() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastProcessor<String> source = UnicastProcessor.create();
      Context context = Context.root().with(span);

      ParallelFlowable<?> result =
          (ParallelFlowable<?>)
              underTest.end(instrumenter, context, "request", source.parallel(), String.class);
      TestSubscriber<?> observer = result.sequential().test();

      verifyNoInteractions(instrumenter);

      observer.cancel();

      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    void endsSpanWhenCancelledExperimentalAttributes() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastProcessor<String> source = UnicastProcessor.create();
      Context context = Context.root().with(span);

      ParallelFlowable<?> result =
          (ParallelFlowable<?>)
              underTestWithExperimentalAttributes.end(
                  instrumenter, context, "request", source.parallel(), String.class);
      TestSubscriber<?> observer = result.sequential().test();

      verifyNoInteractions(instrumenter);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);

      observer.cancel();
      verify(span).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      verify(instrumenter).end(context, "request", null, null);
    }
  }

  @Nested
  class PublisherTest {
    @Test
    void supported() {
      assertThat(underTest.supports(Publisher.class)).isTrue();
    }

    @Test
    void endsSpanWhenCompleted() {
      CustomPublisher source = new CustomPublisher();

      Flowable<?> result =
          (Flowable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onComplete();

      observer.assertComplete();
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanWhenErrored() {
      IllegalStateException exception = new IllegalStateException();
      CustomPublisher source = new CustomPublisher();

      Flowable<?> result =
          (Flowable<?>)
              underTest.end(instrumenter, Context.root(), "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      source.onError(exception);

      observer.assertError(exception);
      verify(instrumenter).end(Context.root(), "request", null, exception);
    }

    @Test
    void endsSpanWhenCancelled() {
      when(span.storeInContext(any())).thenCallRealMethod();
      CustomPublisher source = new CustomPublisher();
      Context context = Context.root().with(span);

      Flowable<?> result =
          (Flowable<?>) underTest.end(instrumenter, context, "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);

      observer.cancel();

      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    void endsSpanWhenCancelledExperimentalAttributes() {
      when(span.storeInContext(any())).thenCallRealMethod();
      CustomPublisher source = new CustomPublisher();
      Context context = Context.root().with(span);

      Flowable<?> result =
          (Flowable<?>)
              underTestWithExperimentalAttributes.end(
                  instrumenter, context, "request", source, String.class);
      TestSubscriber<?> observer = result.test();

      verifyNoInteractions(instrumenter);
      verify(span, never()).setAttribute(CANCELED_ATTRIBUTE_KEY, true);

      observer.cancel();

      verify(span).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      verify(instrumenter).end(context, "request", null, null);
    }

    class CustomPublisher implements Publisher<String>, Subscription {
      Subscriber<? super String> subscriber;

      @Override
      public void subscribe(Subscriber<? super String> subscriber) {
        this.subscriber = subscriber;
        subscriber.onSubscribe(this);
      }

      public void onComplete() {
        this.subscriber.onComplete();
      }

      public void onError(Throwable exception) {
        this.subscriber.onError(exception);
      }

      @Override
      public void request(long l) {}

      @Override
      public void cancel() {}
    }
  }
}
