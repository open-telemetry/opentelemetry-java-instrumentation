/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors.metrics;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.methodIsDeclaredByType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.executors.metrics.ExecutorMetricsRegistry;
import io.opentelemetry.javaagent.bootstrap.executors.metrics.JdkExecutorMetrics;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nullable;
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
        named("start").and(takesArgument(0, Thread.class)).and(takesArguments(1)),
        getClass().getName() + "$StartAdvice");
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
        @Advice.FieldValue(value = "factory", readOnly = false) ThreadFactory threadFactory,
        @Advice.FieldValue("threads") Set<Thread> threads) {
      threadFactory =
          ExecutorMetricsRegistry.preRegister(
              executor, threadFactory, threads, JdkExecutorMetrics.DEFAULT_NAME_NORMALIZATION);
    }
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This Executor executor,
        @Advice.FieldValue("factory") ThreadFactory threadFactory,
        @Advice.Argument(0) @Nullable Thread thread) {
      JdkExecutorMetrics.onWorkerThreadStarted(
          executor, threadFactory, thread == null ? null : thread.getName());
    }
  }

  @SuppressWarnings("unused")
  public static class ShutdownAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This ExecutorService executor,
        @Advice.FieldValue("factory") ThreadFactory threadFactory) {
      if (executor.isShutdown()) {
        ExecutorMetricsRegistry.unregister(executor, threadFactory);
      }
    }
  }
}
