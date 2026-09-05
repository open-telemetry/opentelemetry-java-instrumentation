/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CircuitBreakerDecoratorsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.github.resilience4j.circuitbreaker.CircuitBreaker");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isStatic().and(named("decorateSupplier")), getClass().getName() + "$SupplierAdvice");
    transformer.applyAdviceToMethod(
        isStatic().and(named("decorateCallable")), getClass().getName() + "$CallableAdvice");
    transformer.applyAdviceToMethod(
        isStatic().and(named("decorateRunnable")), getClass().getName() + "$RunnableAdvice");
    transformer.applyAdviceToMethod(
        isStatic().and(named("decorateCompletionStage")),
        getClass().getName() + "$CompletionStageSupplierAdvice");
    transformer.applyAdviceToMethod(
        isStatic().and(named("decorateFuture")), getClass().getName() + "$FutureSupplierAdvice");
    transformer.applyAdviceToMethod(
        isStatic().and(named("decorateFunction")), getClass().getName() + "$FunctionAdvice");
    transformer.applyAdviceToMethod(
        isStatic().and(named("decorateConsumer")), getClass().getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        isStatic()
            .and(
                named("decorateCheckedSupplier")
                    .or(named("decorateCheckedRunnable"))
                    .or(named("decorateCheckedFunction"))
                    .or(named("decorateCheckedConsumer"))),
        getClass().getName() + "$CheckedDecoratorAdvice");
  }

  @SuppressWarnings("unused")
  public static class SupplierAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Supplier<T> onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker, @Advice.Return Supplier<T> result) {
      return Resilience4jCircuitBreakerDecorators.wrapSupplier(circuitBreaker, result);
    }
  }

  @SuppressWarnings("unused")
  public static class CallableAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Callable<T> onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker, @Advice.Return Callable<T> result) {
      return Resilience4jCircuitBreakerDecorators.wrapCallable(circuitBreaker, result);
    }
  }

  @SuppressWarnings("unused")
  public static class RunnableAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Runnable onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker, @Advice.Return Runnable result) {
      return Resilience4jCircuitBreakerDecorators.wrapRunnable(circuitBreaker, result);
    }
  }

  @SuppressWarnings("unused")
  public static class CompletionStageSupplierAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @AssignReturned.ToArguments(@ToArgument(1))
    public static <T> Supplier<CompletionStage<T>> onEnter(
        @Advice.Argument(0) CircuitBreaker circuitBreaker,
        @Advice.Argument(1) Supplier<CompletionStage<T>> supplier) {
      return Resilience4jCircuitBreakerDecorators.wrapCompletionStageSupplier(
          circuitBreaker, supplier);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Supplier<CompletionStage<T>> onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker,
        @Advice.Return Supplier<CompletionStage<T>> result) {
      return Resilience4jCircuitBreakerDecorators.wrapCompletionStageDecoratedSupplier(
          circuitBreaker, result);
    }
  }

  @SuppressWarnings("unused")
  public static class FutureSupplierAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Supplier<Future<T>> onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker,
        @Advice.Return Supplier<Future<T>> result) {
      return Resilience4jCircuitBreakerDecorators.wrapFutureSupplier(circuitBreaker, result);
    }
  }

  @SuppressWarnings("unused")
  public static class FunctionAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T, R> Function<T, R> onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker, @Advice.Return Function<T, R> result) {
      return Resilience4jCircuitBreakerDecorators.wrapFunction(circuitBreaker, result);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> Consumer<T> onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker, @Advice.Return Consumer<T> result) {
      return Resilience4jCircuitBreakerDecorators.wrapConsumer(circuitBreaker, result);
    }
  }

  @SuppressWarnings("unused")
  public static class CheckedDecoratorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @AssignReturned.ToArguments(@ToArgument(1))
    public static Object onEnter(
        @Advice.Argument(0) CircuitBreaker circuitBreaker, @Advice.Argument(1) Object delegate) {
      return Resilience4jCircuitBreakerDecorators.wrapCheckedDelegate(circuitBreaker, delegate);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Object onExit(
        @Advice.Argument(0) CircuitBreaker circuitBreaker, @Advice.Return Object result) {
      return Resilience4jCircuitBreakerDecorators.wrapChecked(circuitBreaker, result);
    }
  }
}
