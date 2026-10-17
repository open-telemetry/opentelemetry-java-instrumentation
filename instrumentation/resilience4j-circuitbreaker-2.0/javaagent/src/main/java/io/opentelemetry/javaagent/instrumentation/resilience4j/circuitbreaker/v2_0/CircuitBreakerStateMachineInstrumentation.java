/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CircuitBreakerStateMachineInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.github.resilience4j.circuitbreaker.internal.CircuitBreakerStateMachine");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("acquirePermission").and(takesArguments(0)),
        getClass().getName() + "$AcquirePermissionAdvice");
    transformer.applyAdviceToMethod(
        named("tryAcquirePermission").and(takesArguments(0)),
        getClass().getName() + "$TryAcquirePermissionAdvice");
    transformer.applyAdviceToMethod(
        named("onResult").and(takesArguments(3)), getClass().getName() + "$OnResultAdvice");
    transformer.applyAdviceToMethod(
        named("publishCircuitErrorEvent").and(takesArguments(4)),
        getClass().getName() + "$PublishCircuitErrorEventAdvice");
  }

  @SuppressWarnings("unused")
  public static class AcquirePermissionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Resilience4jCircuitBreakerSpans.AttemptToken onEnter(
        @Advice.This CircuitBreaker circuitBreaker) {
      return Resilience4jCircuitBreakerSpans.beginAcquisition(circuitBreaker);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker,
        @Advice.Enter @Nullable Resilience4jCircuitBreakerSpans.AttemptToken token,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (throwable == null) {
        Resilience4jCircuitBreakerSpans.start(circuitBreaker, token);
      } else {
        Resilience4jCircuitBreakerSpans.reject(circuitBreaker, token, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class TryAcquirePermissionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Resilience4jCircuitBreakerSpans.AttemptToken onEnter(
        @Advice.This CircuitBreaker circuitBreaker) {
      return Resilience4jCircuitBreakerSpans.beginAcquisition(circuitBreaker);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker,
        @Advice.Enter @Nullable Resilience4jCircuitBreakerSpans.AttemptToken token,
        @Advice.Return boolean permitted,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (throwable == null) {
        if (permitted) {
          Resilience4jCircuitBreakerSpans.start(circuitBreaker, token);
        } else {
          Resilience4jCircuitBreakerSpans.reject(circuitBreaker, token, null);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class PublishCircuitErrorEventAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker,
        @Advice.Argument(3) @Nullable Throwable throwable) {
      Resilience4jCircuitBreakerSpans.endIfResultRecordedAsFailure(circuitBreaker, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class OnResultAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This CircuitBreaker circuitBreaker) {
      Resilience4jCircuitBreakerSpans.enterOnResult(circuitBreaker);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit() {
      Resilience4jCircuitBreakerSpans.endOnResult();
    }
  }
}
