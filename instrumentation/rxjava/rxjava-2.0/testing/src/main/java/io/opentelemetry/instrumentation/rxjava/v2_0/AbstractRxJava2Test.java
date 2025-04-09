/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.internal.operators.flowable.FlowablePublish;
import io.reactivex.internal.operators.observable.ObservablePublish;
import io.reactivex.schedulers.Schedulers;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRxJava2Test {
  private static final String EXCEPTION_MESSAGE = "test exception";
  private static final String PARENT = "publisher-parent";
  private static final String ADD_ONE = "addOne";
  private static final String ADD_TWO = "addTwo";

  protected abstract InstrumentationExtension testing();

  private static Stream<Arguments> schedulers() {
    return Stream.of(
        Arguments.of(Schedulers.newThread()),
        Arguments.of(Schedulers.computation()),
        Arguments.of(Schedulers.single()),
        Arguments.of(Schedulers.trampoline()));
  }

  private int addOne(int i) {
    return testing().runWithSpan(ADD_ONE, () -> i + 1);
  }

  private int addTwo(int i) {
    return testing().runWithSpan(ADD_TWO, () -> i + 2);
  }

  private <T> T createParentSpan(ThrowingSupplier<T, RuntimeException> test) {
    return testing().runWithSpan(PARENT, test);
  }

  private void createParentSpan(ThrowingRunnable<RuntimeException> test) {
    testing().runWithSpan(PARENT, test);
  }

  private enum CancellingSubscriber implements Subscriber<Object> {
    INSTANCE;

    @Override
    public void onSubscribe(Subscription subscription) {
      subscription.cancel();
    }

    @Override
    public void onNext(Object o) {}

    @Override
    public void onError(Throwable throwable) {}

    @Override
    public void onComplete() {}
  }

  @Test
  public void basicMaybe() {
    int result = createParentSpan(() -> Maybe.just(1).map(this::addOne).blockingGet());
    assertThat(result).isEqualTo(2);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void twoOperationsMaybe() {
    int result =
        createParentSpan(() -> Maybe.just(2).map(this::addOne).map(this::addOne).blockingGet());
    assertThat(result).isEqualTo(4);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void delayedMaybe() {
    int result =
        createParentSpan(
            () -> Maybe.just(3).delay(100, TimeUnit.MILLISECONDS).map(this::addOne).blockingGet());
    assertThat(result).isEqualTo(4);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void delayedTwiceMaybe() {
    int result =
        createParentSpan(
            () ->
                Maybe.just(4)
                    .delay(100, TimeUnit.MILLISECONDS)
                    .map(this::addOne)
                    .delay(100, TimeUnit.MILLISECONDS)
                    .map(this::addOne)
                    .blockingGet());
    assertThat(result).isEqualTo(6);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void basicFlowable() {
    Iterable<Integer> result =
        createParentSpan(
            () -> Flowable.fromIterable(asList(5, 6)).map(this::addOne).toList().blockingGet());
    assertThat(result).contains(6, 7);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void twoOperationsFlowable() {
    List<Integer> result =
        createParentSpan(
            () ->
                Flowable.fromIterable(asList(6, 7))
                    .map(this::addOne)
                    .map(this::addOne)
                    .toList()
                    .blockingGet());
    assertThat(result).contains(8, 9);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void delayedFlowable() {
    List<Integer> result =
        createParentSpan(
            () ->
                Flowable.fromIterable(asList(7, 8))
                    .delay(100, TimeUnit.MILLISECONDS)
                    .map(this::addOne)
                    .toList()
                    .blockingGet());
    assertThat(result).contains(8, 9);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void delayedTwiceFlowable() {
    List<Integer> result =
        createParentSpan(
            () ->
                Flowable.fromIterable(asList(8, 9))
                    .delay(100, TimeUnit.MILLISECONDS)
                    .map(this::addOne)
                    .delay(100, TimeUnit.MILLISECONDS)
                    .map(this::addOne)
                    .toList()
                    .blockingGet());
    assertThat(result).contains(10, 11);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void maybeFromCallable() {
    Integer result =
        createParentSpan(
            () -> Maybe.fromCallable(() -> addOne(10)).map(this::addOne).blockingGet());
    assertThat(result).isEqualTo(12);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void basicSingle() {
    Integer result = createParentSpan(() -> Single.just(0).map(this::addOne).blockingGet());
    assertThat(result).isEqualTo(1);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void basicObservable() {
    List<Integer> result =
        createParentSpan(() -> Observable.just(0).map(this::addOne).toList().blockingGet());
    assertThat(result).contains(1);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void connectableFlowable() {
    List<Integer> result =
        createParentSpan(
            () ->
                FlowablePublish.just(0)
                    .delay(100, TimeUnit.MILLISECONDS)
                    .map(this::addOne)
                    .toList()
                    .blockingGet());
    assertThat(result).contains(1);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void connectableObservable() {
    List<Integer> result =
        createParentSpan(
            () ->
                ObservablePublish.just(0)
                    .delay(100, TimeUnit.MILLISECONDS)
                    .map(this::addOne)
                    .toList()
                    .blockingGet());
    assertThat(result).contains(1);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void maybeError() {
    IllegalStateException error = new IllegalStateException(EXCEPTION_MESSAGE);
    assertThatThrownBy(() -> createParentSpan(() -> Maybe.error(error).blockingGet()))
        .isEqualTo(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void flowableError() {
    IllegalStateException error = new IllegalStateException(EXCEPTION_MESSAGE);
    assertThatThrownBy(() -> createParentSpan(() -> Flowable.error(error)).toList().blockingGet())
        .isEqualTo(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void singleError() {
    IllegalStateException error = new IllegalStateException(EXCEPTION_MESSAGE);
    assertThatThrownBy(() -> createParentSpan(() -> Single.error(error)).blockingGet())
        .isEqualTo(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void observableError() {
    IllegalStateException error = new IllegalStateException(EXCEPTION_MESSAGE);
    assertThatThrownBy(() -> createParentSpan(() -> Observable.error(error).toList().blockingGet()))
        .isEqualTo(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void completableError() {
    IllegalStateException error = new IllegalStateException(EXCEPTION_MESSAGE);
    assertThatThrownBy(
            () -> createParentSpan(() -> Completable.error(error).toMaybe().blockingGet()))
        .isEqualTo(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void basicMaybeFailure() {
    IllegalStateException error = new IllegalStateException(EXCEPTION_MESSAGE);
    assertThatThrownBy(
            () ->
                createParentSpan(
                    () ->
                        Maybe.just(1)
                            .map(this::addOne)
                            .map(
                                i -> {
                                  throw error;
                                })
                            .blockingGet()))
        .isEqualTo(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void basicFlowableFailure() {
    IllegalStateException error = new IllegalStateException(EXCEPTION_MESSAGE);
    assertThatThrownBy(
            () ->
                createParentSpan(
                    () ->
                        Flowable.fromIterable(asList(5, 6))
                            .map(this::addOne)
                            .map(
                                i -> {
                                  throw error;
                                })
                            .toList()
                            .blockingGet()))
        .isEqualTo(error);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void basicMaybeCancel() {
    createParentSpan(
        () ->
            Maybe.just(1).toFlowable().map(this::addOne).subscribe(CancellingSubscriber.INSTANCE));
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void basicFlowableCancel() {
    createParentSpan(
        () ->
            Flowable.fromIterable(asList(5, 6))
                .map(this::addOne)
                .subscribe(CancellingSubscriber.INSTANCE));
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void basicSingleCancel() {
    createParentSpan(
        () ->
            Single.just(1).toFlowable().map(this::addOne).subscribe(CancellingSubscriber.INSTANCE));
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void basicCompletableCancel() {
    createParentSpan(
        () ->
            Completable.fromCallable(() -> 1)
                .toFlowable()
                .subscribe(CancellingSubscriber.INSTANCE));
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void basicObservableCancel() {
    createParentSpan(
        () ->
            Observable.just(1)
                .toFlowable(BackpressureStrategy.LATEST)
                .map(this::addOne)
                .subscribe(CancellingSubscriber.INSTANCE));
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  public void basicMaybeChain() {
    createParentSpan(
        () ->
            Maybe.just(1)
                .map(this::addOne)
                .map(this::addOne)
                .concatWith(Maybe.just(1).map(this::addOne))
                .toList()
                .blockingGet());
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void basicFlowableChain() {
    createParentSpan(
        () ->
            Flowable.fromIterable(asList(5, 6))
                .map(this::addOne)
                .map(this::addOne)
                .concatWith(Maybe.just(1).map(this::addOne).toFlowable())
                .toList()
                .blockingGet());
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  // Publisher chain spans have the correct parents from subscription time
  @Test
  public void maybeChainParentSpan() {
    Maybe<Integer> maybe = Maybe.just(42).map(this::addOne).map(this::addTwo);
    testing().runWithSpan("trace-parent", () -> maybe.blockingGet());
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("trace-parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_TWO)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void maybeChainHasSubscriptionContext() {
    Integer result =
        createParentSpan(
            () -> {
              Maybe<Integer> maybe = Maybe.just(1).map(this::addOne);
              return testing()
                  .runWithSpan("intermediate", () -> maybe.map(this::addTwo))
                  .blockingGet();
            });
    assertThat(result).isEqualTo(4);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("intermediate")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_TWO)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void flowableChainHasSubscriptionContext() {
    List<Integer> result =
        createParentSpan(
            () -> {
              Flowable<Integer> flowable = Flowable.fromIterable(asList(1, 2)).map(this::addOne);
              return testing()
                  .runWithSpan("intermediate", () -> flowable.map(this::addTwo))
                  .toList()
                  .blockingGet();
            });
    assertThat(result).contains(4, 5);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("intermediate")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_TWO)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_TWO)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void singleChainHasSubscriptionContext() {
    Integer result =
        createParentSpan(
            () -> {
              Single<Integer> single = Single.just(1).map(this::addOne);
              return testing()
                  .runWithSpan("intermediate", () -> single.map(this::addTwo))
                  .blockingGet();
            });
    assertThat(result).isEqualTo(4);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("intermediate")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_TWO)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  public void observableChainHasSubscriptionContext() {
    List<Integer> result =
        createParentSpan(
            () -> {
              Observable<Integer> observable = Observable.just(1).map(this::addOne);
              return testing()
                  .runWithSpan("intermediate", () -> observable.map(this::addTwo))
                  .toList()
                  .blockingGet();
            });
    assertThat(result).contains(4);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(PARENT).hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("intermediate")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_TWO)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @ParameterizedTest
  @MethodSource("schedulers")
  public void flowableMultiResults(Scheduler scheduler) {
    List<Integer> result =
        testing()
            .runWithSpan(
                "flowable root",
                () -> {
                  return Flowable.fromIterable(asList(1, 2, 3, 4))
                      .parallel()
                      .runOn(scheduler)
                      .flatMap(num -> Maybe.just(num).map(this::addOne).toFlowable())
                      .sequential()
                      .toList()
                      .blockingGet();
                });
    assertThat(result.size()).isEqualTo(4);
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("flowable root").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(ADD_ONE)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @ParameterizedTest
  @MethodSource("schedulers")
  public void maybeMultipleTraceChains(Scheduler scheduler) {
    int iterations = 100;
    RxJava2ConcurrencyTestHelper.launchAndWait(scheduler, iterations, 60000, testing());
    @SuppressWarnings("unchecked")
    Consumer<TraceAssert>[] assertions = (Consumer<TraceAssert>[]) new Consumer<?>[iterations];
    for (int i = 0; i < iterations; i++) {
      int iteration = i;
      assertions[i] =
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("outer")
                          .hasNoParent()
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("middle")
                          .hasParent(trace.getSpan(0))
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("inner")
                          .hasParent(trace.getSpan(1))
                          .hasAttributes(attributeEntry("iteration", iteration)));
    }
    testing()
        .waitAndAssertSortedTraces(
            Comparator.comparing(
                span -> span.get(0).getAttributes().get(AttributeKey.longKey("iteration"))),
            assertions);
    testing().clearData();
  }
}
