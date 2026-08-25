/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
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
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "io.github.resilience4j.circuitbreaker.internal.CircuitBreakerStateMachine");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.github.resilience4j.circuitbreaker.internal.CircuitBreakerStateMachine");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("acquirePermission")).and(takesArguments(0)),
        getClass().getName() + "$AcquirePermissionAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("tryAcquirePermission")).and(takesArguments(0)),
        getClass().getName() + "$TryAcquirePermissionAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("releasePermission")).and(takesArguments(0)),
        getClass().getName() + "$ReleasePermissionAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("onSuccess")).and(takesArguments(1).or(takesArguments(2))),
        getClass().getName() + "$OnSuccessAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("onError")).and(takesArguments(2)),
        getClass().getName() + "$OnErrorAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("onError")).and(takesArguments(3)),
        getClass().getName() + "$NewOnErrorAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("onResult")).and(takesArguments(3)),
        getClass().getName() + "$OnResultAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("publishCircuitErrorEvent")).and(takesArguments(4)),
        getClass().getName() + "$PublishCircuitErrorEventAdvice");
  }

  @SuppressWarnings("unused")
  public static class AcquirePermissionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker, @Advice.Thrown @Nullable Throwable throwable) {
      if (throwable == null) {
        Resilience4jCircuitBreakerSpans.start(circuitBreaker);
      } else {
        Resilience4jCircuitBreakerSpans.reject(circuitBreaker, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class TryAcquirePermissionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker, @Advice.Return boolean permitted) {
      if (permitted) {
        Resilience4jCircuitBreakerSpans.start(circuitBreaker);
      } else {
        Resilience4jCircuitBreakerSpans.reject(circuitBreaker, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ReleasePermissionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This CircuitBreaker circuitBreaker) {
      if (!Resilience4jCircuitBreakerSpans.isInCircuitBreakerCallback(circuitBreaker)) {
        Resilience4jCircuitBreakerSpans.end(circuitBreaker, "cancelled", null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnSuccessAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This CircuitBreaker circuitBreaker) {
      Resilience4jCircuitBreakerSpans.end(circuitBreaker, "success", null);
    }
  }

  @SuppressWarnings("unused")
  public static class OnErrorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This CircuitBreaker circuitBreaker) {
      Resilience4jCircuitBreakerSpans.enterCircuitBreakerCallback(circuitBreaker);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker, @Advice.Argument(1) Throwable throwable) {
      Resilience4jCircuitBreakerSpans.exitCircuitBreakerCallback(circuitBreaker);
      Resilience4jCircuitBreakerSpans.end(circuitBreaker, "failure", throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class NewOnErrorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This CircuitBreaker circuitBreaker) {
      Resilience4jCircuitBreakerSpans.enterCircuitBreakerCallback(circuitBreaker);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker, @Advice.Argument(2) Throwable throwable) {
      Resilience4jCircuitBreakerSpans.exitCircuitBreakerCallback(circuitBreaker);
      Resilience4jCircuitBreakerSpans.end(circuitBreaker, "failure", throwable);
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
    public static void onEnter() {
      Resilience4jCircuitBreakerSpans.enterOnResult();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This CircuitBreaker circuitBreaker,
        @Advice.Argument(2) @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable) {
      boolean ended = Resilience4jCircuitBreakerSpans.exitOnResult();
      if (!ended) {
        Resilience4jCircuitBreakerSpans.endResult(circuitBreaker, result, throwable);
      }
    }
  }
}
