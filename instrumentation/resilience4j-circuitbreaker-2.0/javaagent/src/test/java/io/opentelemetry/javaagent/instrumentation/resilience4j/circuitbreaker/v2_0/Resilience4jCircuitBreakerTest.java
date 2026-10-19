/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.TransitionCheckResult;
import io.github.resilience4j.core.functions.CheckedConsumer;
import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.resilience4j.core.functions.CheckedRunnable;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.core.functions.OnceConsumer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class Resilience4jCircuitBreakerTest {

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean(
          "otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void createsCircuitBreakerSpanWhenCallSucceeds() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    String result = testing.runWithSpan("parent", () -> circuitBreaker.executeSupplier(() -> "ok"));

    assertThat(result).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void circuitBreakerSpanIsParentOfProtectedOperationSpans() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<String> supplier =
        CircuitBreaker.decorateSupplier(
            circuitBreaker, () -> testing.runWithSpan("protected-operation", () -> "ok"));

    String result = testing.runWithSpan("parent", supplier::get);

    assertThat(result).isEqualTo("ok");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                experimental("test-circuit-breaker")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.state"),
                                experimental("closed")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.outcome"),
                                experimental("success"))),
                span ->
                    span.hasName("protected-operation")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))));
  }

  @Test
  void createsCircuitBreakerSpanWhenCallFails() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        circuitBreaker.executeSupplier(
                            () -> {
                              throw exception;
                            })));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedCallableSucceeds() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Callable<String> callable = CircuitBreaker.decorateCallable(circuitBreaker, () -> "ok");

    String result = testing.runWithSpan("parent", callable::call);

    assertThat(result).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedCallableThrowsCheckedException() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Exception exception = new Exception("boom");
    Callable<String> callable =
        CircuitBreaker.decorateCallable(
            circuitBreaker,
            () -> {
              throw exception;
            });

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", callable::call));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsFailureSpanWhenDecoratedCallableThrowsError() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Error error = new AssertionError("boom");
    Callable<String> callable =
        CircuitBreaker.decorateCallable(
            circuitBreaker,
            () -> {
              throw error;
            });

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", callable::call));

    assertThat(thrown).isSameAs(error);
    assertCircuitBreakerSpan("closed", "failure", error);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedRunnableSucceeds() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    AtomicReference<String> value = new AtomicReference<>();
    Runnable runnable = CircuitBreaker.decorateRunnable(circuitBreaker, () -> value.set("ok"));

    testing.runWithSpan("parent", runnable::run);

    assertThat(value.get()).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedRunnableThrows() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");
    Runnable runnable =
        CircuitBreaker.decorateRunnable(
            circuitBreaker,
            () -> {
              throw exception;
            });

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", runnable::run));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedFunctionSucceeds() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Function<String, String> function =
        CircuitBreaker.decorateFunction(circuitBreaker, value -> value + "-ok");

    String result = testing.runWithSpan("parent", () -> function.apply("in"));

    assertThat(result).isEqualTo("in-ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedFunctionThrows() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");
    Function<String, String> function =
        CircuitBreaker.decorateFunction(
            circuitBreaker,
            value -> {
              throw exception;
            });

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> function.apply("in")));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedConsumerSucceeds() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    AtomicReference<String> value = new AtomicReference<>();
    Consumer<String> consumer = CircuitBreaker.decorateConsumer(circuitBreaker, value::set);

    testing.runWithSpan("parent", () -> consumer.accept("ok"));

    assertThat(value.get()).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedConsumerThrows() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");
    Consumer<String> consumer =
        CircuitBreaker.decorateConsumer(
            circuitBreaker,
            value -> {
              throw exception;
            });

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> consumer.accept("in")));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedCheckedRunnableSucceeds() throws Throwable {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    AtomicReference<String> value = new AtomicReference<>();
    CheckedRunnable runnable =
        CircuitBreaker.decorateCheckedRunnable(circuitBreaker, () -> value.set("ok"));

    testing.runWithSpan(
        "parent",
        () -> {
          runnable.run();
          return null;
        });

    assertThat(value.get()).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedCheckedRunnableThrowsCheckedException() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Exception exception = new Exception("boom");
    CheckedRunnable runnable =
        CircuitBreaker.decorateCheckedRunnable(
            circuitBreaker,
            () -> {
              throw exception;
            });

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      runnable.run();
                      return null;
                    }));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedCheckedFunctionSucceeds() throws Throwable {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    CheckedFunction<String, String> function =
        CircuitBreaker.decorateCheckedFunction(circuitBreaker, value -> value + "-ok");

    String result = testing.runWithSpan("parent", () -> function.apply("in"));

    assertThat(result).isEqualTo("in-ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedCheckedFunctionThrowsCheckedException() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Exception exception = new Exception("boom");
    CheckedFunction<String, String> function =
        CircuitBreaker.decorateCheckedFunction(
            circuitBreaker,
            value -> {
              throw exception;
            });

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> function.apply("in")));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedCheckedConsumerSucceeds() throws Throwable {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    AtomicReference<String> value = new AtomicReference<>();
    CheckedConsumer<String> consumer =
        CircuitBreaker.decorateCheckedConsumer(circuitBreaker, value::set);

    testing.runWithSpan(
        "parent",
        () -> {
          consumer.accept("ok");
          return null;
        });

    assertThat(value.get()).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedCheckedConsumerThrowsCheckedException() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Exception exception = new Exception("boom");
    CheckedConsumer<String> consumer =
        CircuitBreaker.decorateCheckedConsumer(
            circuitBreaker,
            value -> {
              throw exception;
            });

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      consumer.accept("in");
                      return null;
                    }));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void compositionDoesNotExtendProtectedOperation(boolean unchecked, boolean failAfter) {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    CheckedSupplier<String> supplier =
        CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> "ok");
    CheckedFunction<String, String> function =
        CircuitBreaker.decorateCheckedFunction(circuitBreaker, value -> value);
    IllegalStateException exception = new IllegalStateException("after");
    Function<String, String> after =
        value ->
            testing.runWithSpan(
                "after",
                () -> {
                  if (failAfter) {
                    throw exception;
                  }
                  return value + "-after";
                });

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      String result =
                          unchecked
                              ? function.unchecked().andThen(after).apply("ok")
                              : supplier.andThen(after::apply).get();
                      assertThat(result).isEqualTo("ok-after");
                      return result;
                    }));

    assertThat(thrown).isSameAs(failAfter ? exception : null);
    assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset()),
                span -> span.hasName("after").hasParent(trace.getSpan(0))));
  }

  @Test
  void doesNotCreateSpanForDirectCompletion() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          invokeOnSuccess(circuitBreaker);
        });

    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void doesNotCreateSpanForDirectOnResult() {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          circuitBreaker.onResult(1L, MILLISECONDS, 500);
        });

    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void rawOnResultInsideDecoratedSupplierDoesNotChangeDecoratedOutcome() {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<Integer> decorated =
        CircuitBreaker.decorateSupplier(
            circuitBreaker,
            () -> {
              circuitBreaker.onResult(1L, MILLISECONDS, 500);
              return 200;
            });

    assertThat(testing.runWithSpan("parent", decorated::get)).isEqualTo(200);

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void rawOnResultInsideDecoratedCompletionStageDoesNotChangeDecoratedOutcome() {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<CompletionStage<Integer>> decorated =
        CircuitBreaker.decorateCompletionStage(
            circuitBreaker,
            () -> {
              circuitBreaker.onResult(1L, MILLISECONDS, 500);
              return CompletableFuture.completedFuture(200);
            });

    assertThat(testing.runWithSpan("parent", () -> decorated.get().toCompletableFuture().join()))
        .isEqualTo(200);

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void rawOnResultInsideDecoratedFutureSupplierDoesNotChangeDecoratedOutcome() throws Exception {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<Future<Integer>> decorated =
        CircuitBreaker.decorateFuture(
            circuitBreaker,
            () -> {
              circuitBreaker.onResult(1L, MILLISECONDS, 500);
              return CompletableFuture.completedFuture(200);
            });

    assertThat(testing.runWithSpan("parent", () -> decorated.get().get())).isEqualTo(200);

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void completingDecoratedStageInsideDecoratedSupplierKeepsStageClassification() {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker stageCircuitBreaker =
        CircuitBreaker.of("stage-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = new CompletableFuture<>();
    Supplier<CompletionStage<Integer>> decoratedStage =
        CircuitBreaker.decorateCompletionStage(stageCircuitBreaker, () -> future);

    CircuitBreaker supplierCircuitBreaker = CircuitBreaker.ofDefaults("supplier-circuit-breaker");
    Supplier<Integer> decoratedSupplier =
        CircuitBreaker.decorateSupplier(
            supplierCircuitBreaker,
            () -> {
              future.complete(500);
              return 200;
            });

    testing.runWithSpan(
        "parent",
        () -> {
          CompletionStage<Integer> stage = decoratedStage.get();
          assertThat(decoratedSupplier.get()).isEqualTo(200);
          assertThat(stage.toCompletableFuture().join()).isEqualTo(500);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker stage-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error()),
                span ->
                    span.hasName("CircuitBreaker supplier-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())));
  }

  @Test
  void rawCallbackInDecoratedOnResultDoesNotCreateAnotherSpan() {
    CircuitBreaker innerCircuitBreaker = CircuitBreaker.ofDefaults("inner-circuit-breaker");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.transitionOnResult(
        result -> {
          innerCircuitBreaker.acquirePermission();
          invokeOnSuccess(innerCircuitBreaker);
          return TransitionCheckResult.noTransition();
        });
    CircuitBreaker outerCircuitBreaker =
        CircuitBreaker.of("outer-circuit-breaker", builder.build());
    Supplier<Integer> decorated = CircuitBreaker.decorateSupplier(outerCircuitBreaker, () -> 500);

    Integer result = testing.runWithSpan("parent", decorated::get);

    assertThat(result).isEqualTo(500);
    assertThat(testing.spans())
        .extracting(span -> span.getName())
        .containsExactlyInAnyOrder("parent", "CircuitBreaker outer-circuit-breaker");
  }

  @Test
  void rawCallbackOnSameBreakerDoesNotCompleteDecoratedAttempt() {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    CircuitBreaker[] circuitBreakerHolder = new CircuitBreaker[1];
    builder.transitionOnResult(
        result -> {
          circuitBreakerHolder[0].acquirePermission();
          invokeOnSuccess(circuitBreakerHolder[0]);
          return TransitionCheckResult.noTransition();
        });
    circuitBreakerHolder[0] = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<Integer> decorated =
        CircuitBreaker.decorateSupplier(circuitBreakerHolder[0], () -> 500);

    Integer result = testing.runWithSpan("parent", decorated::get);

    assertThat(result).isEqualTo(500);
    assertCircuitBreakerSpan("closed", "success");
  }

  @ParameterizedTest
  @CsvSource({"false,true", "true,false", "true,true"})
  void nestedDecoratedAttemptOnSameBreakerKeepsItsClassification(
      boolean outerFailure, boolean innerFailure) {
    AtomicReference<Supplier<Integer>> nested = new AtomicReference<>();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(
        result -> {
          if (Integer.valueOf(500).equals(result)) {
            assertThat(nested.get().get()).isEqualTo(501);
            return outerFailure;
          }
          return innerFailure;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    nested.set(CircuitBreaker.decorateSupplier(circuitBreaker, () -> 501));
    Supplier<Integer> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, () -> 500);

    assertThat(testing.runWithSpan("parent", decorated::get)).isEqualTo(500);

    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
        .isEqualTo((outerFailure ? 1 : 0) + (innerFailure ? 1 : 0));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(outerFailure ? StatusData.error() : StatusData.unset()),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasParent(trace.getSpan(1))
                        .hasStatus(innerFailure ? StatusData.error() : StatusData.unset())));
  }

  @Test
  void completingStageInsideSameBreakerResultPredicateKeepsStageClassification() {
    CompletableFuture<Integer> future = new CompletableFuture<>();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(
        result -> {
          if (Integer.valueOf(200).equals(result)) {
            future.complete(500);
          }
          return Integer.valueOf(500).equals(result);
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<CompletionStage<Integer>> decoratedStage =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, () -> future);
    Supplier<Integer> decoratedSupplier =
        CircuitBreaker.decorateSupplier(circuitBreaker, () -> 200);

    testing.runWithSpan(
        "parent",
        () -> {
          CompletionStage<Integer> stage = decoratedStage.get();
          assertThat(decoratedSupplier.get()).isEqualTo(200);
          assertThat(stage.toCompletableFuture().join()).isEqualTo(500);
        });

    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error()),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())));
  }

  @Test
  void recursiveRawResultsDoNotChangeDecoratedOutcome() {
    CircuitBreaker[] holder = new CircuitBreaker[1];
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(
        result -> {
          if (Integer.valueOf(200).equals(result)) {
            holder[0].onResult(1, MILLISECONDS, 201);
          } else if (Integer.valueOf(201).equals(result)) {
            holder[0].onResult(1, MILLISECONDS, 500);
          }
          return Integer.valueOf(500).equals(result);
        });
    holder[0] = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<Integer> decorated = CircuitBreaker.decorateSupplier(holder[0], () -> 200);

    assertThat(testing.runWithSpan("parent", decorated::get)).isEqualTo(200);

    assertThat(holder[0].getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedCompletionStageTimestampFunctionThrows() {
    IllegalStateException exception = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.currentTimestampFunction(
        clock -> {
          throw exception;
        },
        NANOSECONDS);
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<CompletionStage<String>> supplier = () -> CompletableFuture.completedFuture("ok");
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", decoratedSupplier::get));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsFailureSpanWhenDecoratedCompletionStageSupplierThrows() {
    IllegalStateException exception = new IllegalStateException("boom");
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<CompletionStage<Integer>> decorated =
        CircuitBreaker.decorateCompletionStage(
            circuitBreaker,
            () -> {
              throw exception;
            });

    CompletionStage<Integer> stage = testing.runWithSpan("parent", decorated::get);

    Throwable thrown = catchThrowable(() -> stage.toCompletableFuture().join());
    assertThat(thrown).isInstanceOf(CompletionException.class).hasCause(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsFailureSpanWhenDecoratedCompletionStageSupplierCallbackThrows() {
    IllegalArgumentException originalException = new IllegalArgumentException("original");
    IllegalStateException callbackException = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordException(
        throwable -> {
          throw callbackException;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<CompletionStage<String>> supplier =
        () -> {
          throw originalException;
        };
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", decoratedSupplier::get));

    assertThat(thrown).isSameAs(callbackException);
    assertCircuitBreakerSpan("closed", "failure", callbackException);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedCompletionStageCompletesOnDifferentThread()
      throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicReference<Thread> completionThread = new AtomicReference<>();
    try {
      Thread callingThread = Thread.currentThread();
      CompletionStage<String> stage = testing.runWithSpan("parent", decoratedSupplier::get);
      executor
          .submit(
              () -> {
                completionThread.set(Thread.currentThread());
                return future.complete("ok");
              })
          .get();

      assertThat(completionThread.get()).isNotSameAs(callingThread);
      assertThat(stage.toCompletableFuture().get()).isEqualTo("ok");
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void circuitBreakerSpanIsParentOfDecoratedCompletionStageWork() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Supplier<CompletionStage<String>> supplier =
        () ->
            CompletableFuture.supplyAsync(
                () -> testing.runWithSpan("protected-operation", () -> "ok"), executor);
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);
    try {
      CompletionStage<String> stage = testing.runWithSpan("parent", decoratedSupplier::get);

      assertThat(stage.toCompletableFuture().get()).isEqualTo("ok");
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpanIsParentOfProtectedOperation("success");
  }

  @Test
  void decoratedCompletionStageIgnoresNestedRawAcquisitionOnCompletionThread() throws Exception {
    IllegalStateException exception = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    CircuitBreaker[] circuitBreakerHolder = new CircuitBreaker[1];
    AtomicReference<Thread> callbackThread = new AtomicReference<>();
    builder.recordResult(
        result -> {
          callbackThread.set(Thread.currentThread());
          circuitBreakerHolder[0].acquirePermission();
          invokeOnError(circuitBreakerHolder[0], exception);
          return false;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    circuitBreakerHolder[0] = circuitBreaker;
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Thread callingThread = Thread.currentThread();
    try {
      CompletionStage<String> stage = testing.runWithSpan("parent", decoratedSupplier::get);
      executor.submit(() -> future.complete("ok")).get();

      assertThat(stage.toCompletableFuture().get()).isEqualTo("ok");
      assertThat(callbackThread.get()).isNotSameAs(callingThread);
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsFailureSpanWhenDecoratedCompletionStageCompletesWithCompletionException()
      throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException cause = new IllegalStateException("boom");
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

    CompletionStage<String> stage = testing.runWithSpan("parent", decoratedSupplier::get);
    future.completeExceptionally(new CompletionException(cause));

    Throwable thrown = catchThrowable(() -> stage.toCompletableFuture().get());

    assertThat(thrown).isInstanceOf(ExecutionException.class);
    assertCircuitBreakerSpan("closed", "failure", cause);
  }

  @Test
  void createsCancelledSpanWhenDecoratedCompletionStageIsCancelled() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

    CompletionStage<String> stage = testing.runWithSpan("parent", decoratedSupplier::get);
    assertThat(future.cancel(true)).isTrue();

    Throwable thrown = catchThrowable(() -> stage.toCompletableFuture().get());

    assertThat(thrown).isInstanceOf(CancellationException.class);
    assertCircuitBreakerSpan("closed", "cancelled");
  }

  @Test
  void createsFailureSpanWhenDecoratedCompletionStageCancellationCallbackThrows() {
    IllegalStateException callbackException = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordException(
        throwable -> {
          throw callbackException;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

    assertThat(testing.runWithSpan("parent", decoratedSupplier::get)).isNotNull();
    assertThat(future.cancel(true)).isTrue();

    assertCircuitBreakerSpan("closed", "failure", callbackException);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedCompletionStageResultMatchesRecordResult()
      throws Exception {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = new CompletableFuture<>();
    Supplier<CompletionStage<Integer>> supplier = () -> future;
    Supplier<CompletionStage<Integer>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

    CompletionStage<Integer> stage = testing.runWithSpan("parent", decoratedSupplier::get);
    future.complete(500);

    assertThat(stage.toCompletableFuture().get()).isEqualTo(500);
    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void createsFailureSpanWhenDecoratedCompletionStageResultMatchesPredicateOnDifferentThread()
      throws Exception {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = new CompletableFuture<>();
    Supplier<CompletionStage<Integer>> supplier = () -> future;
    Supplier<CompletionStage<Integer>> decoratedSupplier =
        CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CompletionStage<Integer> stage = testing.runWithSpan("parent", decoratedSupplier::get);
      executor.submit(() -> future.complete(500)).get();

      assertThat(stage.toCompletableFuture().get()).isEqualTo(500);
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedFutureConsumedOnDifferentThread() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    CompletableFuture<String> future = CompletableFuture.completedFuture("ok");
    Supplier<Future<String>> supplier = () -> future;
    Supplier<Future<String>> decoratedSupplier =
        CircuitBreaker.decorateFuture(circuitBreaker, supplier);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicReference<Thread> getThread = new AtomicReference<>();
    try {
      Thread callingThread = Thread.currentThread();
      Future<String> decoratedFuture = testing.runWithSpan("parent", decoratedSupplier::get);
      String result =
          executor
              .submit(
                  () -> {
                    getThread.set(Thread.currentThread());
                    return decoratedFuture.get();
                  })
              .get();

      assertThat(getThread.get()).isNotSameAs(callingThread);
      assertThat(result).isEqualTo("ok");
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void circuitBreakerSpanIsParentOfDecoratedFutureWork() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Supplier<Future<String>> supplier =
        () -> executor.submit(() -> testing.runWithSpan("protected-operation", () -> "ok"));
    Supplier<Future<String>> decoratedSupplier =
        CircuitBreaker.decorateFuture(circuitBreaker, supplier);
    try {
      Future<String> decoratedFuture = testing.runWithSpan("parent", decoratedSupplier::get);

      assertThat(decoratedFuture.get()).isEqualTo("ok");
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpanIsParentOfProtectedOperation("success");
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedFutureResultMatchesRecordResult() throws Exception {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = CompletableFuture.completedFuture(500);
    Supplier<Future<Integer>> supplier = () -> future;
    Supplier<Future<Integer>> decoratedSupplier =
        CircuitBreaker.decorateFuture(circuitBreaker, supplier);

    Future<Integer> decoratedFuture = testing.runWithSpan("parent", decoratedSupplier::get);

    assertThat(decoratedFuture.get()).isEqualTo(500);
    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void createsFailureSpanWhenDecoratedFutureResultMatchesPredicateOnDifferentThread()
      throws Exception {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = CompletableFuture.completedFuture(500);
    Supplier<Future<Integer>> supplier = () -> future;
    Supplier<Future<Integer>> decoratedSupplier =
        CircuitBreaker.decorateFuture(circuitBreaker, supplier);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Integer> decoratedFuture = testing.runWithSpan("parent", decoratedSupplier::get);
      Integer result = executor.submit(() -> decoratedFuture.get()).get();

      assertThat(result).isEqualTo(500);
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,false",
    "false,true,false",
    "true,false,false",
    "true,true,false",
    "false,false,true",
    "false,true,true",
    "true,false,true",
    "true,true,true"
  })
  void concurrentFutureReadersDoNotEndSpanBeforeClassification(
      boolean ownerTimed, boolean followerTimed, boolean failure) throws Exception {
    CountDownLatch classificationStarted = new CountDownLatch(1);
    CountDownLatch releaseClassification = new CountDownLatch(1);
    AtomicInteger classifications = new AtomicInteger();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(
        result -> {
          classifications.incrementAndGet();
          classificationStarted.countDown();
          try {
            assertThat(releaseClassification.await(10, SECONDS)).isTrue();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          return failure;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future =
        new CompletableFuture<>() {
          @Override
          public Integer get() throws InterruptedException, ExecutionException {
            // An unrelated OnceConsumer must not grant this reader completion ownership.
            OnceConsumer.of(0).applyOnce(ignored -> {});
            return super.get();
          }

          @Override
          public Integer get(long timeout, TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            OnceConsumer.of(0).applyOnce(ignored -> {});
            return super.get(timeout, unit);
          }
        };
    future.complete(500);
    Supplier<Future<Integer>> decorated =
        CircuitBreaker.decorateFuture(circuitBreaker, () -> future);
    Future<Integer> decoratedFuture = testing.runWithSpan("parent", decorated::get);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> owner =
          executor.submit(
              () -> ownerTimed ? decoratedFuture.get(10, SECONDS) : decoratedFuture.get());
      assertThat(classificationStarted.await(10, SECONDS)).isTrue();
      Future<Integer> follower =
          executor.submit(
              () -> followerTimed ? decoratedFuture.get(10, SECONDS) : decoratedFuture.get());
      assertThat(follower.get(10, SECONDS)).isEqualTo(500);
      releaseClassification.countDown();
      assertThat(owner.get(10, SECONDS)).isEqualTo(500);
    } finally {
      releaseClassification.countDown();
      executor.shutdownNow();
    }

    assertThat(classifications.get()).isEqualTo(1);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(failure ? 1 : 0);
    assertCircuitBreakerSpan("closed", failure ? "failure" : "success");
  }

  @ParameterizedTest
  @MethodSource("futureGetFailures")
  void decoratedFutureGetEndsSpan(Throwable exception, String outcome, Throwable expectedException)
      throws Exception {
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(decoratedFuture::get);

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", outcome, expectedException);
  }

  private static Stream<Arguments> futureGetFailures() {
    IllegalStateException cause = new IllegalStateException("boom");
    ExecutionException executionException = new ExecutionException(cause);
    IllegalStateException runtimeException = new IllegalStateException("boom");
    return Stream.of(
        argumentSet("cancellation", new CancellationException("boom"), "cancelled", null),
        argumentSet("interruption", new InterruptedException("boom"), "cancelled", null),
        argumentSet("execution failure", executionException, "failure", cause),
        argumentSet("runtime failure", runtimeException, "failure", runtimeException),
        argumentSet("error", new AssertionError("boom"), "failure", null));
  }

  @ParameterizedTest
  @MethodSource("futureTimedGetFailures")
  void decoratedFutureTimedGetEndsSpan(Throwable exception) throws Exception {
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(() -> decoratedFuture.get(1, MILLISECONDS));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  private static Stream<Arguments> futureTimedGetFailures() {
    return Stream.of(
        argumentSet("timeout", new TimeoutException("boom")),
        argumentSet("runtime failure", new IllegalStateException("boom")),
        argumentSet("error", new AssertionError("boom")));
  }

  @Test
  void doesNotCreateSpanForDirectOnError() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          invokeOnError(circuitBreaker, exception);
        });

    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void rawAcquisitionDoesNotBecomeFollowingDecoratedAttempt() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, () -> "ok");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          assertThat(decorated.get()).isEqualTo("ok");
          invokeOnSuccess(circuitBreaker);
        });

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void doesNotCreateSpanForRawAcquisitionsOnDifferentBreakers() {
    CircuitBreaker circuitBreakerA = CircuitBreaker.ofDefaults("a-circuit-breaker");
    CircuitBreaker circuitBreakerB = CircuitBreaker.ofDefaults("b-circuit-breaker");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreakerA.acquirePermission();
          circuitBreakerB.acquirePermission();
          invokeOnSuccess(circuitBreakerB);
          invokeOnSuccess(circuitBreakerA);
        });

    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void doesNotCreateSpanWhenDirectOnErrorCallbackThrows() {
    IllegalArgumentException originalException = new IllegalArgumentException("original");
    IllegalStateException callbackException = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordException(
        throwable -> {
          throw callbackException;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      circuitBreaker.acquirePermission();
                      invokeOnError(circuitBreaker, originalException);
                    }));

    assertThat(thrown).isSameAs(callbackException);
    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void doesNotCreateSpansForOverlappingRawAttempts() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          circuitBreaker.acquirePermission();
          invokeOnSuccess(circuitBreaker);
          invokeOnError(circuitBreaker, exception);
        });

    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void decoratedSupplierIgnoresNestedRawAttemptForSameBreaker() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");
    Supplier<String> decorated =
        CircuitBreaker.decorateSupplier(
            circuitBreaker,
            () -> {
              circuitBreaker.acquirePermission();
              invokeOnError(circuitBreaker, exception);
              return "ok";
            });

    String result = testing.runWithSpan("parent", decorated::get);

    assertThat(result).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedSupplierSucceeds() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<String> supplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> "ok");

    String result = testing.runWithSpan("parent", supplier::get);

    assertThat(result).isEqualTo("ok");
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void checkedSupplierWithNonPublicSubinterfaceSucceeds() throws Throwable {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    LocalCheckedSupplier supplier = () -> "ok";
    CheckedSupplier<String> decorated =
        CircuitBreaker.decorateCheckedSupplier(circuitBreaker, supplier);

    assertThat(testing.runWithSpan("parent", decorated::get)).isEqualTo("ok");

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void checkedSupplierWithNonPublicSubinterfaceSucceedsWithoutParent() throws Throwable {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    LocalCheckedSupplier supplier = () -> "ok";
    CheckedSupplier<String> decorated =
        CircuitBreaker.decorateCheckedSupplier(circuitBreaker, supplier);

    assertThat(decorated.get()).isEqualTo("ok");

    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void createsCircuitBreakerSpanWhenUncheckedCheckedSupplierThrowsError() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    AssertionError error = new AssertionError("boom");
    CheckedSupplier<?> checkedSupplier =
        CircuitBreaker.decorateCheckedSupplier(
            circuitBreaker,
            () -> {
              throw error;
            });
    Supplier<?> supplier = checkedSupplier.unchecked();

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", supplier::get));

    assertThat(thrown).isSameAs(error);
    assertCircuitBreakerSpan("closed", "failure", error);
  }

  @Test
  void checkedSupplierReturningSupplierPreservesApplicationResultIdentity() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<String> applicationSupplier = () -> "ok";
    CheckedSupplier<Supplier<String>> checkedSupplier =
        CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> applicationSupplier);
    Supplier<Supplier<String>> supplier = checkedSupplier.unchecked();

    Object result = testing.runWithSpan("parent", supplier::get);

    assertThat(result).isSameAs(applicationSupplier);
    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void checkedProxyObjectMethodsDoNotCreateCircuitBreakerSpans() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Object checkedSupplier = CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> "ok");

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(checkedSupplier.equals(checkedSupplier)).isTrue();
          assertThat(checkedSupplier.hashCode())
              .isEqualTo(System.identityHashCode(checkedSupplier));
          assertThat(checkedSupplier.toString()).isNotNull();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  void createsCircuitBreakerSpanWhenAcquirePermissionRejected() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    circuitBreaker.transitionToOpenState();

    Throwable thrown =
        catchThrowable(
            () -> testing.runWithSpan("parent", () -> circuitBreaker.executeSupplier(() -> "ok")));

    assertThat(thrown).isInstanceOf(CallNotPermittedException.class);
    assertCircuitBreakerSpan("open", "rejected", thrown);
  }

  @Test
  void doesNotCreateSpanWhenDirectTryAcquirePermissionRejected() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    circuitBreaker.transitionToOpenState();

    boolean permitted = testing.runWithSpan("parent", circuitBreaker::tryAcquirePermission);

    assertThat(permitted).isFalse();
    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void doesNotCreateSpanWhenDirectAcquirePermissionRejected() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    circuitBreaker.transitionToOpenState();

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", circuitBreaker::acquirePermission));

    assertThat(thrown).isInstanceOf(CallNotPermittedException.class);
    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void createsCircuitBreakerSpanWhenIgnoredCallFails() {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom().ignoreExceptions(IllegalArgumentException.class).build();
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", config);
    IllegalArgumentException exception = new IllegalArgumentException("boom");

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        circuitBreaker.executeSupplier(
                            () -> {
                              throw exception;
                            })));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void doesNotCreateSpanWhenDirectPermissionReleased() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
          circuitBreaker.releasePermission();
        });

    assertThat(testing.spans()).extracting(span -> span.getName()).containsExactly("parent");
  }

  @Test
  void doesNotCreateSpanWithoutActiveSpan() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    assertThat(circuitBreaker.executeSupplier(() -> "ok")).isEqualTo("ok");

    assertThat(testing.spans()).isEmpty();
  }

  private static void invokeOnSuccess(CircuitBreaker circuitBreaker) {
    circuitBreaker.onSuccess(1L, MILLISECONDS);
  }

  private static void invokeOnError(CircuitBreaker circuitBreaker, Throwable throwable) {
    circuitBreaker.onError(1L, MILLISECONDS, throwable);
  }

  private static <T> Future<T> decoratedFuture(Future<T> future) throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<Future<T>> supplier = () -> future;
    Supplier<Future<T>> decoratedSupplier = CircuitBreaker.decorateFuture(circuitBreaker, supplier);
    return testing.runWithSpan("parent", decoratedSupplier::get);
  }

  private interface LocalCheckedSupplier extends CheckedSupplier<String> {
    @Override
    String get() throws Throwable;
  }

  private static final class ThrowingFuture<T> implements Future<T> {

    private final Throwable exception;

    private ThrowingFuture(Throwable exception) {
      this.exception = exception;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      throwException();
      return null;
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      if (exception instanceof TimeoutException timeoutException) {
        throw timeoutException;
      }
      throwException();
      return null;
    }

    private void throwException() throws InterruptedException, ExecutionException {
      if (exception instanceof InterruptedException interruptedException) {
        throw interruptedException;
      }
      if (exception instanceof ExecutionException executionException) {
        throw executionException;
      }
      if (exception instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (exception instanceof Error error) {
        throw error;
      }
      throw new AssertionError(exception);
    }
  }

  private static void assertCircuitBreakerSpanIsParentOfProtectedOperation(String outcome) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                experimental("test-circuit-breaker")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.state"),
                                experimental("closed")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.outcome"),
                                experimental(outcome))),
                span ->
                    span.hasName("protected-operation")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))));
  }

  private static void assertCircuitBreakerSpan(String state, String outcome) {
    assertCircuitBreakerSpan(state, outcome, null);
  }

  private static void assertCircuitBreakerSpan(
      String state, String outcome, Throwable expectedException) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> {
                  span.hasName("CircuitBreaker test-circuit-breaker")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(
                              stringKey("resilience4j.circuit_breaker.name"),
                              experimental("test-circuit-breaker")),
                          equalTo(
                              stringKey("resilience4j.circuit_breaker.state"), experimental(state)),
                          equalTo(
                              stringKey("resilience4j.circuit_breaker.outcome"),
                              experimental(outcome)));
                  if ("success".equals(outcome) || "cancelled".equals(outcome)) {
                    return;
                  }
                  span.hasStatus(StatusData.error());
                  if (expectedException != null) {
                    span.hasException(expectedException);
                  }
                }));
  }

  private static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }
}
