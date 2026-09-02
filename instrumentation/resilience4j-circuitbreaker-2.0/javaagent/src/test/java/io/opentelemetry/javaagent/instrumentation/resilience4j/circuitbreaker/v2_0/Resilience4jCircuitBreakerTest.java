/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0.ExperimentalTestHelper.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Resilience4jCircuitBreakerTest {

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
  void createsCircuitBreakerSpanWhenOnSuccessCalledDirectly() throws Exception {
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
  void createsCircuitBreakerSpanWhenOnResultMatchesRecordResult() throws Exception {
    Method recordResult = recordResultMethod();
    Method onResult = onResultMethod();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordResult.invoke(builder, (Predicate<Object>) result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          // Verifies the recordResult -> ResultRecordedAsFailureException ->
          // publishCircuitErrorEvent path is captured as a failure span.
          onResult.invoke(circuitBreaker, 1L, MILLISECONDS, 500);
        });

    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void createsFailureSpanWhenOnResultTransitionThrows() throws Exception {
    Method transitionOnResult = transitionOnResultMethod();
    Method onResult = onResultMethod();
    IllegalStateException exception = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    transitionOnResult.invoke(
        builder,
        (Function<Object, Object>)
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
                      onResult.invoke(circuitBreaker, 1L, MILLISECONDS, 500);
                    }));

    assertThat(thrown).isInstanceOf(InvocationTargetException.class).hasCause(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsFailureSpanWhenOnResultRecordsFailureBeforeTransitionCheck() throws Exception {
    Method recordResult = recordResultMethod();
    Method transitionOnResult = transitionOnResultMethod();
    Method onResult = onResultMethod();
    AtomicReference<Object> transitionResult = new AtomicReference<>();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordResult.invoke(builder, (Predicate<Object>) result -> true);
    transitionOnResult.invoke(
        builder,
        (Function<Object, Object>)
            result -> {
              transitionResult.set(result);
              return noTransitionResult();
            });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          onResult.invoke(circuitBreaker, 1L, MILLISECONDS, 500);
        });

    assertThat(transitionResult.get()).isNull();
    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void onResultOnlySuppressesOnSuccessForSameCircuitBreaker() throws Exception {
    Method transitionOnResult = transitionOnResultMethod();
    CircuitBreaker innerCircuitBreaker = CircuitBreaker.ofDefaults("inner-circuit-breaker");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    transitionOnResult.invoke(
        builder,
        (Function<Object, Object>)
            result -> {
              innerCircuitBreaker.acquirePermission();
              invokeOnSuccessUnchecked(innerCircuitBreaker);
              return noTransitionResult();
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
    assertThat(testing.spans())
        .extracting(span -> span.getName())
        .contains("CircuitBreaker inner-circuit-breaker", "CircuitBreaker outer-circuit-breaker");
  }

  @Test
  void onResultDoesNotSuppressNestedAttemptForSameCircuitBreaker() throws Exception {
    Method transitionOnResult = transitionOnResultMethod();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    CircuitBreaker[] circuitBreakerHolder = new CircuitBreaker[1];
    transitionOnResult.invoke(
        builder,
        (Function<Object, Object>)
            result -> {
              circuitBreakerHolder[0].acquirePermission();
              invokeOnSuccessUnchecked(circuitBreakerHolder[0]);
              return noTransitionResult();
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
    assertThat(testing.spans())
        .extracting(span -> span.getName())
        .containsExactlyInAnyOrder(
            "parent", "CircuitBreaker test-circuit-breaker", "CircuitBreaker test-circuit-breaker");
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsFailureSpanWhenDecoratedCompletionStageTimestampFunctionThrows() throws Exception {
    Method currentTimestampFunction;
    try {
      currentTimestampFunction =
          CircuitBreakerConfig.Builder.class.getMethod(
              "currentTimestampFunction", Function.class, TimeUnit.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "currentTimestampFunction is not available in this Resilience4j version");
      throw e;
    }
    Method decorateCompletionStage = decorateCompletionStageMethod();
    IllegalStateException exception = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    currentTimestampFunction.invoke(
        builder,
        (Function<Clock, Long>)
            clock -> {
              throw exception;
            },
        NANOSECONDS);
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    Supplier<CompletionStage<String>> supplier = () -> CompletableFuture.completedFuture("ok");
    Supplier<CompletionStage<String>> decoratedSupplier =
        (Supplier<CompletionStage<String>>)
            decorateCompletionStage.invoke(null, circuitBreaker, supplier);

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", decoratedSupplier::get));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsCircuitBreakerSpanWhenDecoratedCompletionStageCompletesOnDifferentThread()
      throws Exception {
    Method decorateCompletionStage = decorateCompletionStageMethod();
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        (Supplier<CompletionStage<String>>)
            decorateCompletionStage.invoke(null, circuitBreaker, supplier);
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
  @SuppressWarnings("unchecked")
  void circuitBreakerSpanIsParentOfDecoratedCompletionStageWork() throws Exception {
    Method decorateCompletionStage = decorateCompletionStageMethod();
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Supplier<CompletionStage<String>> supplier =
        () ->
            CompletableFuture.supplyAsync(
                () -> testing.runWithSpan("protected-operation", () -> "ok"), executor);
    Supplier<CompletionStage<String>> decoratedSupplier =
        (Supplier<CompletionStage<String>>)
            decorateCompletionStage.invoke(null, circuitBreaker, supplier);
    try {
      CompletionStage<String> stage = testing.runWithSpan("parent", decoratedSupplier::get);

      assertThat(stage.toCompletableFuture().get()).isEqualTo("ok");
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpanIsParentOfProtectedOperation("success");
  }

  @Test
  @SuppressWarnings("unchecked")
  void decoratedCompletionStageAsyncCallbackPrefersNestedRawAcquisitionForSameBreaker()
      throws Exception {
    Method recordResult = recordResultMethod();
    Method decorateCompletionStage = decorateCompletionStageMethod();
    IllegalStateException exception = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    CircuitBreaker[] circuitBreakerHolder = new CircuitBreaker[1];
    AtomicReference<Thread> callbackThread = new AtomicReference<>();
    recordResult.invoke(
        builder,
        (Predicate<Object>)
            result -> {
              callbackThread.set(Thread.currentThread());
              circuitBreakerHolder[0].acquirePermission();
              invokeOnErrorUnchecked(circuitBreakerHolder[0], exception);
              return false;
            });
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    circuitBreakerHolder[0] = circuitBreaker;
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        (Supplier<CompletionStage<String>>)
            decorateCompletionStage.invoke(null, circuitBreaker, supplier);

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
  @SuppressWarnings("unchecked")
  void createsFailureSpanWhenDecoratedCompletionStageCompletesWithCompletionException()
      throws Exception {
    Method decorateCompletionStage = decorateCompletionStageMethod();
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException cause = new IllegalStateException("boom");
    CompletableFuture<String> future = new CompletableFuture<>();
    Supplier<CompletionStage<String>> supplier = () -> future;
    Supplier<CompletionStage<String>> decoratedSupplier =
        (Supplier<CompletionStage<String>>)
            decorateCompletionStage.invoke(null, circuitBreaker, supplier);

    CompletionStage<String> stage = testing.runWithSpan("parent", decoratedSupplier::get);
    future.completeExceptionally(new CompletionException(cause));

    Throwable thrown = catchThrowable(() -> stage.toCompletableFuture().get());

    assertThat(thrown).isInstanceOf(ExecutionException.class);
    assertCircuitBreakerSpan("closed", "failure", cause);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsCircuitBreakerSpanWhenDecoratedCompletionStageResultMatchesRecordResult()
      throws Exception {
    Method recordResult = recordResultMethod();
    Method decorateCompletionStage = decorateCompletionStageMethod();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordResult.invoke(builder, (Predicate<Object>) result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = new CompletableFuture<>();
    Supplier<CompletionStage<Integer>> supplier = () -> future;
    Supplier<CompletionStage<Integer>> decoratedSupplier =
        (Supplier<CompletionStage<Integer>>)
            decorateCompletionStage.invoke(null, circuitBreaker, supplier);

    CompletionStage<Integer> stage = testing.runWithSpan("parent", decoratedSupplier::get);
    future.complete(500);

    assertThat(stage.toCompletableFuture().get()).isEqualTo(500);
    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsFailureSpanWhenDecoratedCompletionStageResultMatchesPredicateOnDifferentThread()
      throws Exception {
    Method recordResult = recordResultMethod();
    Method decorateCompletionStage = decorateCompletionStageMethod();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordResult.invoke(builder, (Predicate<Object>) result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = new CompletableFuture<>();
    Supplier<CompletionStage<Integer>> supplier = () -> future;
    Supplier<CompletionStage<Integer>> decoratedSupplier =
        (Supplier<CompletionStage<Integer>>)
            decorateCompletionStage.invoke(null, circuitBreaker, supplier);
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
  @SuppressWarnings("unchecked")
  void createsCircuitBreakerSpanWhenDecoratedFutureConsumedOnDifferentThread() throws Exception {
    Method decorateFuture = decorateFutureMethod();
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    CompletableFuture<String> future = CompletableFuture.completedFuture("ok");
    Supplier<Future<String>> supplier = () -> future;
    Supplier<Future<String>> decoratedSupplier =
        (Supplier<Future<String>>) decorateFuture.invoke(null, circuitBreaker, supplier);
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
  @SuppressWarnings("unchecked")
  void circuitBreakerSpanIsParentOfDecoratedFutureWork() throws Exception {
    Method decorateFuture = decorateFutureMethod();
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Supplier<Future<String>> supplier =
        () -> executor.submit(() -> testing.runWithSpan("protected-operation", () -> "ok"));
    Supplier<Future<String>> decoratedSupplier =
        (Supplier<Future<String>>) decorateFuture.invoke(null, circuitBreaker, supplier);
    try {
      Future<String> decoratedFuture = testing.runWithSpan("parent", decoratedSupplier::get);

      assertThat(decoratedFuture.get()).isEqualTo("ok");
    } finally {
      executor.shutdownNow();
    }

    assertCircuitBreakerSpanIsParentOfProtectedOperation("success");
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsCircuitBreakerSpanWhenDecoratedFutureResultMatchesRecordResult() throws Exception {
    Method recordResult = recordResultMethod();
    Method decorateFuture = decorateFutureMethod();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordResult.invoke(builder, (Predicate<Object>) result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = CompletableFuture.completedFuture(500);
    Supplier<Future<Integer>> supplier = () -> future;
    Supplier<Future<Integer>> decoratedSupplier =
        (Supplier<Future<Integer>>) decorateFuture.invoke(null, circuitBreaker, supplier);

    Future<Integer> decoratedFuture = testing.runWithSpan("parent", decoratedSupplier::get);

    assertThat(decoratedFuture.get()).isEqualTo(500);
    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsFailureSpanWhenDecoratedFutureResultMatchesPredicateOnDifferentThread()
      throws Exception {
    Method recordResult = recordResultMethod();
    Method decorateFuture = decorateFutureMethod();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordResult.invoke(builder, (Predicate<Object>) result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());
    CompletableFuture<Integer> future = CompletableFuture.completedFuture(500);
    Supplier<Future<Integer>> supplier = () -> future;
    Supplier<Future<Integer>> decoratedSupplier =
        (Supplier<Future<Integer>>) decorateFuture.invoke(null, circuitBreaker, supplier);
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

  @Test
  void createsCancelledSpanWhenDecoratedFutureGetThrowsCancellationException() throws Exception {
    CancellationException exception = new CancellationException("boom");
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(decoratedFuture::get);

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "cancelled");
  }

  @Test
  void createsCancelledSpanWhenDecoratedFutureGetThrowsInterruptedException() throws Exception {
    InterruptedException exception = new InterruptedException("boom");
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(decoratedFuture::get);

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "cancelled");
  }

  @Test
  void createsFailureSpanWhenDecoratedFutureGetThrowsExecutionException() throws Exception {
    IllegalStateException cause = new IllegalStateException("boom");
    ExecutionException exception = new ExecutionException(cause);
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(decoratedFuture::get);

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", cause);
  }

  @Test
  void createsCircuitBreakerSpanWhenDecoratedFutureGetThrowsRuntimeException() throws Exception {
    IllegalStateException exception = new IllegalStateException("boom");
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(decoratedFuture::get);

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsFailureSpanWhenDecoratedFutureTimedGetThrowsTimeoutException() throws Exception {
    TimeoutException exception = new TimeoutException("boom");
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(() -> decoratedFuture.get(1, MILLISECONDS));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsFailureSpanWhenDecoratedFutureTimedGetThrowsRuntimeException() throws Exception {
    IllegalStateException exception = new IllegalStateException("boom");
    Future<String> decoratedFuture = decoratedFuture(new ThrowingFuture<>(exception));

    Throwable thrown = catchThrowable(() -> decoratedFuture.get(1, MILLISECONDS));

    assertThat(thrown).isSameAs(exception);
    assertCircuitBreakerSpan("closed", "failure", exception);
  }

  @Test
  void createsCircuitBreakerSpanWhenOnErrorCalledDirectly() throws Exception {
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
  void rawRecentAcquisitionsAreTrackedPerCircuitBreaker() throws Exception {
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
  void releasePermissionDoesNotSuppressNestedAttemptForSameCircuitBreaker() throws Exception {
    Method recordException = recordExceptionMethod();
    IllegalArgumentException outerException = new IllegalArgumentException("outer");
    CircuitBreaker[] circuitBreakerHolder = new CircuitBreaker[1];
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordException.invoke(
        builder,
        (Predicate<Throwable>)
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
  void createsFailureSpanWhenOnErrorCallbackThrows() throws Exception {
    Method recordException = recordExceptionMethod();
    IllegalArgumentException originalException = new IllegalArgumentException("original");
    IllegalStateException callbackException = new IllegalStateException("boom");
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordException.invoke(
        builder,
        (Predicate<Throwable>)
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
  void rawOutOfOrderCallbacksRecordRecentSameThreadAttempts() throws Exception {
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
  void decoratedSupplierDoesNotEndNestedRawAttemptForSameBreaker() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");
    Supplier<String> decorated =
        CircuitBreaker.decorateSupplier(
            circuitBreaker,
            () -> {
              circuitBreaker.acquirePermission();
              invokeOnErrorUnchecked(circuitBreaker, exception);
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
  void createsCircuitBreakerSpanWhenUncheckedCheckedSupplierThrowsError() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    AssertionError error = new AssertionError("boom");
    Object checkedSupplier =
        CircuitBreaker.decorateCheckedSupplier(
            circuitBreaker,
            () -> {
              throw error;
            });
    Method unchecked = uncheckedMethod(checkedSupplier);
    Supplier<?> supplier = (Supplier<?>) unchecked.invoke(checkedSupplier);

    Throwable thrown = catchThrowable(() -> testing.runWithSpan("parent", supplier::get));

    assertThat(thrown).isSameAs(error);
    assertCircuitBreakerSpan("closed", "failure", error);
  }

  @Test
  void checkedSupplierReturningSupplierPreservesApplicationResultIdentity() throws Exception {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<String> applicationSupplier = () -> "ok";
    Object checkedSupplier =
        CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> applicationSupplier);
    Method unchecked = uncheckedMethod(checkedSupplier);
    Supplier<?> supplier = (Supplier<?>) unchecked.invoke(checkedSupplier);

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

  private static void invokeOnSuccessUnchecked(CircuitBreaker circuitBreaker) {
    circuitBreaker.onSuccess(1L, MILLISECONDS);
  }

  private static void invokeOnSuccess(CircuitBreaker circuitBreaker) {
    circuitBreaker.onSuccess(1L, MILLISECONDS);
  }

  private static void invokeOnErrorUnchecked(CircuitBreaker circuitBreaker, Throwable throwable) {
    circuitBreaker.onError(1L, MILLISECONDS, throwable);
  }

  private static void invokeOnError(CircuitBreaker circuitBreaker, Throwable throwable) {
    circuitBreaker.onError(1L, MILLISECONDS, throwable);
  }

  private static Method recordResultMethod() throws NoSuchMethodException {
    try {
      return CircuitBreakerConfig.Builder.class.getMethod("recordResult", Predicate.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "recordResult is not available in this Resilience4j version");
      throw e;
    }
  }

  private static Method recordExceptionMethod() throws NoSuchMethodException {
    try {
      return CircuitBreakerConfig.Builder.class.getMethod("recordException", Predicate.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "recordException is not available in this Resilience4j version");
      throw e;
    }
  }

  private static Method transitionOnResultMethod() throws NoSuchMethodException {
    try {
      return CircuitBreakerConfig.Builder.class.getMethod("transitionOnResult", Function.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "transitionOnResult is not available in this Resilience4j version");
      throw e;
    }
  }

  private static Method uncheckedMethod(Object checkedSupplier) throws NoSuchMethodException {
    try {
      return checkedSupplier.getClass().getMethod("unchecked");
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "unchecked is not available in this Resilience4j version");
      throw e;
    }
  }

  private static Method decorateFutureMethod() throws NoSuchMethodException {
    try {
      return CircuitBreaker.class.getMethod("decorateFuture", CircuitBreaker.class, Supplier.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "decorateFuture is not available in this Resilience4j version");
      throw e;
    }
  }

  private static Method decorateCompletionStageMethod() throws NoSuchMethodException {
    try {
      return CircuitBreaker.class.getMethod(
          "decorateCompletionStage", CircuitBreaker.class, Supplier.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "decorateCompletionStage is not available in this Resilience4j version");
      throw e;
    }
  }

  private static Method onResultMethod() throws NoSuchMethodException {
    try {
      return CircuitBreaker.class.getMethod("onResult", long.class, TimeUnit.class, Object.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "onResult is not available in this Resilience4j version");
      throw e;
    }
  }

  private static Object noTransitionResult() {
    try {
      Class<?> transitionCheckResult =
          Class.forName(
              "io.github.resilience4j.circuitbreaker.CircuitBreakerConfig$TransitionCheckResult");
      return transitionCheckResult.getMethod("noTransition").invoke(null);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static <T> Future<T> decoratedFuture(Future<T> future) throws Exception {
    Method decorateFuture = decorateFutureMethod();
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Supplier<Future<T>> supplier = () -> future;
    @SuppressWarnings("unchecked")
    Supplier<Future<T>> decoratedSupplier =
        (Supplier<Future<T>>) decorateFuture.invoke(null, circuitBreaker, supplier);
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
      if (exception instanceof TimeoutException) {
        throw (TimeoutException) exception;
      }
      throwException();
      return null;
    }

    private void throwException() throws InterruptedException, ExecutionException {
      if (exception instanceof InterruptedException) {
        throw (InterruptedException) exception;
      }
      if (exception instanceof ExecutionException) {
        throw (ExecutionException) exception;
      }
      if (exception instanceof RuntimeException) {
        throw (RuntimeException) exception;
      }
      if (exception instanceof Error) {
        throw (Error) exception;
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
}
