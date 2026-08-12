/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
        isMethod()
            .and(namedOneOf("acquirePermission", "tryAcquirePermission"))
            .and(takesArguments(0)),
        getClass().getName() + "$CircuitBreakerAdvice");
  }

  @SuppressWarnings("unused")
  public static class CircuitBreakerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This CircuitBreaker circuitBreaker) {
      Resilience4jCircuitBreakerSpanAttributes.set(circuitBreaker);
    }
  }
}
