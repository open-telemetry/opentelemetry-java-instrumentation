/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RedissonCompletableFutureWrapperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.misc.CompletableFutureWrapper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // used since 3.16.8
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, CompletionStage.class)),
        this.getClass().getName() + "$WrapCompletionStageAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, CompletableFuture.class)),
        this.getClass().getName() + "$WrapCompletableFutureAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapCompletableFutureAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) CompletableFuture<?> completableFuture) {
      completableFuture = CompletableFutureWrapper.wrapContext(completableFuture);
    }
  }

  @SuppressWarnings("unused")
  public static class WrapCompletionStageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) CompletionStage<?> completionStage) {
      completionStage = CompletableFutureWrapper.wrapContext(completionStage.toCompletableFuture());
    }
  }
}
