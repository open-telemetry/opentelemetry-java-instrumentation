/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3.common;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.processors.UnicastProcessor;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subjects.MaybeSubject;
import io.reactivex.rxjava3.subjects.SingleSubject;
import io.reactivex.rxjava3.subjects.UnicastSubject;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractRxJava3WithSpanTest {
  private static final AttributeKey<Boolean> RXJAVA_CANCELED =
      AttributeKey.booleanKey("rxjava.canceled");

  protected abstract AbstractTracedWithSpan newTraced();

  protected abstract InstrumentationExtension testing();

  @Test
  public void captureSpanForCompletedCompletable() {
    TestObserver<Object> observer = new TestObserver<>();
    Completable source = Completable.complete();
    newTraced().completable(source).subscribe(observer);
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.completable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "completable"))));
  }

  @Test
  public void captureSpanForEventuallyCompletedCompletable() throws InterruptedException {
    CompletableSubject source = CompletableSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().completable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onComplete();
    observer.assertComplete();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.completable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "completable"))));
  }

  @Test
  public void captureSpanForErrorCompletable() {
    IllegalStateException error = new IllegalStateException("Boom");
    TestObserver<Object> observer = new TestObserver<>();
    Completable source = Completable.error(error);
    newTraced().completable(source).subscribe(observer);
    observer.assertError(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.completable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "completable"))));
  }

  @Test
  public void captureSpanForEventuallyErrorCompletable() throws InterruptedException {
    IllegalStateException error = new IllegalStateException("Boom");
    CompletableSubject source = CompletableSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().completable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onError(error);
    observer.assertError(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.completable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "completable"))));
  }

  @Test
  public void captureSpanForCanceledCompletable() throws InterruptedException {
    CompletableSubject source = CompletableSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().completable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    observer.dispose();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.completable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "completable"),
                                equalTo(RXJAVA_CANCELED, true))));
  }

  @Test
  public void captureSpanForCompletedMaybe() {
    Maybe<String> source = Maybe.just("Value");
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().maybe(source).subscribe(observer);
    observer.assertValue("Value");
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.maybe")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "maybe"))));
  }

  @Test
  public void captureSpanForEmptyMaybe() {
    Maybe<String> source = Maybe.empty();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().maybe(source).subscribe(observer);
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.maybe")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "maybe"))));
  }

  @Test
  public void captureSpanForEventuallyCompletedMaybe() throws InterruptedException {
    MaybeSubject<String> source = MaybeSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().maybe(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onSuccess("Value");
    observer.assertValue("Value");
    observer.assertComplete();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.maybe")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "maybe"))));
  }

  @Test
  public void captureSpanForErrorMaybe() {
    IllegalStateException error = new IllegalStateException("Boom");
    TestObserver<Object> observer = new TestObserver<>();
    Maybe<String> source = Maybe.error(error);
    newTraced().maybe(source).subscribe(observer);
    observer.assertError(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.maybe")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "maybe"))));
  }

  @Test
  public void captureSpanForEventuallyErrorMaybe() throws InterruptedException {
    IllegalStateException error = new IllegalStateException("Boom");
    MaybeSubject<String> source = MaybeSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().maybe(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onError(error);
    observer.assertError(error);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.maybe")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "maybe"))));
  }

  @Test
  public void captureSpanForCanceledMaybe() throws InterruptedException {
    MaybeSubject<String> source = MaybeSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().maybe(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    observer.dispose();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.maybe")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "maybe"),
                                equalTo(RXJAVA_CANCELED, true))));
  }

  @Test
  public void captureSpanForCompletedSingle() {
    Single<String> source = Single.just("Value");
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().single(source).subscribe(observer);
    observer.assertValue("Value");
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.single")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "single"))));
  }

  @Test
  public void captureSpanForEventuallyCompletedSingle() throws InterruptedException {
    SingleSubject<String> source = SingleSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().single(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onSuccess("Value");
    observer.assertValue("Value");
    observer.assertComplete();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.single")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "single"))));
  }

  @Test
  public void captureSpanForErrorSingle() {
    IllegalStateException error = new IllegalStateException("Boom");
    TestObserver<Object> observer = new TestObserver<>();
    Single<String> source = Single.error(error);
    newTraced().single(source).subscribe(observer);
    observer.assertError(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.single")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "single"))));
  }

  @Test
  public void captureSpanForEventuallyErrorSingle() throws InterruptedException {
    IllegalStateException error = new IllegalStateException("Boom");
    SingleSubject<String> source = SingleSubject.create();
    TestObserver<String> observer = new TestObserver<>();
    newTraced().single(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onError(error);
    observer.assertError(error);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.single")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "single"))));
  }

  @Test
  public void captureSpanForCanceledSingle() throws InterruptedException {
    SingleSubject<String> source = SingleSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().single(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    observer.dispose();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.single")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "single"),
                                equalTo(RXJAVA_CANCELED, true))));
  }

  @Test
  public void captureSpanForCompletedObservable() {
    TestObserver<Object> observer = new TestObserver<>();
    Observable<String> source = Observable.just("Value");
    newTraced().observable(source).subscribe(observer);
    observer.assertValue("Value");
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.observable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "observable"))));
  }

  @Test
  public void captureSpanForEventuallyCompletedObservable() throws InterruptedException {
    TestObserver<Object> observer = new TestObserver<>();
    UnicastSubject<String> source = UnicastSubject.create();
    newTraced().observable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onComplete();
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.observable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "observable"))));
  }

  @Test
  public void captureSpanForErrorObservable() {
    IllegalStateException error = new IllegalStateException("Boom");
    Observable<String> source = Observable.error(error);
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().observable(source).subscribe(observer);
    observer.assertError(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.observable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "observable"))));
  }

  @Test
  public void captureSpanForEventuallyErrorObservable() throws InterruptedException {
    IllegalStateException error = new IllegalStateException("Boom");
    UnicastSubject<String> source = UnicastSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().observable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onError(error);
    observer.assertError(error);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.observable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "observable"))));
  }

  @Test
  public void captureSpanForCanceledObservable() throws InterruptedException {
    UnicastSubject<String> source = UnicastSubject.create();
    TestObserver<Object> observer = new TestObserver<>();
    newTraced().observable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    observer.dispose();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.observable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "observable"),
                                equalTo(RXJAVA_CANCELED, true))));
  }

  @Test
  public void captureSpanForCompletedFlowable() {
    TestSubscriber<Object> observe = new TestSubscriber<>();
    Flowable<String> source = Flowable.just("Value");
    newTraced().flowable(source).subscribe(observe);
    observe.assertValue("Value");
    observe.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.flowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "flowable"))));
  }

  @Test
  public void captureForEventuallyCompletedFlowable() throws InterruptedException {
    UnicastProcessor<String> source = UnicastProcessor.create();
    TestSubscriber<Object> observer = new TestSubscriber<>();
    newTraced().flowable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onComplete();
    observer.assertComplete();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.flowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "flowable"))));
  }

  @Test
  public void captureSpanForErrorFlowable() {
    IllegalStateException error = new IllegalStateException("Boom");
    TestSubscriber<Object> observer = new TestSubscriber<>();
    Flowable<String> source = Flowable.error(error);
    newTraced().flowable(source).subscribe(observer);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.flowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "flowable"))));
  }

  @Test
  public void captureSpanForEventuallyErrorFlowable() throws InterruptedException {
    IllegalStateException error = new IllegalStateException("Boom");
    UnicastProcessor<String> source = UnicastProcessor.create();
    TestSubscriber<Object> observer = new TestSubscriber<>();
    newTraced().flowable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onError(error);
    observer.assertError(error);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.flowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "flowable"))));
  }

  @Test
  public void captureSpanForCanceledFlowable() throws InterruptedException {
    UnicastProcessor<String> source = UnicastProcessor.create();
    TestSubscriber<Object> observer = new TestSubscriber<>();
    newTraced().flowable(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    observer.cancel();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.flowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "flowable"),
                                equalTo(RXJAVA_CANCELED, true))));
  }

  @Test
  public void captureSpanForCompletedParallelFlowable() {
    Flowable<String> source = Flowable.just("Value");
    TestSubscriber<Object> observer = new TestSubscriber<>();
    newTraced().parallelFlowable(source.parallel()).sequential().subscribe(observer);
    observer.assertValue("Value");
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.parallelFlowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "parallelFlowable"))));
  }

  @Test
  public void captureSpanForEventuallyCompletedParallelFlowable() throws InterruptedException {
    UnicastProcessor<String> source = UnicastProcessor.create();
    TestSubscriber<Object> observer = new TestSubscriber<>();
    newTraced().parallelFlowable(source.parallel()).sequential().subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onComplete();
    observer.assertComplete();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.parallelFlowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "parallelFlowable"))));
  }

  @Test
  public void captureSpanForErrorParallelFlowable() {
    IllegalStateException error = new IllegalStateException("Boom");
    TestSubscriber<Object> observer = new TestSubscriber<>();
    Flowable<String> source = Flowable.error(error);
    newTraced().parallelFlowable(source.parallel()).sequential().subscribe(observer);
    observer.assertError(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.parallelFlowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "parallelFlowable"))));
  }

  @Test
  public void captureSpanForEventuallyErrorParallelFlowable() throws InterruptedException {
    IllegalStateException error = new IllegalStateException("Boom");
    TestSubscriber<Object> observer = new TestSubscriber<>();
    UnicastProcessor<String> source = UnicastProcessor.create();
    newTraced().parallelFlowable(source.parallel()).sequential().subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onError(error);
    observer.assertError(error);

    observer.assertError(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.parallelFlowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "parallelFlowable"))));
  }

  @Test
  public void captureSpanForCanceledParallelFlowable() throws InterruptedException {
    TestSubscriber<Object> observer = new TestSubscriber<>();
    UnicastProcessor<String> source = UnicastProcessor.create();
    newTraced().parallelFlowable(source.parallel()).sequential().subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onNext("Value");
    observer.assertValue("Value");

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    observer.cancel();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.parallelFlowable")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "parallelFlowable"),
                                equalTo(RXJAVA_CANCELED, true))));
  }

  @Test
  public void captureSpanForEventuallyCompletedPublisher() throws InterruptedException {
    CustomPublisher source = new CustomPublisher();
    TestSubscriber<String> observer = new TestSubscriber<>();
    newTraced().publisher(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onComplete();
    observer.assertComplete();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.publisher")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "publisher"))));
  }

  @Test
  public void captureSpanForEventuallyErrorPublisher() throws InterruptedException {
    IllegalStateException error = new IllegalStateException("Boom");
    CustomPublisher source = new CustomPublisher();
    TestSubscriber<Object> observer = new TestSubscriber<>();
    newTraced().publisher(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    source.onError(error);
    observer.assertError(error);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.publisher")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(error)
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "publisher"))));
  }

  @Test
  public void captureSpanForCanceledPublisher() throws InterruptedException {
    CustomPublisher source = new CustomPublisher();
    TestSubscriber<Object> observer = new TestSubscriber<>();
    newTraced().publisher(source).subscribe(observer);

    // sleep a bit just to make sure no span is captured
    Thread.sleep(500);
    List<List<SpanData>> traces = testing().waitForTraces(0);
    assertThat(traces).isEmpty();

    observer.cancel();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.publisher")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, val -> val.endsWith(".TracedWithSpan")),
                                equalTo(CODE_FUNCTION, "publisher"),
                                equalTo(RXJAVA_CANCELED, true))));
  }

  static class CustomPublisher implements Publisher<String>, Subscription {

    Subscriber<? super String> subscriber;

    @Override
    public void subscribe(Subscriber<? super String> subscriber) {
      this.subscriber = subscriber;
      subscriber.onSubscribe(this);
    }

    void onComplete() {
      this.subscriber.onComplete();
    }

    void onError(Throwable exception) {
      this.subscriber.onError(exception);
    }

    @Override
    public void request(long l) {}

    @Override
    public void cancel() {}
  }
}
