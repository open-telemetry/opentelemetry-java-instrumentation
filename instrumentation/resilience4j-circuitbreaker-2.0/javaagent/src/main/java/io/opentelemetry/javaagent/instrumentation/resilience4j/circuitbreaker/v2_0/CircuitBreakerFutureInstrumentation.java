/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.resilience4j.core.functions.OnceConsumer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CircuitBreakerFutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.github.resilience4j.circuitbreaker.CircuitBreaker$CircuitBreakerFuture",
        "io.github.resilience4j.core.functions.OnceConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("get").and(takesArguments(0).or(takesArguments(long.class, TimeUnit.class))),
        getClass().getName() + "$GetAdvice");
    transformer.applyAdviceToMethod(
        named("applyOnce").and(takesArguments(Consumer.class)),
        getClass().getName() + "$ApplyOnceAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Resilience4jCircuitBreakerSpans.AttachedPendingSpan onEnter(
        @Advice.This Future<?> future,
        @Advice.FieldValue("onceToCircuitbreaker") OnceConsumer<?> onceConsumer) {
      return Resilience4jCircuitBreakerSpans.enterFutureGet(future, onceConsumer);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable Resilience4jCircuitBreakerSpans.AttachedPendingSpan attachment) {
      if (attachment != null) {
        attachment.clearOnceConsumer();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ApplyOnceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @AssignReturned.ToArguments(@ToArgument(0))
    public static <T> Consumer<T> onEnter(
        @Advice.This OnceConsumer<T> onceConsumer, @Advice.Argument(0) Consumer<T> consumer) {
      return Resilience4jCircuitBreakerSpans.wrapFutureConsumer(onceConsumer, consumer);
    }
  }
}
