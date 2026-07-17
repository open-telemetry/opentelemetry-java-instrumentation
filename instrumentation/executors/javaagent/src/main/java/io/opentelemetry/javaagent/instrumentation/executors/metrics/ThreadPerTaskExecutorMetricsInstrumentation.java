/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors.metrics;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.methodIsDeclaredByType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.executors.ThreadPoolExecutorMetrics;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ThreadPerTaskExecutorMetricsInstrumentation implements TypeInstrumentation {

  private static final String THREAD_PER_TASK_EXECUTOR =
      "java.util.concurrent.ThreadPerTaskExecutor";

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(THREAD_PER_TASK_EXECUTOR);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(1)), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("shutdown", "shutdownNow", "close")
            .and(takesArguments(0))
            .and(methodIsDeclaredByType(named(THREAD_PER_TASK_EXECUTOR))),
        getClass().getName() + "$ShutdownAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This Executor executor,
        @Advice.Argument(0) ThreadFactory threadFactory,
        @Advice.FieldValue("threads") Set<Thread> threads) {
      ThreadPoolExecutorMetrics.register(executor, threadFactory, threads);
    }
  }

  @SuppressWarnings("unused")
  public static class ShutdownAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This Executor executor) {
      ThreadPoolExecutorMetrics.unregister(executor);
    }
  }
}
