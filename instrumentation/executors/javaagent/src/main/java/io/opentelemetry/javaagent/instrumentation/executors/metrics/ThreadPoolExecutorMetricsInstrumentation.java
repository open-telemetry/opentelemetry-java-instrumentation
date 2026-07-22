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

import io.opentelemetry.javaagent.bootstrap.executors.ThreadPoolExecutorMetrics;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
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
        named("setThreadFactory")
            .and(takesArgument(0, ThreadFactory.class))
            .and(takesArguments(1))
            .and(methodIsDeclaredByType(named(ThreadPoolExecutor.class.getName()))),
        getClass().getName() + "$SetThreadFactoryAdvice");
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
    public static void onExit(@Advice.This ThreadPoolExecutor executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor)) {
        ThreadPoolExecutorMetrics.preRegister(executor);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetThreadFactoryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This ThreadPoolExecutor executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor)) {
        ThreadPoolExecutorMetrics.onThreadFactoryChanged(executor);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RunWorkerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ThreadPoolExecutor executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor)) {
        ThreadPoolExecutorMetrics.onWorkerThreadStarted(executor, Thread.currentThread());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RejectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ThreadPoolExecutor executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor)) {
        ThreadPoolExecutorMetrics.recordRejectedTask(executor);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ShutdownAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This ExecutorService executor) {
      if (!(executor instanceof ScheduledThreadPoolExecutor) && executor.isShutdown()) {
        ThreadPoolExecutorMetrics.unregister(executor);
      }
    }
  }
}
