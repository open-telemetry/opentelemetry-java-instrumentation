/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors.metrics;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ThreadPoolExecutorMetricsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named(ThreadPoolExecutor.class.getName()));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(7))
            .and(methodIsDeclaredByType(named(ThreadPoolExecutor.class.getName()))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("runWorker")
            .and(takesArguments(1))
            .and(methodIsDeclaredByType(named(ThreadPoolExecutor.class.getName()))),
        getClass().getName() + "$RunWorkerAdvice");
    transformer.applyAdviceToMethod(
        named("reject")
            .and(takesArgument(0, Runnable.class))
            .and(takesArguments(1))
            .and(methodIsDeclaredByType(named(ThreadPoolExecutor.class.getName()))),
        getClass().getName() + "$RejectAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("shutdown", "shutdownNow").and(takesArguments(0)),
        getClass().getName() + "$ShutdownAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ThreadPoolExecutor executor,
        @Advice.Argument(4) BlockingQueue<Runnable> queue) {
      if (!(executor instanceof ScheduledThreadPoolExecutor)) {
        long queueCapacity = (long) queue.size() + queue.remainingCapacity();
        ExecutorMetricsRegistry.preRegister(
            executor, queueCapacity, JdkExecutorMetrics.DEFAULT_NAME_NORMALIZATION);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RunWorkerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ThreadPoolExecutor executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor)) {
        JdkExecutorMetrics.onWorkerThreadStarted(executor, Thread.currentThread().getName());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RejectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ThreadPoolExecutor executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor)) {
        ExecutorMetricsRegistry.recordRejectedTask(executor);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ShutdownAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This ExecutorService executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor) && executor.isShutdown()) {
        ExecutorMetricsRegistry.unregister(executor);
      }
    }
  }
}
