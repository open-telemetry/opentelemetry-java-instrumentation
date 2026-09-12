/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.TransitionCheckResult;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
  void createsCircuitBreakerSpanWhenOnSuccessCalledDirectly() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          invokeOnSuccess(circuitBreaker);
        });

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsCircuitBreakerSpanWhenOnResultMatchesRecordResult() {
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          // Verifies the recordResult -> ResultRecordedAsFailureException ->
          // publishCircuitErrorEvent path is captured as a failure span.
          circuitBreaker.onResult(1L, MILLISECONDS, 500);
        });

    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void createsFailureSpanWhenOnResultTransitionThrows() {
    IllegalStateException exception = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.transitionOnResult(
        result -> {
          throw exception;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      circuitBreaker.acquirePermission();
                      circuitBreaker.onResult(1L, MILLISECONDS, 500);
                    }));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsFailureSpanWhenOnResultRecordsFailureBeforeTransitionCheck() {
    AtomicReference<Object> transitionResult = new AtomicReference<>();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordResult(result -> true);
    builder.transitionOnResult(
        result -> {
          transitionResult.set(result);
          return TransitionCheckResult.noTransition();
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          circuitBreaker.onResult(1L, MILLISECONDS, 500);
        });

    assertThat(transitionResult.get()).isNull();
    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void onResultOnlySuppressesOnSuccessForSameCircuitBreaker() {
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
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker outer-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                experimental("outer-circuit-breaker")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.state"),
                                experimental("closed")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.outcome"),
                                experimental("success"))),
                span ->
                    span.hasName("CircuitBreaker inner-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                experimental("inner-circuit-breaker")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.state"),
                                experimental("closed")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.outcome"),
                                experimental("success")))));
  }

  @Test
  void onResultDoesNotSuppressNestedAttemptForSameCircuitBreaker() {
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
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                experimental("test-circuit-breaker")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.state"),
                                experimental("closed")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.outcome"),
                                experimental("success")))));
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
  void decoratedCompletionStageAsyncCallbackPrefersNestedRawAcquisitionForSameBreaker()
      throws Exception {
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

    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.unset()))
        .singleElement()
        .satisfies(
            span ->
                assertThat(
                        span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                    .isEqualTo(experimental("success")));
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.error()))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(
                      span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                  .isEqualTo(experimental("failure"));
              assertThat(span.getEvents())
                  .singleElement()
                  .satisfies(
                      event -> {
                        assertThat(event.getAttributes().get(stringKey("exception.type")))
                            .isEqualTo(IllegalStateException.class.getName());
                        assertThat(event.getAttributes().get(stringKey("exception.message")))
                            .isEqualTo("boom");
                      });
            });
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
        argumentSet("runtime failure", runtimeException, "failure", runtimeException));
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
        argumentSet("runtime failure", new IllegalStateException("boom")));
  }

  @Test
  void createsCircuitBreakerSpanWhenOnErrorCalledDirectly() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          invokeOnError(circuitBreaker, exception);
        });

    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void rawRecentAcquisitionsAreTrackedPerCircuitBreaker() {
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker a-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                experimental("a-circuit-breaker")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.state"),
                                experimental("closed")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.outcome"),
                                experimental("success"))),
                span ->
                    span.hasName("CircuitBreaker b-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                experimental("b-circuit-breaker")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.state"),
                                experimental("closed")),
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.outcome"),
                                experimental("success")))));
  }

  @Test
  void releasePermissionDoesNotSuppressNestedAttemptForSameCircuitBreaker() {
    IllegalArgumentException outerException = new IllegalArgumentException("outer");
    CircuitBreaker[] circuitBreakerHolder = new CircuitBreaker[1];
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    builder.recordException(
        throwable -> {
          circuitBreakerHolder[0].acquirePermission();
          circuitBreakerHolder[0].releasePermission();
          return true;
        });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    circuitBreakerHolder[0] = circuitBreaker;

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          invokeOnError(circuitBreaker, outerException);
        });

    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.unset()))
        .singleElement()
        .satisfies(
            span ->
                assertThat(
                        span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                    .isEqualTo(experimental("cancelled")));
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.error()))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(
                      span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                  .isEqualTo(experimental("failure"));
              assertThat(span.getEvents())
                  .singleElement()
                  .satisfies(
                      event -> {
                        assertThat(event.getAttributes().get(stringKey("exception.type")))
                            .isEqualTo(outerException.getClass().getName());
                        assertThat(event.getAttributes().get(stringKey("exception.message")))
                            .isEqualTo(outerException.getMessage());
                      });
            });
  }

  @Test
  void createsFailureSpanWhenOnErrorCallbackThrows() {
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
    assertCircuitBreakerSpan("closed", "failure", callbackException);
  }

  @Test
  void rawOutOfOrderCallbacksRecordRecentSameThreadAttempts() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");

    testing.runWithSpan(
        "parent",
        () -> {
          // Raw callbacks have no attempt identity. Same-thread correlation keeps a recent stack,
          // which avoids drops but still cannot prove application-level identity for overlapping
          // raw attempts on the same breaker.
          circuitBreaker.acquirePermission();
          circuitBreaker.acquirePermission();
          invokeOnSuccess(circuitBreaker);
          invokeOnError(circuitBreaker, exception);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.unset()))
        .singleElement()
        .satisfies(
            span ->
                assertThat(
                        span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                    .isEqualTo(experimental("success")));
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.error()))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(
                      span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                  .isEqualTo(experimental("failure"));
              assertThat(span.getEvents())
                  .singleElement()
                  .satisfies(
                      event -> {
                        assertThat(event.getAttributes().get(stringKey("exception.type")))
                            .isEqualTo(IllegalStateException.class.getName());
                        assertThat(event.getAttributes().get(stringKey("exception.message")))
                            .isEqualTo("boom");
                      });
            });
  }

  @Test
  void decoratedSupplierDoesNotEndNestedRawAttemptForSameBreaker() {
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
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("CircuitBreaker test-circuit-breaker")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))));
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.unset()))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getEvents()).isEmpty();
              assertThat(span.getAttributes().get(stringKey("resilience4j.circuit_breaker.name")))
                  .isEqualTo(experimental("test-circuit-breaker"));
              assertThat(span.getAttributes().get(stringKey("resilience4j.circuit_breaker.state")))
                  .isEqualTo(experimental("closed"));
              assertThat(
                      span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                  .isEqualTo(experimental("success"));
            });
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("CircuitBreaker test-circuit-breaker"))
        .filteredOn(span -> span.getStatus().equals(StatusData.error()))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(stringKey("resilience4j.circuit_breaker.name")))
                  .isEqualTo(experimental("test-circuit-breaker"));
              assertThat(span.getAttributes().get(stringKey("resilience4j.circuit_breaker.state")))
                  .isEqualTo(experimental("closed"));
              assertThat(
                      span.getAttributes().get(stringKey("resilience4j.circuit_breaker.outcome")))
                  .isEqualTo(experimental("failure"));
              assertThat(span.getEvents())
                  .singleElement()
                  .satisfies(
                      event -> {
                        assertThat(event.getAttributes().get(stringKey("exception.type")))
                            .isEqualTo(IllegalStateException.class.getName());
                        assertThat(event.getAttributes().get(stringKey("exception.message")))
                            .isEqualTo("boom");
                      });
            });
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
  void createsCircuitBreakerSpanWhenTryAcquirePermissionRejected() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    circuitBreaker.transitionToOpenState();

    boolean permitted = testing.runWithSpan("parent", circuitBreaker::tryAcquirePermission);

    assertThat(permitted).isFalse();
    assertCircuitBreakerSpan("open", "rejected", null);
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
  void createsCircuitBreakerSpanWhenPermissionReleased() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
          circuitBreaker.releasePermission();
        });

    assertCircuitBreakerSpan("closed", "cancelled", null);
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
