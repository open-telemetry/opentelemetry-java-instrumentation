/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractReactorCoreTest {

  private final InstrumentationExtension testing;

  protected AbstractReactorCoreTest(InstrumentationExtension testing) {
    this.testing = testing;
  }

  @Test
  void basicMono() {
    int result = testing.runWithSpan("parent", () -> Mono.just(1).map(this::addOne).block());
    assertThat(result).isEqualTo(2);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void twoOperationsMono() {
    int result =
        testing.runWithSpan(
            "parent", () -> Mono.just(2).map(this::addOne).map(this::addOne).block());
    assertThat(result).isEqualTo(4);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void delayedMono() {
    int result =
        testing.runWithSpan(
            "parent",
            () -> Mono.just(3).delayElement(Duration.ofMillis(1)).map(this::addOne).block());
    assertThat(result).isEqualTo(4);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void delayedTwiceMono() {
    int result =
        testing.runWithSpan(
            "parent",
            () ->
                Mono.just(4)
                    .delayElement(Duration.ofMillis(1))
                    .map(this::addOne)
                    .delayElement(Duration.ofMillis(1))
                    .map(this::addOne)
                    .block());
    assertThat(result).isEqualTo(6);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void basicFlux() {
    List<Integer> result =
        testing.runWithSpan(
            "parent",
            () -> Flux.fromStream(Stream.of(5, 6)).map(this::addOne).collectList().block());
    assertThat(result).containsExactly(6, 7);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void twoOperationsFlux() {
    List<Integer> result =
        testing.runWithSpan(
            "parent",
            () ->
                Flux.fromStream(Stream.of(6, 7))
                    .map(this::addOne)
                    .map(this::addOne)
                    .collectList()
                    .block());
    assertThat(result).containsExactly(8, 9);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void delayedFlux() {
    List<Integer> result =
        testing.runWithSpan(
            "parent",
            () ->
                Flux.fromStream(Stream.of(7, 8))
                    .delayElements(Duration.ofMillis(1))
                    .map(this::addOne)
                    .collectList()
                    .block());
    assertThat(result).containsExactly(8, 9);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void delayedTwiceFlux() {
    List<Integer> result =
        testing.runWithSpan(
            "parent",
            () ->
                Flux.fromStream(Stream.of(8, 9))
                    .delayElements(Duration.ofMillis(1))
                    .map(this::addOne)
                    .delayElements(Duration.ofMillis(1))
                    .map(this::addOne)
                    .collectList()
                    .block());
    assertThat(result).containsExactly(10, 11);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void monoFromCallable() {
    int result =
        testing.runWithSpan(
            "parent", () -> Mono.fromCallable(() -> addOne(10)).map(this::addOne).block());
    assertThat(result).isEqualTo(12);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void monoError() {
    IllegalStateException error = new IllegalStateException("exception");
    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> Mono.error(error).block()))
        .isEqualTo(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error)));
  }

  @Test
  void fluxError() {
    IllegalStateException error = new IllegalStateException("exception");
    assertThatThrownBy(
            () -> testing.runWithSpan("parent", () -> Flux.error(error).collectList().block()))
        .isEqualTo(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error)));
  }

  @Test
  void monoStepError() {
    IllegalStateException error = new IllegalStateException("exception");
    assertThatThrownBy(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        Mono.just(1)
                            .map(this::addOne)
                            .map(
                                unused -> {
                                  throw error;
                                })
                            .block()))
        .isEqualTo(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error),
                span ->
                    span.hasName("add one")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())));
  }

  @Test
  void fluxStepError() {
    IllegalStateException error = new IllegalStateException("exception");
    assertThatThrownBy(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        Flux.just(5, 6)
                            .map(this::addOne)
                            .map(
                                unused -> {
                                  throw error;
                                })
                            .collectList()
                            .block()))
        .isEqualTo(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error),
                span ->
                    span.hasName("add one")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())));
  }

  @Test
  void cancelMono() {
    testing.runWithSpan("parent", () -> Mono.just(1).subscribe(CancellingSubscriber.INSTANCE));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasStatus(StatusData.unset())));
  }

  @Test
  void cancelFlux() {
    testing.runWithSpan("parent", () -> Flux.just(3, 4).subscribe(CancellingSubscriber.INSTANCE));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasStatus(StatusData.unset())));
  }

  @Test
  void monoChain() {
    int result =
        testing.runWithSpan(
            "parent",
            () ->
                Mono.just(1)
                    .map(this::addOne)
                    .map(this::addOne)
                    .then(Mono.just(1).map(this::addOne))
                    .block());
    assertThat(result).isEqualTo(2);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void fluxChain() {
    int result =
        testing.runWithSpan(
            "parent",
            () ->
                Flux.just(5, 6)
                    .map(this::addOne)
                    .map(this::addOne)
                    .then(Mono.just(1).map(this::addOne))
                    .block());
    assertThat(result).isEqualTo(2);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0))));
  }

  @Test
  void monoChainHasAssemblyContext() {
    int result =
        testing.runWithSpan(
            "parent",
            () -> {
              Mono<Integer> mono = Mono.just(1).map(this::addOne);
              return testing.runWithSpan("intermediate", () -> mono.map(this::addTwo)).block();
            });
    assertThat(result).isEqualTo(4);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("intermediate").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add two").hasParent(trace.getSpan(0))));
  }

  @Test
  void fluxChainHasAssemblyContext() {
    List<Integer> result =
        testing.runWithSpan(
            "parent",
            () -> {
              Flux<Integer> flux = Flux.just(1, 2).map(this::addOne);
              return testing
                  .runWithSpan("intermediate", () -> flux.map(this::addTwo))
                  .collectList()
                  .block();
            });
    assertThat(result).containsExactly(4, 5);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("intermediate").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add two").hasParent(trace.getSpan(0)),
                span -> span.hasName("add one").hasParent(trace.getSpan(0)),
                span -> span.hasName("add two").hasParent(trace.getSpan(0))));
  }

  @Test
  void nestedDelayedMonoHighConcurrency() {
    for (int i = 0; i < 100; i++) {
      int iteration = i;
      Mono<String> outer =
          Mono.just("")
              .map(Function.identity())
              .delayElement(Duration.ofMillis(1))
              .map(Function.identity())
              .delayElement(Duration.ofMillis(1))
              .doOnSuccess(
                  unused -> {
                    Mono<String> middle =
                        Mono.just("")
                            .map(Function.identity())
                            .doOnSuccess(
                                unused2 ->
                                    testing.runWithSpan(
                                        "inner",
                                        () -> Span.current().setAttribute("iteration", iteration)));

                    testing.runWithSpan(
                        "middle",
                        () -> {
                          Span.current().setAttribute("iteration", iteration);
                          middle.subscribe();
                        });
                  });

      // Context must propagate even if only subscribe is in root span scope
      testing.runWithSpan(
          "outer",
          () -> {
            Span.current().setAttribute("iteration", iteration);
            outer.subscribe();
          });
    }

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int iteration = i;
      assertions.add(
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
                          .hasAttributes(attributeEntry("iteration", iteration))));
    }
    testing.waitAndAssertTraces(assertions);
  }

  @Test
  void nestedDelayedFluxHighConcurrency() {
    for (int i = 0; i < 100; i++) {
      int iteration = i;
      Flux<String> outer =
          Flux.just("a", "b")
              .map(Function.identity())
              .delayElements(Duration.ofMillis(1))
              .map(Function.identity())
              .delayElements(Duration.ofMillis(1))
              .doOnEach(
                  middleSignal -> {
                    if (middleSignal.hasValue()) {
                      String value = middleSignal.get();
                      Flux<String> middle =
                          Flux.just("c", "d")
                              .map(Function.identity())
                              .delayElements(Duration.ofMillis(1))
                              .doOnEach(
                                  innerSignal -> {
                                    if (innerSignal.hasValue()) {
                                      testing.runWithSpan(
                                          "inner " + value + innerSignal.get(),
                                          () ->
                                              Span.current().setAttribute("iteration", iteration));
                                    }
                                  });

                      testing.runWithSpan(
                          "middle " + value,
                          () -> {
                            Span.current().setAttribute("iteration", iteration);
                            middle.subscribe();
                          });
                    }
                  });

      // Context must propagate even if only subscribe is in root span scope
      testing.runWithSpan(
          "outer",
          () -> {
            Span.current().setAttribute("iteration", iteration);
            outer.subscribe();
          });
    }

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int iteration = i;
      assertions.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("outer")
                          .hasNoParent()
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("middle a")
                          .hasParent(trace.getSpan(0))
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("inner ac")
                          .hasParent(trace.getSpan(1))
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("inner ad")
                          .hasParent(trace.getSpan(1))
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("middle b")
                          .hasParent(trace.getSpan(0))
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("inner bc")
                          .hasParent(trace.getSpan(4))
                          .hasAttributes(attributeEntry("iteration", iteration)),
                  span ->
                      span.hasName("inner bd")
                          .hasParent(trace.getSpan(4))
                          .hasAttributes(attributeEntry("iteration", iteration))));
    }
    testing.waitAndAssertTraces(assertions);
  }

  private int addOne(int i) {
    return testing.runWithSpan("add one", () -> i + 1);
  }

  private int addTwo(int i) {
    return testing.runWithSpan("add two", () -> i + 2);
  }

  private enum CancellingSubscriber implements Subscriber<Integer> {
    INSTANCE;

    @Override
    public void onSubscribe(Subscription subscription) {
      subscription.cancel();
    }

    @Override
    public void onNext(Integer integer) {}

    @Override
    public void onError(Throwable throwable) {}

    @Override
    public void onComplete() {}
  }
}
