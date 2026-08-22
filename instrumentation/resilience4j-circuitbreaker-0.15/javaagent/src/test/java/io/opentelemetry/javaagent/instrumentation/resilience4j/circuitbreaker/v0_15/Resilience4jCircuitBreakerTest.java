/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15.ExperimentalTestHelper.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
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
  void createsCircuitBreakerSpanWhenOnSuccessCalledDirectly() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          circuitBreaker.onSuccess(1);
        });

    assertCircuitBreakerSpan("closed", "success");
  }

  @Test
  void createsCircuitBreakerSpanWhenOnResultMatchesRecordResultPredicate() throws Exception {
    Method recordResultPredicate = recordResultPredicateMethod();
    Method onResult = onResultMethod();
    CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
    recordResultPredicate.invoke(
        builder, (Predicate<Object>) result -> Integer.valueOf(500).equals(result));
    CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", builder.build());

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          onResult.invoke(circuitBreaker, 1L, MILLISECONDS, 500);
        });

    assertCircuitBreakerSpan("closed", "failure", null);
  }

  @Test
  void createsCircuitBreakerSpanWhenOnErrorCalledDirectly() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    IllegalStateException exception = new IllegalStateException("boom");

    testing.runWithSpan(
        "parent",
        () -> {
          circuitBreaker.acquirePermission();
          circuitBreaker.onError(1, exception);
        });

    assertCircuitBreakerSpan("closed", "failure", exception);
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
  void checkedProxyObjectMethodsDoNotCreateCircuitBreakerSpans() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    Object checkedSupplier = CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> "ok");

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(checkedSupplier.equals(checkedSupplier)).isTrue();
          assertThat(checkedSupplier.hashCode())
              .isEqualTo(System.identityHashCode(checkedSupplier));
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

  private static Method recordResultPredicateMethod() throws NoSuchMethodException {
    try {
      return CircuitBreakerConfig.Builder.class.getMethod("recordResultPredicate", Predicate.class);
    } catch (NoSuchMethodException e) {
      assumeTrue(false, "recordResultPredicate is not available in this Resilience4j version");
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
                              stringKey("resilience.policy.name"),
                              experimental("test-circuit-breaker")),
                          equalTo(
                              stringKey("resilience.circuit_breaker.state"), experimental(state)),
                          equalTo(
                              stringKey("resilience.circuit_breaker.outcome"),
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
