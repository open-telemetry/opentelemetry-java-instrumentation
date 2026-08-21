/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CircuitBreakerDecoratorsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.github.resilience4j.circuitbreaker.CircuitBreaker");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.github.resilience4j.circuitbreaker.CircuitBreaker");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("decorateSupplier")),
        getClass().getName() + "$SupplierAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("decorateCallable")),
        getClass().getName() + "$CallableAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("decorateRunnable")),
        getClass().getName() + "$RunnableAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("decorateCompletionStage")),
        getClass().getName() + "$CompletionStageSupplierAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("decorateFunction")),
        getClass().getName() + "$FunctionAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("decorateConsumer")),
        getClass().getName() + "$ConsumerAdvice");
  }

  @SuppressWarnings("unused")
  public static class SupplierAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Supplier<T> onExit(@Advice.Return Supplier<T> result) {
      return Resilience4jCircuitBreakerDecorators.wrapSupplier(result);
    }
  }

  @SuppressWarnings("unused")
  public static class CallableAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Callable<T> onExit(@Advice.Return Callable<T> result) {
      return Resilience4jCircuitBreakerDecorators.wrapCallable(result);
    }
  }

  @SuppressWarnings("unused")
  public static class RunnableAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Runnable onExit(@Advice.Return Runnable result) {
      return Resilience4jCircuitBreakerDecorators.wrapRunnable(result);
    }
  }

  @SuppressWarnings("unused")
  public static class CompletionStageSupplierAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Supplier<CompletionStage<T>> onExit(
        @Advice.Return Supplier<CompletionStage<T>> result) {
      return Resilience4jCircuitBreakerDecorators.wrapCompletionStageSupplier(result);
    }
  }

  @SuppressWarnings("unused")
  public static class FunctionAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T, R> Function<T, R> onExit(@Advice.Return Function<T, R> result) {
      return Resilience4jCircuitBreakerDecorators.wrapFunction(result);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Consumer<T> onExit(@Advice.Return Consumer<T> result) {
      return Resilience4jCircuitBreakerDecorators.wrapConsumer(result);
    }
  }
}
